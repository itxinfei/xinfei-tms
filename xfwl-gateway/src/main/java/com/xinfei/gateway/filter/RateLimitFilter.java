package com.xinfei.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关限流过滤器（T-SEC-01/A1，覆盖 69 号文档 A1 / A19 兜底）
 *
 * <p>Redis 固定窗口计数（同 Redis 共享计数，多网关实例限流天然共享），四档：</p>
 * <ul>
 *   <li>登录接口 /api/auth/anno/login：{login-per-minute:30} 次/分/IP（防暴力破解/撞库）；</li>
 *   <li>验证码接口 /api/auth/anno/captcha：{captcha-per-minute:30} 次/分/IP（防短信/图形轰炸）；</li>
 *   <li>登录用户：{user-per-minute:1000} 次/分/user（token 转出 userId）；</li>
 *   <li>IP 兜底：{ip-per-minute:5000} 次/分/IP（真实 IP 来自 RequestSanitizeFilter，防伪造 XFF）。</li>
 * </ul>
 *
 * <p>阈值为 dev 演示口径（办公室 NAT 同出口 IP 多人共用，阈值过严会误伤正常登录/轮询——
 * 安全措施不得影响正常业务）；生产上线时经 Nacos 收紧（login 5/captcha 10/user 300/ip 2000）。</p>
 *
 * <p>超限统一 429 JSON（含 retryAfter）；Redis 异常 fail-open（告警日志，不阻断业务——
 * 限流是防御项，不能反过来成为单点故障）。阈值经 yml/Nacos 可调（禁硬编码纪律）。</p>
 *
 * <p>总开关 {@code xinfei.security.rate-limit.enabled}（缺省 true）：dev/test 显式置 false
 * 整体关闭（2026-10-23 拍板：开发期仅少量测试人员，限流误伤登录/验证码/轮询）；生产必须打开。</p>
 *
 * <p>order=-250：晚于 RequestSanitizeFilter(-300)（真实 IP 已就绪）、SecurityHeadersFilter(-260)
 * （安全头已设置），早于 ForwardSameTokenFilter(-100)。</p>
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String ATTR_USER_ID = "gateway.userId";

    /** 计数窗口过期秒数：60s 窗口 + 10s 冗余 */
    private static final long WINDOW_TTL_SECONDS = 70L;

    /**
     * 原子自增并保证计数 key 必有 TTL：INCR 后若 TTL &lt; 0（-1 表示 key 存在但无过期时间，
     * 历史脏数据/异常重建可能产生），立即补设过期，杜绝计数 key 永不过期把某 IP/用户永久锁死
     * （2026-10-23 实测 captcha key TTL=-1、计数只增不减，验证码接口被永久 429 的事故根因）。
     * KEYS[1]=计数 key；ARGV[1]=过期秒数；返回自增后的计数。
     */
    private static final RedisScript<Long> INCR_ENSURE_TTL = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]); "
          + "if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end; "
          + "return c;", Long.class);

    private final ReactiveStringRedisTemplate redis;

    /**
     * 限流总开关，缺省打开（安全默认：任何未显式配置的环境都受保护）。
     * dev/test 经 application.yml 显式置 false 关闭（仅少量测试人员，限流会误伤登录/验证码/看板轮询）；
     * 生产上线必须置 true（T-SEC-01）。关闭时直接放行、不产生任何 Redis 计数 key。
     */
    @Value("${xinfei.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    // dev 演示口径（办公室 NAT 多人共用出口 IP，防误伤正常业务）；生产经 Nacos 收紧
    @Value("${xinfei.security.rate-limit.login-per-minute:30}")
    private long loginPerMinute;

    @Value("${xinfei.security.rate-limit.captcha-per-minute:30}")
    private long captchaPerMinute;

    @Value("${xinfei.security.rate-limit.user-per-minute:1000}")
    private long userPerMinute;

    @Value("${xinfei.security.rate-limit.ip-per-minute:5000}")
    private long ipPerMinute;

    public RateLimitFilter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // dev/test 总开关关闭：直接放行，不读 Redis、不产生计数 key
        if (!rateLimitEnabled) {
            return chain.filter(exchange);
        }
        // CORS 预检不计数
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        String ip = exchange.getAttribute(RequestSanitizeFilter.ATTR_REAL_IP);
        if (ip == null || ip.isBlank()) {
            ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : String.valueOf(exchange.getRequest().getRemoteAddress().getAddress());
        }
        String path = exchange.getRequest().getPath().value().toLowerCase();

        // 敏感匿名接口：按 IP 严限（防撞库/验证码轰炸）
        if (path.startsWith("/api/auth/anno/login")) {
            return check(exchange, chain, new Counter("login", ip, loginPerMinute));
        }
        if (path.startsWith("/api/auth/anno/captcha")) {
            return check(exchange, chain, new Counter("captcha", ip, captchaPerMinute));
        }

        // 通用：IP 兜底 + 登录用户双计数
        String userId = resolveUserId(exchange);
        if (userId != null) {
            // ConcurrentHashMap 禁止 null value，匿名请求（userId=null）不得写入
            exchange.getAttributes().put(ATTR_USER_ID, userId);
        }
        String epochMinute = String.valueOf(System.currentTimeMillis() / 60000L);
        Counter ipCounter = new Counter("ip", ip + ":" + epochMinute, ipPerMinute);
        if (userId == null) {
            return check(exchange, chain, ipCounter);
        }
        return check(exchange, chain, ipCounter, new Counter("user", userId + ":" + epochMinute, userPerMinute));
    }

    @Override
    public int getOrder() {
        return -250;
    }

    /**
     * 依次执行计数器（任一超限即 429），全部通过放行
     */
    private Mono<Void> check(ServerWebExchange exchange, GatewayFilterChain chain, Counter... counters) {
        Mono<Boolean> flow = Mono.just(Boolean.TRUE);
        for (Counter counter : counters) {
            flow = flow.flatMap(ok -> ok ? incrementAndCheck(counter) : Mono.just(Boolean.FALSE));
        }
        return flow.flatMap(ok -> ok
                ? chain.filter(exchange)
                : tooManyRequests(exchange));
    }

    /**
     * 单计数器自增并判断是否超限；Lua 原子保证计数 key 必有过期（正常首次即设，无 TTL 脏 key 自愈补设）。
     * Redis 异常 fail-open 放行并告警。
     */
    private Mono<Boolean> incrementAndCheck(Counter counter) {
        String key = "xinfei:rl:" + counter.scope() + ":" + counter.key();
        return redis.execute(INCR_ENSURE_TTL, List.of(key), String.valueOf(WINDOW_TTL_SECONDS))
                .next()
                .map(v -> v <= counter.limit())
                .onErrorResume(e -> {
                    log.warn("[限流] Redis 计数异常，fail-open 放行: key={}, reason={}", key, e.getMessage());
                    return Mono.just(Boolean.TRUE);
                });
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        long retryAfter = 60 - (System.currentTimeMillis() / 1000L) % 60;
        // Retry-After 头必须在写响应体前设置（响应提交后写头无效）
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfter));
        // 429 统一 JSON（T-SEC-01 验收口径）
        return RequestSanitizeFilter.writeError(exchange,
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, 429,
                "请求过于频繁，请稍后重试");
    }

    /**
     * 从 token 解析登录用户ID（匿名返回 null）；解析失败不阻断（下游鉴权兜底）
     */
    private String resolveUserId(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst("token");
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            return loginId == null ? null : String.valueOf(loginId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 限流计数器：scope 区分计数命名空间，key 为计数主体，limit 为每分钟阈值
     */
    private record Counter(String scope, String key, long limit) {
    }
}
