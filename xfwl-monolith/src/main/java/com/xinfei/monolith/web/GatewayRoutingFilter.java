package com.xinfei.monolith.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.dev33.satoken.same.SaSameUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;
import java.util.Enumeration;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单体版内嵌网关路由过滤器。
 *
 * <p>复刻微服务版 Spring Cloud Gateway 的路由表与 {@code StripPrefix=2} 行为：
 * 前端请求仍带完整网关前缀（如 /api/manager/carrier/page），本过滤器去掉前两段
 * （/api/manager），把请求路径改写为对应模块的内部前缀
 * （/__bff/web-manager/carrier/page），并通过请求包装让请求继续走同一条过滤器链
 * （不使用 forward），保证 Sa-Token 上下文在同一正常链路上生效。</p>
 *
 * <p>同时扮演网关 ForwardSameTokenFilter：注入 Same-Token 头，使下游
 * SameTokenAuthInterceptor 放行。前端无需改动，只需把代理目标从网关 8760
 * 切换到单体 8080。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRoutingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutingFilter.class);

    /** 外部前缀（前两段）→ 目标模块前缀。与网关 application.yml 路由表一一对应。 */
    private static final Map<String, String> ROUTES = new LinkedHashMap<>();

    static {
        ROUTES.put("/api/auth", ModulePrefixResolver.SVC_PREFIX + "xfwl-auth");
        ROUTES.put("/api/v1", ModulePrefixResolver.SVC_PREFIX + "xfwl-auth");
        ROUTES.put("/api/manager", ModulePrefixResolver.BFF_PREFIX + "web-manager");
        ROUTES.put("/api/web-driver", ModulePrefixResolver.BFF_PREFIX + "web-driver");
        ROUTES.put("/api/web-courier", ModulePrefixResolver.BFF_PREFIX + "web-courier");
        ROUTES.put("/api/web-customer", ModulePrefixResolver.BFF_PREFIX + "web-customer");
        ROUTES.put("/api/netty-service", ModulePrefixResolver.SVC_PREFIX + "xfwl-netty");
        ROUTES.put("/api/file", ModulePrefixResolver.SVC_PREFIX + "xfwl-file-server");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;

        String[] segments = path.split("/");
        if (segments.length < 3) {
            filterChain.doFilter(request, response);
            return;
        }
        String externalPrefix = "/" + segments[1] + "/" + segments[2];
        String modulePrefix = ROUTES.get(externalPrefix);
        if (modulePrefix == null) {
            filterChain.doFilter(request, response);
            return;
        }

        StringBuilder target = new StringBuilder(modulePrefix);
        for (int i = 3; i < segments.length; i++) {
            target.append('/').append(segments[i]);
        }
        String targetPath = target.toString();

        String sameToken;
        try {
            sameToken = SaSameUtil.getToken();
        } catch (Exception e) {
            log.error("[单体网关] 生成 Same-Token 失败", e);
            sameToken = null;
        }

        // 复刻网关 ForwardSameTokenFilter：按 token 解析登录用户ID与租户ID并注入下游头。
        // 内部服务（如 base CarrierController）依赖 userid 头做身份校验，
        // 多租户插件依赖 tenantId 头，缺失会回落租户 0 导致 401/查询恒空。
        String tokenValue = request.getHeader("token");
        String userId = null;
        String tenantId = null;
        if (tokenValue != null && !tokenValue.isEmpty()) {
            try {
                Object loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdByToken(tokenValue);
                if (loginId != null) {
                    userId = String.valueOf(loginId);
                }
            } catch (Exception ignore) {
                // token 失效不阻断，下游鉴权自行处理
            }
            try {
                Object tid = cn.dev33.satoken.stp.StpUtil.getTokenSessionByToken(tokenValue)
                        .get("tenantId");
                if (tid != null) {
                    tenantId = String.valueOf(tid);
                }
            } catch (Exception ignore) {
                // 会话异常不阻断
            }
        }

        RewrittenRequest rewritten = new RewrittenRequest(request, targetPath,
                sameToken, userId, tenantId);
        // 基于改写后的 servletPath 重新解析并缓存路径，确保后续 handler 与拦截器使用新路径
        org.springframework.web.util.ServletRequestPathUtils.parseAndCache(rewritten);
        filterChain.doFilter(rewritten, response);
    }

    /**
     * 同时改写请求路径并注入 Same-Token 头的请求包装。
     */
    private static final class RewrittenRequest extends HttpServletRequestWrapper {

        private final String targetPath;
        private final String sameToken;
        private final String userId;
        private final String tenantId;

        private RewrittenRequest(HttpServletRequest request, String targetPath, String sameToken,
                                 String userId, String tenantId) {
            super(request);
            this.targetPath = targetPath;
            this.sameToken = sameToken;
            this.userId = userId;
            this.tenantId = tenantId;
        }

        /** 本包装额外注入/覆盖的头（值为 null 表示不注入）。 */
        private String injectedHeader(String name) {
            if (sameToken != null && SaSameUtil.SAME_TOKEN.equalsIgnoreCase(name)) {
                return sameToken;
            }
            if (userId != null && "userid".equalsIgnoreCase(name)) {
                return userId;
            }
            if (tenantId != null && "tenantId".equalsIgnoreCase(name)) {
                return tenantId;
            }
            return null;
        }

        @Override
        public String getServletPath() {
            return targetPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getRequestURI() {
            String contextPath = getContextPath();
            return (contextPath == null || contextPath.isEmpty())
                    ? targetPath
                    : contextPath + targetPath;
        }

        @Override
        public StringBuffer getRequestURL() {
            String scheme = getScheme();
            int port = getServerPort();
            StringBuilder url = new StringBuilder()
                    .append(scheme).append("://").append(getServerName());
            if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
                url.append(':').append(port);
            }
            url.append(getRequestURI());
            return new StringBuffer(url.toString());
        }

        @Override
        public String getHeader(String name) {
            String injected = injectedHeader(name);
            return injected != null ? injected : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String injected = injectedHeader(name);
            if (injected != null) {
                return Collections.enumeration(Collections.singletonList(injected));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.List<String> all = new java.util.ArrayList<>();
            java.util.List<String> extra = new java.util.ArrayList<>();
            if (sameToken != null) {
                extra.add(SaSameUtil.SAME_TOKEN);
            }
            if (userId != null) {
                extra.add("userid");
            }
            if (tenantId != null) {
                extra.add("tenantId");
            }
            Enumeration<String> names = super.getHeaderNames();
            while (names.hasMoreElements()) {
                String n = names.nextElement();
                if (extra.stream().noneMatch(e -> e.equalsIgnoreCase(n))) {
                    all.add(n);
                }
            }
            all.addAll(extra);
            return Collections.enumeration(all);
        }
    }
}
