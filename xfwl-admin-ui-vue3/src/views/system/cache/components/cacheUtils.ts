/**
 * 缓存管理页展示层工具：TTL/运行时长格式化、登录会话键识别
 */

/** 登录会话键前缀：删除即强制对应用户下线 */
export const SESSION_KEY_PREFIXES = ["Authorization:login:", "online_tokens:"];

/**
 * 受保护的 Redisson 内部结构键前缀：与后端 CacheService.PROTECTED_KEY_PREFIXES 同口径。
 * 不含裸 "global"——global:rate_limit、global:sys_oss_config 等是业务全局配置/限流键。
 */
export const PROTECTED_LOCK_PREFIXES = ["redisson", "{redisson"];

/**
 * 受保护的 Redisson 附属键后缀：与后端 CacheService.PROTECTED_KEY_SUFFIXES 同口径。
 * RMapCache options 键形态 {mapName}:redisson_options，mapName 为任意业务键名。
 */
export const PROTECTED_LOCK_SUFFIXES = [":redisson_options"];

/**
 * 判断是否为登录会话键
 *
 * @param key Redis 键名
 */
export function isSessionKey(key: string): boolean {
  return SESSION_KEY_PREFIXES.some((prefix) => key.startsWith(prefix));
}

/**
 * 判断是否为受保护的 Redisson 内部键，后端 DELETE 会整批拒绝。
 * 前缀或后缀命中任一即可（均忽略大小写）。
 *
 * @param key Redis 键名
 */
export function isProtectedLockKey(key: string): boolean {
  const lower = key.toLowerCase();
  return (
    PROTECTED_LOCK_PREFIXES.some((prefix) => lower.startsWith(prefix.toLowerCase())) ||
    PROTECTED_LOCK_SUFFIXES.some((suffix) => lower.endsWith(suffix.toLowerCase()))
  );
}

/**
 * 格式化剩余生存时间
 *
 * @param ttl 秒；-1=永不过期，其余负值（如 -2=键不存在）显示 -
 */
export function formatTtl(ttl: number): string {
  if (ttl === -1) return "永不过期";
  if (ttl < 0) return "-";
  if (ttl >= 86400) return `${Math.floor(ttl / 86400)} 天`;
  if (ttl >= 3600) return `${Math.floor(ttl / 3600)} 小时`;
  if (ttl >= 60) return `${Math.floor(ttl / 60)} 分钟`;
  return `${ttl} 秒`;
}

/**
 * 格式化 Redis 运行时长（秒 → x天x小时）
 *
 * @param seconds 运行秒数
 */
export function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days} 天 ${hours} 小时`;
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`;
  if (minutes > 0) return `${minutes} 分钟`;
  return `${seconds} 秒`;
}
