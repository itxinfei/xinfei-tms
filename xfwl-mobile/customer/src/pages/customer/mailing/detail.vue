<template>
  <view class="page">
    <u-navbar :title="'运单详情'" :back="true" :borderBottom="false" bgColor="var(--c-surface)" title-color="var(--c-text-1)" left-icon-color="var(--c-text-1)" placeholder />

    <!-- 三态统一：加载中 / 失败（带诊断文案与重试）/ 不存在 / 正常 -->
    <StatusBlock
      :state="detailState"
      :message="errorMsg"
      empty-text="运单不存在"
      :retryable="detailState === 'empty' ? false : true"
      @retry="loadDetail"
    >
      <!-- 状态条 -->
      <view class="status-hero">
        <u-tag :text="statusInfo(detail.status).label" :type="statusInfo(detail.status).type" :plain="false" size="large" />
        <view class="status-time">{{ fmtTime(detail.planPickUpTime || detail.actualDispathedTime) }}</view>
      </view>

      <!-- 基本信息 -->
      <view class="card">
        <view class="card-title">订单信息</view>
        <view class="info-row"><text class="info-label">订单号</text><text class="info-value">{{ detail.id }}</text></view>
        <view class="info-row">
          <text class="info-label">运单号</text>
          <view class="info-copy" @click.stop="copyOrderNo">
            <text class="info-value">{{ detail.tranOrderId || '-' }}</text>
            <text v-if="detail.tranOrderId" class="copy-btn">复制</text>
          </view>
        </view>
        <view class="info-row"><text class="info-label">计划取件</text><text class="info-value">{{ fmtTime(detail.planPickUpTime) }}</text></view>
        <view class="info-row"><text class="info-label">派送完成</text><text class="info-value">{{ fmtTime(detail.actualDispathedTime) }}</text></view>
        <view class="info-row"><text class="info-label">取消时间</text><text class="info-value">{{ fmtTime(detail.cancelTime) }}</text></view>
        <view v-if="detail.name" class="info-row">
          <text class="info-label">快递员</text>
          <text class="info-value">{{ maskName(detail.name) }} {{ maskPhone(detail.mobile) }}</text>
        </view>
      </view>

      <!-- 签收回单（B2：客户只见状态/备注/现场照片，手写签名仅管理端可见） -->
      <view class="card" v-if="detail.signStatus === 1 || detail.signStatus === 2">
        <view class="card-title">签收回单</view>
        <view class="info-row">
          <text class="info-label">签收情况</text>
          <text class="info-value" :class="{ 'pod-reject': detail.signStatus === 2 }">
            {{ detail.signStatus === 2 ? '拒收' : '已签收' }}
          </text>
        </view>
        <view v-if="detail.signRemark" class="info-row">
          <text class="info-label">{{ detail.signStatus === 2 ? '拒收原因' : '签收备注' }}</text>
          <text class="info-value">{{ detail.signRemark }}</text>
        </view>
        <view v-if="detail.signPictures && detail.signPictures.length" class="pod-photos">
          <image
            v-for="(url, i) in detail.signPictures"
            :key="i"
            :src="url"
            class="pod-img"
            mode="aspectFill"
            @click="previewPod(i)"
          />
        </view>
      </view>

      <!-- 物流时间轴 -->
      <view class="card">
        <view class="card-title">物流动态</view>
        <view v-if="routeList.length === 0" class="route-empty">暂无物流动态</view>
        <view v-else class="route-timeline">
          <view v-for="(r, i) in routeList" :key="i" class="route-node">
            <view class="node-left">
              <view class="node-dot" :class="{ last: i === 0 }"></view>
              <view v-if="i !== routeList.length - 1" class="node-line"></view>
            </view>
            <view class="node-body">
              <view class="node-msg">{{ r.msg || '节点更新' }}</view>
              <view class="node-time">{{ fmtTime(r.time) }}</view>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部操作 -->
      <view class="action-bar detail-actions">
        <u-button v-if="canPay" type="primary" shape="circle" text="去支付" class="action-btn" @click="handlePay" />
        <u-button v-if="canCancel" type="error" shape="circle" plain text="取消订单" class="action-btn" @click="handleCancel" />
        <u-button type="primary" shape="circle" plain text="查看实时轨迹" class="action-btn" @click="goTrack" />
        <u-button type="primary" shape="circle" plain text="再来一单" class="action-btn" @click="goAgain" />
      </view>
      <!-- 底部留白由 .page 的 padding-bottom: calc(var(--fixed-bottom-h) + var(--s-6)) 统一承载 -->
    </StatusBlock>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { detail as fetchDetail, route as fetchRoute, pay, cancel } from '../../../common/api/mailing.js';
import { statusInfo, fmtTime } from '../../../common/utils/order.js';
import { maskName, maskPhone } from '../../../common/utils/mask.js';
import { requestSafe, RequestState } from '../../../common/request/safe.js';
import StatusBlock from '../../../components/StatusBlock.vue';

const id = ref('');
const detail = ref(null);
const routeList = ref([]);
const loading = ref(true);
const error = ref(false);
const errorMsg = ref('');

// 仅"待取件(1)"可取消、可支付（《07》§一紧凑码）
const canCancel = computed(() => detail.value && detail.value.status === 1);
const canPay = computed(() => detail.value && detail.value.status === 1);

// 三态出口：加载中 / 失败（带诊断文案与重试）/ 不存在（无重试意义）/ 正常
const detailState = computed(() =>
  (loading.value && !detail.value) ? 'loading'
    : error.value ? 'fail'
      : !detail.value ? 'empty'
        : 'success'
);

const loadDetail = async () => {
  loading.value = true;
  error.value = false;
  errorMsg.value = '';
  // 三值返回：失败不再被吞成空对象，统一由 StatusBlock 展示诊断文案与重试
  const result = await requestSafe(() => fetchDetail(id.value), {
    fallback: '运单详情加载失败，请检查网络后重试',
  });
  if (result.state === RequestState.FAIL) {
    error.value = true;
    errorMsg.value = result.message;
  } else {
    detail.value = result.data || null;
  }
  loading.value = false;
};

const loadRoute = async () => {
  try {
    const data = await fetchRoute(id.value);
    const list = data || [];
    routeList.value = list.slice().sort((a, b) => (a.time > b.time ? 1 : -1));
  } catch (e) {
    uni.showToast({ title: "物流信息加载失败，请重试", icon: "none" })
    routeList.value = [];
  }
};

const handlePay = async () => {
  uni.showModal({
    title: '确认支付',
    content: '确定支付该订单运费吗？',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await pay(id.value);
        uni.showToast({ title: '支付成功', icon: 'success' });
        loadDetail();
        loadRoute();
      } catch (e) {
        uni.showToast({ title: "运单操作失败，请重试", icon: "none" })
      }
    },
  });
};

const handleCancel = async () => {
  uni.showModal({
    title: '取消订单',
    content: '取消后订单将关闭并全额退款，确定取消吗？',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await cancel(id.value);
        uni.showToast({ title: '已取消', icon: 'success' });
        loadDetail();
        loadRoute();
      } catch (e) {
        uni.showToast({ title: "运单操作失败，请重试", icon: "none" })
      }
    },
  });
};

const goTrack = () => uni.navigateTo({ url: `/pages/customer/mailing/track?id=${id.value}` });

// 再来一单：带原寄件/收件地址跳下单页预填（《70》§3.1③）
const goAgain = () => {
  const d = detail.value;
  if (!d) return;
  const sender = d.senderFullAddress || d.senderAddress || '';
  const receiver = d.receiverFullAddress || d.receiverAddress || '';
  uni.navigateTo({
    url:
      '/pages/customer/mailing/create?again=1' +
      '&senderAddress=' + encodeURIComponent(sender) +
      '&receiverAddress=' + encodeURIComponent(receiver),
  });
};

// 签收现场照片全屏预览（客户只见照片，不见手写签名）
const previewPod = (index) => {
  const urls = (detail.value && detail.value.signPictures) || [];
  if (!urls.length) return;
  uni.previewImage({ current: index, urls });
};

const copyOrderNo = () => {
  const no = detail.value && detail.value.tranOrderId;
  if (!no) return;
  uni.setClipboardData({
    data: String(no),
    success: () => uni.showToast({ title: '运单号已复制', icon: 'success' }),
  });
};

onLoad((options) => {
  id.value = options.id || '';
  loadDetail();
  loadRoute();
});
</script>

<style lang="scss" scoped>
.status-hero {
  margin: var(--s-6);
  padding: var(--s-8);
  background: var(--c-surface);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-card);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.status-time {
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.card-title {
  font-size: var(--f-sub);
  font-weight: 600;
  color: var(--c-text-1);
  margin-bottom: var(--s-4);
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: var(--s-2) 0;
  font-size: var(--f-body);
}
.info-label { color: var(--c-text-3); }
.info-value { color: var(--c-text-1); }
.info-copy {
  display: flex;
  align-items: center;
  gap: var(--s-3);
}
.copy-btn {
  font-size: var(--f-tip);
  color: var(--c-primary);
}
.pod-reject { color: var(--c-error); }
.pod-photos {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s-4);
  padding-top: var(--s-3);
}
.pod-img {
  width: var(--sz-200);
  height: var(--sz-200);
  border-radius: var(--r-sm);
  background: var(--c-bg);
}
.route-empty {
  padding: var(--s-8) 0;
  text-align: center;
  color: var(--c-text-3);
  font-size: var(--f-aux);
}
.route-timeline { padding: var(--s-2) 0; }
.route-node { display: flex; }
.node-left {
  width: var(--s-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: var(--s-3);
}
.node-dot {
  width: var(--s-4);
  height: var(--s-4);
  border-radius: 50%;
  background: var(--c-primary);
  margin-top: var(--s-2);
  flex-shrink: 0;
}
.node-dot.last { background: var(--c-success); }
.node-line {
  flex: 1;
  width: 2rpx;
  background: var(--c-border);
  min-height: var(--s-9);
}
.node-body { padding-bottom: var(--s-7); }
.node-msg {
  font-size: var(--f-body);
  color: var(--c-text-1);
}
.node-time {
  margin-top: var(--s-1);
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s-4);
  padding-bottom: var(--s-6);
}
.action-btn { flex: 1; }
</style>
