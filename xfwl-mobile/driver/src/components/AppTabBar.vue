<template>
  <view class="app-tabbar" role="tablist" :aria-label="label">
    <view
      v-for="it in items"
      :key="String(it.key)"
      class="app-tab"
      role="tab"
      :aria-selected="String(it.key === current)"
      :tabindex="it.key === current ? 0 : -1"
      :class="{ active: it.key === current }"
      @click="emit('change', it.key)"
    >
      <text class="t-body">{{ it.text }}</text>
    </view>
  </view>
</template>

<script setup>
// 页内分段筛选（不是底部原生 tabBar）。带 role / aria-selected / tabindex，
// 用于把「我寄的/我收的」与「状态」两类筛选拆成不同组件（P1-1）。
defineProps({
  items: { type: Array, default: () => [] },
  current: { type: [String, Number], default: '' },
  label: { type: String, default: '筛选标签' },
})
const emit = defineEmits(['change'])
</script>

<style lang="scss" scoped>
.app-tabbar { display: flex; gap: var(--s-2); padding: var(--s-2) 0; overflow-x: auto; }
.app-tab {
  flex-shrink: 0;
  padding: var(--s-2) var(--s-4);
  border: 1rpx solid var(--c-border);
  border-radius: var(--r-round);
  background: var(--c-surface);
}
.app-tab.active { border-color: var(--c-primary); background: var(--c-primary-light); }
.app-tab.active :deep(text) { color: var(--c-primary); }
</style>
