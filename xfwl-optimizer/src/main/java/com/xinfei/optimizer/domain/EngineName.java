package com.xinfei.optimizer.domain;

/**
 * 求解引擎标识。RULE 永远可用，作为硬约束兜底；OR_TOOLS 和 TIMEFOLD 按配置灰度开启。
 */
public enum EngineName {
    RULE,
    OR_TOOLS,
    TIMEFOLD
}
