package com.example.server.service;

import com.example.server.model.OverflowStrategy;
import com.example.server.model.QueueStatistics;
import com.example.server.model.TransferTask;
import com.example.server.model.TransferTask.Priority;
import com.example.server.model.TransferTask.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 传输队列服务
 * 实现优先级队列管理、溢出处理、状态统计等功能
 */
@Service
public class TransferQueueService {
    private static final Logger logger = LoggerFactory.getLogger(TransferQueueService.class);

    /**
     * 优先级队列（使用PriorityBlockingQueue实现线程安全）
     */
    private final PriorityBlockingQueue<TransferTask> priorityQueue;

    /**
     * 正在执行的任务映射
     */
    private final ConcurrentHashMap<String, TransferTask> executingTasks = new ConcurrentHashMap<>();

    /**
     * 所有任务映射（用于快速查找）
     */
    private final ConcurrentHashMap<String, TransferTask> allTasks = new ConcurrentHashMap<>();

    /**
     * 队列统计信息
     */
    private final QueueStatistics statistics = new QueueStatistics();

    /**
     * 队列最大长度
     */
    private volatile int maxQueueSize = 1000;

    /**
     * 临时扩容上限
     */
    private volatile int temporaryExpandLimit = 1500;

    /**
     * 当前临时扩容量
     */
    private volatile int currentTemporaryExpand = 0;

    /**
     * 溢出处理策略
     */
    private volatile OverflowStrategy overflowStrategy = OverflowStrategy.REJECT;

    /**
     * 任务超时检查间隔（毫秒）
     */
    private volatile long timeoutCheckIntervalMs = 5000;

    /**
     * 任务默认超时时间（毫秒）
     */
    private volatile long defaultTimeoutMs = 300000; // 5分钟

    /**
     * 默认最大重试次数
     */
    private volatile int defaultMaxRetryCount = 3;

    /**
     * 超时检查调度器
     */
    private ScheduledExecutorService timeoutChecker;

    /**
     * 队列操作锁（用于溢出处理等需要同步的操作）
     */
    private final ReentrantLock queueLock = new ReentrantLock();

    /**
     * 任务入队结果枚举
     */
    public enum EnqueueResult {
        SUCCESS,              // 成功入队
        REJECTED,             // 被拒绝（队列满）
        DEGRADED,             // 降级入队
        EVICTED_AND_ENQUEUED, // 移除旧任务后入队
        TEMPORARY_EXPANDED    // 临时扩容后入队
    }

    public TransferQueueService() {
        this.priorityQueue = new PriorityBlockingQueue<>(100);
    }

    @PostConstruct
    public void init() {
        // 启动超时检查任务
        startTimeoutChecker();
        logger.info("TransferQueueService initialized with maxQueueSize={}, overflowStrategy={}", 
                   maxQueueSize, overflowStrategy);
    }

    @PreDestroy
    public void shutdown() {
        if (timeoutChecker != null) {
            timeoutChecker.shutdown();
            try {
                if (!timeoutChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                    timeoutChecker.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutChecker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("TransferQueueService shutdown complete");
    }

    /**
     * 启动超时检查器
     */
    private void startTimeoutChecker() {
        timeoutChecker = Executors.newSingleThreadScheduledExecutor();
        timeoutChecker.scheduleAtFixedRate(this::checkTimeoutTasks, 
                                          timeoutCheckIntervalMs, 
                                          timeoutCheckIntervalMs, 
                                          TimeUnit.MILLISECONDS);
    }

    /**
     * 检查超时任务
     */
    private void checkTimeoutTasks() {
        try {
            // 检查等待队列中的超时任务
            List<TransferTask> timeoutTasks = new ArrayList<>();
            for (TransferTask task : priorityQueue) {
                if (task.isTimeout() && task.getStatus() == TaskStatus.WAITING) {
                    timeoutTasks.add(task);
                }
            }

            for (TransferTask task : timeoutTasks) {
                if (priorityQueue.remove(task)) {
                    task.markTimeout();
                    statistics.recordCancellationFromQueue(task.getPriority());
                    allTasks.remove(task.getTaskId());
                    logger.warn("Task timeout in queue: {}", task.getTaskId());
                }
            }

            // 检查正在执行的超时任务
            for (TransferTask task : executingTasks.values()) {
                if (task.isTimeout() && task.getStatus() == TaskStatus.EXECUTING) {
                    task.markTimeout();
                    executingTasks.remove(task.getTaskId());
                    statistics.recordCompletion(TaskStatus.TIMEOUT, task.getExecutionTimeMs());
                    logger.warn("Task timeout while executing: {}", task.getTaskId());
                }
            }
        } catch (Exception e) {
            logger.error("Error checking timeout tasks", e);
        }
    }

    /**
     * 任务入队
     * 
     * @param task 传输任务
     * @return 入队结果
     */
    public EnqueueResult enqueue(TransferTask task) {
        queueLock.lock();
        try {
            int currentSize = priorityQueue.size();
            
            // 检查队列是否已满
            if (currentSize >= maxQueueSize + currentTemporaryExpand) {
                return handleOverflow(task);
            }

            // 正常入队
            priorityQueue.put(task);
            allTasks.put(task.getTaskId(), task);
            statistics.recordEnqueue(task.getPriority());
            
            logger.info("Task enqueued: {}, priority={}, queueSize={}", 
                       task.getTaskId(), task.getPriority(), priorityQueue.size());
            
            return EnqueueResult.SUCCESS;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 处理队列溢出
     */
    private EnqueueResult handleOverflow(TransferTask task) {
        switch (overflowStrategy) {
            case REJECT:
                logger.warn("Queue full, task rejected: {}", task.getTaskId());
                return EnqueueResult.REJECTED;

            case DEGRADE:
                // 尝试降级优先级
                if (task.getPriority() != Priority.LOW) {
                    Priority originalPriority = task.getPriority();
                    task.setPriority(Priority.LOW);
                    
                    // 重新检查是否有空间
                    if (priorityQueue.size() < maxQueueSize + currentTemporaryExpand) {
                        priorityQueue.put(task);
                        allTasks.put(task.getTaskId(), task);
                        statistics.recordEnqueue(Priority.LOW);
                        logger.info("Task degraded and enqueued: {} ({} -> {})", 
                                   task.getTaskId(), originalPriority, Priority.LOW);
                        return EnqueueResult.DEGRADED;
                    }
                }
                logger.warn("Queue full, task rejected even after degradation: {}", task.getTaskId());
                return EnqueueResult.REJECTED;

            case EVICT_OLDEST:
                // 移除最旧的低优先级任务
                TransferTask evictedTask = findOldestLowPriorityTask();
                if (evictedTask != null) {
                    priorityQueue.remove(evictedTask);
                    evictedTask.markCancelled();
                    statistics.recordCancellationFromQueue(evictedTask.getPriority());
                    allTasks.remove(evictedTask.getTaskId());
                    
                    priorityQueue.put(task);
                    allTasks.put(task.getTaskId(), task);
                    statistics.recordEnqueue(task.getPriority());
                    
                    logger.info("Evicted oldest low priority task {} to enqueue {}", 
                               evictedTask.getTaskId(), task.getTaskId());
                    return EnqueueResult.EVICTED_AND_ENQUEUED;
                }
                logger.warn("No low priority task to evict, task rejected: {}", task.getTaskId());
                return EnqueueResult.REJECTED;

            case TEMPORARY_EXPAND:
                // 临时扩容
                if (maxQueueSize + currentTemporaryExpand < temporaryExpandLimit) {
                    currentTemporaryExpand += 100;
                    priorityQueue.put(task);
                    allTasks.put(task.getTaskId(), task);
                    statistics.recordEnqueue(task.getPriority());
                    
                    logger.info("Queue temporarily expanded to {}, task enqueued: {}", 
                               maxQueueSize + currentTemporaryExpand, task.getTaskId());
                    return EnqueueResult.TEMPORARY_EXPANDED;
                }
                logger.warn("Temporary expand limit reached, task rejected: {}", task.getTaskId());
                return EnqueueResult.REJECTED;

            default:
                return EnqueueResult.REJECTED;
        }
    }

    /**
     * 查找最旧的低优先级任务
     */
    private TransferTask findOldestLowPriorityTask() {
        TransferTask oldest = null;
        for (TransferTask task : priorityQueue) {
            if (task.getPriority() == Priority.LOW && task.getStatus() == TaskStatus.WAITING) {
                if (oldest == null || task.getEnqueueTime().isBefore(oldest.getEnqueueTime())) {
                    oldest = task;
                }
            }
        }
        return oldest;
    }

    /**
     * 任务出队
     * 
     * @return 优先级最高的任务，如果队列为空返回null
     */
    public TransferTask dequeue() {
        TransferTask task = priorityQueue.poll();
        if (task != null) {
            task.markExecuting();
            executingTasks.put(task.getTaskId(), task);
            statistics.recordDequeue(task.getPriority(), task.getWaitTimeMs());
            
            logger.info("Task dequeued: {}, waitTime={}ms", task.getTaskId(), task.getWaitTimeMs());
        }
        return task;
    }

    /**
     * 尝试在指定时间内出队
     * 
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 任务或null
     */
    public TransferTask dequeue(long timeout, TimeUnit unit) throws InterruptedException {
        TransferTask task = priorityQueue.poll(timeout, unit);
        if (task != null) {
            task.markExecuting();
            executingTasks.put(task.getTaskId(), task);
            statistics.recordDequeue(task.getPriority(), task.getWaitTimeMs());
            
            logger.info("Task dequeued: {}, waitTime={}ms", task.getTaskId(), task.getWaitTimeMs());
        }
        return task;
    }

    /**
     * 标记任务完成
     * 
     * @param taskId 任务ID
     * @param success 是否成功
     * @param failReason 失败原因（可选）
     */
    public void markTaskComplete(String taskId, boolean success, String failReason) {
        TransferTask task = executingTasks.remove(taskId);
        if (task == null) {
            task = allTasks.get(taskId);
            if (task == null) {
                logger.warn("Task not found for completion: {}", taskId);
                return;
            }
        }

        if (success) {
            task.markCompleted();
            statistics.recordCompletion(TaskStatus.COMPLETED, task.getExecutionTimeMs());
            logger.info("Task completed: {}, executionTime={}ms", taskId, task.getExecutionTimeMs());
        } else {
            // 检查是否可以重试
            if (task.canRetry()) {
                task.incrementRetry();
                task.setStatus(TaskStatus.WAITING);
                task.setPriority(task.getPriority()); // 保持原优先级
                
                // 重新入队
                priorityQueue.put(task);
                statistics.recordEnqueue(task.getPriority());
                logger.info("Task retry scheduled: {}, retryCount={}", taskId, task.getRetryCount());
            } else {
                task.markFailed(failReason);
                statistics.recordCompletion(TaskStatus.FAILED, task.getExecutionTimeMs());
                logger.error("Task failed: {}, reason: {}", taskId, failReason);
            }
        }

        // 清理任务记录（保留一段时间用于查询）
        allTasks.put(taskId, task);
    }

    /**
     * 取消任务
     * 
     * @param taskId 任务ID
     * @return 是否成功取消
     */
    public boolean cancelTask(String taskId) {
        queueLock.lock();
        try {
            TransferTask task = allTasks.get(taskId);
            if (task == null) {
                logger.warn("Task not found for cancellation: {}", taskId);
                return false;
            }

            TaskStatus status = task.getStatus();
            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || 
                status == TaskStatus.CANCELLED || status == TaskStatus.TIMEOUT) {
                logger.warn("Task already finished, cannot cancel: {} (status={})", taskId, status);
                return false;
            }

            if (status == TaskStatus.WAITING) {
                // 从等待队列中移除
                if (priorityQueue.remove(task)) {
                    task.markCancelled();
                    statistics.recordCancellationFromQueue(task.getPriority());
                    logger.info("Task cancelled from queue: {}", taskId);
                    return true;
                }
            } else if (status == TaskStatus.EXECUTING) {
                // 正在执行的任务标记为取消（实际执行需要另外处理）
                task.markCancelled();
                executingTasks.remove(taskId);
                statistics.recordCompletion(TaskStatus.CANCELLED, task.getExecutionTimeMs());
                logger.info("Task cancelled while executing: {}", taskId);
                return true;
            }

            return false;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 调整任务优先级
     * 
     * @param taskId 任务ID
     * @param newPriority 新优先级
     * @return 是否成功调整
     */
    public boolean adjustPriority(String taskId, Priority newPriority) {
        queueLock.lock();
        try {
            TransferTask task = allTasks.get(taskId);
            if (task == null) {
                logger.warn("Task not found for priority adjustment: {}", taskId);
                return false;
            }

            if (task.getStatus() != TaskStatus.WAITING) {
                logger.warn("Task not in waiting status, cannot adjust priority: {} (status={})", 
                           taskId, task.getStatus());
                return false;
            }

            Priority oldPriority = task.getPriority();
            if (oldPriority == newPriority) {
                return true;
            }

            // 需要先移除再重新入队（因为优先级变化会影响排序）
            if (priorityQueue.remove(task)) {
                task.setPriority(newPriority);
                priorityQueue.put(task);
                
                // 更新统计
                statistics.recordCancellationFromQueue(oldPriority);
                statistics.recordEnqueue(newPriority);
                
                logger.info("Task priority adjusted: {} ({} -> {})", taskId, oldPriority, newPriority);
                return true;
            }

            return false;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 清空队列
     */
    public void clearQueue() {
        queueLock.lock();
        try {
            while (!priorityQueue.isEmpty()) {
                TransferTask task = priorityQueue.poll();
                if (task != null) {
                    task.markCancelled();
                    statistics.recordCancellationFromQueue(task.getPriority());
                }
            }
            allTasks.clear();
            executingTasks.clear();
            
            // 重置临时扩容
            currentTemporaryExpand = 0;
            
            logger.info("Queue cleared");
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 清空指定优先级的队列
     * 
     * @param priority 优先级
     * @return 清除的任务数量
     */
    public int clearQueueByPriority(Priority priority) {
        queueLock.lock();
        try {
            int count = 0;
            List<TransferTask> toRemove = new ArrayList<>();
            
            for (TransferTask task : priorityQueue) {
                if (task.getPriority() == priority) {
                    toRemove.add(task);
                }
            }

            for (TransferTask task : toRemove) {
                if (priorityQueue.remove(task)) {
                    task.markCancelled();
                    statistics.recordCancellationFromQueue(priority);
                    allTasks.remove(task.getTaskId());
                    count++;
                }
            }

            logger.info("Queue cleared for priority: {}, count={}", priority, count);
            return count;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * 获取队列状态报告
     */
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = statistics.generateReport(maxQueueSize + currentTemporaryExpand);
        status.put("maxQueueSize", maxQueueSize);
        status.put("currentTemporaryExpand", currentTemporaryExpand);
        status.put("temporaryExpandLimit", temporaryExpandLimit);
        status.put("overflowStrategy", overflowStrategy.name());
        status.put("defaultTimeoutMs", defaultTimeoutMs);
        status.put("defaultMaxRetryCount", defaultMaxRetryCount);
        return status;
    }

    /**
     * 获取队列中的任务列表
     * 
     * @param limit 最大数量
     * @return 任务列表
     */
    public List<TransferTask> getQueuedTasks(int limit) {
        List<TransferTask> tasks = new ArrayList<>();
        for (TransferTask task : priorityQueue) {
            if (tasks.size() >= limit) {
                break;
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 获取正在执行的任务列表
     */
    public List<TransferTask> getExecutingTasks() {
        return new ArrayList<>(executingTasks.values());
    }

    /**
     * 获取任务详情
     * 
     * @param taskId 任务ID
     * @return 任务或null
     */
    public TransferTask getTask(String taskId) {
        return allTasks.get(taskId);
    }

    /**
     * 动态调整队列容量
     * 
     * @param newMaxSize 新的最大容量
     */
    public void adjustQueueCapacity(int newMaxSize) {
        if (newMaxSize > 0) {
            this.maxQueueSize = newMaxSize;
            logger.info("Queue capacity adjusted to: {}", newMaxSize);
        }
    }

    /**
     * 设置溢出策略
     * 
     * @param strategy 溢出策略
     */
    public void setOverflowStrategy(OverflowStrategy strategy) {
        this.overflowStrategy = strategy;
        logger.info("Overflow strategy set to: {}", strategy);
    }

    /**
     * 重置临时扩容
     */
    public void resetTemporaryExpand() {
        this.currentTemporaryExpand = 0;
        logger.info("Temporary expand reset to 0");
    }

    // Configuration setters

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public void setTemporaryExpandLimit(int temporaryExpandLimit) {
        this.temporaryExpandLimit = temporaryExpandLimit;
    }

    public void setTimeoutCheckIntervalMs(long timeoutCheckIntervalMs) {
        this.timeoutCheckIntervalMs = timeoutCheckIntervalMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public void setDefaultMaxRetryCount(int defaultMaxRetryCount) {
        this.defaultMaxRetryCount = defaultMaxRetryCount;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public int getDefaultMaxRetryCount() {
        return defaultMaxRetryCount;
    }

    public int getCurrentQueueSize() {
        return priorityQueue.size();
    }

    public QueueStatistics getStatistics() {
        return statistics;
    }

    /**
     * 创建任务ID
     */
    public static String generateTaskId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}