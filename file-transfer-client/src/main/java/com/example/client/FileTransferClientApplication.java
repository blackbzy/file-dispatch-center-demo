package com.example.client;

import com.example.client.config.ClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ClientConfig.class)
public class FileTransferClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileTransferClientApplication.class, args);
    }
}