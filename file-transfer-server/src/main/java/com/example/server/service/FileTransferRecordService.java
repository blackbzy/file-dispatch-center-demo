package com.example.server.service;

import com.example.server.model.FileTransferRecord;
import com.example.server.model.FileTransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件发送记录服务，用于记录和管理文件的发送状态。
 */
@Service
public class FileTransferRecordService {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferRecordService.class);

    /** 文件发送记录映射，键为文件唯一ID，值为发送记录 */
    private final Map<String, FileTransferRecord> records = new ConcurrentHashMap<>();

    /**
     * 生成文件唯一标识符。
     *
     * @param file 文件对象
     * @return 文件唯一标识符（MD5哈希值）
     */
    public String generateFileId(File file) {
        String input = file.getAbsolutePath() + ":" + file.getName() + ":" 
                     + file.length() + ":" + file.lastModified();
        return md5(input);
    }

    /**
     * 计算字符串的MD5值。
     *
     * @param input 输入字符串
     * @return MD5哈希值
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to generate MD5", e);
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 获取或创建文件发送记录。
     *
     * @param file 文件对象
     * @return 文件发送记录
     */
    public FileTransferRecord getOrCreateRecord(File file) {
        String fileId = generateFileId(file);
        return records.computeIfAbsent(fileId, id -> 
            new FileTransferRecord(id, file.getAbsolutePath(), file.getName(), 
                                   file.length(), file.lastModified()));
    }

    /**
     * 检查文件是否可以发送。
     *
     * @param file 文件对象
     * @return true表示可以发送，false表示不可以
     */
    public boolean canSend(File file) {
        FileTransferRecord record = getOrCreateRecord(file);
        FileTransferStatus status = record.getStatus();
        return status == FileTransferStatus.NOT_SENT || status == FileTransferStatus.FAILED;
    }

    /**
     * 更新文件发送状态。
     *
     * @param file   文件对象
     * @param status 新状态
     */
    public void updateStatus(File file, FileTransferStatus status) {
        FileTransferRecord record = getOrCreateRecord(file);
        record.setStatus(status);
        logger.debug("Updated file status: {} -> {}", file.getName(), status);
    }

    /**
     * 更新文件发送状态并记录错误信息。
     *
     * @param file    文件对象
     * @param status  新状态
     * @param error   错误信息
     */
    public void updateStatusWithError(File file, FileTransferStatus status, String error) {
        FileTransferRecord record = getOrCreateRecord(file);
        record.setStatus(status);
        record.setLastError(error);
        if (status == FileTransferStatus.FAILED) {
            record.incrementRetryCount();
        }
        logger.warn("Updated file status: {} -> {}, error: {}", file.getName(), status, error);
    }

    /**
     * 更新文件发送进度。
     *
     * @param file       文件对象
     * @param sentChunks 已发送块数
     * @param totalChunks 总块数
     */
    public void updateProgress(File file, int sentChunks, int totalChunks) {
        FileTransferRecord record = getOrCreateRecord(file);
        record.setSentChunks(sentChunks);
        record.setTotalChunks(totalChunks);
        record.setStatus(FileTransferStatus.SENDING);
    }

    /**
     * 重置文件发送状态，允许重新发送。
     *
     * @param file 文件对象
     */
    public void resetStatus(File file) {
        FileTransferRecord record = getOrCreateRecord(file);
        record.setStatus(FileTransferStatus.NOT_SENT);
        record.setLastError(null);
        logger.info("Reset file status: {}", file.getName());
    }

    /**
     * 获取文件发送记录。
     *
     * @param file 文件对象
     * @return 文件发送记录
     */
    public FileTransferRecord getRecord(File file) {
        return getOrCreateRecord(file);
    }

    /**
     * 获取所有记录的列表。
     *
     * @return 所有记录列表
     */
    public List<FileTransferRecord> getAllRecords() {
        return new ArrayList<>(records.values());
    }

    /**
     * 获取统计信息。
     *
     * @return 统计数据Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        int notSent = 0, sending = 0, sent = 0, failed = 0;
        
        for (FileTransferRecord record : records.values()) {
            switch (record.getStatus()) {
                case NOT_SENT:
                    notSent++;
                    break;
                case SENDING:
                    sending++;
                    break;
                case SENT:
                    sent++;
                    break;
                case FAILED:
                    failed++;
                    break;
            }
        }
        
        stats.put("total", records.size());
        stats.put("notSent", notSent);
        stats.put("sending", sending);
        stats.put("sent", sent);
        stats.put("failed", failed);
        
        return stats;
    }

    /**
     * 清除所有发送记录。
     */
    public void clearAll() {
        records.clear();
        logger.info("Cleared all file transfer records");
    }

    @Override
    public String toString() {
        return "FileTransferRecordService{" +
                "records=" + records.size() +
                '}';
    }
}
