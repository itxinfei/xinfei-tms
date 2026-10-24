package com.xinfei.auth.common.v1;

import lombok.Data;

import java.io.Serializable;

/**
 * 按命名空间清空缓存的结果（系统管理-缓存管理运维页）。
 *
 * <p>达到单次删除上限时接口仍正常返回（非失败），由 {@link #capped} 告知前端
 * 本次被截断、可能尚有残留，避免前端把"部分删除"误判为失败而重复清空。</p>
 */
@Data
public class CacheClearResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实际删除的键数 */
    private long deleted;

    /** 是否触达单次上限而提前停止（true=可能仍有残留，需缩小前缀范围） */
    private boolean capped;

    /** 单次删除上限 */
    private long limit;
}
