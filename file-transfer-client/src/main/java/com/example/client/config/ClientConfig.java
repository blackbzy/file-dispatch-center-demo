package com.example.client.config;

import com.example.common.config.TransferConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "file-transfer.client")
public class ClientConfig {
    private String serverHost = "localhost";
    private int serverPort = TransferConstants.DEFAULT_SERVER_PORT;
    private String fileSavePath = "./client-files";
    private long reconnectInterval = TransferConstants.DEFAULT_RECONNECT_INTERVAL;
    private String validateFlag = TransferConstants.DEFAULT_VALIDATE_FLAG;

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getFileSavePath() {
        return fileSavePath;
    }

    public void setFileSavePath(String fileSavePath) {
        this.fileSavePath = fileSavePath;
    }

    public long getReconnectInterval() {
        return reconnectInterval;
    }

    public void setReconnectInterval(long reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    public String getValidateFlag() {
        return validateFlag;
    }

    public void setValidateFlag(String validateFlag) {
        this.validateFlag = validateFlag;
    }
}