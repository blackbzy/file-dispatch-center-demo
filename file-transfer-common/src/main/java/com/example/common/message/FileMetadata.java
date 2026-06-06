package com.example.common.message;

import java.io.Serializable;

/**
 * 文件元数据实体类。
 * <p>
 * 用于描述传输文件的基本属性信息，包括文件名、文件大小、文件类型、创建时间、最后修改时间及MD5校验值。
 * 该对象在文件传输开始前由发送方发送，供接收方进行文件完整性校验和属性匹配。
 * </p>
 *
 * @author example
 * @version 1.0
 * @since 1.0
 */
public class FileMetadata implements Serializable {

    /** 序列化版本号，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 文件名称（含扩展名）。
     * <p>示例：report.pdf、data.csv</p>
     */
    private String fileName;

    /**
     * 文件大小（单位：字节）。
     * <p>取值范围：fileSize &ge; 0</p>
     */
    private long fileSize;

    /**
     * 文件类型（扩展名）。
     * <p>示例：txt、pdf、jpg、zip</p>
     */
    private String fileType;

    /**
     * 文件创建时间戳（Unix时间戳，毫秒）。
     * <p>通过 {@link java.io.File#lastModified()} 获取</p>
     */
    private long createTime;

    /**
     * 文件最后修改时间戳（Unix时间戳，毫秒）。
     * <p>用于接收方判断本地是否存在更新版本的文件</p>
     */
    private long lastModified;

    /**
     * 文件内容的MD5校验值（32位小写十六进制字符串）。
     * <p>用于文件传输完成后的完整性校验</p>
     */
    private String md5;

    /**
     * 默认无参构造方法。
     * <p>用于JSON反序列化及Spring等框架的实例化需求。</p>
     */
    public FileMetadata() {
    }

    /**
     * 全参构造方法。
     *
     * @param fileName     文件名称（含扩展名）
     * @param fileSize     文件大小（字节）
     * @param fileType     文件类型（扩展名）
     * @param createTime   文件创建时间戳（毫秒）
     * @param lastModified 文件最后修改时间戳（毫秒）
     * @param md5          文件内容的MD5校验值（32位小写十六进制字符串）
     */
    public FileMetadata(String fileName, long fileSize, String fileType,
                       long createTime, long lastModified, String md5) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.createTime = createTime;
        this.lastModified = lastModified;
        this.md5 = md5;
    }

    /**
     * 获取文件名称。
     *
     * @return 文件名称（含扩展名）
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置文件名称。
     *
     * @param fileName 文件名称（含扩展名）
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取文件大小。
     *
     * @return 文件大小（字节）
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * 设置文件大小。
     *
     * @param fileSize 文件大小（字节）
     */
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * 获取文件类型。
     *
     * @return 文件类型（扩展名）
     */
    public String getFileType() {
        return fileType;
    }

    /**
     * 设置文件类型。
     *
     * @param fileType 文件类型（扩展名）
     */
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    /**
     * 获取文件创建时间戳。
     *
     * @return 创建时间戳（毫秒）
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 设置文件创建时间戳。
     *
     * @param createTime 创建时间戳（毫秒）
     */
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取文件最后修改时间戳。
     *
     * @return 最后修改时间戳（毫秒）
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * 设置文件最后修改时间戳。
     *
     * @param lastModified 最后修改时间戳（毫秒）
     */
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * 获取文件内容的MD5校验值。
     *
     * @return MD5校验值（32位小写十六进制字符串）
     */
    public String getMd5() {
        return md5;
    }

    /**
     * 设置文件内容的MD5校验值。
     *
     * @param md5 MD5校验值（32位小写十六进制字符串）
     */
    public void setMd5(String md5) {
        this.md5 = md5;
    }

    /**
     * 返回包含当前对象所有字段值的字符串表示。
     *
     * @return 字段值字符串
     */
    @Override
    public String toString() {
        return "FileMetadata{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", fileType='" + fileType + '\'' +
                ", createTime=" + createTime +
                ", lastModified=" + lastModified +
                ", md5='" + md5 + '\'' +
                '}';
    }
}