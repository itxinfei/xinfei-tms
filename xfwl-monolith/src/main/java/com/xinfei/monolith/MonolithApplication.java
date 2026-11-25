package com.xinfei.monolith;

import com.xinfei.monolith.config.FqnFallbackBeanNameGenerator;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 心飞物流TMS 单体版启动类。
 *
 * <p>装配口径：
 * <ul>
 *   <li>扫描全部业务模块（com.xinfei），controller/service/entity/mapper 均来自微服务版原模块，零代码复制；</li>
 *   <li>排除各微服务自己的启动类（*Application）：它们的 {@code @SpringBootApplication}
 *       元注解带 @ComponentScan，在单体里会被当配置类触发重复扫描，启动类本身无业务逻辑，排除无副作用；</li>
 *   <li>启用 {@code @EnableFeignClients}：约 49 个 Feign 客户端在同进程生成代理，
 *       服务发现用 SimpleDiscoveryClient 静态回环到本机，配合内部前缀命中对应 Controller；</li>
 *   <li>启用 {@code @ConfigurationPropertiesScan}：各模块 @ConfigurationProperties 类
 *       原本由各启动类扫描注册，启动类被排除后在此统一扫描；</li>
 *   <li>不接入 Nacos，配置收敛到本模块 application.yml。</li>
 * </ul>
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.xinfei",
        nameGenerator = FqnFallbackBeanNameGenerator.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.xinfei\\.(?!monolith\\.)[\\w.]*Application"
                ),
                // 排除微服务自带的 MVC 登录拦截配置：其白名单基于相对路径（/anno/** 等），
                // 单体里路径带模块前缀，由单体 MonolithSecurityConfig 以前缀口径统一复刻，
                // 避免跨模块全局拦截器互相干扰。仅不加载、不修改微服务文件。
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                com.xinfei.auth.config.SaTokenConfigure.class,
                                com.xinfei.config.SaTokenConfigure.class,
                                // 该 advice 为 HIGHEST_PRECEDENCE 全局兜底，微服务里只作用于 netty 进程，
                                // 单体里会跨模块抢先把参数校验等异常收口成 500，故不加载，
                                // 由 MonolithExceptionHandler 以前缀/异常口径统一复刻。
                                com.xinfei.config.SaTokenExceptionHandler.class
                        }
                )
        }
)
@ConfigurationPropertiesScan(basePackages = "com.xinfei")
@MapperScan(basePackages = "com.xinfei", annotationClass = Mapper.class,
        nameGenerator = FqnFallbackBeanNameGenerator.class)
@EnableFeignClients(basePackages = "com.xinfei")
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
    }
}
