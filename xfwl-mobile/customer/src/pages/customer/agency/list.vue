<template>
  <view class="page">
    <u-navbar :title="'网点查询'" :back="true" :borderBottom="false" bgColor="var(--c-surface)" title-color="var(--c-text-1)" left-icon-color="var(--c-text-1)" placeholder />

    <view class="filter-bar">
      <u-search v-model="keyword" placeholder="搜索网点名称/地址" :showAction="false" bgColor="var(--c-surface)" @change="reloadDebounced" @clear="reload" />
    </view>

    <!-- 定位排序（《70》§3.1①）：取当前位置→后端按距离升序 -->
    <view class="loc-bar">
      <text class="loc-text">{{ locReady ? '已按距离由近到远排序' : '开启定位，优先展示附近网点' }}</text>
      <text class="loc-btn" @click="onLocate">{{ locReady ? '重新定位' : '开启定位' }}</text>
    </view>

    <StatusBlock :state="listState" message="网点列表加载失败，请检查网络后重试" empty-text="未找到网点" @retry="reload">
    <view class="agency-list">
      <view v-for="item in list" :key="item.id" class="card agency-card" hover-class="card-hover">
        <view class="agency-head">
          <text class="agency-name">{{ item.name || '-' }}</text>
          <u-tag v-if="item.orgType === 1" text="营业网点" type="primary" :plain="true" size="mini" />
          <u-tag v-else text="分拨/转运" type="info" :plain="true" size="mini" />
        </view>
        <view class="agency-row">
          <u-icon name="map" color="var(--c-text-3)" size="16" />
          <text class="agency-text">{{ item.fullAddress || '-' }}</text>
        </view>
        <view class="agency-row">
          <u-icon name="phone" color="var(--c-text-3)" size="16" />
          <text class="agency-text">{{ maskPhone(item.contractNumber) }}</text>
        </view>
        <view v-if="item.manager" class="agency-row">
          <u-icon name="account" color="var(--c-text-3)" size="16" />
          <text class="agency-text">负责人：{{ maskName(item.manager) }}</text>
        </view>
        <view class="agency-foot" hover-class="nav-hover" @click.stop="navToAgency(item)">
          <u-icon name="arrow-right" color="var(--c-primary)" size="14" />
          <text class="nav-text">地图导航</text>
        </view>
      </view>
      <u-loadmore :status="loadStatus" />
    </view>
    </StatusBlock>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import StatusBlock from '../../../components/StatusBlock.vue';
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
import { agencyPage } from '../../../common/api/index.js';
import { maskName, maskPhone } from '../../../common/utils/mask.js';
import { debounce } from '../../../common/request/safe.js';

const keyword = ref('');
const list = ref([]);
const loading = ref(false);
const error = ref(false);
const listState = computed(() =>
  loading.value && list.value.length === 0 ? 'loading'
    : error.value ? 'fail'
      : list.value.length === 0 ? 'empty' : 'success'
);
const pageNum = ref(1);
const pageSize = 10;
const total = ref(0);
const loadStatus = ref('loadmore');
const locReady = ref(false);
const coords = ref({ latitude: '', longitude: '' });

// 取当前位置（wgs84，与库内坐标口径一致）；失败/拒绝则优雅回退为默认排序
const locate = () =>
  new Promise((resolve) => {
    uni.getLocation({
      type: 'wgs84',
      success: (res) => {
        coords.value = { latitude: String(res.latitude), longitude: String(res.longitude) };
        locReady.value = true;
        resolve(true);
      },
      fail: () => {
        locReady.value = false;
        resolve(false);
      },
    });
  });

const onLocate = async () => {
  const ok = await locate();
  if (ok) reload();
};

const loadList = async (reset = false) => {
  if (loading.value) return;
  if (reset) {
    pageNum.value = 1;
    list.value = [];
  }
  loading.value = true;
  error.value = false;
  try {
    const params = {
      keyword: keyword.value,
      page: pageNum.value,
      pagesize: pageSize,
    };
    if (locReady.value) {
      params.latitude = coords.value.latitude;
      params.longitude = coords.value.longitude;
    }
    const data = await agencyPage(params);
    const items = (data && data.items) || [];
    total.value = (data && data.counts) || 0;
    list.value = reset ? items : list.value.concat(items);
    loadStatus.value = list.value.length >= total.value ? 'nomore' : 'loadmore';
  } catch (e) {
    loadStatus.value = 'loadmore';
    if (list.value.length === 0) error.value = true;
  } finally {
    loading.value = false;
  }
};

const reload = () => {
  loadStatus.value = 'loadmore';
  loadList(true);
};
const reloadDebounced = debounce(reload, 300);

// 网点卡片→地图导航（按地址文本检索；网点无坐标契约，《70》§3.1②）
const navToAgency = (item) => {
  const addr = (item && (item.fullAddress || item.address)) || '';
  if (!addr) return;
  // #ifdef H5
  window.open(`https://uri.amap.com/search?keyword=${encodeURIComponent(addr)}`, '_blank');
  // #endif
  // #ifndef H5
  uni.setClipboardData({
    data: addr,
    success: () => uni.showToast({ title: '地址已复制', icon: 'none' }),
  });
  // #endif
};

onPullDownRefresh(async () => {
  await loadList(true);
  uni.stopPullDownRefresh();
});

onReachBottom(() => {
  if (loadStatus.value === 'nomore' || loading.value) return;
  pageNum.value += 1;
  loadList(false);
});

// 首屏：先尝试定位（成功才带坐标），再拉列表
(async () => {
  await locate();
  reload();
})();
</script>

<style lang="scss" scoped>
.filter-bar {
  padding: var(--s-4) var(--s-6) 0;
}
.loc-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--s-3) var(--s-6) 0;
}
.loc-text {
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.loc-btn {
  font-size: var(--f-tip);
  color: var(--c-primary);
}
.state-wrap { padding-top: var(--sz-120); }
.retry-wrap { display: flex; justify-content: center; padding: var(--s-8) 0; }
.sk { padding: var(--s-4) var(--s-6); }
.agency-list {
  padding: var(--s-4) var(--s-6);
}
.agency-card { padding: var(--s-6); }
.card-hover {
  background-color: rgba(255, 0, 51, 0.04);
}
.agency-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--s-1);
  margin-top: var(--s-4);
  padding-top: var(--s-3);
  border-top: 1rpx solid var(--c-border);
}
.nav-hover {
  background-color: rgba(255, 0, 51, 0.04);
}
.nav-text {
  font-size: var(--f-tip);
  color: var(--c-primary);
}
.agency-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--s-3);
}
.agency-name {
  font-size: var(--f-sub);
  font-weight: 600;
  color: var(--c-text-1);
}
.agency-row {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  margin-top: var(--s-2);
  font-size: var(--f-aux);
  color: var(--c-text-2);
}
.agency-text {
  line-height: 1.4;
}
</style>
