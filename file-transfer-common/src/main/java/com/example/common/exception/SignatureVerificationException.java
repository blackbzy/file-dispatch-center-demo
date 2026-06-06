package com.example.common.exception;

/**
 * 签名验证异常，当消息签名验证失败时抛出。
 * <p>
 * 该异常用于标识消息在传输过程中可能被篡改、签名过期或签名缺失等安全问题。
 * 异常对象中包含消息类型和文件名等上下文信息，便于问题定位和安全审计。
 * </p>
 *
 * @author example
 * @version 1.0
 * @since 1.0
 * @see com.example.common.codec.MessageDecoder
 * @see com.example.common.codec.MessageSignature
 */
public class SignatureVerificationException extends RuntimeException {

    /** 触发异常的消息类型 */
    private final String messageType;

    /** 触发异常的文件名称 */
    private final String fileName;

    /**
     * 构造方法，仅指定错误信息。
     *
     * @param message 错误信息描述
     */
    public SignatureVerificationException(String message) {
        super(message);
        this.messageType = null;
        this.fileName = null;
    }

    /**
     * 构造方法，指定错误信息和异常原因。
     *
     * @param message 错误信息描述
     * @param cause   导致此异常的原始异常
     */
    public SignatureVerificationException(String message, Throwable cause) {
        super(message, cause);
        this.messageType = null;
        this.fileName = null;
    }

    /**
     * 构造方法，指定错误信息及上下文信息。
     *
     * @param message     错误信息描述
     * @param messageType 触发异常的消息类型
     * @param fileName    触发异常的文件名称
     */
    public SignatureVerificationException(String message, String messageType, String fileName) {
        super(message);
        this.messageType = messageType;
        this.fileName = fileName;
    }

    /**
     * 构造方法，指定错误信息、上下文信息及异常原因。
     *
     * @param message     错误信息描述
     * @param messageType 触发异常的消息类型
     * @param fileName    触发异常的文件名称
     * @param cause       导致此异常的原始异常
     */
    public SignatureVerificationException(String message, String messageType, String fileName, Throwable cause) {
        super(message, cause);
        this.messageType = messageType;
        this.fileName = fileName;
    }

    /**
     * 获取触发异常的消息类型。
     *
     * @return 消息类型，可能为 {@code null}
     */
    public String getMessageType() {
        return messageType;
    }

    /**
     * 获取触发异常的文件名称。
     *
     * @return 文件名称，可能为 {@code null}
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 返回包含异常详细信息的字符串表示。
     *
     * @return 异常信息字符串
     */
    @Override
    public String toString() {
        return "SignatureVerificationException{" +
                "message='" + getMessage() + '\'' +
                ", messageType=" + messageType +
                ", fileName='" + fileName + '\'' +
                '}';
    }
}