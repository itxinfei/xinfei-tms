package com.xinfei.auth.common.v1;

import lombok.Data;

import java.io.Serializable;

/**
 * 缓存命名空间聚合条目（按键前缀分组，供运维页按业务域浏览/清空）。
 */
@Data
public class CacheNamespaceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命名空间前缀（键首段；Authorization: 开头取前两段） */
    private String prefix;

    /** 采样命中的键数量 */
    private long count;

    /** 可直接用于 /cache 分页搜索的 glob 表达式（prefix + ":*"） */
    private String pattern;
}
