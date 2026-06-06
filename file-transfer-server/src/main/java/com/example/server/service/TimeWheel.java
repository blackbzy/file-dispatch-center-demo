package com.example.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 时间轮定时器实现，用于高效管理定时任务。
 * 采用分层时间轮设计，支持毫秒级精度的定时任务调度。
 */
public class TimeWheel {
    private static final Logger logger = LoggerFactory.getLogger(TimeWheel.class);

    /** 时间轮槽位数 */
    private static final int WHEEL_SIZE = 60;
    
    /** 槽位间隔（毫秒） */
    private final long tickMs;
    
    /** 当前时间槽索引 */
    private volatile int currentIndex = 0;
    
    /** 时间轮槽位，每个槽位包含一个任务队列 */
    private final ConcurrentLinkedQueue<TimerTask>[] slots;
    
    /** 定时器线程 */
    private ScheduledExecutorService scheduler;
    
    /** 任务取消映射 */
    private final ConcurrentHashMap<String, Boolean> cancelledTasks = new ConcurrentHashMap<>();
    
    /** 任务回调 */
    private final TaskCallback callback;

    /**
     * 定时器任务接口
     */
    public interface TimerTask {
        /**
         * 获取任务ID
         */
        String getId();
        
        /**
         * 获取延迟时间（毫秒）
         */
        long getDelayMs();
        
        /**
         * 执行任务
         */
        void run();
    }

    /**
     * 任务回调接口
     */
    public interface TaskCallback {
        /**
         * 任务到期时调用
         */
        void onTaskExpired(TimerTask task);
    }

    /**
     * 创建时间轮
     * @param tickMs 槽位间隔（毫秒）
     * @param callback 任务回调
     */
    public TimeWheel(long tickMs, TaskCallback callback) {
        this.tickMs = tickMs;
        this.callback = callback;
        this.slots = new ConcurrentLinkedQueue[WHEEL_SIZE];
        for (int i = 0; i < WHEEL_SIZE; i++) {
            slots[i] = new ConcurrentLinkedQueue<>();
        }
    }

    /**
     * 启动时间轮
     */
    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TimeWheel");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, tickMs, TimeUnit.MILLISECONDS);
        logger.info("TimeWheel started with tick: {}ms", tickMs);
    }

    /**
     * 停止时间轮
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("TimeWheel stopped");
    }

    /**
     * 添加定时任务
     * @param task 定时任务
     */
    public void addTask(TimerTask task) {
        if (task == null || cancelledTasks.containsKey(task.getId())) {
            return;
        }
        
        // 计算应该放入哪个槽位
        long delayMs = task.getDelayMs();
        int slotsAhead = (int) (delayMs / tickMs);
        int targetIndex = (currentIndex + slotsAhead) % WHEEL_SIZE;
        
        slots[targetIndex].add(task);
        logger.debug("Task added to slot {}: {}", targetIndex, task.getId());
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     */
    public void cancelTask(String taskId) {
        cancelledTasks.put(taskId, true);
        // 同时尝试从队列中移除
        for (ConcurrentLinkedQueue<TimerTask> slot : slots) {
            slot.removeIf(task -> task.getId().equals(taskId));
        }
        cancelledTasks.remove(taskId);
        logger.debug("Task cancelled: {}", taskId);
    }

    /**
     * 时间轮tick
     */
    private void tick() {
        try {
            ConcurrentLinkedQueue<TimerTask> currentSlot = slots[currentIndex];
            
            while (!currentSlot.isEmpty()) {
                TimerTask task = currentSlot.poll();
                
                // 检查任务是否已取消
                if (cancelledTasks.containsKey(task.getId())) {
                    cancelledTasks.remove(task.getId());
                    continue;
                }
                
                logger.debug("Task expired: {}", task.getId());
                callback.onTaskExpired(task);
            }
            
            // 移动到下一个槽位
            currentIndex = (currentIndex + 1) % WHEEL_SIZE;
        } catch (Exception e) {
            logger.error("Error processing time wheel tick", e);
        }
    }

    /**
     * 获取当前槽位索引
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * 获取时间轮状态信息
     */
    public String getStatusInfo() {
        int totalTasks = 0;
        for (ConcurrentLinkedQueue<TimerTask> slot : slots) {
            totalTasks += slot.size();
        }
        return String.format("TimeWheel{currentIndex=%d, totalTasks=%d, tickMs=%d}", 
                           currentIndex, totalTasks, tickMs);
    }
}
