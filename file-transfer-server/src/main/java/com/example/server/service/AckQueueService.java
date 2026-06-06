package com.example.server.service;

import com.example.server.model.AckRecord;
import com.example.server.model.AckRecord.AckStatus;
import com.example.server.model.FileTransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ack队列服务，管理文件传输的确认机制。
 * 负责跟踪文件发送后的确认状态，处理超时和重试逻辑。
 */
@Service
public class AckQueueService {
    private static final Logger logger = LoggerFactory.getLogger(AckQueueService.class);

    /** 默认超时时间（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    
    /** 默认重试间隔（毫秒） */
    private static final long DEFAULT_RETRY_INTERVAL_MS = 5000;
    
    /** 默认时间轮tick间隔（毫秒） */
    private static final long TIME_WHEEL_TICK_MS = 1000;

    /** ack记录映射 */
    private final Map<String, AckRecord> ackRecords = new ConcurrentHashMap<>();
    
    /** 时间轮实例 */
    private TimeWheel timeWheel;
    
    /** 文件发送记录服务 */
    @Autowired
    private FileTransferRecordService fileTransferRecordService;
    
    /** 文件传输服务 */
    @Autowired
    private FileTransferService fileTransferService;

    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        // 创建时间轮，处理超时任务
        timeWheel = new TimeWheel(TIME_WHEEL_TICK_MS, this::handleExpiredTask);
        timeWheel.start();
        logger.info("AckQueueService initialized");
    }

    /**
     * 销毁
     */
    @PreDestroy
    public void destroy() {
        if (timeWheel != null) {
            timeWheel.stop();
        }
        ackRecords.clear();
        logger.info("AckQueueService destroyed");
    }

    /**
     * 添加ack记录到队列
     * 
     * @param channelId 通道ID
     * @param fileId 文件ID
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param fileMd5 文件MD5
     * @param timeoutMs 超时时间（毫秒）
     * @param maxRetryCount 最大重试次数
     */
    public void addAckRecord(String channelId, String fileId, String fileName, 
                             long fileSize, String fileMd5, long timeoutMs, int maxRetryCount) {
        AckRecord record = new AckRecord(channelId, fileId, fileName, 
                                         fileSize, fileMd5, timeoutMs, maxRetryCount);
        ackRecords.put(record.getId(), record);
        
        // 添加到时间轮等待超时
        scheduleTimeout(record);
        
        logger.info("Added ack record: {} -> {}", record.getId(), fileName);
    }

    /**
     * 添加ack记录（使用默认参数）
     */
    public void addAckRecord(String channelId, String fileId, String fileName, 
                             long fileSize, String fileMd5) {
        addAckRecord(channelId, fileId, fileName, fileSize, fileMd5, 
                     DEFAULT_TIMEOUT_MS, 3);
    }

    /**
     * 处理客户端ack
     * 
     * @param channelId 通道ID
     * @param fileId 文件ID
     * @return true表示处理成功，false表示记录不存在或已过期
     */
    public boolean handleAck(String channelId, String fileId) {
        String recordId = channelId + ":" + fileId;
        AckRecord record = ackRecords.get(recordId);
        
        if (record == null) {
            logger.warn("Ack record not found: {}", recordId);
            return false;
        }
        
        // 更新状态为已确认
        record.setStatus(AckStatus.ACKED);
        
        // 从时间轮中移除
        timeWheel.cancelTask(recordId);
        
        // 从ack队列中移除
        ackRecords.remove(recordId);
        
        // 创建文件去重记录（仅在成功确认后创建）
        createFileDuplicationRecord(record);
        
        logger.info("Ack received for file: {} ({})", record.getFileName(), recordId);
        
        return true;
    }

    /**
     * 创建文件去重记录
     */
    private void createFileDuplicationRecord(AckRecord ackRecord) {
        // 根据文件属性创建一个虚拟文件对象用于去重记录
        // 实际上应该使用真实的文件路径来创建记录
        // 这里简化处理，使用文件信息更新去重状态
        java.io.File dummyFile = new java.io.File(ackRecord.getFileName());
        fileTransferRecordService.updateStatus(dummyFile, FileTransferStatus.SENT);
        
        logger.debug("Created file duplication record for: {}", ackRecord.getFileName());
    }

    /**
     * 处理超时任务
     */
    private void handleExpiredTask(TimeWheel.TimerTask task) {
        String recordId = task.getId();
        AckRecord record = ackRecords.get(recordId);
        
        if (record == null) {
            return;
        }
        
        // 检查是否已经被确认
        if (record.getStatus() == AckStatus.ACKED) {
            ackRecords.remove(recordId);
            return;
        }
        
        logger.warn("Ack timeout for file: {} ({})", record.getFileName(), recordId);
        
        // 检查是否可以重试
        if (record.canRetry()) {
            // 更新状态为重试中
            record.setStatus(AckStatus.RETRYING);
            record.incrementRetryCount();
            
            logger.info("Scheduling retry for file: {} (attempt {}/{})", 
                       record.getFileName(), record.getRetryCount(), record.getMaxRetryCount());
            
            // 重新添加到时间轮等待重试
            scheduleTimeout(record);
            
            // 触发重试传输
            triggerRetry(record);
        } else {
            // 超过最大重试次数，标记为失败
            record.setStatus(AckStatus.FAILED);
            record.setLastError("Max retry attempts exceeded");
            
            // 从队列中移除
            ackRecords.remove(recordId);
            
            // 更新文件状态为失败
            java.io.File dummyFile = new java.io.File(record.getFileName());
            fileTransferRecordService.updateStatusWithError(dummyFile, 
                FileTransferStatus.FAILED, "Ack timeout after " + record.getMaxRetryCount() + " attempts");
            
            logger.error("Ack failed after {} retries: {}", 
                       record.getMaxRetryCount(), record.getFileName());
        }
    }

    /**
     * 触发重试传输
     */
    private void triggerRetry(AckRecord record) {
        // 这里应该调用文件传输服务重新发送文件
        // 实际实现中需要获取通道并重新发送
        logger.info("Triggering retry for file: {}", record.getFileName());
        
        // 调用FileTransferService进行重试（需要通道信息）
        // 简化处理：记录日志
    }

    /**
     * 安排超时任务
     */
    private void scheduleTimeout(AckRecord record) {
        // 创建定时任务
        TimeWheel.TimerTask task = new TimeWheel.TimerTask() {
            @Override
            public String getId() {
                return record.getId();
            }
            
            @Override
            public long getDelayMs() {
                return record.getTimeoutMs();
            }
            
            @Override
            public void run() {
                // 由时间轮回调处理
            }
        };
        
        // 添加到时间轮
        timeWheel.addTask(task);
    }

    /**
     * 获取ack记录
     */
    public AckRecord getAckRecord(String channelId, String fileId) {
        return ackRecords.get(channelId + ":" + fileId);
    }

    /**
     * 移除ack记录
     */
    public void removeAckRecord(String channelId, String fileId) {
        String recordId = channelId + ":" + fileId;
        timeWheel.cancelTask(recordId);
        ackRecords.remove(recordId);
    }

    /**
     * 获取当前等待ack的记录数量
     */
    public int getPendingCount() {
        return (int) ackRecords.values().stream()
            .filter(r -> r.getStatus() == AckStatus.WAITING || r.getStatus() == AckStatus.RETRYING)
            .count();
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        int waiting = 0, acked = 0, timeout = 0, retrying = 0, failed = 0;
        
        for (AckRecord record : ackRecords.values()) {
            switch (record.getStatus()) {
                case WAITING:
                    waiting++;
                    break;
                case ACKED:
                    acked++;
                    break;
                case TIMEOUT:
                    timeout++;
                    break;
                case RETRYING:
                    retrying++;
                    break;
                case FAILED:
                    failed++;
                    break;
            }
        }
        
        stats.put("total", ackRecords.size());
        stats.put("waiting", waiting);
        stats.put("acked", acked);
        stats.put("timeout", timeout);
        stats.put("retrying", retrying);
        stats.put("failed", failed);
        stats.put("timeWheelStatus", timeWheel != null ? timeWheel.getStatusInfo() : "stopped");
        
        return stats;
    }
}
