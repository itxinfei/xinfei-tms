package com.xinfei.auth.common.v1;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单个缓存键的内容详情（系统管理-缓存管理运维页）。
 *
 * <p>按 Redis TYPE 分发读取：string 直接取值（UTF-8 字节上限 4096），
 * hash/list/set/zset 容器类最多返回前 100 个元素，{@link #totalSize} 给全量数；
 * stream/module 等不支持的类型 {@link #supported}=false。</p>
 */
@Data
public class CacheValueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 键名 */
    private String key;

    /** Redis 类型：string/hash/list/set/zset/none 等 */
    private String type;

    /** 剩余生存时间（秒）；-1=永不过期；-2=键不存在 */
    private Long ttl;

    /** 是否支持内容预览（stream/module 等为 false） */
    private boolean supported;

    /** 内容是否被截断（string 超 4096 字节 / 容器超 100 元素） */
    private boolean truncated;

    /** 全量大小：string=UTF-8 字节数；hash/list/set/zset=元素总数 */
    private long totalSize;

    /** string 类型的值（其余类型为 null） */
    private String stringValue;

    /** 容器类型的元素条目（string 类型为空列表） */
    private List<Entry> entries;

    /**
     * 容器元素条目：按容器类型择字段填充。
     * hash 填 field/value；list 填 value；set 填 member；zset 填 member/score。
     */
    @Data
    public static class Entry implements Serializable {

        private static final long serialVersionUID = 1L;

        /** hash 字段名 */
        private String field;

        /** hash 字段值 / list 元素值 */
        private String value;

        /** set / zset 成员 */
        private String member;

        /** zset 成员分数 */
        private Double score;
    }
}
