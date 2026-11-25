<template>
  <view class="page">
    <u-navbar :title="'我的'" :back="false" :borderBottom="false" bgColor="var(--c-surface)" title-color="var(--c-text-1)" left-icon-color="var(--c-text-1)" placeholder />

    <!-- 用户信息 -->
    <view class="profile-hero">
      <view class="avatar">
        <image v-if="profile && profile.avatar" :src="profile.avatar" mode="aspectFill" class="avatar-img" />
        <view v-else class="avatar-default">{{ profileName }}</view>
      </view>
      <view class="profile-info">
        <view class="profile-name">{{ profile ? (profile.name || '心飞用户') : '心飞用户' }}</view>
        <view class="profile-phone">{{ profile ? maskPhone(profile.phone) : '加载中...' }}</view>
      </view>
    </view>

    <!-- 功能入口：分组卡片（《70》§3.1） -->
    <view v-for="(group, gi) in menuGroups" :key="group.title" class="menu-group">
      <view class="group-title">{{ group.title }}</view>
      <view class="card menu-card" :class="{ 'menu-card-first': gi === 0 }">
        <view
          v-for="item in group.items"
          :key="item.text"
          class="menu-row"
          hover-class="menu-hover"
          @click="item.click"
        >
          <u-icon :name="item.icon" color="var(--c-primary)" size="20" />
          <text class="menu-text">{{ item.text }}</text>
          <u-icon name="arrow-right" color="var(--c-text-4)" size="18" />
        </view>
      </view>
    </view>

    <!-- 退出登录回到内容流，不再与原生 tabBar 争夺底部固定层 -->
    <view class="logout-wrap">
      <u-button type="error" shape="circle" plain text="退出登录" class="logout-btn" @click="handleLogout" />
    </view>
    <!-- 底部导航由 pages.json 原生 tabBar 提供 -->
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '../../../stores/user.js';
import { maskPhone, maskName } from '../../../common/utils/mask.js';

const userStore = useUserStore();
const profile = ref(null);

const profileName = computed(() => {
  const name = profile.value && profile.value.name ? profile.value.name : (userStore.userInfo && userStore.userInfo.name);
  return name ? maskName(name) : '客';
});

const load = async () => {
  if (!userStore.isLogin) {
    uni.reLaunch({ url: '/pages/customer/login/index' });
    return;
  }
  try {
    profile.value = await userStore.fetchProfile();
  } catch (e) {
    uni.showToast({ title: "个人信息加载失败，请重试", icon: "none" })
    profile.value = null;
  }
};

const goAddress = () => uni.navigateTo({ url: '/pages/customer/address/list' });
// 标签页用 switchTab，保留页面栈与筛选状态；其余页面用 navigateTo
const goMailingList = () => uni.switchTab({ url: '/pages/customer/mailing/list' });
const goAgency = () => uni.navigateTo({ url: '/pages/customer/agency/list' });
const goAbout = () => uni.showToast({ title: '心飞速运 v1.0', icon: 'none' });
const goService = () => uni.showToast({ title: '客服热线即将开放', icon: 'none' });

// 菜单分组（《70》§3.1：寄件服务 / 设置 / 关于）
const menuGroups = [
  {
    title: '寄件服务',
    items: [
      { text: '我的运单', icon: 'order', click: goMailingList },
      { text: '网点查询', icon: 'map', click: goAgency },
    ],
  },
  {
    title: '设置',
    items: [
      { text: '地址簿', icon: 'list-dot', click: goAddress },
      { text: '联系客服', icon: 'server', click: goService },
    ],
  },
  {
    title: '关于',
    items: [{ text: '关于心飞', icon: 'info-circle', click: goAbout }],
  },
];

const handleLogout = () => {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout();
      }
    },
  });
};

onShow(() => {
  load();
});
</script>

<style lang="scss" scoped>
.profile-hero {
  background: var(--c-surface);
  border: 1rpx solid var(--c-border);
  border-radius: var(--r-md);
  padding: var(--s-6) var(--s-5);
  display: flex;
  align-items: center;
}
.avatar {
  width: var(--s-13);
  height: var(--s-13);
  border-radius: 50%;
  overflow: hidden;
  margin-right: var(--s-6);
  background: var(--c-fill-1);
  flex-shrink: 0;
}
.avatar-img { width: 100%; height: 100%; }
.avatar-default {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-2);
  font-size: var(--f-title);
  font-weight: var(--fw-semibold);
}
.profile-name {
  color: var(--c-text-1);
  font-size: var(--f-title);
  font-weight: var(--fw-semibold);
}
.profile-phone {
  margin-top: var(--s-2);
  color: var(--c-text-3);
  font-size: var(--f-aux);
}
.menu-group {
  margin-top: var(--s-2);
}
.group-title {
  margin: 0 var(--s-8) var(--s-3);
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.menu-card {
  margin: 0 var(--s-6) var(--s-5);
  padding: var(--s-2) var(--s-6);
}
.menu-card-first {
  margin-top: calc(var(--s-6) * -1);
}
.menu-row {
  display: flex;
  align-items: center;
  padding: var(--s-7) 0;
  border-bottom: 1rpx solid var(--c-border);
}
.menu-row:last-child { border-bottom: none; }
.menu-hover {
  background-color: var(--c-primary-light);
}
.menu-text {
  flex: 1;
  margin-left: var(--s-4);
  font-size: var(--f-body);
  color: var(--c-text-1);
}
.logout-wrap { margin: var(--s-8) var(--s-4); }
.logout-btn { height: var(--s-13); }
</style>
