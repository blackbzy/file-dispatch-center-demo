package com.example.server.handler;

import com.example.common.message.TransferMessage;
import com.example.common.message.TransferMessage.MessageType;
import com.example.server.config.ServerConfig;
import com.example.server.service.AckQueueService;
import com.example.server.service.ChannelManager;
import com.example.server.service.FileTransferService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMessageHandler extends SimpleChannelInboundHandler<TransferMessage> {
    private static final Logger logger = LoggerFactory.getLogger(ServerMessageHandler.class);

    private final FileTransferService fileTransferService;
    private final ServerConfig serverConfig;
    private final ChannelManager channelManager;
    private final AckQueueService ackQueueService;
    private boolean authenticated = false;

    public ServerMessageHandler(FileTransferService fileTransferService, ServerConfig serverConfig, 
                               ChannelManager channelManager, AckQueueService ackQueueService) {
        this.fileTransferService = fileTransferService;
        this.serverConfig = serverConfig;
        this.channelManager = channelManager;
        this.ackQueueService = ackQueueService;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel active: {}", ctx.channel().id());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TransferMessage msg) throws Exception {
        Channel channel = ctx.channel();

        if (!authenticated) {
            handleAuthentication(ctx, msg);
            return;
        }

        switch (msg.getMessageType()) {
            case ACK:
                handleAck(ctx, msg);
                break;
            case HEARTBEAT:
                logger.debug("Received heartbeat from channel: {}", channel.id());
                break;
            case END:
                handleEndAck(ctx, msg);
                break;
            default:
                logger.warn("Unknown message type: {}", msg.getMessageType());
        }
    }

    private void handleAuthentication(ChannelHandlerContext ctx, TransferMessage msg) {
        if (msg.getMessageType() == MessageType.AUTH_REQUEST) {
            String receivedFlag = msg.getValidateFlag();
            boolean success = serverConfig.getValidateFlag().equals(receivedFlag);

            TransferMessage response = TransferMessage.authResponse(success, 
                success ? "Authentication successful" : "Invalid validation flag");
            
            ctx.writeAndFlush(response);

            if (success) {
                authenticated = true;
                channelManager.registerChannel(ctx.channel());
                logger.info("Client authenticated: {}, total connected: {}", 
                           ctx.channel().id(), channelManager.getActiveChannelCount());
            } else {
                logger.warn("Authentication failed for channel: {}", ctx.channel().id());
                ctx.close();
            }
        } else {
            logger.warn("Unauthenticated message received, closing channel: {}", ctx.channel().id());
            ctx.close();
        }
    }

    private void handleAck(ChannelHandlerContext ctx, TransferMessage msg) {
        fileTransferService.processAck(ctx.channel(), msg.getFileName(), msg.getChunkIndex());
        logger.debug("Received ACK for file: {}, chunk: {}", msg.getFileName(), msg.getChunkIndex());
    }

    /**
     * 处理客户端发送的END确认消息
     * 当客户端接收到文件传输完成的END标志后，会发送此消息作为确认
     */
    private void handleEndAck(ChannelHandlerContext ctx, TransferMessage msg) {
        String channelId = ctx.channel().id().asLongText();
        String fileName = msg.getFileName();
        
        // 使用文件名作为文件ID（实际应该使用更唯一的标识符）
        String fileId = generateFileId(fileName, msg.getFileMetadata());
        
        logger.info("Received END ack for file: {} from channel: {}", fileName, channelId);
        
        // 处理ack
        boolean success = ackQueueService.handleAck(channelId, fileId);
        
        if (success) {
            logger.info("Successfully processed END ack for file: {}", fileName);
        } else {
            logger.warn("Failed to process END ack for file: {}, record not found", fileName);
        }
    }

    /**
     * 生成文件ID
     */
    private String generateFileId(String fileName, com.example.common.message.FileMetadata metadata) {
        if (metadata != null && metadata.getMd5() != null) {
            return metadata.getMd5();
        }
        return fileName;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (authenticated) {
            channelManager.unregisterChannel(ctx.channel());
            logger.warn("Channel disconnected: {}, remaining: {}", 
                       ctx.channel().id(), channelManager.getActiveChannelCount());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in channel: {} - {}", ctx.channel().id(), cause.getMessage());
        if (authenticated) {
            channelManager.unregisterChannel(ctx.channel());
        }
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (channel.isWritable()) {
            logger.debug("Channel writability changed: writable, channel: {}", channel.id());
        } else {
            logger.warn("Channel writability changed: not writable, channel: {}", channel.id());
        }
        super.channelWritabilityChanged(ctx);
    }
}