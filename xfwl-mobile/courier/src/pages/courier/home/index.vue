<template>
  <view class="page">
    <!-- 品牌头：主色改为令牌后白字对比度 5.83:1，不再用硬编码高饱和红 -->
    <view class="hero">
      <view class="hero-bar"><text class="hero-brand">心飞速运 · 快递员工作台</text></view>
      <text class="t-title hero-title">您好，快递员</text>
      <text class="t-aux hero-sub">取件派件 · 扫码接单 · 实时追踪</text>
    </view>

    <!-- 作业端第一步：扫码。主按钮 ≥96rpx，删掉只赋值不加载数据的装饰 u-tabs -->
    <view class="card scan-cta">
      <text class="t-sub scan-label">开始作业</text>
      <u-button
        type="primary"
        text="扫码接单"
        shape="circle"
        :customStyle="ctaStyle"
        @click="onScan"
      />
      <text class="t-tip scan-hint">扫描运单条码，直接打开任务详情</text>
    </view>

    <!-- 取派任务量：失败给可见文案与重试，不把失败伪装成 0 -->
    <view class="card stat-card">
      <view class="stat-item">
        <text class="stat-num">{{ statFailed ? '—' : pickupCount }}</text>
        <text class="stat-label">取件任务</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ statFailed ? '—' : dispatchCount }}</text>
        <text class="stat-label">派件任务</text>
      </view>
    </view>
    <view v-if="statFailed" class="stat-error">
      <text class="t-tip">任务数暂不可用</text>
      <u-button type="primary" plain size="mini" text="重试" @click="loadCount" />
    </view>

    <!-- 其余入口降为次要宫格，不与扫码争主视觉 -->
    <view class="card entry-grid">
      <view class="entry" @click="goTab('pages/courier/task/list')">
        <view class="entry-icon"><u-icon name="list" size="40" color="var(--c-primary)"></u-icon></view>
        <text class="entry-text">我的任务</text>
      </view>
      <view class="entry" @click="goStats">
        <view class="entry-icon"><u-icon name="rmb" size="40" color="var(--c-primary)"></u-icon></view>
        <text class="entry-text">绩效统计</text>
      </view>
      <view class="entry" @click="goWarehousing">
        <view class="entry-icon"><u-icon name="box" size="40" color="var(--c-primary)"></u-icon></view>
        <text class="entry-text">批量入库</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
// 快递员工作台：扫码优先 + 取派计数三态；无装饰控件、无可点击但无落地的入口
import { ref } from 'vue'
import { courierCount } from '@/common/api/courier'
import { onShow } from '@dcloudio/uni-app'

const ctaStyle = { height: 'var(--s-13)', fontSize: 'var(--f-sub)' }
const stat = ref<any>(null)
const statFailed = ref(false)
const pickupCount = ref(0)
const dispatchCount = ref(0)

function goTab(path: string) {
  uni.switchTab({ url: '/' + path })
}

// 绩效统计不是 tabBar 页，switchTab 不可达，走 navigateTo
function goStats() {
  uni.navigateTo({ url: '/pages/courier/stats/index' })
}

// 扫码接单：解析运单号后跳详情；取消扫码也给出明确反馈
function onScan() {
  uni.scanCode({
    success: (res) => {
      const id = (res.result || '').trim()
      if (!id) {
        uni.showToast({ title: '未识别到运单号', icon: 'none' })
        return
      }
      uni.navigateTo({ url: '/pages/courier/task/detail?id=' + id })
    },
    fail: () => {
      uni.showToast({ title: '已取消扫码', icon: 'none' })
    },
  })
}

// 批量入库：非 tabBar 页，走 navigateTo
function goWarehousing() {
  uni.navigateTo({ url: '/pages/courier/task/warehousing' })
}

// 首页计数：taskType 1=取件 2=派件；失败进入可见错误态，不清空成 0
async function loadCount() {
  try {
    const [p, d] = await Promise.all([
      courierCount({ taskType: 1 }),
      courierCount({ taskType: 2 }),
    ])
    pickupCount.value = Number(p) || 0
    dispatchCount.value = Number(d) || 0
    stat.value = true
    statFailed.value = false
  } catch (e) {
    stat.value = false
    statFailed.value = true
    uni.showToast({ title: '任务数暂不可用', icon: 'none' })
  }
}

onShow(() => loadCount())
</script>

<style lang="scss" scoped>
.hero {
  background: linear-gradient(135deg, var(--c-primary) 0%, var(--c-primary-dark) 100%);
  margin: calc(var(--s-4) * -1) calc(var(--s-4) * -1) var(--s-4);
  padding: calc(var(--s-8) + env(safe-area-inset-top)) var(--s-6) var(--s-8);
}
.hero-bar {
  display: flex;
  justify-content: center;
  margin-bottom: var(--s-2);
}
.hero-brand {
  font-size: var(--f-sub);
  font-weight: var(--fw-semibold);
  color: var(--c-primary-on);
}
.hero-title { display: block; color: var(--c-primary-on); }
.hero-sub { display: block; color: var(--c-primary-on); margin-top: var(--s-2); }
.scan-cta { padding: var(--s-5); }
.scan-label { display: block; margin-bottom: var(--s-4); }
.scan-hint { display: block; text-align: center; margin-top: var(--s-3); }
.stat-card { display: flex; }
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-num {
  font-size: var(--f-title);
  color: var(--c-primary);
  font-weight: var(--fw-semibold);
}
.stat-label {
  font-size: var(--f-tip);
  color: var(--c-text-3);
  margin-top: var(--s-1);
}
.stat-error {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-error);
  margin-bottom: var(--s-4);
}
.stat-error :deep(.u-button) { margin-left: var(--s-3); }
.entry-grid { display: flex; justify-content: space-around; }
.entry { display: flex; flex-direction: column; align-items: center; }
.entry-icon {
  width: var(--s-13);
  height: var(--s-13);
  border-radius: var(--r-md);
  background: var(--c-primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
}
.entry-text {
  font-size: var(--f-aux);
  color: var(--c-text-2);
  margin-top: var(--s-2);
}
</style>
