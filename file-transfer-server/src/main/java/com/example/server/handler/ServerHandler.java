package com.example.server.handler;

import com.example.common.codec.MessageDecoder;
import com.example.common.codec.MessageEncoder;
import com.example.common.config.TransferConstants;
import com.example.server.config.ServerConfig;
import com.example.server.service.AckQueueService;
import com.example.server.service.ChannelManager;
import com.example.server.service.FileTransferService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerHandler extends ChannelInitializer<SocketChannel> {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private final FileTransferService fileTransferService;
    private final ServerConfig serverConfig;
    private final ChannelManager channelManager;
    private final AckQueueService ackQueueService;

    public ServerHandler(FileTransferService fileTransferService, ServerConfig serverConfig, 
                        ChannelManager channelManager, AckQueueService ackQueueService) {
        this.fileTransferService = fileTransferService;
        this.serverConfig = serverConfig;
        this.channelManager = channelManager;
        this.ackQueueService = ackQueueService;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        logger.debug("Initializing channel: {}", ch.id());
        
        // MessageDecoder 和 MessageEncoder 已经包含 LengthFieldBasedFrameDecoder/LengthFieldPrepender
        ch.pipeline()
            .addLast(new LoggingHandler(LogLevel.DEBUG))
            .addLast("decoder", new MessageDecoder(
                TransferConstants.DEFAULT_SIGNATURE_KEY,
                TransferConstants.DEFAULT_SIGNATURE_ALGORITHM,
                TransferConstants.DEFAULT_SIGNATURE_ENABLED,
                TransferConstants.SIGNATURE_TIMESTAMP_TOLERANCE))
            .addLast("encoder", new MessageEncoder(
                TransferConstants.DEFAULT_SIGNATURE_KEY,
                TransferConstants.DEFAULT_SIGNATURE_ALGORITHM,
                TransferConstants.DEFAULT_SIGNATURE_ENABLED))
            .addLast("handler", new ServerMessageHandler(fileTransferService, serverConfig, 
                                                      channelManager, ackQueueService));
    }
}