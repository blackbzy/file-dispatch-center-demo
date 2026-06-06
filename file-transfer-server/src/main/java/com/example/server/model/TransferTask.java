package com.example.server.model;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 传输任务实体类
 * 支持优先级队列管理
 */
public class TransferTask implements Comparable<TransferTask> {
    
    /**
     * 任务优先级枚举
     */
    public enum Priority {
        HIGH(3),    // 高优先级
        MEDIUM(2),  // 中优先级
        LOW(1);     // 低优先级
        
        private final int value;
        
        Priority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static Priority fromValue(int value) {
            for (Priority p : values()) {
                if (p.value == value) {
                    return p;
                }
            }
            return MEDIUM;
        }
    }
    
    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        WAITING,      // 等待中
        EXECUTING,    // 执行中
        COMPLETED,    // 已完成
        FAILED,       // 失败
        CANCELLED,    // 已取消
        TIMEOUT       // 超时
    }
    
    /**
     * 任务ID（唯一标识）
     */
    private final String taskId;
    
    /**
     * 文件名
     */
    private final String fileName;
    
    /**
     * 文件路径
     */
    private final String filePath;
    
    /**
     * 文件大小（字节）
     */
    private final long fileSize;
    
    /**
     * 文件MD5
     */
    private final String fileMd5;
    
    /**
     * 目标通道ID
     */
    private final String channelId;
    
    /**
     * 任务优先级
     */
    private volatile Priority priority;
    
    /**
     * 任务状态
     */
    private volatile TaskStatus status;
    
    /**
     * 入队时间
     */
    private final LocalDateTime enqueueTime;
    
    /**
     * 开始执行时间
     */
    private volatile LocalDateTime startTime;
    
    /**
     * 完成时间
     */
    private volatile LocalDateTime completeTime;
    
    /**
     * 重试次数
     */
    private final AtomicInteger retryCount = new AtomicInteger(0);
    
    /**
     * 最大重试次数
     */
    private final int maxRetryCount;
    
    /**
     * 任务超时时间（毫秒）
     */
    private final long timeoutMs;
    
    /**
     * 失败原因
     */
    private volatile String failReason;
    
    /**
     * 业务扩展数据
     */
    private volatile Object extraData;

    public TransferTask(String taskId, String fileName, String filePath, long fileSize, 
                       String fileMd5, String channelId, Priority priority, 
                       int maxRetryCount, long timeoutMs) {
        this.taskId = taskId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileMd5 = fileMd5;
        this.channelId = channelId;
        this.priority = priority;
        this.status = TaskStatus.WAITING;
        this.enqueueTime = LocalDateTime.now();
        this.maxRetryCount = maxRetryCount;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 比较方法：优先级高的排在前面，相同优先级按入队时间排序（FIFO）
     */
    @Override
    public int compareTo(TransferTask other) {
        int priorityCompare = Integer.compare(other.priority.getValue(), this.priority.getValue());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return this.enqueueTime.compareTo(other.enqueueTime);
    }

    /**
     * 判断是否可以重试
     */
    public boolean canRetry() {
        return retryCount.get() < maxRetryCount && status != TaskStatus.CANCELLED;
    }

    /**
     * 增加重试次数
     */
    public int incrementRetry() {
        return retryCount.incrementAndGet();
    }

    /**
     * 判断任务是否超时
     */
    public boolean isTimeout() {
        if (startTime == null) {
            // 等待超时：从入队时间开始计算
            long waitMs = java.time.Duration.between(enqueueTime, LocalDateTime.now()).toMillis();
            return waitMs >= timeoutMs;
        }
        // 执行超时：从开始时间计算
        long elapsedMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        return elapsedMs >= timeoutMs;
    }

    /**
     * 获取等待时间（毫秒）
     */
    public long getWaitTimeMs() {
        LocalDateTime endTime = startTime != null ? startTime : LocalDateTime.now();
        return java.time.Duration.between(enqueueTime, endTime).toMillis();
    }

    /**
     * 获取执行时间（毫秒）
     */
    public long getExecutionTimeMs() {
        if (startTime == null) {
            return 0;
        }
        LocalDateTime endTime = completeTime != null ? completeTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, endTime).toMillis();
    }

    /**
     * 标记任务开始执行
     */
    public void markExecuting() {
        this.status = TaskStatus.EXECUTING;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 标记任务完成
     */
    public void markCompleted() {
        this.status = TaskStatus.COMPLETED;
        this.completeTime = LocalDateTime.now();
    }

    /**
     * 标记任务失败
     */
    public void markFailed(String reason) {
        this.status = TaskStatus.FAILED;
        this.failReason = reason;
        this.completeTime = LocalDateTime.now();
    }

    /**
     * 标记任务取消
     */
    public void markCancelled() {
        this.status = TaskStatus.CANCELLED;
        this.completeTime = LocalDateTime.now();
    }

    /**
     * 标记任务超时
     */
    public void markTimeout() {
        this.status = TaskStatus.TIMEOUT;
        this.completeTime = LocalDateTime.now();
    }

    // Getters and Setters
    
    public String getTaskId() {
        return taskId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public String getChannelId() {
        return channelId;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public LocalDateTime getEnqueueTime() {
        return enqueueTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public String getFailReason() {
        return failReason;
    }

    public Object getExtraData() {
        return extraData;
    }

    public void setExtraData(Object extraData) {
        this.extraData = extraData;
    }

    @Override
    public String toString() {
        return "TransferTask{" +
                "taskId='" + taskId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", channelId='" + channelId + '\'' +
                ", priority=" + priority +
                ", status=" + status +
                ", retryCount=" + retryCount.get() +
                ", waitTimeMs=" + getWaitTimeMs() +
                '}';
    }
}