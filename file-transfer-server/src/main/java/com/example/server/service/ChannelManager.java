package com.example.server.service;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通道管理服务，负责通道状态监控和重连逻辑。
 */
@Service
public class ChannelManager {
    private static final Logger logger = LoggerFactory.getLogger(ChannelManager.class);

    /** 已连接通道映射，键为通道ID，值为通道 */
    private final Map<String, Channel> activeChannels = new ConcurrentHashMap<>();
    
    /** 定时任务执行器，用于重连 */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 注册通道。
     *
     * @param channel 通道
     */
    public void registerChannel(Channel channel) {
        String channelId = channel.id().asLongText();
        activeChannels.put(channelId, channel);
        logger.info("Channel registered: {}, total: {}", channelId, activeChannels.size());
    }

    /**
     * 注销通道。
     *
     * @param channel 通道
     */
    public void unregisterChannel(Channel channel) {
        String channelId = channel.id().asLongText();
        activeChannels.remove(channelId);
        logger.info("Channel unregistered: {}, total: {}", channelId, activeChannels.size());
    }

    /**
     * 检查通道是否可用。
     *
     * @param channel 通道
     * @return true表示可用，false表示不可用
     */
    public boolean isChannelActive(Channel channel) {
        return channel != null && channel.isActive();
    }

    /**
     * 获取所有可用通道。
     *
     * @return 可用通道列表
     */
    public Map<String, Channel> getActiveChannels() {
        return new ConcurrentHashMap<>(activeChannels);
    }

    /**
     * 获取可用通道数量。
     *
     * @return 可用通道数量
     */
    public int getActiveChannelCount() {
        return activeChannels.size();
    }

    /**
     * 根据通道ID获取通道。
     *
     * @param channelId 通道ID
     * @return 通道或null
     */
    public Channel getChannel(String channelId) {
        return activeChannels.get(channelId);
    }

    /**
     * 发送消息到指定通道，带成功/失败回调。
     *
     * @param channel 通道
     * @param message 消息
     * @param successListener 成功回调
     * @param failureListener 失败回调
     */
    public void sendMessageWithCallback(Channel channel, Object message, 
                                        Runnable successListener, 
                                        Runnable failureListener) {
        if (!isChannelActive(channel)) {
            logger.warn("Channel is not active, cannot send message");
            if (failureListener != null) {
                failureListener.run();
            }
            return;
        }

        channel.writeAndFlush(message).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    if (successListener != null) {
                        successListener.run();
                    }
                } else {
                    logger.error("Failed to send message", future.cause());
                    if (failureListener != null) {
                        failureListener.run();
                    }
                }
            }
        });
    }

    /**
     * 关闭所有通道并释放资源。
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (Channel channel : activeChannels.values()) {
            if (channel.isActive()) {
                channel.close();
            }
        }
        activeChannels.clear();
        logger.info("Channel manager shutdown complete");
    }
}
