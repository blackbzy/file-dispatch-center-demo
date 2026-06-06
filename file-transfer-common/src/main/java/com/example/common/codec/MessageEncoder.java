package com.example.common.codec;

import com.example.common.config.TransferConstants;
import com.example.common.message.TransferMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息编码器，负责将 {@link TransferMessage} 对象编码为网络传输字节流。
 * <p>
 * 编码流程：
 * <ol>
 *   <li>预处理消息（自动填充传输类型、结束标识等字段）</li>
 *   <li>若启用签名，则生成时间戳和数字签名</li>
 *   <li>将消息序列化为JSON字节数组</li>
 *   <li>写入4字节长度前缀 + JSON字节数据</li>
 * </ol>
 * </p>
 * <p>
 * 编码后的字节流格式：
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
 * @see MessageDecoder
 */
public class MessageEncoder extends MessageToByteEncoder<TransferMessage> {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(MessageEncoder.class);

    /** JSON序列化器 */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    /** 签名工具实例 */
    private final MessageSignature signature;

    /** 签名功能是否启用 */
    private final boolean signatureEnabled;

    /**
     * 默认构造方法，使用系统默认配置。
     * <p>签名功能默认启用，密钥和算法取自 {@link TransferConstants}。</p>
     */
    public MessageEncoder() {
        this(TransferConstants.DEFAULT_SIGNATURE_ENABLED);
    }

    /**
     * 构造方法，允许控制签名功能的启用状态。
     *
     * @param signatureEnabled {@code true} 启用签名功能，{@code false} 禁用签名功能
     */
    public MessageEncoder(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
        this.signature = new MessageSignature();
    }

    /**
     * 构造方法，允许自定义签名密钥、算法及启用状态。
     *
     * @param secretKey        签名密钥
     * @param algorithm        HMAC算法名称，如 "HmacSHA256"
     * @param signatureEnabled {@code true} 启用签名功能
     */
    public MessageEncoder(String secretKey, String algorithm, boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
        this.signature = new MessageSignature(secretKey, algorithm);
    }

    /**
     * 将 {@link TransferMessage} 对象编码为字节流。
     * <p>
     * 编码前会自动进行消息预处理（填充传输类型、结束标识），
     * 若启用签名则自动生成时间戳和数字签名。
     * </p>
     *
     * @param ctx Netty通道上下文
     * @param msg 待编码的消息对象
     * @param out 输出字节缓冲区
     * @throws Exception 编码过程中发生的异常
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, TransferMessage msg, ByteBuf out) throws Exception {
        preProcessMessage(msg);

        if (signatureEnabled) {
            msg.setTimestamp(System.currentTimeMillis());
            String sig = signature.generateSignature(msg);
            msg.setSignature(sig);
            logger.debug("Generated signature for message: type={}, length={}",
                msg.getMessageType(), sig != null ? sig.length() : 0);
        }

        byte[] jsonBytes = objectMapper.writeValueAsBytes(msg);
        int length = jsonBytes.length;
        out.writeInt(length);
        out.writeBytes(jsonBytes);

        logger.debug("Encoded message: type={}, transferType={}, length={}, signed={}",
            msg.getMessageType(), msg.getTransferType(), length, signatureEnabled);
    }

    /**
     * 消息预处理，自动填充缺失的关键字段。
     * <p>
     * 处理逻辑：
     * <ul>
     *   <li>若 {@code transferType} 为空，根据 {@code messageType} 自动推断</li>
     *   <li>若消息类型为 END 但 {@code endFlag} 未设置，自动设为 {@code true}</li>
     * </ul>
     * </p>
     *
     * @param msg 待预处理的消息对象
     */
    private void preProcessMessage(TransferMessage msg) {
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