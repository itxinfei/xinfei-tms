<template>
  <div v-loading="loading" class="cache-monitor">
    <el-card shadow="never">
      <template #header>
        <div class="cache-monitor__header">
          <span class="cache-monitor__title">Redis 概览</span>
          <el-button type="primary" plain :loading="loading" @click="fetchInfo">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="error" description="加载失败">
        <el-button type="primary" @click="fetchInfo">重试</el-button>
      </el-empty>

      <el-descriptions v-else-if="info" :column="3" border>
        <el-descriptions-item label="Redis 版本">
          {{ info.redisVersion || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="运行时长">
          {{ formatUptime(info.uptimeSeconds) }}
        </el-descriptions-item>
        <el-descriptions-item label="连接客户端数">
          {{ info.connectedClients }}
        </el-descriptions-item>
        <el-descriptions-item label="内存占用">
          {{ info.usedMemoryHuman }}
          <span class="cache-monitor__sub">/ 上限 {{ memoryLimitText }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="键总数">{{ info.dbSize }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" class="cache-monitor__distribution">
      <template #header>
        <span class="cache-monitor__title">键类型分布</span>
      </template>
      <el-empty v-if="!info || info.typeDistribution.length === 0" description="暂无数据" />
      <div v-else class="cache-monitor__bars">
        <div
          v-for="item in info.typeDistribution"
          :key="item.type"
          class="cache-monitor__bar-row"
        >
          <el-tag size="small" type="info" class="cache-monitor__bar-label">
            {{ item.type }}
          </el-tag>
          <el-progress
            :percentage="resolvePercent(item.count)"
            :stroke-width="14"
            :show-text="false"
            class="cache-monitor__bar"
          />
          <span class="cache-monitor__bar-count">{{ item.count }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import CacheAPI from "@/api/system/cache";
import type { CacheInfo } from "@/api/system/cache";
import { formatUptime } from "./cacheUtils";

defineOptions({
  name: "CacheMonitor",
  inheritAttrs: false,
});

const loading = ref(false);
const error = ref(false);
const info = ref<CacheInfo>();

// maxmemory 为 0（INFO 返回 0B）表示未设置内存上限
const memoryLimitText = computed(() => {
  const max = info.value?.maxMemoryHuman;
  if (!max || max === "0B" || max === "0" || Number.parseFloat(max) === 0) {
    return "未限制";
  }
  return max;
});

const totalCount = computed(() =>
  (info.value?.typeDistribution ?? []).reduce((sum, item) => sum + item.count, 0)
);

/**
 * 采样口径下的类型占比，仅用于条形长度对比
 *
 * @param count 当前类型键数量
 */
function resolvePercent(count: number): number {
  if (totalCount.value === 0) return 0;
  return Math.round((count / totalCount.value) * 100);
}

/**
 * 拉取 Redis 概览信息
 */
async function fetchInfo(): Promise<void> {
  loading.value = true;
  error.value = false;
  try {
    info.value = await CacheAPI.getInfo();
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(fetchInfo);
</script>

<style scoped>
.cache-monitor {
  display: flex;
  flex-direction: column;
  gap: var(--page-gap);
  min-height: 0;
}

.cache-monitor__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cache-monitor__title {
  font-weight: 600;
}

.cache-monitor__sub {
  color: var(--el-text-color-secondary);
}

.cache-monitor__distribution {
  flex: 1 1 auto;
  min-height: 0;
}

.cache-monitor__bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cache-monitor__bar-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.cache-monitor__bar-label {
  flex: none;
  width: 72px;
  justify-content: center;
}

.cache-monitor__bar {
  flex: 1 1 auto;
}

.cache-monitor__bar-count {
  flex: none;
  width: 60px;
  text-align: right;
}
</style>
