package com.example.server.task;

import com.example.server.config.ServerConfig;
import com.example.server.service.FileTransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ScheduledFileTask {
    private static final Logger logger = LoggerFactory.getLogger(ScheduledFileTask.class);

    private final FileTransferService fileTransferService;
    private final ServerConfig serverConfig;
    
    // 防止任务重叠执行
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public ScheduledFileTask(FileTransferService fileTransferService, ServerConfig serverConfig) {
        this.fileTransferService = fileTransferService;
        this.serverConfig = serverConfig;
    }

    @Scheduled(cron = "${file-transfer.server.cron-expression:0 0/5 * * * ?}")
    public void scheduledFileTransfer() {
        if (!isRunning.compareAndSet(false, true)) {
            logger.warn("Scheduled task is already running, skipping this execution");
            return;
        }

        try {
            logger.info("Executing scheduled file transfer task");
            
            // 检查配置的文件列表
            if (!serverConfig.getScheduledFiles().isEmpty()) {
                logger.info("Distributing configured files: {}", serverConfig.getScheduledFiles());
                fileTransferService.distributeFiles(serverConfig.getScheduledFiles());
            } else {
                logger.info("No scheduled files configured, distributing all files");
                fileTransferService.distributeAllFiles();
            }
        } catch (Exception e) {
            logger.error("Error in scheduled file transfer task", e);
        } finally {
            isRunning.set(false);
        }
    }
}