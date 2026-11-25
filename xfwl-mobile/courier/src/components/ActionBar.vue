<template>
  <view class="action-bar" role="group" aria-label="底部操作栏">
    <slot />
  </view>
</template>

<script setup>
// 唯一底部固定层。位置 / 安全区 / z-index / 占位全部由全局 .action-bar 与
// .page 的 padding-bottom: calc(var(--fixed-bottom-h) + var(--s-6)) 提供，
// 页面不得再叠加第二个 position: fixed 或手写 140rpx / 160rpx 占位（R4 / R8）。
</script>

<style lang="scss" scoped>
</style>
