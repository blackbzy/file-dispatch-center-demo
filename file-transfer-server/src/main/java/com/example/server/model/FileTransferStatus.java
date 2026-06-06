package com.example.server.model;

/**
 * 文件发送状态枚举。
 */
public enum FileTransferStatus {
    /** 未发送 */
    NOT_SENT,
    /** 发送中 */
    SENDING,
    /** 已发送 */
    SENT,
    /** 发送失败 */
    FAILED
}
