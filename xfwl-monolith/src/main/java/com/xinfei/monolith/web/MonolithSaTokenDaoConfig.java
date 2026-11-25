package com.xinfei.monolith.web;

import cn.dev33.satoken.dao.SaTokenDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 单体 Sa-Token DAO 装配。
 *
 * <p>{@code SaTokenDaoForRedisTemplate} 经其 jar 内 AutoConfiguration.imports 自动注册。
 * 此处再声明 {@link MonolithSaTokenDao} 并标 {@link Primary}，使 Sa-Token 注入 SaTokenDao
 * 时优先选用兼容老 Redis 的实现；原自动配置 bean 仍保留、不影响其它装配。</p>
 */
@Configuration
public class MonolithSaTokenDaoConfig {

    @Bean
    @Primary
    public SaTokenDao monolithSaTokenDao(RedisConnectionFactory connectionFactory) {
        MonolithSaTokenDao dao = new MonolithSaTokenDao();
        // 初始化父类的 StringRedisTemplate（幂等，内部 isInit 门控）
        dao.init(connectionFactory);
        return dao;
    }
}
