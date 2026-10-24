<template>
  <div class="cache-browser">
    <el-card class="page-search" shadow="never">
      <div class="cache-browser__chips">
        <span class="cache-browser__chips-label">前缀分组</span>
        <el-check-tag :checked="!params.pattern" @change="handleChipClick('')">
          全部
        </el-check-tag>
        <el-check-tag
          v-for="ns in visibleNamespaces"
          :key="ns.prefix"
          :checked="params.pattern === ns.pattern"
          @change="handleChipClick(ns.pattern)"
        >
          {{ ns.prefix }} ({{ ns.count }})
        </el-check-tag>
      </div>

      <el-form :model="params" :inline="true" label-width="auto">
        <el-form-item prop="pattern" label="匹配模式">
          <el-input
            v-model="params.pattern"
            placeholder="Redis glob，如 Authorization:login:*（空=全部）"
            clearable
            style="width: 320px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleResetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="page-content" shadow="never">
      <div class="page-toolbar">
        <div class="page-toolbar__left">
          <el-button
            v-hasPerm="['sys:cache:delete']"
            type="danger"
            :disabled="!hasSelection"
            @click="handleBatchDelete"
          >
            批量删除
          </el-button>
          <el-button
            v-if="activeNamespace"
            v-hasPerm="['sys:cache:clear']"
            type="danger"
            plain
            @click="handleClearNamespace"
          >
            清空该前缀
          </el-button>
        </div>
        <div class="page-toolbar__right">
          <el-tooltip content="刷新" placement="top">
            <el-button class="page-icon-btn" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <div class="page-table-wrapper">
        <el-table
          ref="dataTableRef"
          v-loading="loading"
          class="page-table"
          :data="list"
          height="100%"
          highlight-current-row
          @selection-change="handleSelectionChange"
        >
          <template #empty>
            <el-empty v-if="error" description="加载失败">
              <el-button type="primary" @click="fetchData">重试</el-button>
            </el-empty>
            <el-empty v-else description="暂无数据" />
          </template>
          <el-table-column
            type="selection"
            width="55"
            align="center"
            :selectable="isDeletableRow"
          />
          <el-table-column label="键名" prop="key" min-width="320" show-overflow-tooltip />
          <el-table-column label="类型" prop="type" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="resolveTypeTag((row as CacheKeyItem).type)">
                {{ (row as CacheKeyItem).type }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="剩余生存时间" width="140" align="center">
            <template #default="{ row }">
              {{ formatTtl((row as CacheKeyItem).ttl) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleView(row as CacheKeyItem)">
                查看值
              </el-button>
              <el-tooltip
                :disabled="!isProtectedLockKey((row as CacheKeyItem).key)"
                content="受保护的 Redisson 内部键，禁止删除"
                placement="top"
              >
                <span>
                  <el-button
                    v-hasPerm="['sys:cache:delete']"
                    type="danger"
                    link
                    size="small"
                    :disabled="isProtectedLockKey((row as CacheKeyItem).key)"
                    @click="handleDelete(row as CacheKeyItem)"
                  >
                    删除
                  </el-button>
                </span>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <pagination
        v-if="total > 0"
        v-model:total="total"
        v-model:page="params.pageNum"
        v-model:limit="params.pageSize"
        class="page-pagination"
        @pagination="fetchData"
      />
    </el-card>

    <CacheValueDrawer v-model="drawerVisible" :cache-key="drawerKey" />
  </div>
</template>

<script setup lang="ts">
import { Refresh } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox, type TableInstance } from "element-plus";

import CacheAPI from "@/api/system/cache";
import type { CacheKeyItem, CacheKeyQueryParams, CacheNamespace } from "@/api/system/cache";
import { usePageTable } from "@/composables";
import CacheValueDrawer from "./CacheValueDrawer.vue";
import { formatTtl, isProtectedLockKey, isSessionKey } from "./cacheUtils";

defineOptions({
  name: "CacheBrowser",
  inheritAttrs: false,
});

const dataTableRef = ref<TableInstance>();

const { loading, error, list, total, params, fetchData, handleQuery, handleResetQuery } =
  usePageTable<CacheKeyItem, CacheKeyQueryParams>({
    initialParams: {
      pageNum: 1,
      pageSize: 10,
      pattern: "",
    },
    request: CacheAPI.getPage,
  });

// 前缀分组 chips 数据（后端按采样聚合，受保护的内部前缀后端不下发，前端再过滤一层双保险）
const namespaces = ref<CacheNamespace[]>([]);

// 展示用分组：剔除 Redisson 锁等受保护内部分组
const visibleNamespaces = computed(() =>
  namespaces.value.filter((ns) => !isProtectedLockKey(ns.prefix))
);

// 当前 pattern 命中某个命名空间 chip 时才允许"清空该前缀"
const activeNamespace = computed(() =>
  namespaces.value.find((ns) => ns.pattern === params.pattern)
);

const selectedKeys = ref<string[]>([]);
const hasSelection = computed(() => selectedKeys.value.length > 0);

const drawerVisible = ref(false);
const drawerKey = ref("");

const TYPE_TAG_MAP: Record<
  string,
  "primary" | "success" | "warning" | "danger" | "info"
> = {
  string: "primary",
  hash: "success",
  list: "warning",
  set: "danger",
  zset: "danger",
};

/**
 * 类型标签配色，未知类型兜底 info
 *
 * @param type Redis 键类型
 */
function resolveTypeTag(type: string): "primary" | "success" | "warning" | "danger" | "info" {
  return TYPE_TAG_MAP[type] ?? "info";
}

/**
 * 表格勾选变化（键表主键为 key 而非 id，单独维护选中键集合）
 *
 * @param selection 当前勾选行
 */
function handleSelectionChange(selection: CacheKeyItem[]): void {
  selectedKeys.value = selection.map((item) => item.key);
}

/**
 * 受保护内部键（Redisson 锁）行不可勾选，批量删除通道在 UI 层即不可触达
 *
 * @param row 当前键行
 */
function isDeletableRow(row: CacheKeyItem): boolean {
  return !isProtectedLockKey(row.key);
}

/**
 * 加载前缀分组
 */
async function fetchNamespaces(): Promise<void> {
  try {
    namespaces.value = await CacheAPI.getNamespaces();
  } catch {
    // 分组加载失败不阻塞键浏览，错误提示由请求拦截器统一处理
    namespaces.value = [];
  }
}

/**
 * 点击前缀 chip：按该前缀 glob 重新查询
 *
 * @param pattern 命名空间 glob，空串=全部
 */
function handleChipClick(pattern: string): void {
  params.pattern = pattern;
  handleQuery();
}

/**
 * 刷新键列表与前缀计数
 */
function handleRefresh(): void {
  fetchData();
  fetchNamespaces();
}

/**
 * 查看键值，打开右侧抽屉
 *
 * @param row 当前键行
 */
function handleView(row: CacheKeyItem): void {
  drawerKey.value = row.key;
  drawerVisible.value = true;
}

/**
 * 删除单个键（会话键额外强提示）
 *
 * @param row 当前键行
 */
async function handleDelete(row: CacheKeyItem): Promise<void> {
  const sessionTip = isSessionKey(row.key)
    ? "\n该键为登录会话键，删除后对应用户将被强制下线。"
    : "";
  try {
    await ElMessageBox.confirm(
      `确认删除键 ${row.key}？删除后不可恢复。${sessionTip}`,
      "删除确认",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
        customClass: "cache-confirm-message",
      }
    );
  } catch {
    return;
  }

  const deleted = await CacheAPI.deleteKeys([row.key]);
  if (deleted > 0) {
    ElMessage.success("已删除");
  } else {
    ElMessage.warning("键可能已过期，实际未删除");
  }
  dataTableRef.value?.clearSelection();
  handleRefresh();
}

/**
 * 批量删除勾选键，勾选含会话键时提示强制下线后果
 */
async function handleBatchDelete(): Promise<void> {
  const keys = [...selectedKeys.value];
  if (keys.length === 0) {
    ElMessage.warning("请勾选删除项");
    return;
  }

  const sessionTip = keys.some(isSessionKey)
    ? "\n选中的键中包含登录会话键（Authorization:login: / online_tokens:），删除后相关用户将被强制下线。"
    : "";
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${keys.length} 个缓存键？删除后不可恢复。${sessionTip}`,
      "批量删除",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
        customClass: "cache-confirm-message",
      }
    );
  } catch {
    return;
  }

  const deleted = await CacheAPI.deleteKeys(keys);
  ElMessage.success(`已删除 ${deleted} 个键`);
  dataTableRef.value?.clearSelection();
  handleRefresh();
}

/**
 * 清空当前命名空间前缀，须手输前缀名二次确认
 */
async function handleClearNamespace(): Promise<void> {
  const ns = activeNamespace.value;
  if (!ns) return;

  // 登录会话前缀的清空后果升级为全员下线提示
  const sessionTip =
    ns.prefix === "Authorization:login"
      ? "\n该前缀为登录会话键，清空后全部在线用户将被强制下线！"
      : ns.prefix === "online_tokens"
        ? "\n该前缀为登录会话键，清空后相关在线用户将被强制下线！"
        : "";

  try {
    await ElMessageBox.prompt(
      `将清空前缀「${ns.prefix}」下的全部缓存键（当前采样约 ${ns.count} 个），此操作不可恢复。${sessionTip}\n请手动输入前缀名以确认：`,
      "清空该前缀",
      {
        confirmButtonText: "确认清空",
        cancelButtonText: "取消",
        type: "warning",
        customClass: "cache-confirm-message",
        inputPlaceholder: ns.prefix,
        inputValidator: (input) => input === ns.prefix || "前缀名输入不一致",
      }
    );
  } catch {
    return;
  }

  const result = await CacheAPI.clearNamespace(ns.prefix);
  dataTableRef.value?.clearSelection();
  handleRefresh();
  if (result.capped) {
    // 达到单次删除上限时 HTTP 仍成功，但前缀下仍有残留键，需用户缩小范围继续清理
    ElMessage.warning(
      `已删除 ${result.deleted} 个，达到单次上限，仍有剩余，请缩小前缀范围`
    );
  } else {
    ElMessage.success(`前缀已清空，共删除 ${result.deleted} 个键`);
  }
}

onMounted(() => {
  fetchData();
  fetchNamespaces();
});
</script>

<style scoped>
.cache-browser {
  display: flex;
  flex-direction: column;
  gap: var(--page-gap);
  height: 100%;
  min-height: 0;
}

.cache-browser__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.cache-browser__chips-label {
  margin-right: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
