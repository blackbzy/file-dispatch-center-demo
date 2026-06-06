package com.example.common.message;

import java.io.Serializable;

/**
 * 文件传输消息实体类，作为客户端与服务端之间通信的数据载体。
 * <p>
 * 该类封装了文件传输过程中所需的全部信息，包括消息类型、传输类型、分片参数、
 * 文件元数据、签名信息等。支持多种消息类型的构建，如认证请求、文件分片、
 * 确认应答、结束信号等。
 * </p>
 * <p>
 * 为保证向后兼容性，新旧字段（如 chunkIndex 与 chunkParams）之间保持双向同步。
 * </p>
 *
 * @author example
 * @version 1.0
 * @since 1.0
 */
public class TransferMessage implements Serializable {

    /** 序列化版本号，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 认证令牌，用于客户端与服务端之间的身份验证。
     * <p>仅在 {@link MessageType#AUTH_REQUEST} 消息中使用。</p>
     */
    private String validateFlag;

    /**
     * 消息类型，标识当前消息的业务类别。
     * <p>取值参见 {@link MessageType} 枚举定义。</p>
     */
    private MessageType messageType;

    /**
     * 消息携带的二进制数据。
     * <p>在 {@link MessageType#FILE_CHUNK} 消息中表示文件分片的字节内容。</p>
     */
    private byte[] data;

    /**
     * 传输类型，标识当前数据的传输类别。
     * <p>取值参见 {@link TransferCategory} 枚举定义，如 FILE_DATA、CONTROL_COMMAND 等。</p>
     */
    private String transferType;

    /**
     * 传输结束标识。
     * <p>当消息类型为 {@link MessageType#END} 时，该值为 {@code true}。</p>
     */
    private boolean endFlag;

    /**
     * 分片参数对象，包含分片总数、当前分片序号、分片大小及偏移量。
     * <p>仅在文件分片传输时使用，与 {@link #chunkIndex}、{@link #totalChunks} 字段保持同步。</p>
     */
    private ChunkParams chunkParams;

    /**
     * 文件元数据对象，包含文件名、文件大小、文件类型、MD5校验值等属性。
     * <p>与 {@link #fileName}、{@link #fileLastModified} 字段保持同步。</p>
     */
    private FileMetadata fileMetadata;

    /**
     * 当前分片序号（从0开始）。
     * <p>为向后兼容保留的字段，与 {@link #chunkParams} 中的 chunkIndex 保持同步。</p>
     */
    private int chunkIndex;

    /**
     * 分片总数。
     * <p>为向后兼容保留的字段，与 {@link #chunkParams} 中的 totalChunks 保持同步。</p>
     */
    private int totalChunks;

    /**
     * 文件最后修改时间戳（毫秒）。
     * <p>为向后兼容保留的字段，与 {@link #fileMetadata} 中的 lastModified 保持同步。</p>
     */
    private long fileLastModified;

    /**
     * 文件名称（含扩展名）。
     * <p>为向后兼容保留的字段，与 {@link #fileMetadata} 中的 fileName 保持同步。</p>
     */
    private String fileName;

    /**
     * 确认应答标识。
     * <p>用于唯一标识一次确认应答，可选字段。</p>
     */
    private String ackId;

    /**
     * 操作成功标识。
     * <p>在 {@link MessageType#AUTH_RESPONSE} 中表示认证是否成功。</p>
     */
    private boolean success;

    /**
     * 错误信息描述。
     * <p>当 {@link #success} 为 {@code false} 时，包含具体的错误原因。</p>
     */
    private String errorMessage;

    /**
     * 消息签名值。
     * <p>基于HMAC-SHA256算法生成，用于验证消息内容的完整性和防篡改。</p>
     */
    private String signature;

    /**
     * 消息生成时间戳（Unix时间戳，毫秒）。
     * <p>用于签名验证中的时间戳校验，防止重放攻击。</p>
     */
    private long timestamp;

    /**
     * 消息类型枚举定义。
     */
    public enum MessageType {
        /** 认证请求消息 */
        AUTH_REQUEST,
        /** 认证响应消息 */
        AUTH_RESPONSE,
        /** 文件分片数据消息 */
        FILE_CHUNK,
        /** 确认应答消息 */
        ACK,
        /** 传输结束信号消息 */
        END,
        /** 错误通知消息 */
        ERROR,
        /** 心跳检测消息 */
        HEARTBEAT,
        /** 文件元数据消息 */
        FILE_METADATA
    }

    /**
     * 传输类别枚举定义。
     */
    public enum TransferCategory {
        /** 普通数据 */
        NORMAL_DATA("NORMAL_DATA"),
        /** 文件数据 */
        FILE_DATA("FILE_DATA"),
        /** 控制指令 */
        CONTROL_COMMAND("CONTROL_COMMAND"),
        /** 元数据 */
        METADATA("METADATA");

        /** 枚举对应的字符串值 */
        private final String value;

        /**
         * 构造方法。
         *
         * @param value 枚举对应的字符串值
         */
        TransferCategory(String value) {
            this.value = value;
        }

        /**
         * 获取枚举对应的字符串值。
         *
         * @return 字符串值
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * 默认无参构造方法。
     * <p>用于JSON反序列化及Spring等框架的实例化需求。</p>
     */
    public TransferMessage() {
    }

    /**
     * 构建认证请求消息。
     *
     * @param validateFlag 认证令牌
     * @return 认证请求消息对象
     */
    public static TransferMessage authRequest(String validateFlag) {
        TransferMessage msg = new TransferMessage();
        msg.setValidateFlag(validateFlag);
        msg.setMessageType(MessageType.AUTH_REQUEST);
        msg.setTransferType(TransferCategory.CONTROL_COMMAND.getValue());
        return msg;
    }

    /**
     * 构建认证响应消息。
     *
     * @param success      认证是否成功
     * @param errorMessage 错误信息（认证失败时有效）
     * @return 认证响应消息对象
     */
    public static TransferMessage authResponse(boolean success, String errorMessage) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.AUTH_RESPONSE);
        msg.setTransferType(TransferCategory.CONTROL_COMMAND.getValue());
        msg.setSuccess(success);
        msg.setErrorMessage(errorMessage);
        return msg;
    }

    /**
     * 构建文件分片消息（兼容旧版本字段）。
     *
     * @param data             分片二进制数据
     * @param chunkIndex       当前分片序号
     * @param totalChunks      分片总数
     * @param fileName         文件名称
     * @param fileLastModified 文件最后修改时间戳
     * @return 文件分片消息对象
     */
    public static TransferMessage fileChunk(byte[] data, int chunkIndex, int totalChunks,
                                          String fileName, long fileLastModified) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.FILE_CHUNK);
        msg.setTransferType(TransferCategory.FILE_DATA.getValue());
        msg.setData(data);
        msg.setChunkIndex(chunkIndex);
        msg.setTotalChunks(totalChunks);
        msg.setFileName(fileName);
        msg.setFileLastModified(fileLastModified);

        ChunkParams params = new ChunkParams(chunkIndex, totalChunks,
            data.length, (long) chunkIndex * data.length);
        msg.setChunkParams(params);

        FileMetadata metadata = new FileMetadata();
        metadata.setFileName(fileName);
        metadata.setLastModified(fileLastModified);
        msg.setFileMetadata(metadata);

        return msg;
    }

    /**
     * 构建文件分片消息（使用完整分片参数和文件元数据）。
     *
     * @param data         分片二进制数据
     * @param chunkParams  分片参数对象
     * @param fileMetadata 文件元数据对象
     * @return 文件分片消息对象
     */
    public static TransferMessage fileChunkWithMetadata(byte[] data, ChunkParams chunkParams,
                                                       FileMetadata fileMetadata) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.FILE_CHUNK);
        msg.setTransferType(TransferCategory.FILE_DATA.getValue());
        msg.setData(data);
        msg.setChunkParams(chunkParams);
        msg.setFileMetadata(fileMetadata);

        msg.setChunkIndex(chunkParams.getChunkIndex());
        msg.setTotalChunks(chunkParams.getTotalChunks());
        msg.setFileName(fileMetadata.getFileName());
        msg.setFileLastModified(fileMetadata.getLastModified());

        return msg;
    }

    /**
     * 构建文件元数据消息。
     *
     * @param metadata 文件元数据对象
     * @return 文件元数据消息对象
     */
    public static TransferMessage fileMetadata(FileMetadata metadata) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.FILE_METADATA);
        msg.setTransferType(TransferCategory.METADATA.getValue());
        msg.setFileMetadata(metadata);
        msg.setFileName(metadata.getFileName());
        msg.setFileLastModified(metadata.getLastModified());
        return msg;
    }

    /**
     * 构建确认应答消息。
     *
     * @param chunkIndex 已确认的分片序号
     * @param fileName   文件名称
     * @return 确认应答消息对象
     */
    public static TransferMessage ack(int chunkIndex, String fileName) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.ACK);
        msg.setTransferType(TransferCategory.CONTROL_COMMAND.getValue());
        msg.setChunkIndex(chunkIndex);
        msg.setFileName(fileName);
        return msg;
    }

    /**
     * 构建带应答标识的确认应答消息。
     *
     * @param chunkIndex 已确认的分片序号
     * @param fileName   文件名称
     * @param ackId      确认应答标识
     * @return 确认应答消息对象
     */
    public static TransferMessage ackWithParams(int chunkIndex, String fileName, String ackId) {
        TransferMessage msg = ack(chunkIndex, fileName);
        msg.setAckId(ackId);
        return msg;
    }

    /**
     * 构建传输结束信号消息。
     *
     * @param fileName 文件名称
     * @return 结束信号消息对象
     */
    public static TransferMessage end(String fileName) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.END);
        msg.setTransferType(TransferCategory.CONTROL_COMMAND.getValue());
        msg.setEndFlag(true);
        msg.setFileName(fileName);
        return msg;
    }

    /**
     * 构建带文件元数据的传输结束信号消息。
     *
     * @param fileName 文件名称
     * @param metadata 文件元数据对象
     * @return 结束信号消息对象
     */
    public static TransferMessage endWithMetadata(String fileName, FileMetadata metadata) {
        TransferMessage msg = end(fileName);
        msg.setFileMetadata(metadata);
        return msg;
    }

    /**
     * 构建错误通知消息。
     *
     * @param errorMessage 错误信息描述
     * @return 错误通知消息对象
     */
    public static TransferMessage error(String errorMessage) {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.ERROR);
        msg.setTransferType(TransferCategory.CONTROL_COMMAND.getValue());
        msg.setSuccess(false);
        msg.setErrorMessage(errorMessage);
        return msg;
    }

    /**
     * 构建心跳检测消息。
     *
     * @return 心跳检测消息对象
     */
    public static TransferMessage heartbeat() {
        TransferMessage msg = new TransferMessage();
        msg.setMessageType(MessageType.HEARTBEAT);
        msg.setTransferType(TransferCategory.CONTROL_COMMAND.getValue());
        return msg;
    }

    /**
     * 获取认证令牌。
     *
     * @return 认证令牌
     */
    public String getValidateFlag() {
        return validateFlag;
    }

    /**
     * 设置认证令牌。
     *
     * @param validateFlag 认证令牌
     */
    public void setValidateFlag(String validateFlag) {
        this.validateFlag = validateFlag;
    }

    /**
     * 获取消息类型。
     *
     * @return 消息类型枚举值
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * 设置消息类型。
     *
     * @param messageType 消息类型枚举值
     */
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    /**
     * 获取消息携带的二进制数据。
     *
     * @return 二进制数据字节数组
     */
    public byte[] getData() {
        return data;
    }

    /**
     * 设置消息携带的二进制数据。
     *
     * @param data 二进制数据字节数组
     */
    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * 获取传输类型。
     *
     * @return 传输类型字符串值
     */
    public String getTransferType() {
        return transferType;
    }

    /**
     * 设置传输类型。
     *
     * @param transferType 传输类型字符串值
     */
    public void setTransferType(String transferType) {
        this.transferType = transferType;
    }

    /**
     * 判断是否为传输结束消息。
     *
     * @return {@code true} 表示传输结束，{@code false} 表示未结束
     */
    public boolean isEndFlag() {
        return endFlag;
    }

    /**
     * 设置传输结束标识。
     *
     * @param endFlag {@code true} 表示传输结束
     */
    public void setEndFlag(boolean endFlag) {
        this.endFlag = endFlag;
    }

    /**
     * 获取分片参数对象。
     *
     * @return 分片参数对象，可能为 {@code null}
     */
    public ChunkParams getChunkParams() {
        return chunkParams;
    }

    /**
     * 设置分片参数对象，并同步更新 {@link #chunkIndex} 和 {@link #totalChunks} 字段。
     *
     * @param chunkParams 分片参数对象
     */
    public void setChunkParams(ChunkParams chunkParams) {
        this.chunkParams = chunkParams;
        if (chunkParams != null) {
            this.chunkIndex = chunkParams.getChunkIndex();
            this.totalChunks = chunkParams.getTotalChunks();
        }
    }

    /**
     * 获取文件元数据对象。
     *
     * @return 文件元数据对象，可能为 {@code null}
     */
    public FileMetadata getFileMetadata() {
        return fileMetadata;
    }

    /**
     * 设置文件元数据对象，并同步更新 {@link #fileName} 和 {@link #fileLastModified} 字段。
     *
     * @param fileMetadata 文件元数据对象
     */
    public void setFileMetadata(FileMetadata fileMetadata) {
        this.fileMetadata = fileMetadata;
        if (fileMetadata != null) {
            this.fileName = fileMetadata.getFileName();
            this.fileLastModified = fileMetadata.getLastModified();
        }
    }

    /**
     * 获取当前分片序号。
     * <p>若 {@link #chunkParams} 不为空，优先返回其中的 chunkIndex。</p>
     *
     * @return 当前分片序号
     */
    public int getChunkIndex() {
        if (chunkParams != null) {
            return chunkParams.getChunkIndex();
        }
        return chunkIndex;
    }

    /**
     * 设置当前分片序号，并同步更新 {@link #chunkParams} 中的值。
     *
     * @param chunkIndex 当前分片序号
     */
    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
        if (chunkParams != null) {
            chunkParams.setChunkIndex(chunkIndex);
        }
    }

    /**
     * 获取分片总数。
     * <p>若 {@link #chunkParams} 不为空，优先返回其中的 totalChunks。</p>
     *
     * @return 分片总数
     */
    public int getTotalChunks() {
        if (chunkParams != null) {
            return chunkParams.getTotalChunks();
        }
        return totalChunks;
    }

    /**
     * 设置分片总数，并同步更新 {@link #chunkParams} 中的值。
     *
     * @param totalChunks 分片总数
     */
    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
        if (chunkParams != null) {
            chunkParams.setTotalChunks(totalChunks);
        }
    }

    /**
     * 获取文件最后修改时间戳。
     * <p>若 {@link #fileMetadata} 不为空，优先返回其中的 lastModified。</p>
     *
     * @return 最后修改时间戳（毫秒）
     */
    public long getFileLastModified() {
        if (fileMetadata != null) {
            return fileMetadata.getLastModified();
        }
        return fileLastModified;
    }

    /**
     * 设置文件最后修改时间戳，并同步更新 {@link #fileMetadata} 中的值。
     *
     * @param fileLastModified 最后修改时间戳（毫秒）
     */
    public void setFileLastModified(long fileLastModified) {
        this.fileLastModified = fileLastModified;
        if (fileMetadata != null) {
            fileMetadata.setLastModified(fileLastModified);
        }
    }

    /**
     * 获取文件名称。
     * <p>若 {@link #fileMetadata} 不为空，优先返回其中的 fileName。</p>
     *
     * @return 文件名称（含扩展名）
     */
    public String getFileName() {
        if (fileMetadata != null) {
            return fileMetadata.getFileName();
        }
        return fileName;
    }

    /**
     * 设置文件名称，并同步更新 {@link #fileMetadata} 中的值。
     *
     * @param fileName 文件名称（含扩展名）
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
        if (fileMetadata != null) {
            fileMetadata.setFileName(fileName);
        }
    }

    /**
     * 获取确认应答标识。
     *
     * @return 确认应答标识，可能为 {@code null}
     */
    public String getAckId() {
        return ackId;
    }

    /**
     * 设置确认应答标识。
     *
     * @param ackId 确认应答标识
     */
    public void setAckId(String ackId) {
        this.ackId = ackId;
    }

    /**
     * 判断操作是否成功。
     *
     * @return {@code true} 表示成功，{@code false} 表示失败
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 设置操作成功标识。
     *
     * @param success {@code true} 表示成功
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * 获取错误信息描述。
     *
     * @return 错误信息，可能为 {@code null}
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 设置错误信息描述。
     *
     * @param errorMessage 错误信息
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 获取消息签名值。
     *
     * @return 签名值，可能为 {@code null}
     */
    public String getSignature() {
        return signature;
    }

    /**
     * 设置消息签名值。
     *
     * @param signature 签名值
     */
    public void setSignature(String signature) {
        this.signature = signature;
    }

    /**
     * 获取消息生成时间戳。
     *
     * @return 时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 设置消息生成时间戳。
     *
     * @param timestamp 时间戳（毫秒）
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 返回包含当前对象关键字段值的字符串表示。
     * <p>签名值以 "present"/"null" 形式展示，避免日志中暴露完整签名。</p>
     *
     * @return 字段值字符串
     */
    @Override
    public String toString() {
        return "TransferMessage{" +
                "messageType=" + messageType +
                ", transferType='" + transferType + '\'' +
                ", endFlag=" + endFlag +
                ", chunkParams=" + chunkParams +
                ", fileMetadata=" + fileMetadata +
                ", fileName='" + fileName + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", totalChunks=" + totalChunks +
                ", success=" + success +
                ", signature='" + (signature != null ? "present" : "null") + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}