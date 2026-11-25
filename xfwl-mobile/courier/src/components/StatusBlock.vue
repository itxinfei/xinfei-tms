<template>
  <view class="status-block" role="status" aria-live="polite">
    <view v-if="state === 'loading'" class="sb-row">
      <u-loading-icon mode="circle" size="22" :text="loadingText" />
    </view>
    <view v-else-if="state === 'fail'" class="sb-col">
      <text class="sb-msg t-body" role="alert">{{ message || '数据暂不可用' }}</text>
      <u-button size="mini" :plain="true" text="重试" @click="emit('retry')" />
    </view>
    <view v-else-if="state === 'empty'" class="sb-col">
      <u-empty mode="data" :text="emptyText || '暂无数据'" />
      <u-button v-if="retryable" size="mini" :plain="true" text="刷新" @click="emit('retry')" />
    </view>
    <view v-else-if="state === 'nomore'" class="sb-row">
      <text class="t-tip">没有更多了</text>
    </view>
    <slot v-else />
  </view>
</template>

<script setup>
// 三态统一出口：加载中 / 失败（带重试与可诊断文案）/ 空 / 加载更多 / 正常。
// state 来自 shared/request/safe.js 的 requestSafe，页面不再把失败伪装成 0 / null / --。
defineProps({
  state: { type: String, default: 'success' },
  message: { type: String, default: '' },
  loadingText: { type: String, default: '加载中…' },
  emptyText: { type: String, default: '暂无数据' },
  retryable: { type: Boolean, default: true },
})
const emit = defineEmits(['retry'])
</script>

<style lang="scss" scoped>
.status-block { padding: var(--s-8) var(--s-4); }
.sb-row { display: flex; justify-content: center; align-items: center; }
.sb-col { display: flex; flex-direction: column; align-items: center; gap: var(--s-4); }
.sb-msg { color: var(--c-text-3); }
</style>
