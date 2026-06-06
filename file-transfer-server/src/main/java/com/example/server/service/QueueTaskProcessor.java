package com.example.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 队列任务处理器
 * 定期从队列中取出任务并执行
 */
@Service
public class QueueTaskProcessor {
    private static final Logger logger = LoggerFactory.getLogger(QueueTaskProcessor.class);

    private final TransferQueueService queueService;
    private final FileTransferService fileTransferService;
    private final ScheduledExecutorService scheduler;

    /**
     * 任务处理间隔（毫秒）
     */
    private volatile long processIntervalMs = 100;

    /**
     * 每次处理的最大任务数
     */
    private volatile int maxTasksPerProcess = 10;

    /**
     * 是否启用队列处理
     */
    private volatile boolean enabled = true;

    public QueueTaskProcessor(TransferQueueService queueService, FileTransferService fileTransferService) {
        this.queueService = queueService;
        this.fileTransferService = fileTransferService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @PostConstruct
    public void init() {
        startProcessing();
        logger.info("QueueTaskProcessor initialized, processIntervalMs={}, maxTasksPerProcess={}", 
                   processIntervalMs, maxTasksPerProcess);
    }

    @PreDestroy
    public void shutdown() {
        stopProcessing();
        logger.info("QueueTaskProcessor shutdown complete");
    }

    /**
     * 启动队列处理
     */
    public void startProcessing() {
        enabled = true;
        scheduler.scheduleAtFixedRate(this::processTasks, 
                                      processIntervalMs, 
                                      processIntervalMs, 
                                      TimeUnit.MILLISECONDS);
        logger.info("Queue processing started");
    }

    /**
     * 停止队列处理
     */
    public void stopProcessing() {
        enabled = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Queue processing stopped");
    }

    /**
     * 处理队列中的任务
     */
    private void processTasks() {
        if (!enabled) {
            return;
        }

        try {
            int processedCount = 0;
            while (processedCount < maxTasksPerProcess && queueService.getCurrentQueueSize() > 0) {
                fileTransferService.processQueueTask();
                processedCount++;
            }

            if (processedCount > 0) {
                logger.debug("Processed {} tasks from queue", processedCount);
            }
        } catch (Exception e) {
            logger.error("Error processing queue tasks", e);
        }
    }

    /**
     * 设置处理间隔
     */
    public void setProcessIntervalMs(long processIntervalMs) {
        this.processIntervalMs = processIntervalMs;
    }

    /**
     * 设置每次处理的最大任务数
     */
    public void setMaxTasksPerProcess(int maxTasksPerProcess) {
        this.maxTasksPerProcess = maxTasksPerProcess;
    }

    /**
     * 设置是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getProcessIntervalMs() {
        return processIntervalMs;
    }

    public int getMaxTasksPerProcess() {
        return maxTasksPerProcess;
    }
}