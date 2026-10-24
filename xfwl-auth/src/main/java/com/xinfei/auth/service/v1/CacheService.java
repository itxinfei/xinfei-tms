package com.xinfei.auth.service.v1;

import com.xinfei.auth.common.BizException;
import com.xinfei.auth.common.v1.CacheClearResultVO;
import com.xinfei.auth.common.v1.CacheInfoVO;
import com.xinfei.auth.common.v1.CacheKeyVO;
import com.xinfei.auth.common.v1.CacheNamespaceVO;
import com.xinfei.auth.common.v1.CacheValueVO;
import com.xinfei.auth.common.v1.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * 缓存管理（USR-03）：Redis 键 SCAN 查看、概览统计、命名空间聚合、键值预览与批量删除。
 *
 * <p>dev 运维辅助页：单次扫描上限 {@value #SCAN_LIMIT} 个键（超出即截断，
 * 提示缩小 pattern），不做全库 KEYS 阻塞式扫描。删除操作由前端二次确认，
 * 审计留痕随操作日志拦截器（OperLogInterceptor）全局覆盖，本服务不重复记录。</p>
 */
@Service
@RequiredArgsConstructor
public class CacheService {

    /** 单次扫描上限：dev 数据量级小；达到上限即截断，引导用更精确的 pattern */
    private static final int SCAN_LIMIT = 2000;

    /** SCAN 单次 hint（提示值，非精确批次） */
    private static final int SCAN_BATCH = 200;

    /** 容器类型（hash/list/set/zset）单次预览元素上限 */
    private static final int VALUE_ENTRY_LIMIT = 100;

    /** string 值预览的 UTF-8 字节上限 */
    private static final int STRING_MAX_BYTES = 4096;

    /** 批量删除/按命名空间清空时每批删除的键数 */
    private static final int DELETE_BATCH_SIZE = 200;

    /** 批量删除接口单次允许的键数上限 */
    private static final int DELETE_KEYS_LIMIT = 200;

    /** 分页页大小上限（超出回退默认值） */
    private static final long PAGE_SIZE_MAX = 200;

    /** 分页参数非法时的默认页大小 */
    private static final long PAGE_SIZE_DEFAULT = 10;

    /** 命名空间前缀最小长度（防误清空） */
    private static final int MIN_NAMESPACE_PREFIX_LENGTH = 3;

    /** 鉴权会话键的特殊前缀：冒号分段时归并到前两段 */
    private static final String AUTHORIZATION_PREFIX = "Authorization:";

    /**
     * 受保护的 Redisson 内部结构键前缀（忽略大小写，startsWith 匹配）：
     * redisson__timeout__set: 等延时/超时队列键、{@code {redisson...}} hash-tag 键。
     * 注意不含裸 "global"——global:xxx 是业务全局配置/限流键（如 global:rate_limit:），
     * 清限流是合法运维操作，误保护会导致这些键不可见不可删。
     */
    private static final String[] PROTECTED_KEY_PREFIXES = {
            "redisson", "{redisson"
    };

    // 已知盲区：RLock 键名为调用方任意传入（getLock("any") → 键即 "any"），
    // 前缀/后缀规则无法通用识别；将来应用代码引入 Redisson 分布式锁时，必须约定
    // 统一前缀（如 lock:）并加入本表。当前全仓无 Redisson 客户端调用，dev 库内
    // 锁键均为上游 RMapCache 遗留结构。

    /**
     * 受保护的 Redisson 附属键后缀（忽略大小写，endsWith 匹配）：
     * RMapCache 自动维护的 options 键，形态 {@code {mapName}:redisson_options}，
     * mapName 是任意业务键名（如 {sys_dept}、{global:sys_client}），只能按后缀识别。
     */
    private static final String[] PROTECTED_KEY_SUFFIXES = {
            ":redisson_options"
    };

    private final StringRedisTemplate redisTemplate;

    public PageResult<CacheKeyVO> page(long pageNum, long pageSize, String pattern) {
        // 入参兜底：页码最小 1；页大小非法（<1 或 >200）回退默认 10
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > PAGE_SIZE_MAX) {
            pageSize = PAGE_SIZE_DEFAULT;
        }
        String match = StringUtils.hasText(pattern) ? pattern.trim() : "*";
        List<CacheKeyVO> all = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(match).count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                if (all.size() >= SCAN_LIMIT) {
                    break;
                }
                String key = cursor.next();
                CacheKeyVO vo = new CacheKeyVO();
                vo.setKey(key);
                vo.setType(typeName(key));
                vo.setTtl(redisTemplate.getExpire(key));
                all.add(vo);
            }
        }

        PageResult<CacheKeyVO> result = new PageResult<>();
        result.setTotal(all.size());
        // 先以 long 计算偏移，防 pageNum 接近 Long.MAX_VALUE 时乘法溢出为负
        long offset = (pageNum - 1) * pageSize;
        if (offset < 0 || offset > all.size()) {
            offset = all.size();
        }
        int from = (int) offset;
        int to = (int) Math.min(from + pageSize, all.size());
        result.setList(all.subList(from, to));
        return result;
    }

    /**
     * Redis 概览信息：INFO/DBSIZE 指标 + 采样类型分布。
     */
    public CacheInfoVO info() {
        Properties props = redisTemplate.execute((RedisCallback<Properties>) connection -> connection.info());

        CacheInfoVO vo = new CacheInfoVO();
        if (props != null) {
            vo.setRedisVersion(props.getProperty("redis_version"));
            vo.setUptimeSeconds(parseLong(props, "uptime_in_seconds"));
            vo.setConnectedClients(parseLong(props, "connected_clients"));
            vo.setUsedMemoryBytes(parseLong(props, "used_memory"));
            vo.setUsedMemoryHuman(props.getProperty("used_memory_human"));
            vo.setMaxMemoryHuman(props.getProperty("maxmemory_human"));
        }

        Long dbSize = redisTemplate.execute((RedisCallback<Long>) connection -> connection.dbSize());
        vo.setDbSize(dbSize == null ? 0L : dbSize);

        // 类型分布：复用 SCAN 采样，按 TYPE 聚合
        Map<String, Long> counters = new TreeMap<>();
        for (String key : sampleKeys()) {
            String type = typeName(key);
            counters.merge(type, 1L, Long::sum);
        }
        List<CacheInfoVO.TypeCount> distribution = new ArrayList<>();
        counters.forEach((type, count) -> distribution.add(new CacheInfoVO.TypeCount(type, count)));
        distribution.sort(Comparator.comparingLong(CacheInfoVO.TypeCount::getCount).reversed());
        vo.setTypeDistribution(distribution);
        return vo;
    }

    /**
     * 命名空间聚合（SCAN {@value #SCAN_LIMIT} 采样）。
     *
     * <p>简化规则：仅统计含冒号的键，按首段聚合；{@code Authorization:} 开头的
     * 会话键（如 Authorization:login:token:xxx）归并到前两段；无冒号的顶层
     * 孤立键不计入。pattern 即 prefix + ":*"，可直接用于 /cache 分页搜索。</p>
     *
     * <p>受保护的 Redisson 内部分组（前缀命中 {@link #PROTECTED_KEY_PREFIXES}）不下发。</p>
     *
     * <p>已知边界：若键段本身含 glob 元字符（* ? [ ]），拼出的 pattern 在 SCAN
     * MATCH 下匹配范围可能被放大，dev 阶段业务键不含此类字符，不做转义处理。</p>
     */
    public List<CacheNamespaceVO> namespaces() {
        Map<String, Long> counters = new LinkedHashMap<>();
        for (String key : sampleKeys()) {
            int index = key.indexOf(':');
            if (index <= 0) {
                // 无冒号的顶层孤立键不计入
                continue;
            }
            String prefix;
            if (key.startsWith(AUTHORIZATION_PREFIX)) {
                int second = key.indexOf(':', AUTHORIZATION_PREFIX.length());
                prefix = second > 0 ? key.substring(0, second) : key.substring(0, index);
            } else {
                prefix = key.substring(0, index);
            }
            // 受保护的内部分组（Redisson 锁等）不下发，前端既不展示也无法经"清空该前缀"触达
            if (isProtectedKey(prefix)) {
                continue;
            }
            counters.merge(prefix, 1L, Long::sum);
        }

        List<CacheNamespaceVO> result = new ArrayList<>();
        counters.forEach((prefix, count) -> {
            CacheNamespaceVO vo = new CacheNamespaceVO();
            vo.setPrefix(prefix);
            vo.setCount(count);
            vo.setPattern(prefix + ":*");
            result.add(vo);
        });
        result.sort(Comparator.comparingLong(CacheNamespaceVO::getCount).reversed());
        return result;
    }

    /**
     * 键值预览：按 TYPE 分发，单元素上限 {@value #VALUE_ENTRY_LIMIT}，
     * string UTF-8 字节上限 {@value #STRING_MAX_BYTES}。
     * 扫描与读值间存在竞态（键可能刚好过期），各分支把空结果当正常返回。
     */
    public CacheValueVO value(String key) {
        if (!StringUtils.hasText(key)) {
            throw BizException.validFail("缺少 key");
        }
        DataType dataType = redisTemplate.type(key);
        if (dataType == null || dataType == DataType.NONE) {
            throw BizException.validFail("键不存在或已过期");
        }

        CacheValueVO vo = new CacheValueVO();
        vo.setKey(key);
        vo.setType(dataType.name().toLowerCase());
        vo.setTtl(redisTemplate.getExpire(key));
        vo.setSupported(true);
        vo.setEntries(new ArrayList<>());

        try {
            switch (dataType) {
                case STRING:
                    fillString(vo, key);
                    break;
                case HASH:
                    fillHash(vo, key);
                    break;
                case LIST:
                    fillList(vo, key);
                    break;
                case SET:
                    fillSet(vo, key);
                    break;
                case ZSET:
                    fillZSet(vo, key);
                    break;
                default:
                    // stream/module 等类型不支持内容预览
                    vo.setSupported(false);
                    break;
            }
        } catch (RedisSystemException e) {
            // type 判定与读值是两次往返：期间键被其他类型重写会触发 WRONGTYPE，
            // 不允许以 500 逃逸，转业务异常提示刷新
            if (isWrongType(e)) {
                throw BizException.validFail("键状态已变更，请刷新后重试");
            }
            throw e;
        }
        return vo;
    }

    /**
     * 批量删除键（不可恢复；Authorization:login: 与 online_tokens: 为登录会话键，
     * 删除即下线对应会话；Redisson 锁等受保护内部键任何情况下都拒绝删除）。
     *
     * <p>协议限制：键名以逗号分隔，因此无法删除键名本身包含英文逗号的键
     * （逗号协议会被当作分隔符，dev 业务键不含该字符）；单次最多
     * {@value #DELETE_KEYS_LIMIT} 个，底层按每批 {@value #DELETE_BATCH_SIZE} 个 DEL。
     * 黑名单按整批拒绝处理，不跳过——避免调用方误判部分键已删除。</p>
     *
     * @param keys 逗号分隔的键名
     * @return 实际删除的键数
     */
    public long delete(String keys) {
        List<String> keyList = splitKeys(keys);
        if (keyList.size() > DELETE_KEYS_LIMIT) {
            throw BizException.validFail("单次最多删除 " + DELETE_KEYS_LIMIT + " 个键");
        }
        for (String key : keyList) {
            if (isProtectedKey(key)) {
                throw BizException.validFail("受保护的内部键，禁止删除：" + key);
            }
        }
        long deleted = 0L;
        for (int from = 0; from < keyList.size(); from += DELETE_BATCH_SIZE) {
            int to = Math.min(from + DELETE_BATCH_SIZE, keyList.size());
            Long count = redisTemplate.delete(keyList.subList(from, to));
            if (count != null) {
                deleted += count;
            }
        }
        return deleted;
    }

    /**
     * 按命名空间前缀批量清空（高危，独立 sys:cache:clear 权限）。
     *
     * <p>后端硬校验：trim 后长度 ≥3、禁止 glob 通配符与反斜杠转义符
     * （防 {@code red\isson} 经 SCAN MATCH 转义匹配字面 redisson 绕过黑名单）、
     * 保护内部前缀黑名单；SCAN prefix+":*" 分批删除（每批
     * {@value #DELETE_BATCH_SIZE}），累计达 {@value #SCAN_LIMIT} 即停止扫描，
     * 以 {@link CacheClearResultVO#isCapped()} 告知调用方可能仍有残留。
     * 触顶属正常返回而非失败，避免"已部分删除却报错"诱发前端重复清空。</p>
     *
     * @return 删除结果（删除数、是否触顶、上限）
     */
    public CacheClearResultVO clearNamespace(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            throw BizException.validFail("缺少 prefix");
        }
        String normalized = prefix.trim();
        if (normalized.length() < MIN_NAMESPACE_PREFIX_LENGTH) {
            throw BizException.validFail("前缀长度至少 " + MIN_NAMESPACE_PREFIX_LENGTH + " 位");
        }
        if (containsWildcard(normalized)) {
            throw BizException.validFail("前缀禁止包含通配符 * ? [ ] 与转义符 \\");
        }
        if (isProtectedKey(normalized)) {
            throw BizException.validFail("受保护的内部前缀，禁止清空");
        }

        String pattern = normalized + ":*";
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
        long deleted = 0L;
        boolean capped = false;
        List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // 前缀本身合法不代表其下每个键都可删：{sys_dept}:* 下含
                // {sys_dept}:redisson_options 这类 Redisson 后缀保护键，必须逐键兜底
                if (isProtectedKey(key)) {
                    continue;
                }
                batch.add(key);
                if (batch.size() >= DELETE_BATCH_SIZE) {
                    deleted += flushDelete(batch);
                    batch.clear();
                    if (deleted >= SCAN_LIMIT) {
                        capped = true;
                        break;
                    }
                }
            }
        }
        if (!capped && !batch.isEmpty()) {
            deleted += flushDelete(batch);
        }

        CacheClearResultVO result = new CacheClearResultVO();
        result.setDeleted(deleted);
        result.setCapped(capped);
        result.setLimit(SCAN_LIMIT);
        return result;
    }

    /** string 取值与截断 */
    private void fillString(CacheValueVO vo, String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        vo.setTotalSize(bytes.length);
        if (bytes.length > STRING_MAX_BYTES) {
            vo.setTruncated(true);
            // 按 UTF-8 字符边界截断：忽略被切断的末尾残字节，避免解码出 U+FFFD
            CharBuffer charBuffer = CharBuffer.allocate(STRING_MAX_BYTES);
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(bytes, 0, STRING_MAX_BYTES), charBuffer, true);
            charBuffer.flip();
            vo.setStringValue(charBuffer.toString());
        } else {
            vo.setStringValue(value);
        }
    }

    /** hash 预览：field/value，HSCAN 取前 100 项 */
    private void fillHash(CacheValueVO vo, String key) {
        Long size = redisTemplate.opsForHash().size(key);
        long total = size == null ? 0L : size;
        vo.setTotalSize(total);
        if (total == 0L) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().count(VALUE_ENTRY_LIMIT).build();
        try (Cursor<Map.Entry<Object, Object>> cursor = redisTemplate.opsForHash().scan(key, options)) {
            while (cursor.hasNext() && vo.getEntries().size() < VALUE_ENTRY_LIMIT) {
                Map.Entry<Object, Object> entry = cursor.next();
                CacheValueVO.Entry item = new CacheValueVO.Entry();
                itemField(item, entry.getKey());
                item.setValue(entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                vo.getEntries().add(item);
            }
        }
        vo.setTruncated(total > VALUE_ENTRY_LIMIT);
    }

    /** list 预览：value，LRANGE 0..99 */
    private void fillList(CacheValueVO vo, String key) {
        Long size = redisTemplate.opsForList().size(key);
        long total = size == null ? 0L : size;
        vo.setTotalSize(total);
        if (total == 0L) {
            return;
        }
        List<String> values = redisTemplate.opsForList().range(key, 0, VALUE_ENTRY_LIMIT - 1);
        if (values != null) {
            for (String value : values) {
                CacheValueVO.Entry item = new CacheValueVO.Entry();
                item.setValue(value);
                vo.getEntries().add(item);
            }
        }
        vo.setTruncated(total > VALUE_ENTRY_LIMIT);
    }

    /** set 预览：member，SSCAN 取前 100 项 */
    private void fillSet(CacheValueVO vo, String key) {
        Long size = redisTemplate.opsForSet().size(key);
        long total = size == null ? 0L : size;
        vo.setTotalSize(total);
        if (total == 0L) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().count(VALUE_ENTRY_LIMIT).build();
        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(key, options)) {
            while (cursor.hasNext() && vo.getEntries().size() < VALUE_ENTRY_LIMIT) {
                CacheValueVO.Entry item = new CacheValueVO.Entry();
                item.setMember(cursor.next());
                vo.getEntries().add(item);
            }
        }
        vo.setTruncated(total > VALUE_ENTRY_LIMIT);
    }

    /** zset 预览：member/score，ZRANGE 0..99 WITHSCORES */
    private void fillZSet(CacheValueVO vo, String key) {
        Long size = redisTemplate.opsForZSet().zCard(key);
        long total = size == null ? 0L : size;
        vo.setTotalSize(total);
        if (total == 0L) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().rangeWithScores(key, 0, VALUE_ENTRY_LIMIT - 1);
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                CacheValueVO.Entry item = new CacheValueVO.Entry();
                item.setMember(tuple.getValue());
                item.setScore(tuple.getScore());
                vo.getEntries().add(item);
            }
        }
        vo.setTruncated(total > VALUE_ENTRY_LIMIT);
    }

    /** 采样键集合：SCAN * 最多 {@value #SCAN_LIMIT} 个 */
    private List<String> sampleKeys() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match("*").count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                if (keys.size() >= SCAN_LIMIT) {
                    break;
                }
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    /** 设置 hash 条目的字段名（StringRedisTemplate 序列化器下实际为 String） */
    private void itemField(CacheValueVO.Entry item, Object field) {
        item.setField(field == null ? null : String.valueOf(field));
    }

    /** 批量删除并返回删除数（空批次直接返回 0） */
    private long flushDelete(List<String> batch) {
        Long count = redisTemplate.delete(batch);
        return count == null ? 0L : count;
    }

    /** 逗号拆分键名：去空白、跳空项、空列表校验 */
    private List<String> splitKeys(String keys) {
        if (!StringUtils.hasText(keys)) {
            throw BizException.validFail("缺少 keys");
        }
        List<String> result = new ArrayList<>();
        for (String part : keys.split(",")) {
            String key = part.trim();
            if (!key.isEmpty()) {
                result.add(key);
            }
        }
        if (result.isEmpty()) {
            throw BizException.validFail("缺少 keys");
        }
        return result;
    }

    /** 读取键类型（扫描后过期的竞态按 none 返回） */
    private String typeName(String key) {
        DataType type = redisTemplate.type(key);
        return (type == null ? DataType.NONE : type).name().toLowerCase();
    }

    /** 解析 INFO 数值属性，缺失或异常时返回 0 */
    private long parseLong(Properties props, String name) {
        String value = props.getProperty(name);
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 是否包含 glob 通配符或反斜杠转义符。
     * 反斜杠在 SCAN MATCH 中用于转义元字符（如 red\isson 匹配字面 redisson），
     * 必须一并禁止，否则可借转义绕过 startsWith 前缀黑名单。
     */
    private boolean containsWildcard(String text) {
        return text.indexOf('*') >= 0 || text.indexOf('?') >= 0
                || text.indexOf('[') >= 0 || text.indexOf(']') >= 0
                || text.indexOf('\\') >= 0;
    }

    /**
     * 是否命中受保护内部键规则（Redisson 内部结构键，忽略大小写）：
     * 前缀命中 {@link #PROTECTED_KEY_PREFIXES} 或后缀命中 {@link #PROTECTED_KEY_SUFFIXES}。
     */
    private boolean isProtectedKey(String text) {
        for (String protectedPrefix : PROTECTED_KEY_PREFIXES) {
            if (startsWithIgnoreCase(text, protectedPrefix)) {
                return true;
            }
        }
        for (String protectedSuffix : PROTECTED_KEY_SUFFIXES) {
            if (endsWithIgnoreCase(text, protectedSuffix)) {
                return true;
            }
        }
        return false;
    }

    /** 忽略大小写的 startsWith */
    private boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /** 忽略大小写的 endsWith */
    private boolean endsWithIgnoreCase(String text, String suffix) {
        int offset = text.length() - suffix.length();
        return offset >= 0 && text.regionMatches(true, offset, suffix, 0, suffix.length());
    }

    /** 沿异常链判断是否为 WRONGTYPE（TYPE 判定后键被异类型重写的竞态） */
    private boolean isWrongType(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("WRONGTYPE")) {
                return true;
            }
        }
        return false;
    }
}
