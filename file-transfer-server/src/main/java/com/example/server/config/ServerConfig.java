package com.example.server.config;

import com.example.common.config.TransferConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "file-transfer.server")
public class ServerConfig {
    private int port = TransferConstants.DEFAULT_SERVER_PORT;
    private String fileSourcePath = "./server-files";
    private String cronExpression = "0 0/5 * * * ?";
    private int retryCount = TransferConstants.DEFAULT_RETRY_COUNT;
    private long retryInterval = TransferConstants.DEFAULT_RETRY_INTERVAL;
    private int chunkSize = TransferConstants.DEFAULT_CHUNK_SIZE;
    private String validateFlag = TransferConstants.DEFAULT_VALIDATE_FLAG;
    private List<String> scheduledFiles = new ArrayList<>();
    
    // 流量控制配置
    private int soSndbuf = 10485760;
    private int soRcvbuf = 10485760;
    private boolean tcpNodelay = true;
    private int writeBufferLowWatermark = 32768;
    private int writeBufferHighWatermark = 65536;

    // 队列管理配置
    private int maxQueueSize = 1000;
    private int temporaryExpandLimit = 1500;
    private String overflowStrategy = "REJECT";  // REJECT, DEGRADE, EVICT_OLDEST, TEMPORARY_EXPAND
    private long queueTimeoutMs = 300000;  // 5分钟
    private long timeoutCheckIntervalMs = 5000;  // 5秒
    private int defaultMaxRetryCount = 3;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getFileSourcePath() {
        return fileSourcePath;
    }

    public void setFileSourcePath(String fileSourcePath) {
        this.fileSourcePath = fileSourcePath;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getValidateFlag() {
        return validateFlag;
    }

    public void setValidateFlag(String validateFlag) {
        this.validateFlag = validateFlag;
    }

    public List<String> getScheduledFiles() {
        return scheduledFiles;
    }

    public void setScheduledFiles(List<String> scheduledFiles) {
        this.scheduledFiles = scheduledFiles;
    }

    public int getSoSndbuf() {
        return soSndbuf;
    }

    public void setSoSndbuf(int soSndbuf) {
        this.soSndbuf = soSndbuf;
    }

    public int getSoRcvbuf() {
        return soRcvbuf;
    }

    public void setSoRcvbuf(int soRcvbuf) {
        this.soRcvbuf = soRcvbuf;
    }

    public boolean isTcpNodelay() {
        return tcpNodelay;
    }

    public void setTcpNodelay(boolean tcpNodelay) {
        this.tcpNodelay = tcpNodelay;
    }

    public int getWriteBufferLowWatermark() {
        return writeBufferLowWatermark;
    }

    public void setWriteBufferLowWatermark(int writeBufferLowWatermark) {
        this.writeBufferLowWatermark = writeBufferLowWatermark;
    }

    public int getWriteBufferHighWatermark() {
        return writeBufferHighWatermark;
    }

    public void setWriteBufferHighWatermark(int writeBufferHighWatermark) {
        this.writeBufferHighWatermark = writeBufferHighWatermark;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int getTemporaryExpandLimit() {
        return temporaryExpandLimit;
    }

    public void setTemporaryExpandLimit(int temporaryExpandLimit) {
        this.temporaryExpandLimit = temporaryExpandLimit;
    }

    public String getOverflowStrategy() {
        return overflowStrategy;
    }

    public void setOverflowStrategy(String overflowStrategy) {
        this.overflowStrategy = overflowStrategy;
    }

    public long getQueueTimeoutMs() {
        return queueTimeoutMs;
    }

    public void setQueueTimeoutMs(long queueTimeoutMs) {
        this.queueTimeoutMs = queueTimeoutMs;
    }

    public long getTimeoutCheckIntervalMs() {
        return timeoutCheckIntervalMs;
    }

    public void setTimeoutCheckIntervalMs(long timeoutCheckIntervalMs) {
        this.timeoutCheckIntervalMs = timeoutCheckIntervalMs;
    }

    public int getDefaultMaxRetryCount() {
        return defaultMaxRetryCount;
    }

    public void setDefaultMaxRetryCount(int defaultMaxRetryCount) {
        this.defaultMaxRetryCount = defaultMaxRetryCount;
    }
}