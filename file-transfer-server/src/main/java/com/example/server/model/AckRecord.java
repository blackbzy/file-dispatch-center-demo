package com.example.server.model;

import java.time.LocalDateTime;

/**
 * 确认记录类，用于跟踪文件传输的确认状态。
 */
public class AckRecord {
    
    /** 唯一标识（通道ID + 文件ID） */
    private String id;
    
    /** 通道ID */
    private String channelId;
    
    /** 文件ID */
    private String fileId;
    
    /** 文件名 */
    private String fileName;
    
    /** 文件大小 */
    private long fileSize;
    
    /** 文件MD5 */
    private String fileMd5;
    
    /** 发送时间 */
    private LocalDateTime sendTime;
    
    /** 超时时间 */
    private long timeoutMs;
    
    /** 重试次数 */
    private int retryCount;
    
    /** 最大重试次数 */
    private int maxRetryCount;
    
    /** 状态 */
    private AckStatus status;
    
    /** 最后错误信息 */
    private String lastError;

    /**
     * ack状态枚举
     */
    public enum AckStatus {
        /** 等待确认 */
        WAITING,
        /** 已确认 */
        ACKED,
        /** 超时 */
        TIMEOUT,
        /** 重试中 */
        RETRYING,
        /** 失败 */
        FAILED
    }

    public AckRecord(String channelId, String fileId, String fileName, 
                     long fileSize, String fileMd5, long timeoutMs, int maxRetryCount) {
        this.id = channelId + ":" + fileId;
        this.channelId = channelId;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileMd5 = fileMd5;
        this.sendTime = LocalDateTime.now();
        this.timeoutMs = timeoutMs;
        this.maxRetryCount = maxRetryCount;
        this.retryCount = 0;
        this.status = AckStatus.WAITING;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }

    public LocalDateTime getSendTime() { return sendTime; }
    public void setSendTime(LocalDateTime sendTime) { this.sendTime = sendTime; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public AckStatus getStatus() { return status; }
    public void setStatus(AckStatus status) { this.status = status; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    /**
     * 判断是否已超时
     */
    public boolean isTimeout() {
        if (status != AckStatus.WAITING && status != AckStatus.RETRYING) {
            return false;
        }
        long elapsedMs = java.time.Duration.between(sendTime, LocalDateTime.now()).toMillis();
        return elapsedMs >= timeoutMs;
    }

    /**
     * 判断是否可以重试
     */
    public boolean canRetry() {
        return retryCount < maxRetryCount;
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.sendTime = LocalDateTime.now();
    }
}
