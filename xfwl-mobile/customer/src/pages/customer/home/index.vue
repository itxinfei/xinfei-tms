<template>
  <view class="page home-page">
    <!-- 中性标题区：品牌 + 问候；不再用整屏红渐变承载白字（对比度 3.07:1） -->
    <view class="home-head">
      <text class="head-brand t-aux">心飞速运</text>
      <text class="t-title head-greeting">{{ greeting }}</text>
      <text class="t-aux head-sub">寄件、查件，从这里开始</text>
    </view>

    <!-- 整页唯一视觉主操作：品牌红只占这一小块 -->
    <view class="primary-cta">
      <u-button
        type="primary"
        text="我要寄件"
        shape="circle"
        :customStyle="ctaStyle"
        @click="goMailing"
      />
      <text class="t-tip cta-hint">在线下单 · 运费即时试算</text>
    </view>

    <!-- 查单输入：关键词通过一次性本地存储带到运单列表（switchTab 不支持 query） -->
    <view class="card search-card">
      <text class="t-sub search-label">查询运单</text>
      <view class="search-row">
        <u-input v-model="waybillNo" placeholder="输入运单号" border="surround" clearable />
        <u-button type="primary" plain text="查询" @click="goSearch" />
      </view>
    </view>

    <!-- 进行中订单：加载中 / 成功 / 失败三态，失败给可见文案与重试 -->
    <view class="card count-card">
      <view class="count-main">
        <text class="t-aux">进行中的订单</text>
        <text class="t-display count-num">{{ countText }}</text>
      </view>
      <view class="count-side">
        <text class="t-tip" :class="{ 'is-error': countFailed }">{{ countState }}</text>
        <u-button v-if="countFailed" type="primary" plain size="mini" text="重试" @click="loadCount" />
      </view>
    </view>

    <!-- 次要入口降为一行文字链接，不参与主视觉竞争 -->
    <view class="secondary">
      <text class="t-body link" @click="goAddress">地址簿</text>
      <text class="t-body link" @click="goAgency">网点查询</text>
      <text class="t-body link" @click="goPriceQuery">运费查询</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { count } from '../../../common/api/mailing.js';
import { useUserStore } from '../../../stores/user.js';

const userStore = useUserStore();
const waybillNo = ref('');
const countNum = ref(0);
const countLoading = ref(true);
const countFailed = ref(false);

// 主按钮高度用令牌，不写死 rpx；--s-13 = 96rpx，满足单手触控热区下限
const ctaStyle = { height: 'var(--s-13)', fontSize: 'var(--f-sub)' };

const greeting = computed(() => {
  const h = new Date().getHours();
  const part = h < 6 ? '凌晨好' : h < 9 ? '早上好' : h < 12 ? '上午好' : h < 14 ? '中午好' : h < 18 ? '下午好' : h < 22 ? '晚上好' : '夜深了';
  const info = userStore.userInfo;
  const name = info && (info.name || info.nickname);
  return name ? `${part}，${name}` : part;
});

const countText = computed(() =>
  countLoading.value ? '…' : countFailed.value ? '—' : String(countNum.value)
);
const countState = computed(() =>
  countLoading.value ? '正在获取' : countFailed.value ? '暂不可用' : '实时更新'
);

const loadCount = async () => {
  countLoading.value = true;
  countFailed.value = false;
  try {
    const data = await count();
    countNum.value = data && data.count !== undefined ? Number(data.count) || 0 : 0;
  } catch (e) {
    countFailed.value = true;
    uni.showToast({ title: '订单数暂不可用', icon: 'none' });
  } finally {
    countLoading.value = false;
  }
};

const goMailing = () => uni.navigateTo({ url: '/pages/customer/mailing/create' });
const goAddress = () => uni.navigateTo({ url: '/pages/customer/address/list' });
const goAgency = () => uni.navigateTo({ url: '/pages/customer/agency/list' });
const goPriceQuery = () => uni.navigateTo({ url: '/pages/customer/price/query' });

const goSearch = () => {
  const kw = waybillNo.value.trim();
  if (!kw) {
    uni.showToast({ title: '请输入运单号', icon: 'none' });
    return;
  }
  uni.setStorageSync('home_search_keyword', kw);
  uni.switchTab({ url: '/pages/customer/mailing/list' });
};

onShow(() => {
  if (!userStore.isLogin) {
    uni.reLaunch({ url: '/pages/customer/login/index' });
    return;
  }
  loadCount();
});
</script>

<style lang="scss" scoped>
.home-page {
  padding: 0 var(--s-4) var(--s-10);
}
.home-head {
  display: flex;
  flex-direction: column;
  padding: calc(var(--s-8) + env(safe-area-inset-top)) 0 var(--s-6);
}
.head-brand { letter-spacing: var(--s-1); }
.head-greeting { margin-top: var(--s-3); }
.head-sub { margin-top: var(--s-2); }
.primary-cta { margin: 0 0 var(--s-8); }
.cta-hint { display: block; text-align: center; margin-top: var(--s-3); }
.search-card { padding: var(--s-4); }
.search-label { display: block; margin-bottom: var(--s-3); }
.search-row { display: flex; align-items: center; }
.search-row :deep(.u-input) { flex: 1; margin-right: var(--s-2); }
.search-row :deep(.u-button) { flex-shrink: 0; }
.count-card { display: flex; justify-content: space-between; align-items: center; }
.count-main { display: flex; flex-direction: column; }
.count-num { margin-top: var(--s-1); }
.count-side { display: flex; flex-direction: column; align-items: flex-end; }
.count-side :deep(.u-button) { margin-top: var(--s-2); }
.is-error { color: var(--c-error); }
.secondary {
  display: flex;
  justify-content: space-around;
  padding: var(--s-6) 0;
}
.link { color: var(--c-text-3); text-decoration: underline; }
</style>
