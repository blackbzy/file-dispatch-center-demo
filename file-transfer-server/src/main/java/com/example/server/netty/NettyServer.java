package com.example.server.netty;

import com.example.server.config.ServerConfig;
import com.example.server.handler.ServerHandler;
import com.example.server.service.AckQueueService;
import com.example.server.service.ChannelManager;
import com.example.server.service.FileTransferService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NettyServer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    private final ServerConfig serverConfig;
    private final FileTransferService fileTransferService;
    private final ChannelManager channelManager;
    private final AckQueueService ackQueueService;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(ServerConfig serverConfig, FileTransferService fileTransferService, 
                      ChannelManager channelManager, AckQueueService ackQueueService) {
        this.serverConfig = serverConfig;
        this.fileTransferService = fileTransferService;
        this.channelManager = channelManager;
        this.ackQueueService = ackQueueService;
    }

    @Override
    public void run(String... args) throws Exception {
        startServer();
    }

    public void startServer() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    // 配置流量控制参数
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, serverConfig.isTcpNodelay())
                    .childOption(ChannelOption.SO_SNDBUF, serverConfig.getSoSndbuf())
                    .childOption(ChannelOption.SO_RCVBUF, serverConfig.getSoRcvbuf())
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                        new WriteBufferWaterMark(
                            serverConfig.getWriteBufferLowWatermark(), 
                            serverConfig.getWriteBufferHighWatermark()))
                    .childHandler(new ServerHandler(fileTransferService, serverConfig, channelManager, ackQueueService));

            ChannelFuture future = bootstrap.bind(serverConfig.getPort()).sync();
            logger.info("Netty server started on port: {}", serverConfig.getPort());
            logger.info("Flow control configuration: SO_SNDBUF={}B, SO_RCVBUF={}B, TCP_NODELAY={}", 
                        serverConfig.getSoSndbuf(), serverConfig.getSoRcvbuf(), serverConfig.isTcpNodelay());

            future.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        channelManager.shutdown();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        logger.info("Netty server shutdown complete");
    }
}