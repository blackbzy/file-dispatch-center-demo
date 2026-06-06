package com.example.common.codec;

import com.example.common.config.TransferConstants;
import com.example.common.exception.SignatureVerificationException;
import com.example.common.message.TransferMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 消息解码器，负责将网络传输字节流解码为 {@link TransferMessage} 对象。
 * <p>
 * 解码流程：
 * <ol>
 *   <li>读取4字节长度前缀，确定JSON数据长度</li>
 *   <li>等待缓冲区中积累足够的数据</li>
 *   <li>读取JSON字节数组并反序列化为消息对象</li>
 *   <li>若启用签名，验证消息签名的有效性和时间戳</li>
 *   <li>适配旧版本消息格式（自动推断传输类型等）</li>
 * </ol>
 * </p>
 * <p>
 * 解码后的字节流格式：
 * <pre>
 * +--------+----------------+
 * | 长度   | JSON数据       |
 * | (4字节)| (变长)         |
 * +--------+----------------+
 * </pre>
 * </p>
 *
 * @author example
 * @version 1.0
 * @since 1.0
 * @see MessageEncoder
 */
public class MessageDecoder extends ByteToMessageDecoder {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(MessageDecoder.class);

    /** JSON反序列化器 */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    /** 单帧最大字节数（10MB），超过此值视为异常帧 */
    private static final int MAX_FRAME_SIZE = 10 * 1024 * 1024;

    /** 签名工具实例 */
    private final MessageSignature signature;

    /** 签名功能是否启用 */
    private final boolean signatureEnabled;

    /** 签名时间戳容差（毫秒），超过此容差视为重放攻击 */
    private final long timestampTolerance;

    /**
     * 默认构造方法，使用系统默认配置。
     * <p>签名功能默认启用，时间戳容差为5分钟。</p>
     */
    public MessageDecoder() {
        this(TransferConstants.DEFAULT_SIGNATURE_ENABLED, TransferConstants.SIGNATURE_TIMESTAMP_TOLERANCE);
    }

    /**
     * 构造方法，允许控制签名功能和时间戳容差。
     *
     * @param signatureEnabled     {@code true} 启用签名验证，{@code false} 跳过签名验证
     * @param timestampTolerance   时间戳容差（毫秒），用于防止重放攻击
     */
    public MessageDecoder(boolean signatureEnabled, long timestampTolerance) {
        this.signatureEnabled = signatureEnabled;
        this.timestampTolerance = timestampTolerance;
        this.signature = new MessageSignature();
    }

    /**
     * 构造方法，允许自定义签名密钥、算法及验证参数。
     *
     * @param secretKey            签名密钥
     * @param algorithm            HMAC算法名称，如 "HmacSHA256"
     * @param signatureEnabled     {@code true} 启用签名验证
     * @param timestampTolerance   时间戳容差（毫秒）
     */
    public MessageDecoder(String secretKey, String algorithm, boolean signatureEnabled, long timestampTolerance) {
        this.signatureEnabled = signatureEnabled;
        this.timestampTolerance = timestampTolerance;
        this.signature = new MessageSignature(secretKey, algorithm);
    }

    /**
     * 将字节流解码为 {@link TransferMessage} 对象列表。
     * <p>
     * 该方法会被Netty框架循环调用，直到输入缓冲区中没有完整的消息帧为止。
     * 若缓冲区中数据不足一个完整帧，方法会返回并等待更多数据到达。
     * </p>
     *
     * @param ctx Netty通道上下文
     * @param in  输入字节缓冲区
     * @param out 解码后的消息对象列表
     * @throws Exception 解码过程中发生的异常，包括 {@link SignatureVerificationException}
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {
            return;
        }

        in.markReaderIndex();
        int length = in.readInt();

        if (length <= 0 || length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Invalid frame length: " + length);
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        byte[] jsonBytes = new byte[length];
        in.readBytes(jsonBytes);

        try {
            TransferMessage msg = objectMapper.readValue(jsonBytes, TransferMessage.class);

            if (!verifySignature(msg)) {
                String errorMsg = String.format(
                    "Signature verification failed for message: type=%s, fileName=%s, timestamp=%d",
                    msg.getMessageType(), msg.getFileName(), msg.getTimestamp());
                logger.error(errorMsg);
                throw new SignatureVerificationException(errorMsg,
                    msg.getMessageType() != null ? msg.getMessageType().name() : "UNKNOWN",
                    msg.getFileName());
            }

            adaptLegacyMessage(msg);
            out.add(msg);
            logger.debug("Decoded message: type={}, transferType={}, endFlag={}, signatureVerified={}",
                msg.getMessageType(), msg.getTransferType(), msg.isEndFlag(), signatureEnabled);
        } catch (SignatureVerificationException e) {
            logger.error("Signature verification exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to decode message", e);
            throw e;
        }
    }

    /**
     * 验证消息的签名和时间戳。
     * <p>
     * 验证流程：
     * <ol>
     *   <li>若签名功能禁用，直接返回 {@code true}</li>
     *   <li>检查时间戳是否在容差范围内（防止重放攻击）</li>
     *   <li>检查签名值是否存在</li>
     *   <li>使用HMAC算法验证签名的正确性</li>
     * </ol>
     * </p>
     *
     * @param msg 待验证的消息对象
     * @return {@code true} 表示验证通过，{@code false} 表示验证失败
     */
    private boolean verifySignature(TransferMessage msg) {
        if (!signatureEnabled) {
            logger.trace("Signature verification is disabled, skipping verification");
            return true;
        }

        if (msg.getTimestamp() > 0) {
            long currentTime = System.currentTimeMillis();
            long timeDiff = Math.abs(currentTime - msg.getTimestamp());
            if (timeDiff > timestampTolerance) {
                logger.warn("Message timestamp is out of tolerance: timestamp={}, current={}, diff={}ms, tolerance={}ms",
                    msg.getTimestamp(), currentTime, timeDiff, timestampTolerance);
                return false;
            }
        }

        if (msg.getSignature() == null || msg.getSignature().isEmpty()) {
            logger.warn("Message signature is missing: type={}, fileName={}",
                msg.getMessageType(), msg.getFileName());
            return false;
        }

        return signature.verifySignature(msg);
    }

    /**
     * 适配旧版本消息格式，确保向后兼容性。
     * <p>
     * 处理逻辑：
     * <ul>
     *   <li>若 {@code transferType} 为空，根据 {@code messageType} 自动推断</li>
     *   <li>若消息类型为 END 但 {@code endFlag} 未设置，自动设为 {@code true}</li>
     * </ul>
     * </p>
     *
     * @param msg 待适配的消息对象
     */
    private void adaptLegacyMessage(TransferMessage msg) {
        if (msg.getTransferType() == null) {
            if (msg.getMessageType() != null) {
                switch (msg.getMessageType()) {
                    case FILE_CHUNK:
                    case FILE_METADATA:
                        msg.setTransferType(TransferMessage.TransferCategory.FILE_DATA.getValue());
                        break;
                    case AUTH_REQUEST:
                    case AUTH_RESPONSE:
                    case ACK:
                    case END:
                    case ERROR:
                    case HEARTBEAT:
                        msg.setTransferType(TransferMessage.TransferCategory.CONTROL_COMMAND.getValue());
                        break;
                    default:
                        msg.setTransferType(TransferMessage.TransferCategory.NORMAL_DATA.getValue());
                }
            }
        }

        if (msg.getMessageType() == TransferMessage.MessageType.END && !msg.isEndFlag()) {
            msg.setEndFlag(true);
        }
    }
}