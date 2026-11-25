<template>
  <view class="page">
    <u-navbar :title="'我的运单'" :back="false" :borderBottom="false" bgColor="var(--c-surface)" title-color="var(--c-text-1)" left-icon-color="var(--c-text-1)" placeholder />

    <!-- 筛选 -->
    <view class="filter-bar">
      <u-search v-model="keyword" placeholder="搜索订单号" :showAction="false" bgColor="var(--c-surface)" @change="onSearchDebounced" @clear="reload" />
      <!-- P1-1：两行筛选语义拆分。方向用分段控件（实心选中），状态用 chips（描边选中），灰度可辨 -->
      <u-subsection
        class="type-segment"
        :list="mailTypes.map((t) => t.label)"
        :current="mailType"
        activeColor="var(--c-primary)"
        inactiveColor="var(--c-text-2)"
        bgColor="var(--c-fill-1)"
        @change="onTypeChange"
      />
      <AppTabBar
        label="运单状态"
        :items="statusTabItems"
        :current="statusKey"
        @change="switchStatus"
      />
    </view>

    <!-- 列表：三态统一由 StatusBlock 提供，失败带可诊断文案与重试 -->
    <StatusBlock
      :state="listState"
      :message="errorMsg"
      empty-text="暂无运单，去首页寄第一单吧"
      @retry="reload"
    >
      <view class="order-list">
      <view v-for="item in list" :key="item.id" class="card order-card" hover-class="card-hover" @click="goDetail(item.id)">
        <view class="order-head">
          <view class="order-no">单号 {{ item.id }}</view>
          <u-tag :text="statusInfo(item.status).label" :type="statusInfo(item.status).type" :plain="false" />
        </view>
        <view class="order-route">
          <view class="route-item">
            <view class="route-dot send"></view>
            <view class="route-text">{{ item.senderFullAddress || item.senderAddress || '-' }}</view>
          </view>
          <view class="route-item">
            <view class="route-dot recv"></view>
            <view class="route-text">{{ item.receiverFullAddress || item.receiverAddress || '-' }}</view>
          </view>
        </view>
        <view class="order-foot">
          <view class="foot-left">
            <text class="amount">¥{{ item.amount ?? '-' }}</text>
            <text class="pay">{{ payMethodLabel(item.paymentMethod) }}</text>
            <text class="time">{{ fmtTime(item.createTime) }}</text>
          </view>
          <view v-if="item.routeDTO && item.routeDTO.msg" class="foot-route">{{ item.routeDTO.msg }}</view>
        </view>
      </view>
      <u-loadmore :status="loadStatus" />
      </view>
    </StatusBlock>

    <!-- 底部导航由 pages.json 原生 tabBar 提供，不再叠加页内 u-tabbar -->
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
import { page, statusFilters } from '../../../common/api/mailing.js';
import { statusInfo, payMethodLabel, fmtTime } from '../../../common/utils/order.js';
import { useUserStore } from '../../../stores/user.js';
import { debounce, createInFlight, requestSafe, RequestState, pageParams, pagePayload } from '../../../common/request/safe.js';
import StatusBlock from '../../../components/StatusBlock.vue';
import AppTabBar from '../../../components/AppTabBar.vue';

const userStore = useUserStore();
const keyword = ref('');
const mailType = ref(0); // 0 我寄的 / 1 我收的
const mailTypes = [
  { label: '我寄的', value: 0 },
  { label: '我收的', value: 1 },
];
const list = ref([]);
const loading = ref(false);
const error = ref(false);
const errorMsg = ref('');
const pageNum = ref(1);
const pageSize = 10;
const total = ref(0);
const loadStatus = ref('loadmore');
const inFlight = createInFlight(); // P1-3：onShow 与下拉刷新并发时复用同一个 Promise

const listState = computed(() =>
  loading.value && list.value.length === 0 ? 'loading'
    : error.value ? 'fail'
      : list.value.length === 0 ? 'empty' : 'success'
);

// 状态筛选档位（后端 status-filters 下发，禁前端硬编码；失败保底"全部"）
const statusTabs = ref([{ key: 'all', label: '全部', statusList: null }]);
const statusKey = ref('all');
const statusTabItems = computed(() =>
  statusTabs.value.map((t) => ({ key: t.key, text: t.label }))
);
const onTypeChange = (index) => switchType(mailTypes[index].value);
let statusTabsLoaded = false;
const loadStatusTabs = async () => {
  if (statusTabsLoaded) return;
  statusTabsLoaded = true;
  try {
    const data = await statusFilters();
    if (Array.isArray(data) && data.length) {
      statusTabs.value = data;
    }
  } catch (e) {
    uni.showToast({ title: "筛选条件加载失败，已显示全部", icon: "none" })
    /* 保底全部档 */
  }
};
const switchStatus = (key) => {
  if (statusKey.value === key) return;
  statusKey.value = key;
  reload();
};

const loadList = async (reset = false) => {
  if (reset) {
    pageNum.value = 1;
    list.value = [];
  }
  loading.value = true;
  error.value = false;
  errorMsg.value = '';
  const params = {
    keyword: keyword.value,
    ...pageParams(pageNum.value, pageSize, 'lower'),
    mailType: mailType.value,
  };
  const curStatus = statusTabs.value.find((t) => t.key === statusKey.value);
  if (curStatus && Array.isArray(curStatus.statusList) && curStatus.statusList.length) {
    params.statusList = curStatus.statusList.join(',');
  }
  // 三值返回 + in-flight 去重：失败不再被吞成空数组，也不重复发请求
  const result = await inFlight('waybill-list', () =>
    requestSafe(page(params), { fallback: '运单加载失败，请检查网络后重试' })
  );
  if (result.state === RequestState.FAIL) {
    loadStatus.value = 'loadmore';
    if (list.value.length === 0) error.value = true;
    errorMsg.value = result.message;
  } else {
    const { items, counts } = pagePayload(result.data);
    total.value = counts;
    list.value = reset ? items : list.value.concat(items);
    loadStatus.value = list.value.length >= total.value ? 'nomore' : 'loadmore';
  }
  loading.value = false;
};

const reload = () => {
  loadStatus.value = 'loadmore';
  loadList(true);
};
const switchType = (v) => {
  mailType.value = v;
  reload();
};
const onSearch = () => reload();

const onSearchDebounced = debounce(onSearch, 300);
const goDetail = (id) => uni.navigateTo({ url: `/pages/customer/mailing/detail?id=${id}` });
// 标签切换统一由原生 tabBar 处理，页面内不再 reLaunch 清栈（切走再切回保留筛选与滚动位置）

onPullDownRefresh(async () => {
  await loadList(true);
  uni.stopPullDownRefresh();
});

onReachBottom(() => {
  if (loadStatus.value === 'nomore' || loading.value) return;
  pageNum.value += 1;
  loadList(false);
});

onShow(() => {
  if (!userStore.isLogin) {
    uni.reLaunch({ url: '/pages/customer/login/index' });
    return;
  }
  // 首页「查询运单」经 switchTab 进入（switchTab 不支持 query），用一次性本地存储接关键词
  const pending = uni.getStorageSync('home_search_keyword');
  if (pending) {
    keyword.value = pending;
    uni.removeStorageSync('home_search_keyword');
  }
  loadStatusTabs();
  loadList(true);
});
</script>

<style lang="scss" scoped>
.filter-bar {
  padding: var(--s-4) var(--s-6) 0;
}
/* 分段控件独占一行；状态 chips 由 AppTabBar 自带样式，两者形态与灰度明确区分 */
.type-segment {
  margin-top: var(--s-4);
}
.card-hover {
  background-color: var(--c-primary-light);
}
.order-list {
  padding: var(--s-4) var(--s-6);
}
.order-card {
  padding: var(--s-6);
}
.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-no {
  font-size: var(--f-sub);
  color: var(--c-text-1);
  font-weight: 600;
}
.order-route {
  margin-top: var(--s-4);
}
.route-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: var(--s-2);
}
.route-dot {
  width: var(--s-3);
  height: var(--s-3);
  border-radius: 50%;
  margin: var(--s-2) var(--s-3) 0 0;
  flex-shrink: 0;
}
.route-dot.send { background: var(--c-success); }
.route-dot.recv { background: var(--c-primary); }
.route-text {
  font-size: var(--f-aux);
  color: var(--c-text-2);
  line-height: 1.5;
}
.order-foot {
  margin-top: var(--s-4);
  padding-top: var(--s-4);
  border-top: 1rpx solid var(--c-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.foot-left {
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.amount {
  font-size: var(--f-sub);
  color: var(--c-error);
  font-weight: 600;
  margin-right: var(--s-3);
}
.pay { margin-right: var(--s-3); }
.foot-route {
  max-width: var(--sz-320);
  font-size: var(--f-tip);
  color: var(--c-primary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
