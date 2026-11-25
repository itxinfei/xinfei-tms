<template>
  <view class="page">
    <u-navbar :title="'地址簿'" :back="true" :borderBottom="false" bgColor="var(--c-surface)" title-color="var(--c-text-1)" left-icon-color="var(--c-text-1)" placeholder />

    <!-- 搜索 -->
    <view class="filter-bar">
      <u-search v-model="keyword" placeholder="搜索姓名/电话/地址" :showAction="false" bgColor="var(--c-surface)" @change="reloadDebounced" @clear="reload" />
    </view>

    <!-- 三态统一由公共组件承接：加载 / 失败+重试 / 空 / 正常 -->
    <StatusBlock
      :state="listState"
      message="地址簿加载失败，请检查网络后重试"
      empty-text="暂无地址，点下方按钮添加"
      @retry="reload"
    >
      <view class="addr-list">
      <view
        v-for="item in list"
        :key="item.id"
        class="card addr-card"
        :class="{ 'addr-default': item.isDefault === 1 }"
        hover-class="card-hover"
        @click="onItemClick(item)"
      >
        <view class="addr-head">
          <view class="addr-person">
            <text class="addr-name">{{ maskName(item.name) }}</text>
            <text class="addr-phone">{{ maskPhone(item.phoneNumber) }}</text>
            <u-tag v-if="item.isDefault === 1" text="默认" type="success" :plain="true" size="mini" />
          </view>
          <view v-if="!selectMode" class="addr-ops">
            <u-icon name="edit-pen" color="var(--c-primary)" size="20" aria-label="编辑地址" @click.stop="goEdit(item.id)" />
            <u-icon name="trash" color="var(--c-error)" size="20" aria-label="删除地址" @click.stop="handleDelete(item)" />
          </view>
        </view>
        <view class="addr-detail">{{ item.fullAddress || item.address || '-' }}</view>
        <view v-if="item.companyName" class="addr-company">{{ item.companyName }}</view>
      </view>
      <u-loadmore :status="loadStatus" />
      </view>
    </StatusBlock>

    <!-- 底部新增 -->
    <ActionBar v-if="!selectMode">
      <u-button type="primary" shape="circle" text="新增地址" class="add-btn" @click="goEdit('')" />
    </ActionBar>
    <!-- 底部留白由 .page 的 padding-bottom: calc(var(--fixed-bottom-h) + var(--s-6)) 统一承载 -->
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import ActionBar from '../../../components/ActionBar.vue';
import { onShow, onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
import StatusBlock from '../../../components/StatusBlock.vue';
import { page, remove } from '../../../common/api/address.js';
import { maskName, maskPhone } from '../../../common/utils/mask.js';
import { debounce } from '../../../common/request/safe.js';

const keyword = ref('');
const list = ref([]);
const loading = ref(false);
const error = ref(false);
const pageNum = ref(1);
const pageSize = 10;
const total = ref(0);
const loadStatus = ref('loadmore');
const selectMode = ref(''); // '' 普通模式；'send'/'receipt' 选择回传
// 三态收敛为单一状态值，交给 StatusBlock 统一渲染
const listState = computed(() =>
  loading.value && list.value.length === 0 ? 'loading'
    : error.value ? 'fail'
      : list.value.length === 0 ? 'empty' : 'success'
);

const loadList = async (reset = false) => {
  if (loading.value) return;
  if (reset) {
    pageNum.value = 1;
    list.value = [];
  }
  loading.value = true;
  error.value = false;
  try {
    // 注意：地址簿分页参数是 pageSize（驼峰大写S）
    const data = await page({
      keyword: keyword.value,
      page: pageNum.value,
      pageSize: pageSize,
    });
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

const onItemClick = (item) => {
  if (selectMode.value) {
    uni.$emit('addressSelected', { role: selectMode.value, item });
    uni.navigateBack({ delta: 1 });
    return;
  }
  goEdit(item.id);
};

const goEdit = (id) => {
  uni.navigateTo({ url: id ? `/pages/customer/address/edit?id=${id}` : '/pages/customer/address/edit' });
};

const handleDelete = (item) => {
  uni.showModal({
    title: '删除地址',
    content: `确定删除「${item.name}」的地址吗？`,
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await remove(item.id);
        uni.showToast({ title: '已删除', icon: 'success' });
        reload();
      } catch (e) {
        uni.showToast({ title: "地址操作失败，请重试", icon: "none" })
      }
    },
  });
};

onLoad((options) => {
  selectMode.value = options.selectMode || '';
});

onShow(() => {
  reload();
});

onPullDownRefresh(async () => {
  await loadList(true);
  uni.stopPullDownRefresh();
});

onReachBottom(() => {
  if (loadStatus.value === 'nomore' || loading.value) return;
  pageNum.value += 1;
  loadList(false);
});
</script>

<style lang="scss" scoped>
.filter-bar {
  padding: var(--s-4) var(--s-6) 0;
}
.state-wrap { padding-top: var(--sz-120); }
.retry-wrap { display: flex; justify-content: center; padding: var(--s-8) 0; }
.sk { padding: var(--s-4) var(--s-6); }
.addr-list {
  padding: var(--s-4) var(--s-6);
}
.addr-card { padding: var(--s-6); }
.addr-default {
  background-color: rgba(255, 0, 51, 0.04);
  border-color: rgba(255, 0, 51, 0.16);
}
.card-hover {
  background-color: rgba(255, 0, 51, 0.04);
}
.addr-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.addr-person {
  display: flex;
  align-items: center;
  gap: var(--s-4);
}
.addr-name {
  font-size: var(--f-sub);
  font-weight: 600;
  color: var(--c-text-1);
}
.addr-phone {
  font-size: var(--f-aux);
  color: var(--c-text-3);
}
.addr-ops {
  display: flex;
  gap: var(--s-6);
}
.addr-detail {
  margin-top: var(--s-3);
  font-size: var(--f-body);
  color: var(--c-text-2);
  line-height: 1.5;
}
.addr-company {
  margin-top: var(--s-1);
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.add-btn { height: var(--sz-88); }
</style>
