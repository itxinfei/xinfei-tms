<template>
  <el-drawer
    v-model="visible"
    :title="cacheKey || '缓存值'"
    size="640px"
    destroy-on-close
  >
    <div v-loading="loading" class="cache-value">
      <template v-if="detail">
        <div class="cache-value__meta">
          <el-tag size="small" type="info">{{ detail.type }}</el-tag>
          <span class="cache-value__ttl">TTL：{{ formatTtl(detail.ttl) }}</span>
        </div>

        <el-alert
          v-if="detail.truncated"
          class="cache-value__alert"
          type="warning"
          :closable="false"
          show-icon
          :title="truncatedTip"
        />

        <el-empty v-if="!detail.supported" description="该类型暂不支持在线查看" />

        <pre v-else-if="detail.type === 'string'" class="cache-value__string">{{ prettyString }}</pre>

        <el-table
          v-else-if="detail.type === 'hash'"
          :data="detail.entries ?? []"
          border
          max-height="60vh"
        >
          <el-table-column label="字段（field）" prop="field" min-width="180" show-overflow-tooltip />
          <el-table-column label="值（value）" prop="value" min-width="240" show-overflow-tooltip />
        </el-table>

        <el-table
          v-else-if="detail.type === 'list'"
          :data="detail.entries ?? []"
          border
          max-height="60vh"
        >
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column label="值（value）" prop="value" min-width="320" show-overflow-tooltip />
        </el-table>

        <el-table
          v-else-if="detail.type === 'set'"
          :data="detail.entries ?? []"
          border
          max-height="60vh"
        >
          <el-table-column label="成员（member）" prop="member" min-width="320" show-overflow-tooltip />
        </el-table>

        <el-table
          v-else-if="detail.type === 'zset'"
          :data="detail.entries ?? []"
          border
          max-height="60vh"
        >
          <el-table-column label="成员（member）" prop="member" min-width="260" show-overflow-tooltip />
          <el-table-column label="分数（score）" prop="score" width="140" align="right" />
        </el-table>
      </template>

      <el-empty v-else-if="!loading" description="暂无数据" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import CacheAPI from "@/api/system/cache";
import type { CacheValue } from "@/api/system/cache";
import { formatTtl } from "./cacheUtils";

defineOptions({
  name: "CacheValueDrawer",
  inheritAttrs: false,
});

const props = defineProps<{
  /** 抽屉显隐 */
  modelValue: boolean;
  /** 待查看的完整键名 */
  cacheKey: string;
}>();

const emit = defineEmits<{
  (event: "update:modelValue", value: boolean): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});

const loading = ref(false);
const detail = ref<CacheValue | null>(null);

// string 截断按字节计数；容器截断按元素条数计数，提示文案分别说明
const truncatedTip = computed(() => {
  if (!detail.value?.truncated) return "";
  return detail.value.type === "string"
    ? `内容较大，仅展示前 4KB，共 ${detail.value.totalSize} 字节`
    : `内容较大，仅展示前 100 条，共 ${detail.value.totalSize} 条`;
});

// string 值优先按 JSON 美化展示，解析失败则原样显示
const prettyString = computed(() => {
  const raw = detail.value?.stringValue ?? "";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
});

/**
 * 拉取键内容
 */
async function fetchValue(): Promise<void> {
  if (!props.cacheKey) return;
  loading.value = true;
  detail.value = null;
  try {
    detail.value = await CacheAPI.getValue(props.cacheKey);
  } catch {
    // 业务异常已由请求拦截器统一提示
    detail.value = null;
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) fetchValue();
  }
);
</script>

<style scoped>
.cache-value__meta {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.cache-value__ttl {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.cache-value__alert {
  margin-bottom: 12px;
}

.cache-value__string {
  max-height: 60vh;
  padding: 12px;
  margin: 0;
  overflow: auto;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
}
</style>
