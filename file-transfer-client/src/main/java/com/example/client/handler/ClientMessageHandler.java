package com.example.client.handler;

import com.example.common.message.TransferMessage;
import com.example.common.message.TransferMessage.MessageType;
import com.example.client.config.ClientConfig;
import com.example.client.service.FileReceiveService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMessageHandler extends SimpleChannelInboundHandler<TransferMessage> {
    private static final Logger logger = LoggerFactory.getLogger(ClientMessageHandler.class);

    private final FileReceiveService fileReceiveService;
    private final ClientConfig clientConfig;
    private boolean authenticated = false;

    public ClientMessageHandler(FileReceiveService fileReceiveService, ClientConfig clientConfig) {
        this.fileReceiveService = fileReceiveService;
        this.clientConfig = clientConfig;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Connected to server, sending authentication request");
        TransferMessage authRequest = TransferMessage.authRequest(clientConfig.getValidateFlag());
        ctx.writeAndFlush(authRequest);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TransferMessage msg) throws Exception {
        switch (msg.getMessageType()) {
            case AUTH_RESPONSE:
                handleAuthResponse(ctx, msg);
                break;
            case FILE_METADATA:
                if (authenticated) {
                    handleFileMetadata(ctx, msg);
                }
                break;
            case FILE_CHUNK:
                if (authenticated) {
                    handleFileChunk(ctx, msg);
                }
                break;
            case END:
                if (authenticated) {
                    handleEndSignal(ctx, msg);
                }
                break;
            case ERROR:
                logger.error("Received error from server: {}", msg.getErrorMessage());
                break;
            case HEARTBEAT:
                logger.debug("Received heartbeat from server");
                break;
            default:
                logger.warn("Unknown message type: {}", msg.getMessageType());
        }
    }

    private void handleAuthResponse(ChannelHandlerContext ctx, TransferMessage msg) {
        if (msg.isSuccess()) {
            authenticated = true;
            logger.info("Authentication successful");
        } else {
            logger.error("Authentication failed: {}", msg.getErrorMessage());
            ctx.close();
        }
    }

    private void handleFileMetadata(ChannelHandlerContext ctx, TransferMessage msg) {
        fileReceiveService.processMetadata(msg);
        logger.debug("Processed metadata for file: {}", msg.getFileName());
    }

    private void handleFileChunk(ChannelHandlerContext ctx, TransferMessage msg) {
        boolean success = fileReceiveService.processFileChunk(msg);
        
        TransferMessage ack = TransferMessage.ack(msg.getChunkIndex(), msg.getFileName());
        ctx.writeAndFlush(ack);
        
        logger.debug("Processed chunk {} of file {}, sending ACK", msg.getChunkIndex(), msg.getFileName());
    }

    private void handleEndSignal(ChannelHandlerContext ctx, TransferMessage msg) {
        logger.info("Received END signal for file: {}", msg.getFileName());
        fileReceiveService.completeFile(msg.getFileName());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.warn("Disconnected from server");
        authenticated = false;
        fileReceiveService.reset();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in client channel", cause);
        authenticated = false;
        fileReceiveService.reset();
        ctx.close();
    }
}