<template>
  <view class="page driver-home">
    <!-- 中性头部：品牌红只留小面积点缀，不再铺满渐变 hero -->
    <view class="head">
      <view class="head-top">
        <text class="brand"><text class="accent-dot">·</text>心飞速运 · 司机端</text>
        <text class="head-date t-aux">{{ today }}</text>
      </view>
      <text class="greeting t-title">您好，{{ displayName }}</text>
      <text class="head-sub t-aux">今日运输任务与车辆定位</text>
    </view>

    <!-- 常驻定位上报状态条：正常 / 定位延迟 / 离线队列 N 条，失败不再静默 -->
    <view class="report-bar" role="status" aria-live="polite">
      <view class="report-dot" :class="reportStatus"></view>
      <view class="report-body">
        <text class="report-title">{{ statusTitle }}</text>
        <text class="report-desc t-aux">{{ statusDesc }}</text>
      </view>
      <u-button
        size="small"
        type="primary"
        :plain="true"
        :text="pushing ? '上报中' : '立即上报'"
        :loading="pushing"
        :disabled="pushing"
        @click="flushAndReport"
      />
    </view>
    <view class="report-switch">
      <text class="t-body">每 10 秒自动上报位置</text>
      <u-switch :value="reporting" activeColor="var(--c-primary)" @change="toggleReportDebounced" />
    </view>

    <!-- 计数三态：失败显示「—」与重试，不把失败伪装成 0 -->
    <view class="card stat-card">
      <view class="stat-item" @click="goWait">
        <text class="stat-num">{{ countState === 'fail' ? '—' : waitCount }}</text>
        <text class="stat-label t-tip">待提货</text>
      </view>
      <view class="stat-item" @click="goOnTheWay">
        <text class="stat-num">{{ countState === 'fail' ? '—' : onTheWayCount }}</text>
        <text class="stat-label t-tip">在途任务</text>
      </view>
      <view class="stat-item" @click="goHistory">
        <text class="stat-num">{{ countState === 'fail' ? '—' : doneCount }}</text>
        <text class="stat-label t-tip">已完成</text>
      </view>
    </view>
    <view v-if="countState === 'fail'" class="fail-line">
      <text class="t-aux">任务数暂不可用</text>
      <u-button size="mini" :plain="true" text="重试" @click="loadCount" />
    </view>

    <!-- 单手主操作：屏幕下 1/3，热区不小于主按钮标尺，与 courier/customer 结构不同 -->
    <view class="main-cta-wrap">
      <text class="cta-hint t-aux">当前最优先</text>
      <u-button
        type="primary"
        shape="circle"
        :text="primaryCtaText"
        :customStyle="ctaStyle"
        @click="goPrimary"
      />
      <text class="cta-note t-tip">户外单手操作 · 按钮固定在拇指可达区</text>
    </view>

    <!-- 次级入口用文字链，不再平铺 4 张入口卡 -->
    <view class="sub-links">
      <text class="sub-link t-body" @click="goOnTheWay">在途任务</text>
      <text class="sub-link t-body" @click="goCar">车辆信息</text>
      <text class="sub-link t-body" @click="goHistory">历史任务</text>
    </view>

    <view v-if="errorMsg" class="err-banner t-aux" role="alert">{{ errorMsg }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  cargoWait,
  cargoOnTheWay,
  cargoHistory,
  locationPush,
  userProfile,
} from '@/common/api/driver'
import { getOfflineQueue, removeOfflineItem } from '@/common/utils/offline'
import { useUserStore } from '@/common/store/user'
import { maskName } from '@/common/utils/desensitive'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const displayName = computed(() => maskName(userInfo.value?.name) || '司机')
const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})

const waitCount = ref(0)
const onTheWayCount = ref(0)
const doneCount = ref(0)
const countState = ref<'loading' | 'ok' | 'fail'>('loading')
const truckId = ref('')
const errorMsg = ref('')

// ============ 定位上报：状态条 + 离线队列 ============
const reporting = ref(false)
const pushing = ref(false)
const reportStatus = ref<'idle' | 'ok' | 'delay' | 'offline'>('idle')
const offlineCount = ref(0)
const lastPushAt = ref('')
let timer: any = null

const statusTitle = computed(() => {
  if (reportStatus.value === 'ok') return '定位上报正常'
  if (reportStatus.value === 'delay') return '定位获取延迟'
  if (reportStatus.value === 'offline') return `离线队列 ${offlineCount.value} 条待补传`
  return '定位上报未开启'
})
const statusDesc = computed(() => {
  if (reportStatus.value === 'ok') return lastPushAt.value ? `最近上报 ${lastPushAt.value}` : '车辆位置已同步'
  if (reportStatus.value === 'delay') return '请检查定位权限与 GPS 信号'
  if (reportStatus.value === 'offline') return '网络恢复后点「立即上报」补传'
  return '开启自动上报，或点「立即上报」手动同步一次'
})
const primaryCtaText = computed(() =>
  countState.value === 'fail' ? '查看待提货任务' : `查看待提货任务${waitCount.value ? '（' + waitCount.value + '）' : ''}`
)
const ctaStyle = { height: 'var(--s-13)', minHeight: 'var(--s-13)', fontSize: 'var(--f-sub)' }

function refreshQueue() {
  try {
    offlineCount.value = getOfflineQueue().length
  } catch (e) {
    offlineCount.value = 0
    errorMsg.value = '离线队列读取失败，请重启应用后重试'
  }
  if (offlineCount.value > 0) reportStatus.value = 'offline'
}

function getLocation(): Promise<{ lng: number; lat: number }> {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'wgs84',
      success: (res) => resolve({ lng: res.longitude, lat: res.latitude }),
      fail: () => reject(new Error('定位获取失败')),
    })
  })
}

function clockText() {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// manual=true 时给一次确证反馈；定时上报只更新状态条，避免每 10 秒弹窗
async function pushOnce(manual = false) {
  if (pushing.value) return
  pushing.value = true
  try {
    const loc = await getLocation()
    await locationPush({
      businessId: userInfo.value?.id || '',
      name: userInfo.value?.name || '',
      phone: userInfo.value?.phone || '',
      licensePlate: userInfo.value?.licensePlate || '',
      type: 'truck',
      lng: loc.lng,
      lat: loc.lat,
      currentTime: new Date().toISOString(),
      team: userInfo.value?.team || '',
      transportTaskId: '',
      coordSystem: 'WGS84',
      source: 'MOBILE',
    })
    errorMsg.value = ''
    reportStatus.value = 'ok'
    lastPushAt.value = clockText()
    if (manual) uni.showToast({ title: '位置已上报', icon: 'success' })
  } catch (e: any) {
    const locationFailed = /定位获取失败/.test(String(e?.message || ''))
    errorMsg.value = locationFailed
      ? '定位获取失败，请检查定位权限与 GPS 信号'
      : '上报失败，已加入离线队列，待网络恢复补传'
    reportStatus.value = locationFailed ? 'delay' : 'offline'
    if (manual) uni.showToast({ title: errorMsg.value, icon: 'none' })
  } finally {
    pushing.value = false
    refreshQueue()
  }
}

// 先补传离线队列，再上报当前位置；逐条移除成功项
async function flushAndReport() {
  const queued = getOfflineQueue()
  if (!queued.length) {
    await pushOnce(true)
    return
  }
  let ok = 0
  let fail = 0
  for (const item of queued) {
    try {
      await new Promise<void>((resolve, reject) => {
        uni.request({
          url: item.url,
          method: item.method as any,
          data: item.data,
          header: { 'content-type': 'application/json', token: uni.getStorageSync('xfwl_token') || '' },
          success: () => resolve(),
          fail: (err) => reject(err),
        })
      })
      removeOfflineItem(item.id)
      ok += 1
    } catch (e) {
      fail += 1
      errorMsg.value = `仍有 ${fail} 条定位未补传，请稍后重试`
    }
  }
  refreshQueue()
  if (fail === 0) errorMsg.value = ''
  uni.showToast({
    title: fail ? `补传 ${ok} 条，${fail} 条仍待补传` : `已补传 ${ok} 条`,
    icon: 'none',
  })
  await pushOnce(false)
}

// 防抖：开关连点不重复起定时器，也不重复立即上报
function debounce<T extends (...args: any[]) => void>(fn: T, wait = 300) {
  let t: any = null
  return (...args: Parameters<T>) => {
    if (t) clearTimeout(t)
    t = setTimeout(() => fn(...args), wait)
  }
}

function toggleReport(val: boolean) {
  reporting.value = val
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (val) {
    pushOnce(false)
    timer = setInterval(() => pushOnce(false), 10000)
  } else {
    reportStatus.value = 'idle'
  }
}
const toggleReportDebounced = debounce((val: boolean) => toggleReport(val), 300)

function goWait() {
  uni.switchTab({ url: '/pages/driver/task/wait' })
}
function goPrimary() {
  goWait()
}
function goOnTheWay() {
  uni.navigateTo({ url: '/pages/driver/task/onTheWay' })
}
function goHistory() {
  uni.navigateTo({ url: '/pages/driver/task/history' })
}

// 车辆随运输任务走；预取失败不阻塞，点击入口时兜底再取
async function goCar() {
  let id = truckId.value
  if (!id) {
    try {
      const p = await userProfile()
      id = p?.truckId || ''
    } catch (e) {
      errorMsg.value = '车辆信息暂不可用，请稍后重试'
      uni.showToast({ title: errorMsg.value, icon: 'none' })
    }
  }
  uni.navigateTo({ url: '/pages/driver/car/info' + (id ? '?id=' + id : '') })
}

async function loadCount() {
  countState.value = 'loading'
  try {
    const [waitRes, onRes, doneRes] = await Promise.all([
      cargoWait({ page: 1, pagesize: 1 }),
      cargoOnTheWay(),
      cargoHistory({ page: 1, pagesize: 1 }),
    ])
    waitCount.value = (waitRes && waitRes.counts) || 0
    onTheWayCount.value = onRes && typeof onRes === 'object' && Object.keys(onRes).length > 0 ? 1 : 0
    doneCount.value = (doneRes && doneRes.counts) || 0
    countState.value = 'ok'
  } catch (e) {
    countState.value = 'fail'
    uni.showToast({ title: '任务数暂不可用，点重试再试一次', icon: 'none' })
  }
}

async function loadTruck() {
  try {
    const p = await userProfile()
    truckId.value = p?.truckId || ''
  } catch (e) {
    errorMsg.value = '车辆信息预取失败，进入车辆页时会重试'
  }
}

onShow(() => {
  loadCount()
  loadTruck()
  refreshQueue()
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.head {
  padding: var(--s-5) var(--s-1) var(--s-6);
  display: flex;
  flex-direction: column;
}
.head-top { display: flex; justify-content: space-between; align-items: center; }
.brand { font-size: var(--f-aux); color: var(--c-text-2); }
.greeting { margin-top: var(--s-4); }
.head-sub { margin-top: var(--s-1); }
.report-bar {
  display: flex;
  align-items: center;
  background: var(--c-surface);
  border: 1rpx solid var(--c-border);
  border-left: var(--s-1) solid var(--c-primary);
  border-radius: var(--r-md);
  padding: var(--s-4);
  margin-bottom: var(--s-3);
}
.report-dot {
  width: var(--s-3);
  height: var(--s-3);
  border-radius: var(--r-round);
  background: var(--c-text-4);
  margin-right: var(--s-3);
  flex-shrink: 0;
}
.report-dot.ok { background: var(--c-success); }
.report-dot.delay { background: var(--c-warning); }
.report-dot.offline { background: var(--c-error); }
.report-body { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.report-title { font-size: var(--f-body); font-weight: var(--fw-semibold); color: var(--c-text-1); }
.report-desc { margin-top: var(--s-1); }
.report-switch {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--s-1) var(--s-4);
}
.stat-card { display: flex; }
.stat-item { flex: 1; display: flex; flex-direction: column; align-items: center; }
.stat-num {
  font-size: var(--f-title);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-tight);
  color: var(--c-text-1);
}
.stat-label { margin-top: var(--s-2); }
.fail-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--s-2) var(--s-4);
}
.main-cta-wrap {
  margin-top: var(--s-9);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.cta-hint { margin-bottom: var(--s-3); }
.cta-note { margin-top: var(--s-3); }
.sub-links {
  display: flex;
  justify-content: space-around;
  margin-top: var(--s-8);
  padding: var(--s-4) 0;
  border-top: 1rpx solid var(--c-border);
}
.sub-link { color: var(--c-text-2); padding: var(--s-2) var(--s-4); }
.err-banner {
  margin-top: var(--s-4);
  padding: var(--s-3) var(--s-4);
  background: var(--c-error-light, var(--c-fill-1));
  color: var(--c-error);
  border-radius: var(--r-sm);
}
</style>
