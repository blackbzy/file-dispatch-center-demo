package com.example.server.controller;

import com.example.server.model.FileTransferRecord;
import com.example.server.model.TransferTask.Priority;
import com.example.server.service.AckQueueService;
import com.example.server.service.ChannelManager;
import com.example.server.service.FileTransferRecordService;
import com.example.server.service.FileTransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {
    private static final Logger logger = LoggerFactory.getLogger(TransferController.class);

    private final FileTransferService fileTransferService;
    private final ChannelManager channelManager;
    private final FileTransferRecordService recordService;
    private final AckQueueService ackQueueService;

    public TransferController(FileTransferService fileTransferService, 
                            ChannelManager channelManager, 
                            FileTransferRecordService recordService,
                            AckQueueService ackQueueService) {
        this.fileTransferService = fileTransferService;
        this.channelManager = channelManager;
        this.recordService = recordService;
        this.ackQueueService = ackQueueService;
    }

    @PostMapping("/file")
    public ResponseEntity<String> triggerFileTransfer(@RequestParam String fileName) {
        logger.info("Manual file transfer triggered for: {}", fileName);
        fileTransferService.distributeFileToAllClients(fileName);
        return ResponseEntity.ok("File transfer initiated: " + fileName);
    }

    @PostMapping("/files")
    public ResponseEntity<String> triggerFilesTransfer(@RequestBody List<String> fileNames) {
        logger.info("Manual file transfer triggered for files: {}", fileNames);
        fileTransferService.distributeFiles(fileNames);
        return ResponseEntity.ok("File transfers initiated: " + fileNames.size());
    }

    @PostMapping("/all")
    public ResponseEntity<String> triggerAllFilesTransfer() {
        logger.info("Manual transfer of all files triggered");
        fileTransferService.distributeAllFiles();
        return ResponseEntity.ok("All files transfer initiated");
    }

    /**
     * 使用优先级队列分发文件
     */
    @PostMapping("/file-with-priority")
    public ResponseEntity<Map<String, Object>> triggerFileTransferWithPriority(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "MEDIUM") Priority priority) {
        logger.info("Priority file transfer triggered for: {} with priority: {}", fileName, priority);
        
        String taskId = fileTransferService.distributeFileWithQueue(fileName, priority);
        
        Map<String, Object> result = new HashMap<>();
        result.put("fileName", fileName);
        result.put("priority", priority.name());
        result.put("taskId", taskId);
        result.put("message", taskId != null ? "File transfer queued" : "Failed to queue file transfer");
        result.put("success", taskId != null);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTransferStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("connectedClients", channelManager.getActiveChannelCount());
        status.put("statistics", recordService.getStatistics());
        status.put("records", recordService.getAllRecords());
        logger.info("Status requested");
        return ResponseEntity.ok(status);
    }

    @PostMapping("/reset/all")
    public ResponseEntity<String> resetAllRecords() {
        recordService.clearAll();
        logger.info("All file transfer records cleared");
        return ResponseEntity.ok("All file transfer records cleared");
    }

    @GetMapping("/ack/status")
    public ResponseEntity<Map<String, Object>> getAckQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("pendingAckCount", ackQueueService.getPendingCount());
        status.put("statistics", ackQueueService.getStatistics());
        logger.info("Ack queue status requested");
        return ResponseEntity.ok(status);
    }
}