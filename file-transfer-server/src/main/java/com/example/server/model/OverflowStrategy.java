package com.example.server.model;

/**
 * 队列溢出处理策略
 */
public enum OverflowStrategy {
    
    /**
     * 拒绝新任务：队列满时直接拒绝新的入队请求
     */
    REJECT,
    
    /**
     * 降级处理：将高优先级任务降级为低优先级，尝试入队
     */
    DEGRADE,
    
    /**
     * 移除最旧任务：移除队列中最旧的低优先级任务，为新任务腾出空间
     */
    EVICT_OLDEST,
    
    /**
     * 临时扩容：临时增加队列容量（需要配置临时扩容上限）
     */
    TEMPORARY_EXPAND
}