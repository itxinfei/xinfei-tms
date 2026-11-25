<template>
  <u-form-item :label="label" :prop="prop" :required="required" :borderBottom="borderBottom">
    <view class="ff-body">
      <slot />
      <text v-if="error" class="ff-error t-tip" role="alert">{{ error }}</text>
    </view>
  </u-form-item>
</template>

<script setup>
// 字段级就近错误：错误文案贴在输入控件下方，配合 focusField(prop) 定位首个错误字段。
defineProps({
  label: { type: String, default: '' },
  prop: { type: String, default: '' },
  required: { type: Boolean, default: false },
  borderBottom: { type: Boolean, default: true },
  error: { type: String, default: '' },
})
</script>

<style lang="scss" scoped>
.ff-body { width: 100%; display: flex; flex-direction: column; gap: var(--s-1); }
.ff-error { color: var(--c-error); }
</style>
