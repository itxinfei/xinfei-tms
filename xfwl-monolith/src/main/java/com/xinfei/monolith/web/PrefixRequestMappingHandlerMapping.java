package com.xinfei.monolith.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 单体版自定义 {@link RequestMappingHandlerMapping}。
 *
 * <p>利用 Spring MVC 原生的 {@code pathPrefixes} 机制（按 Controller 类型谓词
 * 追加路径前缀），为含 Controller 的全部模块注册唯一前缀。原本同进程内重复的
 * 相对路径（如 BFF 与内部服务的 /carrier）被拆到不同前缀下，注册期不再产生
 * ambiguous mapping。每个前缀配一个谓词，通过 {@link ModulePrefixResolver}
 * 计算该类的真实前缀并与当前 key 比较，只有归属匹配的 Controller 才命中。</p>
 */
public class PrefixRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    /** 内部服务模块（Feign 回环目标）。 */
    private static final List<String> SERVICE_MODULES = List.of(
            "xfwl-base", "xfwl-oms", "xfwl-work", "xfwl-user", "xfwl-dispatch",
            "xfwl-netty", "xfwl-aggregation", "xfwl-auth", "xfwl-file-server",
            "xfwl-optimizer");

    /** 移动/管理端 BFF 模块（前端目标）。 */
    private static final List<String> BFF_MODULES = List.of(
            "xfwl-web-manager", "xfwl-web-driver", "xfwl-web-courier", "xfwl-web-customer");

    @Override
    public void afterPropertiesSet() {
        Map<String, Predicate<Class<?>>> prefixes = new LinkedHashMap<>();
        for (String module : SERVICE_MODULES) {
            String prefix = ModulePrefixResolver.SVC_PREFIX + module;
            prefixes.put(prefix, handlerType ->
                    prefix.equals(ModulePrefixResolver.prefixFor(handlerType)));
        }
        for (String module : BFF_MODULES) {
            String prefix = ModulePrefixResolver.BFF_PREFIX + module.substring("xfwl-".length());
            prefixes.put(prefix, handlerType ->
                    prefix.equals(ModulePrefixResolver.prefixFor(handlerType)));
        }
        setPathPrefixes(prefixes);
        super.afterPropertiesSet();
    }
}
