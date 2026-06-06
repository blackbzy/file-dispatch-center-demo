package com.example.server.service;

import com.example.common.message.ChunkParams;
import com.example.common.message.FileMetadata;
import com.example.common.message.TransferMessage;
import com.example.server.config.ServerConfig;
import com.example.server.model.FileTransferStatus;
import com.example.server.model.TransferTask;
import com.example.server.model.TransferTask.Priority;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FileTransferService {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferService.class);

    private final ServerConfig serverConfig;
    private final ChannelManager channelManager;
    private final FileTransferRecordService recordService;
    private final AckQueueService ackQueueService;
    private final TransferQueueService transferQueueService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private final Map<String, FileTransferTask> activeTransfers = new ConcurrentHashMap<>();

    public FileTransferService(ServerConfig serverConfig, ChannelManager channelManager, 
                              FileTransferRecordService recordService, AckQueueService ackQueueService,
                              TransferQueueService transferQueueService) {
        this.serverConfig = serverConfig;
        this.channelManager = channelManager;
        this.recordService = recordService;
        this.ackQueueService = ackQueueService;
        this.transferQueueService = transferQueueService;
    }

    @PostConstruct
    public void init() {
        Path path = Paths.get(serverConfig.getFileSourcePath());
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("Created file source directory: {}", path);
            } catch (IOException e) {
                logger.error("Failed to create file source directory", e);
            }
        }
    }

    public void processAck(Channel channel, String fileName, int chunkIndex) {
        String key = channel.id().asLongText() + ":" + fileName;
        FileTransferTask task = activeTransfers.get(key);
        if (task != null) {
            task.onAck(chunkIndex);
        }
    }

    public void distributeFileToAllClients(String fileName) {
        File file = new File(serverConfig.getFileSourcePath(), fileName);
        if (!file.exists()) {
            logger.error("File not found: {}", file.getAbsolutePath());
            return;
        }

        // 检查文件是否已在发送中或已成功发送
        if (!recordService.canSend(file)) {
            logger.info("Skipping file transfer: {} (status: {})", 
                       fileName, recordService.getRecord(file).getStatus());
            return;
        }

        int activeClientCount = channelManager.getActiveChannelCount();
        if (activeClientCount == 0) {
            logger.warn("No active clients connected, cannot distribute file: {}", fileName);
            return;
        }

        logger.info("Starting file distribution: {} to {} clients", fileName, activeClientCount);

        for (Map.Entry<String, Channel> entry : channelManager.getActiveChannels().entrySet()) {
            Channel channel = entry.getValue();
            if (channel.isActive()) {
                distributeFileToClient(channel, file);
            }
        }
    }

    /**
     * 使用优先级队列分发文件
     * 
     * @param fileName 文件名
     * @param priority 优先级
     * @return 任务ID
     */
    public String distributeFileWithQueue(String fileName, Priority priority) {
        File file = new File(serverConfig.getFileSourcePath(), fileName);
        if (!file.exists()) {
            logger.error("File not found: {}", file.getAbsolutePath());
            return null;
        }

        // 检查文件是否已在发送中或已成功发送
        if (!recordService.canSend(file)) {
            logger.info("Skipping file transfer: {} (status: {})", 
                       fileName, recordService.getRecord(file).getStatus());
            return null;
        }

        int activeClientCount = channelManager.getActiveChannelCount();
        if (activeClientCount == 0) {
            logger.warn("No active clients connected, cannot distribute file: {}", fileName);
            return null;
        }

        // 为每个客户端创建传输任务并加入队列
        String taskIdPrefix = TransferQueueService.generateTaskId();
        int taskCount = 0;

        for (Map.Entry<String, Channel> entry : channelManager.getActiveChannels().entrySet()) {
            Channel channel = entry.getValue();
            if (channel.isActive()) {
                String taskId = taskIdPrefix + "-" + taskCount;
                String fileMd5 = calculateMd5(file);
                
                TransferTask task = new TransferTask(
                    taskId,
                    fileName,
                    file.getAbsolutePath(),
                    file.length(),
                    fileMd5,
                    channel.id().asLongText(),
                    priority,
                    serverConfig.getDefaultMaxRetryCount(),
                    serverConfig.getQueueTimeoutMs()
                );
                
                TransferQueueService.EnqueueResult result = transferQueueService.enqueue(task);
                if (result == TransferQueueService.EnqueueResult.SUCCESS) {
                    taskCount++;
                    logger.info("Task enqueued: {} for file {} to channel {}", 
                               taskId, fileName, channel.id().asShortText());
                } else {
                    logger.warn("Task enqueue failed: {} with result {}", taskId, result);
                }
            }
        }

        logger.info("Created {} tasks for file {} with priority {}", taskCount, fileName, priority);
        return taskIdPrefix;
    }

    /**
     * 处理队列中的任务
     * 从队列中取出任务并执行文件传输
     */
    public void processQueueTask() {
        TransferTask task = transferQueueService.dequeue();
        if (task == null) {
            return;
        }

        executorService.submit(() -> {
            try {
                Channel channel = channelManager.getChannel(task.getChannelId());
                if (channel == null || !channel.isActive()) {
                    logger.warn("Channel not available for task: {}", task.getTaskId());
                    transferQueueService.markTaskComplete(task.getTaskId(), false, "Channel not available");
                    return;
                }

                File file = new File(task.getFilePath());
                if (!file.exists()) {
                    logger.error("File not found for task: {}", task.getTaskId());
                    transferQueueService.markTaskComplete(task.getTaskId(), false, "File not found");
                    return;
                }

                // 执行文件传输
                sendFileInternal(channel, file, task);
                
            } catch (Exception e) {
                logger.error("Error processing queue task: {}", task.getTaskId(), e);
                transferQueueService.markTaskComplete(task.getTaskId(), false, e.getMessage());
            }
        });
    }

    /**
     * 内部文件发送方法
     */
    private void sendFileInternal(Channel channel, File file, TransferTask task) {
        try {
            FileMetadata fileMetadata = createFileMetadata(file);
            ChunkParams chunkParams = new ChunkParams(serverConfig.getChunkSize(), 0, file.length());
            
            // 发送文件头
            TransferMessage headerMsg = TransferMessage.fileHeader(file.getName(), fileMetadata, chunkParams);
            channel.writeAndFlush(headerMsg);
            
            // 发送文件数据块
            byte[] buffer = new byte[serverConfig.getChunkSize()];
            FileInputStream fis = new FileInputStream(file);
            int chunkIndex = 0;
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);
                TransferMessage chunkMsg = TransferMessage.fileChunk(file.getName(), chunkIndex, chunkData);
                
                // 等待通道可写
                while (!channel.isWritable() && channel.isActive()) {
                    Thread.sleep(100);
                }
                
                if (channel.isActive()) {
                    channel.writeAndFlush(chunkMsg);
                    chunkIndex++;
                } else {
                    fis.close();
                    transferQueueService.markTaskComplete(task.getTaskId(), false, "Channel disconnected");
                    return;
                }
            }
            
            fis.close();
            
            // 发送结束信号
            TransferMessage endMsg = TransferMessage.endWithMetadata(file.getName(), fileMetadata);
            channel.writeAndFlush(endMsg);
            
            // 标记任务完成
            transferQueueService.markTaskComplete(task.getTaskId(), true, null);
            
            // 注册ack记录
            String channelId = channel.id().asLongText();
            String fileId = fileMetadata.getMd5() != null ? fileMetadata.getMd5() : file.getName();
            ackQueueService.addAckRecord(
                channelId,
                fileId,
                file.getName(),
                file.length(),
                fileMetadata.getMd5(),
                serverConfig.getRetryInterval() * 3,
                serverConfig.getRetryCount()
            );
            
            logger.info("File transfer completed: {} to channel {}", file.getName(), channel.id().asShortText());
            
        } catch (Exception e) {
            logger.error("Error sending file: {}", file.getName(), e);
            transferQueueService.markTaskComplete(task.getTaskId(), false, e.getMessage());
        }
    }

    /**
     * 计算文件MD5
     */
    private String calculateMd5(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            fis.close();
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error calculating MD5 for file: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 创建文件元数据
     */
    private FileMetadata createFileMetadata(File file) {
        String md5 = calculateMd5(file);
        return new FileMetadata(file.getName(), file.length(), md5, System.currentTimeMillis());
    }

    public void distributeFileToClient(Channel channel, File file) {
        executorService.submit(() -> {
            try {
                // 再次检查状态（因为可能多个客户端同时请求）
                if (!recordService.canSend(file)) {
                    logger.debug("Skipping file transfer to {}: {} (status: {})", 
                                channel.id(), file.getName(), 
                                recordService.getRecord(file).getStatus());
                    return;
                }

                String key = channel.id().asLongText() + ":" + file.getName();
                FileTransferTask task = new FileTransferTask(channel, file, serverConfig, recordService);
                activeTransfers.put(key, task);
                task.start();
                activeTransfers.remove(key);
            } catch (Exception e) {
                logger.error("Failed to transfer file: {}", file.getName(), e);
                recordService.updateStatusWithError(file, FileTransferStatus.FAILED, e.getMessage());
            }
        });
    }

    public void distributeFiles(List<String> fileNames) {
        for (String fileName : fileNames) {
            distributeFileToAllClients(fileName);
        }
    }

    public void distributeAllFiles() {
        File dir = new File(serverConfig.getFileSourcePath());
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    distributeFileToAllClients(file.getName());
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
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

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < name.length() - 1) {
            return name.substring(lastDotIndex + 1).toLowerCase();
        }
        return "unknown";
    }

    private class FileTransferTask {
        private final Channel channel;
        private final File file;
        private final ServerConfig config;
        private final FileTransferRecordService recordService;
        private final int chunkSize;
        private final int totalChunks;
        private final FileMetadata fileMetadata;
        private final Map<Integer, Boolean> ackedChunks = new ConcurrentHashMap<>();

        public FileTransferTask(Channel channel, File file, ServerConfig config, FileTransferRecordService recordService) {
            this.channel = channel;
            this.file = file;
            this.config = config;
            this.recordService = recordService;
            this.chunkSize = config.getChunkSize();
            this.totalChunks = (int) Math.ceil((double) file.length() / chunkSize);
            
            this.fileMetadata = new FileMetadata();
            this.fileMetadata.setFileName(file.getName());
            this.fileMetadata.setFileSize(file.length());
            this.fileMetadata.setFileType(getFileExtension(file));
            this.fileMetadata.setCreateTime(file.lastModified());
            this.fileMetadata.setLastModified(file.lastModified());
            this.fileMetadata.setMd5(calculateFileMD5(file));

            for (int i = 0; i < totalChunks; i++) {
                ackedChunks.put(i, false);
            }
        }

        public void start() throws IOException {
            logger.info("Starting transfer task for file: {}, total chunks: {}, MD5: {}", 
                file.getName(), totalChunks, fileMetadata.getMd5());
            
            // 更新状态为发送中
            recordService.updateStatus(file, FileTransferStatus.SENDING);
            recordService.updateProgress(file, 0, totalChunks);

            TransferMessage metadataMsg = TransferMessage.fileMetadata(fileMetadata);
            if (channel.isActive()) {
                channel.writeAndFlush(metadataMsg);
                logger.info("Sent metadata for file: {}", file.getName());
            }

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[chunkSize];
                int bytesRead;
                int chunkIndex = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunkData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                    ChunkParams chunkParams = new ChunkParams(
                        chunkIndex, totalChunks, chunkSize, (long) chunkIndex * chunkSize);
                    
                    TransferMessage msg = TransferMessage.fileChunkWithMetadata(
                        chunkData, chunkParams, fileMetadata);
                    
                    sendWithRetry(msg, chunkIndex);
                    
                    // 更新进度
                    recordService.updateProgress(file, chunkIndex + 1, totalChunks);
                    chunkIndex++;
                }

                waitForAllAcks();
                sendEndSignal();
                
                // 发送成功
                recordService.updateStatus(file, FileTransferStatus.SENT);
                logger.info("File transfer completed: {}", file.getName());
            } catch (Exception e) {
                logger.error("File transfer failed: {}", file.getName(), e);
                recordService.updateStatusWithError(file, FileTransferStatus.FAILED, e.getMessage());
                throw e;
            }
        }

        private void sendWithRetry(TransferMessage msg, int chunkIndex) {
            int retries = 0;
            while (retries <= config.getRetryCount()) {
                // 检查通道可写性
                while (!channel.isWritable() && channel.isActive()) {
                    logger.debug("Channel not writable, waiting... channel: {}", channel.id());
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                
                if (channel.isActive()) {
                    channel.writeAndFlush(msg);
                    logger.debug("Sent chunk {} of file {}, size: {}", 
                        chunkIndex, file.getName(), msg.getData().length);

                    if (waitForAck(chunkIndex)) {
                        return;
                    }
                } else {
                    logger.warn("Channel is inactive, stopping transfer");
                    return;
                }
                retries++;
                if (retries <= config.getRetryCount()) {
                    try {
                        Thread.sleep(config.getRetryInterval());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            logger.error("Failed to send chunk {} after {} retries", chunkIndex, config.getRetryCount());
        }

        private boolean waitForAck(int chunkIndex) {
            long startTime = System.currentTimeMillis();
            long timeout = config.getRetryInterval() * 2;

            while (System.currentTimeMillis() - startTime < timeout) {
                if (ackedChunks.getOrDefault(chunkIndex, false)) {
                    return true;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }

        public void onAck(int chunkIndex) {
            ackedChunks.put(chunkIndex, true);
        }

        private void waitForAllAcks() {
            for (int i = 0; i < totalChunks; i++) {
                while (!ackedChunks.getOrDefault(i, false)) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private void sendEndSignal() {
            if (channel.isActive()) {
                TransferMessage endMsg = TransferMessage.endWithMetadata(file.getName(), fileMetadata);
                channel.writeAndFlush(endMsg);
                logger.info("Sent END signal for file: {}, MD5: {}", file.getName(), fileMetadata.getMd5());
                
                // 在发送结束信号后注册 ack 记录
                registerAckRecord();
            }
        }

        /**
         * 注册 ack 记录，等待客户端确认
         */
        private void registerAckRecord() {
            String channelId = channel.id().asLongText();
            String fileId = fileMetadata.getMd5() != null ? fileMetadata.getMd5() : file.getName();
            
            ackQueueService.addAckRecord(
                channelId,
                fileId,
                file.getName(),
                file.length(),
                fileMetadata.getMd5(),
                config.getRetryInterval() * 3,  // 超时时间设为重试间隔的3倍
                config.getRetryCount()
            );
            
            logger.info("Registered ack record for file: {} (channel: {})", file.getName(), channelId);
        }
    }
}