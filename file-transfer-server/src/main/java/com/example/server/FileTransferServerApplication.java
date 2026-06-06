package com.example.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.example.server.config.ServerConfig;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ServerConfig.class)
public class FileTransferServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileTransferServerApplication.class, args);
    }
}