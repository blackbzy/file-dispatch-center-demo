package com.example.server.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件发送记录实体类。
 */
public class FileTransferRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 文件唯一标识符（基于路径+文件名+大小+修改时间的哈希值） */
    private String fileId;
    /** 文件路径 */
    private String filePath;
    /** 文件名称 */
    private String fileName;
    /** 文件大小 */
    private long fileSize;
    /** 最后修改时间 */
    private long lastModified;
    /** 发送状态 */
    private FileTransferStatus status;
    /** 已发送的块数 */
    private int sentChunks;
    /** 总块数 */
    private int totalChunks;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
    /** 失败重试次数 */
    private int retryCount;
    /** 最后失败的错误信息 */
    private String lastError;

    public FileTransferRecord(String fileId, String filePath, String fileName, 
                              long fileSize, long lastModified) {
        this.fileId = fileId;
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.lastModified = lastModified;
        this.status = FileTransferStatus.NOT_SENT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.retryCount = 0;
    }

    // Getters and Setters
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

    public FileTransferStatus getStatus() { return status; }
    public void setStatus(FileTransferStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public int getSentChunks() { return sentChunks; }
    public void setSentChunks(int sentChunks) { this.sentChunks = sentChunks; }

    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
