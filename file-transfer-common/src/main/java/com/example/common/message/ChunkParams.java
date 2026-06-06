package com.example.common.message;

import java.io.Serializable;

/**
 * 文件分片传输参数实体类。
 * <p>
 * 用于描述文件分片传输过程中的分片索引、分片总数、分片大小及分片偏移量等关键信息。
 * 该对象在发送方构建分片消息时创建，在接收方解析分片消息时使用，支持文件分片的精确定位与重组。
 * </p>
 *
 * @author example
 * @version 1.0
 * @since 1.0
 */
public class ChunkParams implements Serializable {

    /** 序列化版本号，用于保证序列化兼容性 */
    private static final long serialVersionUID = 1L;

    /**
     * 当前分片序号（从0开始）。
     * <p>取值范围：0 &le; chunkIndex &lt; totalChunks</p>
     */
    private int chunkIndex;

    /**
     * 分片总数。
     * <p>表示当前文件被切分后的总分片数量，取值必须大于0。</p>
     */
    private int totalChunks;

    /**
     * 分片大小（单位：字节）。
     * <p>表示每个分片的预期数据大小，通常由服务端配置决定（默认1MB）。
     * 最后一个分片的实际数据大小可能小于此值。</p>
     */
    private int chunkSize;

    /**
     * 当前分片在原始文件中的字节偏移量。
     * <p>计算公式：offset = chunkIndex * chunkSize</p>
     */
    private long offset;

    /**
     * 默认无参构造方法。
     * <p>用于JSON反序列化及Spring等框架的实例化需求。</p>
     */
    public ChunkParams() {
    }

    /**
     * 全参构造方法。
     *
     * @param chunkIndex  当前分片序号，从0开始
     * @param totalChunks 分片总数，必须大于0
     * @param chunkSize   分片大小（字节）
     * @param offset      当前分片在文件中的字节偏移量
     */
    public ChunkParams(int chunkIndex, int totalChunks, int chunkSize, long offset) {
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.offset = offset;
    }

    /**
     * 获取当前分片序号。
     *
     * @return 当前分片序号，从0开始
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * 设置当前分片序号。
     *
     * @param chunkIndex 当前分片序号，从0开始
     */
    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    /**
     * 获取分片总数。
     *
     * @return 分片总数
     */
    public int getTotalChunks() {
        return totalChunks;
    }

    /**
     * 设置分片总数。
     *
     * @param totalChunks 分片总数，必须大于0
     */
    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    /**
     * 获取分片大小。
     *
     * @return 分片大小（字节）
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * 设置分片大小。
     *
     * @param chunkSize 分片大小（字节）
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * 获取当前分片在文件中的字节偏移量。
     *
     * @return 字节偏移量
     */
    public long getOffset() {
        return offset;
    }

    /**
     * 设置当前分片在文件中的字节偏移量。
     *
     * @param offset 字节偏移量
     */
    public void setOffset(long offset) {
        this.offset = offset;
    }

    /**
     * 返回包含当前对象所有字段值的字符串表示。
     *
     * @return 字段值字符串
     */
    @Override
    public String toString() {
        return "ChunkParams{" +
                "chunkIndex=" + chunkIndex +
                ", totalChunks=" + totalChunks +
                ", chunkSize=" + chunkSize +
                ", offset=" + offset +
                '}';
    }
}