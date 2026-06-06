package com.example.client.handler;

import com.example.common.codec.MessageDecoder;
import com.example.common.codec.MessageEncoder;
import com.example.client.config.ClientConfig;
import com.example.client.service.FileReceiveService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LoggingHandler;

public class ClientHandler extends ChannelInitializer<SocketChannel> {
    private final FileReceiveService fileReceiveService;
    private final ClientConfig clientConfig;

    public ClientHandler(FileReceiveService fileReceiveService, ClientConfig clientConfig) {
        this.fileReceiveService = fileReceiveService;
        this.clientConfig = clientConfig;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline()
            .addLast(new LoggingHandler())
            .addLast(new MessageDecoder())
            .addLast(new MessageEncoder())
            .addLast(new ClientMessageHandler(fileReceiveService, clientConfig));
    }
}