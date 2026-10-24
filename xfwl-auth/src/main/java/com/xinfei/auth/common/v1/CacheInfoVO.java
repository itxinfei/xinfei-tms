package com.xinfei.auth.common.v1;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Redis 概览信息（系统管理-缓存管理运维页）。
 *
 * <p>指标取自 {@code INFO} 与 {@code DBSIZE}；类型分布为 SCAN 采样统计
 * （上限 2000 键），非全库精确值，仅用于 dev 运维观测。</p>
 */
@Data
public class CacheInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Redis 版本号 */
    private String redisVersion;

    /** 运行时长（秒） */
    private long uptimeSeconds;

    /** 当前连接的客户端数 */
    private long connectedClients;

    /** 已用内存（字节） */
    private long usedMemoryBytes;

    /** 已用内存（人类可读，如 1.5M） */
    private String usedMemoryHuman;

    /** 最大内存上限（人类可读；0 表示未限制时 INFO 返回 0） */
    private String maxMemoryHuman;

    /** 当前库键总数（DBSIZE） */
    private long dbSize;

    /** 键类型分布（按数量降序，采样统计） */
    private List<TypeCount> typeDistribution;

    /**
     * 类型-数量条目。
     */
    @Data
    public static class TypeCount implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Redis 类型：string/hash/list/set/zset 等 */
        private String type;

        /** 采样命中数量 */
        private long count;

        public TypeCount() {
        }

        public TypeCount(String type, long count) {
            this.type = type;
            this.count = count;
        }
    }
}
