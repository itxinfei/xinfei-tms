package com.xinfei.monolith.web;

import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 单体版 Web MVC 装配扩展。
 *
 * <p>用 {@link PrefixRequestMappingHandlerMapping} 替换 Boot 默认的
 * {@link RequestMappingHandlerMapping}，使各模块 Controller 在注册时获得
 * 模块唯一前缀。该类仅在单体模块生效，微服务各进程不依赖此模块，零影响。</p>
 */
@Configuration
public class MonolithWebMvcRegistrations implements WebMvcRegistrations {

    @Override
    public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
        return new PrefixRequestMappingHandlerMapping();
    }
}
