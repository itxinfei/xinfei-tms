package com.xinfei.monolith.web;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * 单体版 Feign 回环装配。
 *
 * <p>微服务版中 BFF 通过 Feign 经服务发现调用各内部服务；单体里服务发现被
 * SimpleDiscoveryClient 静态指向本进程（localhost:8080），内部服务 Controller
 * 已加模块前缀，因此这里给每个 Feign 请求按目标服务名追加内部前缀
 * （/__svc/xfwl-base 等），使回环请求命中对应 Controller。</p>
 *
 * <p>注意路径构成：{@code @FeignClient(path="/carrier")} 的类级路径在 {@link Target#url()}
 * （如 http://xfwl-base/carrier），而 {@link RequestTemplate#path()} 只含方法级路径
 * （如 /page）。因此必须把前缀插到"类级路径之前"，重设 target 的 origin 并把
 * 前缀+类级路径+方法路径作为新 uri，否则会拼成 /carrier/__svc/.../page 导致 404。
 * 无需修改任何 @FeignClient 接口，49 个客户端统一生效。</p>
 */
@Configuration
public class FeignLoopbackConfig {

    @Bean
    public RequestInterceptor monolithPrefixInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                Target<?> target = template.feignTarget();
                if (target == null) {
                    return;
                }
                String prefix = ModulePrefixResolver.prefixForService(target.name());
                if (prefix == null) {
                    return;
                }

                // 解析 target.url()：origin（http://xfwl-base）+ 类级路径（/carrier）
                URI targetUri = URI.create(target.url());
                String origin = targetUri.getScheme() + "://" + targetUri.getAuthority();
                String classPath = targetUri.getRawPath() == null ? "" : targetUri.getRawPath();

                String methodPath = template.path();
                if (methodPath == null || methodPath.isBlank()) {
                    methodPath = "";
                }

                String fullPath = prefix + classPath + methodPath;
                if (fullPath.startsWith(prefix + "/") || fullPath.equals(prefix)) {
                    // 已带前缀，避免重复处理
                    if (template.path().startsWith(prefix)) {
                        return;
                    }
                }

                template.target(origin);
                template.uri(fullPath);
            }
        };
    }
}
