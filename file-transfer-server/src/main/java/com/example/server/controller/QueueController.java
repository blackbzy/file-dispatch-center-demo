package com.example.server.controller;

import com.example.server.model.OverflowStrategy;
import com.example.server.model.TransferTask;
import com.example.server.model.TransferTask.Priority;
import com.example.server.service.TransferQueueService;
import com.example.server.service.TransferQueueService.EnqueueResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 队列管理API控制器
 */
@RestController
@RequestMapping("/api/queue")
public class QueueController {
    private static final Logger logger = LoggerFactory.getLogger(QueueController.class);

    private final TransferQueueService queueService;

    public QueueController(TransferQueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * 获取队列状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        logger.info("Queue status requested");
        Map<String, Object> status = queueService.getQueueStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 获取队列中的任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getQueuedTasks(@RequestParam(defaultValue = "50") int limit) {
        logger.info("Queued tasks requested, limit={}", limit);
        
        List<TransferTask> queuedTasks = queueService.getQueuedTasks(limit);
        List<TransferTask> executingTasks = queueService.getExecutingTasks();
        
        Map<String, Object> result = new HashMap<>();
        result.put("queuedTasks", queuedTasks);
        result.put("queuedCount", queuedTasks.size());
        result.put("executingTasks", executingTasks);
        result.put("executingCount", executingTasks.size());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> getTaskDetail(@PathVariable String taskId) {
        logger.info("Task detail requested: {}", taskId);
        
        TransferTask task = queueService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> detail = new HashMap<>();
        detail.put("taskId", task.getTaskId());
        detail.put("fileName", task.getFileName());
        detail.put("filePath", task.getFilePath());
        detail.put("fileSize", task.getFileSize());
        detail.put("fileMd5", task.getFileMd5());
        detail.put("channelId", task.getChannelId());
        detail.put("priority", task.getPriority().name());
        detail.put("status", task.getStatus().name());
        detail.put("enqueueTime", task.getEnqueueTime().toString());
        detail.put("startTime", task.getStartTime() != null ? task.getStartTime().toString() : null);
        detail.put("completeTime", task.getCompleteTime() != null ? task.getCompleteTime().toString() : null);
        detail.put("waitTimeMs", task.getWaitTimeMs());
        detail.put("executionTimeMs", task.getExecutionTimeMs());
        detail.put("retryCount", task.getRetryCount());
        detail.put("maxRetryCount", task.getMaxRetryCount());
        detail.put("canRetry", task.canRetry());
        detail.put("failReason", task.getFailReason());
        
        return ResponseEntity.ok(detail);
    }

    /**
     * 取消任务
     */
    @DeleteMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        logger.info("Task cancellation requested: {}", taskId);
        
        boolean success = queueService.cancelTask(taskId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("success", success);
        result.put("message", success ? "Task cancelled successfully" : "Failed to cancel task");
        
        return ResponseEntity.ok(result);
    }

    /**
     * 调整任务优先级
     */
    @PutMapping("/task/{taskId}/priority")
    public ResponseEntity<Map<String, Object>> adjustPriority(
            @PathVariable String taskId,
            @RequestParam Priority priority) {
        logger.info("Priority adjustment requested: {} -> {}", taskId, priority);
        
        boolean success = queueService.adjustPriority(taskId, priority);
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("newPriority", priority.name());
        result.put("success", success);
        result.put("message", success ? "Priority adjusted successfully" : "Failed to adjust priority");
        
        return ResponseEntity.ok(result);
    }

    /**
     * 清空队列
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearQueue(
            @RequestParam(required = false) Priority priority) {
        logger.info("Queue clear requested, priority={}", priority);
        
        int clearedCount;
        if (priority != null) {
            clearedCount = queueService.clearQueueByPriority(priority);
        } else {
            queueService.clearQueue();
            clearedCount = -1; // 表示清空全部
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("clearedCount", clearedCount);
        result.put("message", priority != null ? 
                   "Cleared " + clearedCount + " tasks with priority " + priority : 
                   "All queue cleared");
        
        return ResponseEntity.ok(result);
    }

    /**
     * 动态调整队列容量
     */
    @PutMapping("/capacity")
    public ResponseEntity<Map<String, Object>> adjustCapacity(@RequestParam int maxSize) {
        logger.info("Queue capacity adjustment requested: {}", maxSize);
        
        queueService.adjustQueueCapacity(maxSize);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("newMaxSize", maxSize);
        result.put("message", "Queue capacity adjusted to " + maxSize);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 设置溢出策略
     */
    @PutMapping("/overflow-strategy")
    public ResponseEntity<Map<String, Object>> setOverflowStrategy(@RequestParam OverflowStrategy strategy) {
        logger.info("Overflow strategy change requested: {}", strategy);
        
        queueService.setOverflowStrategy(strategy);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("strategy", strategy.name());
        result.put("message", "Overflow strategy set to " + strategy);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 重置临时扩容
     */
    @PostMapping("/reset-temporary-expand")
    public ResponseEntity<Map<String, Object>> resetTemporaryExpand() {
        logger.info("Temporary expand reset requested");
        
        queueService.resetTemporaryExpand();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Temporary expand reset to 0");
        
        return ResponseEntity.ok(result);
    }

    /**
     * 重置统计信息
     */
    @PostMapping("/reset-statistics")
    public ResponseEntity<Map<String, Object>> resetStatistics() {
        logger.info("Statistics reset requested");
        
        queueService.getStatistics().reset();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Statistics reset");
        
        return ResponseEntity.ok(result);
    }

    /**
     * 手动添加任务（用于测试）
     */
    @PostMapping("/task")
    public ResponseEntity<Map<String, Object>> addTask(
            @RequestParam String fileName,
            @RequestParam String filePath,
            @RequestParam(required = false, defaultValue = "MEDIUM") Priority priority,
            @RequestParam(required = false) String channelId) {
        logger.info("Manual task addition requested: {}", fileName);
        
        String taskId = TransferQueueService.generateTaskId();
        TransferTask task = new TransferTask(
            taskId,
            fileName,
            filePath,
            0,  // fileSize
            null,  // fileMd5
            channelId != null ? channelId : "manual-test",
            priority,
            queueService.getDefaultMaxRetryCount(),
            queueService.getMaxQueueSize()  // 使用队列容量作为超时参考
        );
        
        EnqueueResult result = queueService.enqueue(task);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("enqueueResult", result.name());
        response.put("success", result == EnqueueResult.SUCCESS);
        response.put("message", "Task " + (result == EnqueueResult.SUCCESS ? "enqueued" : "rejected") + 
                   " with result: " + result);
        
        return ResponseEntity.ok(response);
    }
}