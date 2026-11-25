package com.xinfei.monolith.web;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 兼容老版本 Redis（&lt; 6.0）的 Sa-Token DAO。
 *
 * <p>父类 {@link SaTokenDaoForRedisTemplate#setStringAndKeepTTL} 在"更新值且保留剩余 TTL"时
 * 发出 {@code SET key value KEEPTTL}，而 KEEPTTL 是 Redis 6.0 才引入的选项；本地开发机的
 * Redis 3.0.504 不识别，会回 ERR syntax error，导致登录写会话 500。</p>
 *
 * <p>这里用等价且 Redis 3.0 即支持的两步操作替代：先读剩余 TTL（秒），再用普通
 * {@code SET key value EX 剩余秒}；无 TTL 的永久键则普通 SET 不设过期。语义与 KEEPTTL 一致，
 * 对 Redis 6+ 同样适用，故不影响生产。仅在单体装配层覆盖，不修改微服务与 Sa-Token 源码。</p>
 */
public class MonolithSaTokenDao extends SaTokenDaoForRedisTemplate {

    @Override
    public void setStringAndKeepTTL(String key, String value) {
        Long ttlSeconds = stringRedisTemplate.getExpire(key);
        if (ttlSeconds == null || ttlSeconds < 0) {
            // -1 永久键 / -2 键不存在：普通写入，不附加过期
            stringRedisTemplate.opsForValue().set(key, value);
        } else {
            stringRedisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        }
    }
}
