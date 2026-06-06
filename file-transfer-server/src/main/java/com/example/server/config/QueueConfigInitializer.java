package com.example.server.config;

import com.example.server.model.OverflowStrategy;
import com.example.server.service.TransferQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 队列配置初始化器
 * 在应用启动后初始化队列服务配置
 */
@Component
public class QueueConfigInitializer {
    private static final Logger logger = LoggerFactory.getLogger(QueueConfigInitializer.class);

    private final ServerConfig serverConfig;
    private final TransferQueueService queueService;

    public QueueConfigInitializer(ServerConfig serverConfig, TransferQueueService queueService) {
        this.serverConfig = serverConfig;
        this.queueService = queueService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeQueueConfig() {
        // 设置队列容量
        queueService.setMaxQueueSize(serverConfig.getMaxQueueSize());
        queueService.setTemporaryExpandLimit(serverConfig.getTemporaryExpandLimit());
        
        // 设置溢出策略
        try {
            OverflowStrategy strategy = OverflowStrategy.valueOf(serverConfig.getOverflowStrategy());
            queueService.setOverflowStrategy(strategy);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid overflow strategy: {}, using default REJECT", serverConfig.getOverflowStrategy());
            queueService.setOverflowStrategy(OverflowStrategy.REJECT);
        }
        
        // 设置超时配置
        queueService.setDefaultTimeoutMs(serverConfig.getQueueTimeoutMs());
        queueService.setTimeoutCheckIntervalMs(serverConfig.getTimeoutCheckIntervalMs());
        queueService.setDefaultMaxRetryCount(serverConfig.getDefaultMaxRetryCount());
        
        logger.info("Queue configuration initialized: maxQueueSize={}, overflowStrategy={}, timeoutMs={}", 
                   serverConfig.getMaxQueueSize(), serverConfig.getOverflowStrategy(), serverConfig.getQueueTimeoutMs());
    }
}