package com.example.server.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 队列统计信息
 * 用于监控队列状态和性能指标
 */
public class QueueStatistics {

    /**
     * 各优先级队列长度
     */
    private final Map<TransferTask.Priority, AtomicLong> queueLengthByPriority = new ConcurrentHashMap<>();

    /**
     * 队列总长度
     */
    private final AtomicLong totalQueueLength = new AtomicLong(0);

    /**
     * 各状态任务数量
     */
    private final Map<TransferTask.TaskStatus, AtomicLong> taskCountByStatus = new ConcurrentHashMap<>();

    /**
     * 累计入队任务数
     */
    private final AtomicLong totalEnqueuedCount = new AtomicLong(0);

    /**
     * 累计出队任务数
     */
    private final AtomicLong totalDequeuedCount = new AtomicLong(0);

    /**
     * 累计完成任务数
     */
    private final AtomicLong totalCompletedCount = new AtomicLong(0);

    /**
     * 累计失败任务数
     */
    private final AtomicLong totalFailedCount = new AtomicLong(0);

    /**
     * 累计取消任务数
     */
    private final AtomicLong totalCancelledCount = new AtomicLong(0);

    /**
     * 累计超时任务数
     */
    private final AtomicLong totalTimeoutCount = new AtomicLong(0);

    /**
     * 队列峰值长度
     */
    private final AtomicLong peakQueueLength = new AtomicLong(0);

    /**
     * 累计等待时间（毫秒）
     */
    private final AtomicLong totalWaitTimeMs = new AtomicLong(0);

    /**
     * 累计执行时间（毫秒）
     */
    private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);

    /**
     * 统计开始时间
     */
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 最后一次入队时间
     */
    private volatile LocalDateTime lastEnqueueTime;

    /**
     * 最后一次出队时间
     */
    private volatile LocalDateTime lastDequeueTime;

    public QueueStatistics() {
        // 初始化各优先级计数器
        for (TransferTask.Priority priority : TransferTask.Priority.values()) {
            queueLengthByPriority.put(priority, new AtomicLong(0));
        }
        // 初始化各状态计数器
        for (TransferTask.TaskStatus status : TransferTask.TaskStatus.values()) {
            taskCountByStatus.put(status, new AtomicLong(0));
        }
    }

    /**
     * 记录任务入队
     */
    public void recordEnqueue(TransferTask.Priority priority) {
        totalQueueLength.incrementAndGet();
        queueLengthByPriority.get(priority).incrementAndGet();
        totalEnqueuedCount.incrementAndGet();
        taskCountByStatus.get(TransferTask.TaskStatus.WAITING).incrementAndGet();
        lastEnqueueTime = LocalDateTime.now();
        
        // 更新峰值
        long current = totalQueueLength.get();
        long peak;
        do {
            peak = peakQueueLength.get();
            if (current <= peak) {
                break;
            }
        } while (!peakQueueLength.compareAndSet(peak, current));
    }

    /**
     * 记录任务出队
     */
    public void recordDequeue(TransferTask.Priority priority, long waitTimeMs) {
        totalQueueLength.decrementAndGet();
        queueLengthByPriority.get(priority).decrementAndGet();
        totalDequeuedCount.incrementAndGet();
        taskCountByStatus.get(TransferTask.TaskStatus.WAITING).decrementAndGet();
        taskCountByStatus.get(TransferTask.TaskStatus.EXECUTING).incrementAndGet();
        totalWaitTimeMs.addAndGet(waitTimeMs);
        lastDequeueTime = LocalDateTime.now();
    }

    /**
     * 记录任务完成
     */
    public void recordCompletion(TransferTask.TaskStatus status, long executionTimeMs) {
        taskCountByStatus.get(TransferTask.TaskStatus.EXECUTING).decrementAndGet();
        taskCountByStatus.get(status).incrementAndGet();
        totalExecutionTimeMs.addAndGet(executionTimeMs);

        switch (status) {
            case COMPLETED:
                totalCompletedCount.incrementAndGet();
                break;
            case FAILED:
                totalFailedCount.incrementAndGet();
                break;
            case CANCELLED:
                totalCancelledCount.incrementAndGet();
                break;
            case TIMEOUT:
                totalTimeoutCount.incrementAndGet();
                break;
            default:
                break;
        }
    }

    /**
     * 记录任务取消（从等待队列中取消）
     */
    public void recordCancellationFromQueue(TransferTask.Priority priority) {
        totalQueueLength.decrementAndGet();
        queueLengthByPriority.get(priority).decrementAndGet();
        taskCountByStatus.get(TransferTask.TaskStatus.WAITING).decrementAndGet();
        taskCountByStatus.get(TransferTask.TaskStatus.CANCELLED).incrementAndGet();
        totalCancelledCount.incrementAndGet();
    }

    /**
     * 获取平均等待时间（毫秒）
     */
    public double getAverageWaitTimeMs() {
        long dequeued = totalDequeuedCount.get();
        if (dequeued == 0) {
            return 0;
        }
        return (double) totalWaitTimeMs.get() / dequeued;
    }

    /**
     * 获取平均执行时间（毫秒）
     */
    public double getAverageExecutionTimeMs() {
        long completed = totalCompletedCount.get();
        if (completed == 0) {
            return 0;
        }
        return (double) totalExecutionTimeMs.get() / completed;
    }

    /**
     * 获取队列负载百分比
     */
    public double getLoadPercentage(int maxQueueSize) {
        if (maxQueueSize <= 0) {
            return 0;
        }
        return (double) totalQueueLength.get() / maxQueueSize * 100;
    }

    /**
     * 获取运行时长（毫秒）
     */
    public long getRunningTimeMs() {
        return java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
    }

    // Getters

    public long getQueueLength(TransferTask.Priority priority) {
        return queueLengthByPriority.get(priority).get();
    }

    public long getTotalQueueLength() {
        return totalQueueLength.get();
    }

    public long getTaskCount(TransferTask.TaskStatus status) {
        return taskCountByStatus.get(status).get();
    }

    public long getTotalEnqueuedCount() {
        return totalEnqueuedCount.get();
    }

    public long getTotalDequeuedCount() {
        return totalDequeuedCount.get();
    }

    public long getTotalCompletedCount() {
        return totalCompletedCount.get();
    }

    public long getTotalFailedCount() {
        return totalFailedCount.get();
    }

    public long getTotalCancelledCount() {
        return totalCancelledCount.get();
    }

    public long getTotalTimeoutCount() {
        return totalTimeoutCount.get();
    }

    public long getPeakQueueLength() {
        return peakQueueLength.get();
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getLastEnqueueTime() {
        return lastEnqueueTime;
    }

    public LocalDateTime getLastDequeueTime() {
        return lastDequeueTime;
    }

    /**
     * 重置统计信息
     */
    public void reset() {
        totalQueueLength.set(0);
        peakQueueLength.set(0);
        totalEnqueuedCount.set(0);
        totalDequeuedCount.set(0);
        totalCompletedCount.set(0);
        totalFailedCount.set(0);
        totalCancelledCount.set(0);
        totalTimeoutCount.set(0);
        totalWaitTimeMs.set(0);
        totalExecutionTimeMs.set(0);

        for (AtomicLong counter : queueLengthByPriority.values()) {
            counter.set(0);
        }
        for (AtomicLong counter : taskCountByStatus.values()) {
            counter.set(0);
        }
    }

    /**
     * 生成统计报告
     */
    public Map<String, Object> generateReport(int maxQueueSize) {
        Map<String, Object> report = new java.util.HashMap<>();
        
        // 队列长度信息
        Map<String, Long> queueLengths = new java.util.HashMap<>();
        for (TransferTask.Priority priority : TransferTask.Priority.values()) {
            queueLengths.put(priority.name(), getQueueLength(priority));
        }
        report.put("queueLengthByPriority", queueLengths);
        report.put("totalQueueLength", getTotalQueueLength());
        report.put("peakQueueLength", getPeakQueueLength());
        report.put("loadPercentage", String.format("%.2f%%", getLoadPercentage(maxQueueSize)));
        
        // 任务状态统计
        Map<String, Long> statusCounts = new java.util.HashMap<>();
        for (TransferTask.TaskStatus status : TransferTask.TaskStatus.values()) {
            statusCounts.put(status.name(), getTaskCount(status));
        }
        report.put("taskCountByStatus", statusCounts);
        
        // 累计统计
        report.put("totalEnqueuedCount", getTotalEnqueuedCount());
        report.put("totalDequeuedCount", getTotalDequeuedCount());
        report.put("totalCompletedCount", getTotalCompletedCount());
        report.put("totalFailedCount", getTotalFailedCount());
        report.put("totalCancelledCount", getTotalCancelledCount());
        report.put("totalTimeoutCount", getTotalTimeoutCount());
        
        // 性能指标
        report.put("averageWaitTimeMs", String.format("%.2f", getAverageWaitTimeMs()));
        report.put("averageExecutionTimeMs", String.format("%.2f", getAverageExecutionTimeMs()));
        
        // 时间信息
        report.put("startTime", startTime.toString());
        report.put("runningTimeMs", getRunningTimeMs());
        report.put("lastEnqueueTime", lastEnqueueTime != null ? lastEnqueueTime.toString() : null);
        report.put("lastDequeueTime", lastDequeueTime != null ? lastDequeueTime.toString() : null);
        
        return report;
    }
}