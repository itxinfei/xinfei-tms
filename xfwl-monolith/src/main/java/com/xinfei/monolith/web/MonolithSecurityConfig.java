package com.xinfei.monolith.web;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;

import com.xinfei.auth.interceptor.OperLogInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 单体版统一安全配置（装配层，不修改微服务）。
 *
 * <p>微服务里鉴权分两层：网关登录闸门（白名单外要求登录）+ 各服务 Same-Token 入站校验。
 * 单体合并后，微服务自带的两个 MVC 登录配置（auth/netty 的 SaTokenConfigure）已在
 * {@code MonolithApplication} 的 ComponentScan 中不加载，由本类按模块前缀口径统一复刻：</p>
 *
 * <ul>
 *   <li>登录校验仅作用于"前端入口"：BFF 全部（/__bff/**）与前端直连的三个内部服务
 *       （auth/netty/file-server）；仅被 Feign 回环调用的内部服务（base/oms/work/user/
 *       dispatch/aggregation/optimizer）不要求登录，与微服务一致；</li>
 *   <li>白名单：auth 匿名/内部端点、netty 服务间 Feign 端点、file 图片匿名查看、探针；</li>
 *   <li>OperLog 审计：仅对 auth 服务路径注册，沿用微服务排除口径。</li>
 * </ul>
 *
 * <p>Same-Token 入站校验仍由各模块 5 个 SameTokenConfigure 全局承担，本类不重复。</p>
 */
@Configuration
public class MonolithSecurityConfig implements WebMvcConfigurer {

    private final OperLogInterceptor operLogInterceptor;

    public MonolithSecurityConfig(OperLogInterceptor operLogInterceptor) {
        this.operLogInterceptor = operLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录校验：仅前端入口（BFF + 前端直连的 auth/netty/file）
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(
                        "/__bff/**",
                        "/__svc/xfwl-auth/**",
                        "/__svc/xfwl-netty/**",
                        "/__svc/xfwl-file-server/**")
                .excludePathPatterns(
                        // auth 匿名接口（登录/验证码）
                        "/__svc/xfwl-auth/anno/**",
                        // auth 服务间 Feign 内部端点
                        "/__svc/xfwl-auth/internal/**",
                        // netty 服务间 Feign 端点
                        "/__svc/xfwl-netty/alarm/report",
                        "/__svc/xfwl-netty/trace/byTasks",
                        // netty mock 手动入口
                        "/__svc/xfwl-netty/mock/**",
                        // file 图片匿名查看（img src 无法携带 token）
                        "/__svc/xfwl-file-server/view/**",
                        // 探针
                        "/actuator/**");

        // 写操作审计：仅 auth 服务自身管理端点
        registry.addInterceptor(operLogInterceptor)
                .addPathPatterns("/__svc/xfwl-auth/**")
                .excludePathPatterns(
                        "/__svc/xfwl-auth/anno/**",
                        "/__svc/xfwl-auth/internal/**",
                        "/actuator/**");
    }
}
