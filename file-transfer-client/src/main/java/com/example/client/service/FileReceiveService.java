package com.example.client.service;

import com.example.common.message.FileMetadata;
import com.example.common.message.TransferMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileReceiveService {
    private static final Logger logger = LoggerFactory.getLogger(FileReceiveService.class);

    private final String savePath;
    private final Map<String, FileWriteContext> fileContexts = new ConcurrentHashMap<>();
    private final Map<String, FileMetadata> fileMetadataMap = new ConcurrentHashMap<>();

    public FileReceiveService(com.example.client.config.ClientConfig clientConfig) {
        this.savePath = clientConfig.getFileSavePath();
    }

    @PostConstruct
    public void init() {
        Path path = Paths.get(savePath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("Created file save directory: {}", path);
            } catch (IOException e) {
                logger.error("Failed to create file save directory", e);
            }
        }
    }

    public void processMetadata(TransferMessage msg) {
        FileMetadata metadata = msg.getFileMetadata();
        if (metadata != null && metadata.getFileName() != null) {
            fileMetadataMap.put(metadata.getFileName(), metadata);
            logger.info("Received metadata for file: {}, size: {}, MD5: {}", 
                metadata.getFileName(), metadata.getFileSize(), metadata.getMd5());
        }
    }

    public boolean processFileChunk(TransferMessage msg) {
        String fileName = msg.getFileName();
        FileMetadata metadata = fileMetadataMap.getOrDefault(fileName, null);
        
        FileWriteContext context = fileContexts.computeIfAbsent(fileName, 
            k -> new FileWriteContext(k, msg.getTotalChunks(), msg.getFileLastModified(), metadata));

        return context.writeChunk(msg.getChunkIndex(), msg.getData());
    }

    public void completeFile(String fileName) {
        FileWriteContext context = fileContexts.remove(fileName);
        if (context != null) {
            boolean success = context.close();
            
            FileMetadata metadata = fileMetadataMap.remove(fileName);
            if (metadata != null && success) {
                validateFileIntegrity(fileName, metadata);
            }
            
            logger.info("File transfer completed: {}, success: {}", fileName, success);
        }
    }

    private void validateFileIntegrity(String fileName, FileMetadata metadata) {
        if (metadata.getMd5() == null || metadata.getMd5().isEmpty()) {
            logger.debug("No MD5 checksum provided for file: {}", fileName);
            return;
        }

        File savedFile = new File(savePath, fileName);
        if (!savedFile.exists()) {
            logger.error("File not found for integrity check: {}", fileName);
            return;
        }

        String calculatedMd5 = calculateFileMD5(savedFile);
        if (calculatedMd5 != null && calculatedMd5.equalsIgnoreCase(metadata.getMd5())) {
            logger.info("Integrity check passed for file: {}, MD5: {}", fileName, metadata.getMd5());
        } else {
            logger.error("Integrity check FAILED for file: {}. Expected MD5: {}, Actual MD5: {}", 
                fileName, metadata.getMd5(), calculatedMd5);
            savedFile.delete();
        }
    }

    private String calculateFileMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Failed to calculate MD5 for file: {}", file.getName(), e);
            return null;
        }
    }

    public void reset() {
        for (FileWriteContext context : fileContexts.values()) {
            context.close();
        }
        fileContexts.clear();
        fileMetadataMap.clear();
        logger.info("File receive service reset");
    }

    public boolean hasMetadata(String fileName) {
        return fileMetadataMap.containsKey(fileName);
    }

    public FileMetadata getMetadata(String fileName) {
        return fileMetadataMap.get(fileName);
    }

    private class FileWriteContext {
        private final RandomAccessFile raf;
        private final String fileName;
        private final int totalChunks;
        private final long expectedFileSize;
        private final Map<Integer, Boolean> receivedChunks = new ConcurrentHashMap<>();
        private int receivedCount = 0;

        public FileWriteContext(String fileName, int totalChunks, long fileLastModified, FileMetadata metadata) {
            this.fileName = fileName;
            this.totalChunks = totalChunks;
            this.expectedFileSize = metadata != null ? metadata.getFileSize() : -1;

            for (int i = 0; i < totalChunks; i++) {
                receivedChunks.put(i, false);
            }

            File outputFile = new File(savePath, fileName);
            try {
                if (outputFile.exists() && outputFile.lastModified() >= fileLastModified) {
                    logger.info("File already exists and is up to date: {}", fileName);
                    this.raf = null;
                    receivedCount = totalChunks;
                } else {
                    this.raf = new RandomAccessFile(outputFile, "rw");
                    if (metadata != null && metadata.getFileSize() > 0) {
                        this.raf.setLength(metadata.getFileSize());
                    }
                    logger.info("Created file for writing: {}, expected size: {}", fileName, expectedFileSize);
                }
            } catch (IOException e) {
                logger.error("Failed to create output file: {}", fileName, e);
                throw new RuntimeException(e);
            }
        }

        public synchronized boolean writeChunk(int chunkIndex, byte[] data) {
            if (raf == null) {
                receivedChunks.put(chunkIndex, true);
                receivedCount++;
                return true;
            }

            if (receivedChunks.getOrDefault(chunkIndex, false)) {
                logger.debug("Chunk {} already received for file: {}", chunkIndex, fileName);
                return true;
            }

            try {
                ChunkParams params = new ChunkParams();
                int chunkSize = params.getChunkSize() > 0 ? params.getChunkSize() : 1024 * 1024;
                long position = (long) chunkIndex * chunkSize;
                raf.seek(position);
                raf.write(data);
                receivedChunks.put(chunkIndex, true);
                receivedCount++;

                logger.debug("Wrote chunk {} of file {}, progress: {}/{}", 
                    chunkIndex, fileName, receivedCount, totalChunks);

                return true;
            } catch (IOException e) {
                logger.error("Failed to write chunk {} of file {}", chunkIndex, fileName, e);
                return false;
            }
        }

        public synchronized boolean close() {
            boolean allChunksReceived = receivedCount == totalChunks;
            
            if (raf != null) {
                try {
                    raf.close();
                    logger.info("Closed file: {}", fileName);
                } catch (IOException e) {
                    logger.error("Failed to close file: {}", fileName, e);
                    return false;
                }
            }

            if (!allChunksReceived) {
                logger.warn("File transfer incomplete for {}: received {}/{} chunks", 
                    fileName, receivedCount, totalChunks);
                File file = new File(savePath, fileName);
                if (file.exists()) {
                    file.delete();
                    logger.info("Deleted incomplete file: {}", fileName);
                }
                return false;
            }

            if (expectedFileSize > 0) {
                File file = new File(savePath, fileName);
                if (file.length() != expectedFileSize) {
                    logger.warn("File size mismatch for {}: expected {}, actual {}", 
                        fileName, expectedFileSize, file.length());
                }
            }

            return true;
        }
    }

    private static class ChunkParams {
        private int chunkSize = 1024 * 1024;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }
    }
}