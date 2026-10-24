import request from "@/utils/request";
import type { PageResult } from "@/api/common";
import type {
  CacheClearResult,
  CacheInfo,
  CacheKeyItem,
  CacheKeyQueryParams,
  CacheNamespace,
  CacheValue,
} from "./types";

const CACHE_BASE_URL = "/api/v1/cache";

const CacheAPI = {
  /**
   * 缓存键分页（SCAN 匹配，单次上限 2000 条）
   */
  getPage(queryParams: CacheKeyQueryParams) {
    return request<unknown, PageResult<CacheKeyItem>>({
      url: `${CACHE_BASE_URL}`,
      method: "get",
      params: queryParams,
    });
  },

  /**
   * Redis 概览信息（版本/运行时长/内存/键类型分布）
   */
  getInfo() {
    return request<unknown, CacheInfo>({
      url: `${CACHE_BASE_URL}/info`,
      method: "get",
    });
  },

  /**
   * 按键前缀聚合的命名空间列表（供前缀 chips 浏览）
   */
  getNamespaces() {
    return request<unknown, CacheNamespace[]>({
      url: `${CACHE_BASE_URL}/namespaces`,
      method: "get",
    });
  },

  /**
   * 查看单个缓存键的内容
   *
   * @param key 完整键名
   */
  getValue(key: string) {
    return request<unknown, CacheValue>({
      url: `${CACHE_BASE_URL}/value`,
      method: "get",
      params: { key },
    });
  },

  /**
   * 批量删除缓存键（不可恢复，前端须二次确认）
   * 后端拒绝删除 Redisson 锁等受保护内部键（整批报错）
   *
   * @param keys 完整键名列表，逗号拼接后随 query 传递
   * @returns 实际删除的键数
   */
  deleteKeys(keys: string[]) {
    return request<unknown, number>({
      url: `${CACHE_BASE_URL}`,
      method: "delete",
      params: { keys: keys.join(",") },
    });
  },

  /**
   * 清空指定前缀下的缓存键（不可恢复，前端须手输前缀确认）
   * 达到单次删除上限时 capped=true，仍有剩余需缩小前缀范围
   *
   * @param prefix 命名空间前缀
   */
  clearNamespace(prefix: string) {
    return request<unknown, CacheClearResult>({
      url: `${CACHE_BASE_URL}/namespace`,
      method: "delete",
      params: { prefix },
    });
  },
};

export default CacheAPI;
export * from "./types";
