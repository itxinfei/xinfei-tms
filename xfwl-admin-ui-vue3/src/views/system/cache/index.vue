<template>
  <div class="page-container cache-page">
    <el-alert
      class="cache-page__alert"
      type="warning"
      :closable="false"
      show-icon
      title="删除操作不可恢复；Authorization:login: 与 online_tokens: 前缀为登录会话键，删除即强制对应用户下线。单次最多展示 2000 个匹配键，请用更精确的模式缩小范围。"
    />

    <el-tabs v-model="activeTab" class="cache-page__tabs">
      <el-tab-pane label="缓存监控" name="monitor" lazy>
        <CacheMonitor />
      </el-tab-pane>
      <el-tab-pane label="缓存浏览" name="browser" lazy>
        <CacheBrowser />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import CacheMonitor from "./components/CacheMonitor.vue";
import CacheBrowser from "./components/CacheBrowser.vue";

defineOptions({
  name: "Cache",
  inheritAttrs: false,
});

const activeTab = ref("monitor");
</script>

<style scoped>
.cache-page__alert {
  flex: none;
}

/* 打通 Tab 内容区高度链，让缓存浏览页表格区域可自适应滚动 */
.cache-page__tabs {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.cache-page__tabs :deep(.el-tabs__header) {
  flex: none;
  margin: 0 0 var(--page-gap);
}

.cache-page__tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
}

.cache-page__tabs :deep(.el-tab-pane) {
  height: 100%;
}
</style>
