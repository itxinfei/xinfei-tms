import type { BaseQueryParams, PageResult } from "@/api/common";

/** 缓存键查询参数 */
export interface CacheKeyQueryParams extends BaseQueryParams {
  /** 匹配模式（Redis glob），如 Authorization:login:*；空=全部 */
  pattern?: string;
}

/** 缓存键对象 */
export interface CacheKeyItem {
  /** 键名 */
  key: string;
  /** Redis 类型：string/hash/list/set/zset */
  type: string;
  /** 剩余生存时间（秒）；-1=永不过期 */
  ttl: number;
}

/** 键类型分布条目 */
export interface CacheTypeCount {
  /** Redis 类型：string/hash/list/set/zset 等 */
  type: string;
  /** 采样命中数量 */
  count: number;
}

/** Redis 概览信息（INFO + DBSIZE，类型分布为 SCAN 采样统计） */
export interface CacheInfo {
  /** Redis 版本号 */
  redisVersion: string;
  /** 运行时长（秒） */
  uptimeSeconds: number;
  /** 当前连接的客户端数 */
  connectedClients: number;
  /** 已用内存（字节） */
  usedMemoryBytes: number;
  /** 已用内存（人类可读，如 1.5M） */
  usedMemoryHuman: string;
  /** 最大内存上限（人类可读；0/空表示未限制） */
  maxMemoryHuman: string;
  /** 当前库键总数（DBSIZE） */
  dbSize: number;
  /** 键类型分布（按数量降序，采样统计） */
  typeDistribution: CacheTypeCount[];
}

/** 缓存命名空间聚合条目（按键前缀分组） */
export interface CacheNamespace {
  /** 命名空间前缀（键首段；Authorization: 开头取前两段） */
  prefix: string;
  /** 采样命中的键数量 */
  count: number;
  /** 可直接用于分页搜索的 glob 表达式（prefix + ":*"） */
  pattern: string;
}

/** 容器类型元素条目：按容器类型择字段填充 */
export interface CacheEntry {
  /** hash 字段名 */
  field?: string;
  /** hash 字段值 / list 元素值 */
  value?: string;
  /** set / zset 成员 */
  member?: string;
  /** zset 成员分数 */
  score?: number;
}

/** 清空命名空间前缀的结果 */
export interface CacheClearResult {
  /** 本次实际删除的键数量 */
  deleted: number;
  /** 是否因达到单次删除上限而仍有剩余（HTTP 仍为成功响应） */
  capped: boolean;
  /** 单次删除上限 */
  limit: number;
}

/** 单个缓存键的内容详情 */
export interface CacheValue {
  /** 键名 */
  key: string;
  /** Redis 类型：string/hash/list/set/zset/none 等 */
  type: string;
  /** 剩余生存时间（秒）；-1=永不过期；-2=键不存在 */
  ttl: number;
  /** 是否支持内容预览（stream/module 等为 false） */
  supported: boolean;
  /** 内容是否被截断（string 超 4096 字节 / 容器超 100 元素） */
  truncated: boolean;
  /** 全量大小：string=UTF-8 字节数；hash/list/set/zset=元素总数 */
  totalSize: number;
  /** string 类型的值（其余类型为 undefined） */
  stringValue?: string;
  /** 容器类型的元素条目（string 类型为空） */
  entries?: CacheEntry[];
}

export type { PageResult };
