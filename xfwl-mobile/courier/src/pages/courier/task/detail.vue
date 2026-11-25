<template>
  <view class="page" v-if="detail">
    <!-- 头部：运单号 + 状态 + 任务类型 -->
    <view class="card head-card">
      <view class="head-no">
        <text class="head-label">运单号</text>
        <text class="head-value">{{ detail.orderNumber || detail.id }}</text>
      </view>
      <view class="head-tags">
        <u-tag :text="statusView(detail.status).text" :type="statusView(detail.status).type" size="mini" />
        <u-tag :text="typeView(detail.taskType).text" type="primary" plain size="mini" />
      </view>
    </view>

    <!-- 寄件人 -->
    <view class="card addr-card">
      <text class="addr-title">寄件人</text>
      <view class="addr-row"><text class="addr-key">姓名</text><text class="addr-val">{{ maskName(sender?.name) }}</text></view>
      <view class="addr-row" hover-class="row-hover" @click="callPhone(sender?.phoneNumber)">
        <text class="addr-key">电话</text><text class="addr-val addr-link">{{ maskPhone(sender?.phoneNumber) }}</text>
      </view>
      <view class="addr-row"><text class="addr-key">单位</text><text class="addr-val">{{ sender?.company || '—' }}</text></view>
      <view class="addr-row" hover-class="row-hover" @click="navTo(sender?.address)">
        <text class="addr-key">地址</text><text class="addr-val addr-link">{{ sender?.address || '—' }}</text>
      </view>
    </view>

    <!-- 收件人 -->
    <view class="card addr-card">
      <text class="addr-title">收件人</text>
      <view class="addr-row"><text class="addr-key">姓名</text><text class="addr-val">{{ maskName(receiver?.name) }}</text></view>
      <view class="addr-row" hover-class="row-hover" @click="callPhone(receiver?.phoneNumber)">
        <text class="addr-key">电话</text><text class="addr-val addr-link">{{ maskPhone(receiver?.phoneNumber) }}</text>
      </view>
      <view class="addr-row"><text class="addr-key">单位</text><text class="addr-val">{{ receiver?.company || '—' }}</text></view>
      <view class="addr-row" hover-class="row-hover" @click="navTo(receiver?.address)">
        <text class="addr-key">地址</text><text class="addr-val addr-link">{{ receiver?.address || '—' }}</text>
      </view>
    </view>

    <!-- 货物信息（详情才有） -->
    <view class="card goods-card" v-if="hasGoods">
      <text class="addr-title">货物信息</text>
      <view class="addr-row"><text class="addr-key">货物类型</text><text class="addr-val">{{ detail.goodsTypeName || '—' }}</text></view>
      <view class="addr-row"><text class="addr-key">重量</text><text class="addr-val">{{ detail.weight != null ? detail.weight + ' kg' : '—' }}</text></view>
      <view class="addr-row"><text class="addr-key">数量</text><text class="addr-val">{{ detail.quantity != null ? detail.quantity : '—' }}</text></view>
      <view class="addr-row"><text class="addr-key">支付方式</text><text class="addr-val">{{ detail.paymentMethod || '—' }}</text></view>
      <view class="addr-row"><text class="addr-key">金额</text><text class="addr-val amount">{{ detail.amount != null ? '¥' + detail.amount : '—' }}</text></view>
    </view>

    <!-- 路由时间轴（标题常驻：无节点也可进地图看实时位置） -->
    <view class="card route-card">
      <view class="route-head">
        <text class="addr-title" style="margin-bottom: 0">路由轨迹</text>
        <text class="route-entry" @click="goRoute">地图路线 ›</text>
      </view>
      <!-- 修改点：uview-plus 无 u-time-line 组件，改用普通 view + CSS 时间轴 -->
      <view class="tl" v-if="routeList.length">
        <view class="tl-row" v-for="(node, idx) in routeList" :key="idx">
          <view class="tl-dot"></view>
          <view class="route-node">
            <text class="route-agency">{{ node.agencyName || '—' }}</text>
            <text class="route-time">{{ node.arrivalTime || '' }}</text>
          </view>
        </view>
      </view>
      <text v-else class="route-empty">暂无路由节点，可进地图查看实时位置</text>
    </view>
  </view>

  <!-- 操作按钮（按状态显示：未完成时可操作） -->
  <ActionBar v-if="canOperate">
    <u-button type="primary" text="去签收" shape="circle" @click="goSign"></u-button>
    <u-button type="success" text="入库" shape="circle" @click="goWarehousing"></u-button>
    <u-button type="warning" text="交接" shape="circle" @click="goHandover"></u-button>
    <u-button type="info" text="核验" shape="circle" @click="goVerify"></u-button>
    <u-button type="error" text="异常上报" shape="circle" @click="goException"></u-button>
  </ActionBar>
</template>

<script setup lang="ts">
// 任务详情：接 id，调 taskDetail + taskRoute，展示收寄件人 / 货物 / 路由，按状态显示操作按钮
import { ref, computed } from 'vue'
import ActionBar from '@/components/ActionBar.vue'
import { taskDetail, taskRoute } from '@/common/api/courier'
import { taskStatusView, taskTypeView } from '@/common/constants'
import { maskName, maskPhone } from '@/common/utils/desensitive'
import { onLoad } from '@dcloudio/uni-app'

const id = ref('')
const detail = ref<any>(null)
const routeList = ref<any[]>([])

const sender = computed(() => detail.value?.sender || null)
const receiver = computed(() => detail.value?.receiver || null)
const hasGoods = computed(
  () =>
    detail.value &&
    (detail.value.goodsTypeName != null ||
      detail.value.weight != null ||
      detail.value.amount != null)
)
// 未完成（非已完成 4 / 已取消 5）时显示操作按钮
const canOperate = computed(
  () => detail.value && detail.value.status !== 4 && detail.value.status !== 5
)

function statusView(s?: number) {
  return taskStatusView(s)
}
function typeView(t?: number) {
  return taskTypeView(t)
}

// route 返回可能是数组，或 {items:[...]} / {routeList:[...]}，统一兜底
function normalizeRoute(r: any): any[] {
  if (!r) return []
  if (Array.isArray(r)) return r
  if (Array.isArray(r.items)) return r.items
  if (Array.isArray(r.routeList)) return r.routeList
  return []
}

async function loadDetail() {
  try {
    const [d, r] = await Promise.all([taskDetail(id.value), taskRoute(id.value)])
    detail.value = d || null
    routeList.value = normalizeRoute(r)
  } catch (e) {
    uni.showToast({ title: "任务详情加载失败，请重试", icon: "none" })
    detail.value = null
  }
}

function goSign() {
  uni.navigateTo({
    url:
      '/pages/courier/pod/sign?tranOrderId=' +
      (detail.value?.tranOrderId || id.value) +
      '&orderNumber=' +
      (detail.value?.orderNumber || ''),
  })
}
function goWarehousing() {
  uni.navigateTo({
    url: '/pages/courier/task/warehousing?tranOrderId=' + (detail.value?.tranOrderId || id.value),
  })
}
function goHandover() {
  uni.navigateTo({
    url: '/pages/courier/task/handover?tranOrderId=' + (detail.value?.tranOrderId || id.value),
  })
}
function goVerify() {
  uni.navigateTo({
    url: '/pages/courier/task/verify?orderNumber=' + (detail.value?.orderNumber || ''),
  })
}
// 异常上报：tranOrderId 优先，任务 id 作为 transportTaskId 兜底（report 页三参容错）
function goException() {
  uni.navigateTo({
    url:
      '/pages/courier/exception/report?tranOrderId=' +
      (detail.value?.tranOrderId || id.value) +
      '&orderNumber=' +
      encodeURIComponent(detail.value?.orderNumber || '') +
      '&transportTaskId=' +
      id.value,
  })
}
// 配送路线：route 页按任务 id 取节点 + 10s 轮询实时位置
function goRoute() {
  uni.navigateTo({ url: '/pages/courier/task/route?id=' + id.value })
}

// 联系客户（展示脱敏，拨号用原值，《70》§3.3）
function callPhone(phone?: string) {
  if (!phone) return
  uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} })
}

// 地图导航（地址文本检索，任务侧无坐标契约）
function navTo(address?: string) {
  const addr = (address || '').trim()
  if (!addr) return
  // #ifdef H5
  window.open(`https://uri.amap.com/search?keyword=${encodeURIComponent(addr)}`, '_blank')
  // #endif
  // #ifndef H5
  uni.setClipboardData({
    data: addr,
    success: () => uni.showToast({ title: '地址已复制', icon: 'none' }),
  })
  // #endif
}

// onLoad 直接接收页面参数（uni-app 自动注入，不能从 'vue' 导入）
onLoad((options: any) => {
  id.value = options?.id || ''
  loadDetail()
})
</script>

<style lang="scss" scoped>
.head-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-no {
  display: flex;
  flex-direction: column;
}
.head-label {
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.head-value {
  font-size: var(--f-sub);
  color: var(--c-text-1);
  font-weight: bold;
  margin-top: var(--s-1);
}
.head-tags {
  display: flex;
  gap: var(--s-2);
}
.addr-title {
  display: block;
  font-size: var(--f-sub);
  color: var(--c-text-1);
  font-weight: bold;
  margin-bottom: var(--s-2);
}
.addr-row {
  display: flex;
  font-size: var(--f-aux);
  margin-top: var(--s-1);
}
.addr-key {
  width: var(--sz-140);
  color: var(--c-text-3);
}
.addr-val {
  flex: 1;
  color: var(--c-text-2);
}
.addr-link {
  color: var(--c-primary);
}
.row-hover {
  background-color: rgba(255, 0, 51, 0.04);
}
.addr-val.amount {
  color: var(--c-error);
  font-weight: bold;
}
.route-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--s-2);
}
.route-entry {
  font-size: var(--f-aux);
  color: var(--c-primary);
}
.route-empty {
  display: block;
  font-size: var(--f-tip);
  color: var(--c-text-3);
  padding: var(--s-1) 0;
}
.route-node {
  display: flex;
  justify-content: space-between;
  flex: 1;
  padding-bottom: var(--s-3);
  border-bottom: 1rpx solid var(--c-border);
}
.tl-row {
  display: flex;
  align-items: flex-start;
}
.tl-dot {
  width: var(--s-3);
  height: var(--s-3);
  border-radius: 50%;
  background: var(--c-primary);
  margin: var(--s-2) var(--s-3) 0 0;
  flex-shrink: 0;
}
.route-agency {
  font-size: var(--f-aux);
  color: var(--c-text-1);
}
.route-time {
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.action-bar {
  display: flex;
  gap: var(--s-2);
  flex-wrap: wrap;
}
</style>
