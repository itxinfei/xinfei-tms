package com.xinfei.auth.controller.v1;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xinfei.auth.common.v1.ApiResult;
import com.xinfei.auth.common.v1.CacheClearResultVO;
import com.xinfei.auth.common.v1.CacheInfoVO;
import com.xinfei.auth.common.v1.CacheKeyVO;
import com.xinfei.auth.common.v1.CacheNamespaceVO;
import com.xinfei.auth.common.v1.CacheValueVO;
import com.xinfei.auth.common.v1.PageResult;
import com.xinfei.auth.service.v1.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 缓存管理（youlai 契约风格 /api/v1/cache，USR-03）：
 * 概览、命名空间、键分页、键值预览、单/批量删除与命名空间清空。
 * 列表/详情为查看权限，删除与清空各设独立按钮权限；删除动作由 OperLogInterceptor 记 OPER 审计。
 */
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheService cacheService;

    @GetMapping
    @SaCheckPermission("sys:cache:list")
    public ApiResult<PageResult<CacheKeyVO>> page(
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
            @RequestParam(value = "pattern", required = false) String pattern) {
        return ApiResult.success(cacheService.page(pageNum, pageSize, pattern));
    }

    /** Redis 概览：INFO/DBSIZE 指标 + 键类型采样分布 */
    @GetMapping("/info")
    @SaCheckPermission("sys:cache:list")
    public ApiResult<CacheInfoVO> info() {
        return ApiResult.success(cacheService.info());
    }

    /** 命名空间聚合（按冒号首段分组） */
    @GetMapping("/namespaces")
    @SaCheckPermission("sys:cache:list")
    public ApiResult<List<CacheNamespaceVO>> namespaces() {
        return ApiResult.success(cacheService.namespaces());
    }

    /** 键值内容预览 */
    @GetMapping("/value")
    @SaCheckPermission("sys:cache:list")
    public ApiResult<CacheValueVO> value(@RequestParam("key") String key) {
        return ApiResult.success(cacheService.value(key));
    }

    /** 批量删除键（逗号分隔），返回实际删除个数 */
    @DeleteMapping
    @SaCheckPermission("sys:cache:delete")
    public ApiResult<Long> delete(@RequestParam("keys") String keys) {
        return ApiResult.success(cacheService.delete(keys));
    }

    /** 按命名空间前缀清空（高危，独立权限），返回删除数与是否触顶 */
    @DeleteMapping("/namespace")
    @SaCheckPermission("sys:cache:clear")
    public ApiResult<CacheClearResultVO> clearNamespace(@RequestParam("prefix") String prefix) {
        return ApiResult.success(cacheService.clearNamespace(prefix));
    }
}
