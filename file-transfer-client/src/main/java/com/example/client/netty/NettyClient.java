package com.example.client.netty;

import com.example.client.config.ClientConfig;
import com.example.client.handler.ClientHandler;
import com.example.client.service.FileReceiveService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class NettyClient implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private final ClientConfig clientConfig;
    private final FileReceiveService fileReceiveService;

    private EventLoopGroup group;
    private Channel channel;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    public NettyClient(ClientConfig clientConfig, FileReceiveService fileReceiveService) {
        this.clientConfig = clientConfig;
        this.fileReceiveService = fileReceiveService;
    }

    @Override
    public void run(String... args) throws Exception {
        startClientAsync();
    }

    public void startClientAsync() {
        reconnectExecutor.scheduleWithFixedDelay(this::connect, 0, 1, TimeUnit.SECONDS);
    }

    private synchronized void connect() {
        if (channel != null && channel.isActive()) {
            return;
        }

        group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ClientHandler(fileReceiveService, clientConfig));

        try {
            ChannelFuture future = bootstrap.connect(clientConfig.getServerHost(), clientConfig.getServerPort()).sync();
            channel = future.channel();
            logger.info("Connected to server: {}:{}", clientConfig.getServerHost(), clientConfig.getServerPort());

            future.channel().closeFuture().addListener(f -> {
                logger.warn("Disconnected from server, scheduling reconnect");
                scheduleReconnect();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Connection attempt interrupted", e);
        } catch (Exception e) {
            logger.warn("Failed to connect to server: {}:{}, retrying...", 
                clientConfig.getServerHost(), clientConfig.getServerPort());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }

        reconnectExecutor.schedule(() -> {
            if (channel == null || !channel.isActive()) {
                connect();
            }
        }, clientConfig.getReconnectInterval(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        reconnectExecutor.shutdownNow();
        
        if (channel != null) {
            channel.close();
        }
        
        if (group != null) {
            group.shutdownGracefully();
        }
        
        logger.info("Netty client shutdown complete");
    }
}