package com.example.common.codec;

import com.example.common.config.TransferConstants;
import com.example.common.message.TransferMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 消息签名工具类，提供基于HMAC-SHA256的消息签名生成与验证功能。
 * <p>
 * 该类用于确保消息在传输过程中的完整性和防篡改性。签名基于消息内容的关键字段
 * 进行计算，任何对消息内容的修改都会导致签名验证失败。
 * </p>
 * <p>
 * 同时提供MD5数据校验功能，用于文件内容的完整性验证。
 * </p>
 *
 * @author example
 * @version 1.0
 * @since 1.0
 */
public class MessageSignature {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(MessageSignature.class);

    /** JSON序列化器，用于将消息对象转换为签名字符串 */
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    /** 签名密钥 */
    private final String secretKey;

    /** HMAC算法名称 */
    private final String algorithm;

    /**
     * 默认构造方法，使用系统默认的签名密钥和算法。
     * <p>密钥取自 {@link TransferConstants#DEFAULT_SIGNATURE_KEY}，
     * 算法取自 {@link TransferConstants#DEFAULT_SIGNATURE_ALGORITHM}。</p>
     */
    public MessageSignature() {
        this(TransferConstants.DEFAULT_SIGNATURE_KEY, TransferConstants.DEFAULT_SIGNATURE_ALGORITHM);
    }

    /**
     * 带参数的构造方法，允许自定义签名密钥和算法。
     *
     * @param secretKey 签名密钥，长度建议不少于16个字符
     * @param algorithm HMAC算法名称，如 "HmacSHA256"、"HmacSHA512"
     */
    public MessageSignature(String secretKey, String algorithm) {
        this.secretKey = secretKey;
        this.algorithm = algorithm;
    }

    /**
     * 为指定消息生成数字签名。
     * <p>
     * 签名过程：将消息对象序列化为JSON字符串（排除signature和timestamp字段），
     * 然后使用HMAC算法计算哈希值，最后进行Base64编码。
     * </p>
     *
     * @param message 待签名的消息对象
     * @return Base64编码的签名字符串；生成失败时返回 {@code null}
     */
    public String generateSignature(TransferMessage message) {
        try {
            String content = serializeMessageForSignature(message);
            return computeHmac(content);
        } catch (Exception e) {
            logger.error("Failed to generate signature for message", e);
            return null;
        }
    }

    /**
     * 验证指定消息的签名是否有效。
     * <p>
     * 验证过程：重新计算消息的签名，并与消息中携带的签名值进行安全比较
     * （使用 {@link MessageDigest#isEqual(byte[], byte[])} 防止时序攻击）。
     * </p>
     *
     * @param message 待验证的消息对象，必须包含 {@code signature} 字段
     * @return {@code true} 表示签名验证通过，{@code false} 表示验证失败
     */
    public boolean verifySignature(TransferMessage message) {
        if (message == null || message.getSignature() == null) {
            logger.warn("Message or signature is null, verification failed");
            return false;
        }

        try {
            String content = serializeMessageForSignature(message);
            String expectedSignature = computeHmac(content);

            boolean valid = MessageDigest.isEqual(
                message.getSignature().getBytes(StandardCharsets.UTF_8),
                (expectedSignature != null ? expectedSignature : "").getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                logger.warn("Signature verification failed for message type: {}, fileName: {}",
                    message.getMessageType(), message.getFileName());
            }

            return valid;
        } catch (Exception e) {
            logger.error("Signature verification error", e);
            return false;
        }
    }

    /**
     * 将消息对象序列化为用于签名的字符串。
     * <p>
     * 注意：该方法会创建消息的临时副本，并排除 {@code signature} 和 {@code timestamp}
     * 字段，以确保签名的稳定性（签名值本身不参与签名计算）。
     * </p>
     *
     * @param message 原始消息对象
     * @return JSON格式的字符串表示
     * @throws Exception 序列化过程中发生的异常
     */
    private String serializeMessageForSignature(TransferMessage message) throws Exception {
        TransferMessage tempMsg = new TransferMessage();
        tempMsg.setMessageType(message.getMessageType());
        tempMsg.setTransferType(message.getTransferType());
        tempMsg.setChunkIndex(message.getChunkIndex());
        tempMsg.setTotalChunks(message.getTotalChunks());
        tempMsg.setFileName(message.getFileName());
        tempMsg.setFileLastModified(message.getFileLastModified());
        tempMsg.setValidateFlag(message.getValidateFlag());
        tempMsg.setEndFlag(message.isEndFlag());
        tempMsg.setSuccess(message.isSuccess());
        tempMsg.setErrorMessage(message.getErrorMessage());
        tempMsg.setData(message.getData());

        if (message.getChunkParams() != null) {
            TransferMessage.ChunkParams clonedParams = new TransferMessage.ChunkParams();
            clonedParams.setChunkIndex(message.getChunkParams().getChunkIndex());
            clonedParams.setTotalChunks(message.getChunkParams().getTotalChunks());
            clonedParams.setChunkSize(message.getChunkParams().getChunkSize());
            clonedParams.setOffset(message.getChunkParams().getOffset());
            tempMsg.setChunkParams(clonedParams);
        }

        if (message.getFileMetadata() != null) {
            TransferMessage.FileMetadata clonedMetadata = new TransferMessage.FileMetadata();
            clonedMetadata.setFileName(message.getFileMetadata().getFileName());
            clonedMetadata.setFileSize(message.getFileMetadata().getFileSize());
            clonedMetadata.setFileType(message.getFileMetadata().getFileType());
            clonedMetadata.setCreateTime(message.getFileMetadata().getCreateTime());
            clonedMetadata.setLastModified(message.getFileMetadata().getLastModified());
            clonedMetadata.setMd5(message.getFileMetadata().getMd5());
            tempMsg.setFileMetadata(clonedMetadata);
        }

        return objectMapper.writeValueAsString(tempMsg);
    }

    /**
     * 使用HMAC算法计算指定内容的哈希值。
     *
     * @param content 待计算哈希的字符串内容
     * @return Base64编码的HMAC哈希值
     * @throws NoSuchAlgorithmException 指定的算法不存在
     * @throws InvalidKeyException      密钥格式无效
     */
    private String computeHmac(String content) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), algorithm);
        mac.init(keySpec);
        byte[] rawHmac = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }

    /**
     * 计算指定字节数组的MD5校验值。
     *
     * @param data 待计算的字节数组
     * @return 32位小写十六进制MD5字符串；计算失败时返回 {@code null}
     */
    public static String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not found", e);
            return null;
        }
    }

    /**
     * 验证指定字节数组的MD5校验值是否匹配。
     *
     * @param data        待验证的字节数组
     * @param expectedMD5 期望的MD5校验值（32位小写十六进制字符串）
     * @return {@code true} 表示校验通过，{@code false} 表示校验失败
     */
    public static boolean verifyMD5(byte[] data, String expectedMD5) {
        String calculatedMD5 = calculateMD5(data);
        if (calculatedMD5 == null || expectedMD5 == null) {
            return false;
        }
        return calculatedMD5.equalsIgnoreCase(expectedMD5);
    }
}