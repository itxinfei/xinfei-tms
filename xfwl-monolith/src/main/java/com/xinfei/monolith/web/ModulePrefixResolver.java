package com.xinfei.monolith.web;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单体 Web 层模块归属解析器。
 *
 * <p>多个微服务模块（base/oms/work/user/netty/dispatch 及四个 BFF）共用
 * {@code com.xinfei.controller} 基础包，无法仅凭包名区分来源。这里在运行时
 * 读取 Controller 类的实际资源 URL（fat jar 嵌套 jar 路径或 target/classes 目录），
 * 解析出 Maven 模块名，再映射为单体内部唯一前缀，使原本同路径的 Controller
 * 在 {@code RequestMappingHandlerMapping} 注册时互不冲突。</p>
 *
 * <p>前缀规则：
 * <ul>
 *   <li>BFF（xfwl-web-*）：{@code /__bff/<短名>}，如 xfwl-web-manager → /__bff/web-manager；</li>
 *   <li>内部服务（其余）：{@code /__svc/<模块名>}，如 xfwl-base → /__svc/xfwl-base。</li>
 * </ul>
 * 双下划线前缀仅用于进程内路由，不对外暴露，也不与业务单词路径冲突。</p>
 */
public final class ModulePrefixResolver {

    /** fat jar 内嵌套业务 jar：BOOT-INF/lib/xfwl-base-1.0.0-SNAPSHOT.jar */
    private static final Pattern NESTED_JAR =
            Pattern.compile("BOOT-INF/lib/(xfwl-[a-z0-9-]+?)-\\d");
    /** 开发/exploded 类目录：.../xfwl-base/target/classes/... */
    private static final Pattern CLASSES_DIR =
            Pattern.compile("(xfwl-[a-z0-9-]+?)/target/classes");
    /** 任意已命名模块 jar：xfwl-xxx-1.0.0... */
    private static final Pattern ANY_JAR =
            Pattern.compile("(xfwl-[a-z0-9-]+?)-\\d+\\.");
    /** 兜底：路径中出现的模块名 */
    private static final Pattern ANY_MODULE =
            Pattern.compile("(xfwl-[a-z0-9-]+)");

    public static final String BFF_PREFIX = "/__bff/";
    public static final String SVC_PREFIX = "/__svc/";

    private static final Map<Class<?>, String> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> SERVICE_CACHE = new ConcurrentHashMap<>();

    private ModulePrefixResolver() {
    }

    /**
     * 解析 Controller 类的内部前缀（含末尾斜杠前的完整前缀，不含尾部斜杠）。
     *
     * @param handlerType Controller 类型
     * @return 内部前缀；无法识别模块时返回 {@code null}（不加前缀，保持原路径）
     */
    public static String prefixFor(Class<?> handlerType) {
        return CLASS_CACHE.computeIfAbsent(handlerType, ModulePrefixResolver::resolve);
    }

    /**
     * 按 Feign 目标服务名返回内部前缀，供 Feign 回环请求拼接。
     *
     * @param serviceName 服务名（如 xfwl-base）
     * @return 内部前缀，如 /__svc/xfwl-base
     */
    public static String prefixForService(String serviceName) {
        return SERVICE_CACHE.computeIfAbsent(serviceName, name -> {
            if (name == null || name.isBlank()) {
                return null;
            }
            if (name.startsWith("xfwl-web-")) {
                return BFF_PREFIX + name.substring("xfwl-".length());
            }
            return SVC_PREFIX + name;
        });
    }

    private static String resolve(Class<?> handlerType) {
        String resource = "/" + handlerType.getName().replace('.', '/') + ".class";
        URL url = handlerType.getResource(resource);
        if (url == null) {
            return null;
        }
        String location = url.toString();
        String module = firstGroup(NESTED_JAR, location);
        if (module == null) {
            module = firstGroup(CLASSES_DIR, location);
        }
        if (module == null) {
            module = firstGroup(ANY_JAR, location);
        }
        if (module == null) {
            Matcher m = ANY_MODULE.matcher(location);
            String last = null;
            while (m.find()) {
                last = m.group(1);
            }
            module = last;
        }
        if (module == null) {
            return null;
        }
        if (module.startsWith("xfwl-web-")) {
            return BFF_PREFIX + module.substring("xfwl-".length());
        }
        return SVC_PREFIX + module;
    }

    private static String firstGroup(Pattern p, String input) {
        Matcher m = p.matcher(input);
        return m.find() ? m.group(1) : null;
    }
}
