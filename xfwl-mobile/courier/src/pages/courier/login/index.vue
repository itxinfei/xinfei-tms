<template>
  <view class="page login-page">
    <!-- 品牌区：放大 logo + 端名 + slogan -->
    <view class="login-brand">
      <image class="login-logo" src="/static/logo-full-login.png" mode="widthFix" />
      <view class="login-end">心飞快递员</view>
      <view class="login-slogan">心飞物流，让货运更简单</view>
    </view>

    <!-- 表单卡 -->
    <view class="login-card">
      <view class="login-title">欢迎回来</view>
      <view class="login-sub">请登录账号，开启高效配送服务</view>

      <u-form ref="formRef" :model="form" :rules="loginRules" errorType="none">
        <FormField prop="account" :borderBottom="false">
          <view class="field" id="field-account">
            <u--input v-model="form.account" placeholder="请输入账号" clearable prefixIcon="account" />
          </view>
        </FormField>
        <FormField prop="password" :borderBottom="false">
          <view class="field" id="field-password">
            <u--input v-model="form.password" placeholder="请输入密码" type="password" clearable prefixIcon="lock" />
          </view>
        </FormField>
        <FormField prop="code" :borderBottom="false">
          <view class="field field-captcha" id="field-code">
            <u--input v-model="form.code" placeholder="输入验证码" class="captcha-input" />
            <image class="captcha-img" :src="captchaUrl" mode="aspectFill" @click="refreshCaptcha" />
          </view>
        </FormField>
      </u-form>

      <view class="field-remember" @click="remember = !remember">
        <view class="remember-box" :class="{ on: remember }">
          <u-icon v-if="remember" name="checkmark" color="var(--c-primary-on)" size="12"></u-icon>
        </view>
        <text class="remember-text">记住账号</text>
      </view>

      <u-button
        class="login-btn"
        type="primary"
        shape="circle"
        :text="loading ? '登录中…' : '登 录'"
        :loading="loading"
        :disabled="loading"
        @click="onLogin"
      />
      <view v-if="loginError" class="login-error t-aux" role="alert">{{ loginError }}</view>

      <view class="login-agree">登录即代表同意《用户协议》和《隐私政策》</view>
    </view>

    <view class="login-footer">心飞快递员</view>
  </view>
</template>

<script setup lang="ts">
// 登录页：复用 courier 端鉴权接口与用户 store，登录成功后跳快递员首页
import { ref, reactive } from 'vue'
import FormField from '@/components/FormField.vue'
import { firstValidateError, focusField } from '@/common/ui-form.js'
import { loginApi, captchaSrc } from '@/common/api/courier'
import { TENANT_CODE } from '@/common/config'
import { useUserStore } from '@/common/store/user'

const form = reactive({ account: '', password: '', code: '' })
const formRef = ref<any>(null)
const loginRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}
const key = ref('')
const loading = ref(false)
const loginError = ref('')
const userStore = useUserStore()
const captchaUrl = ref('')

// 记住账号（《70》§3.4.1）：成功登录后保留账号，下次自动回填
const REMEMBER_KEY = 'xfwl_remember_account'
const remember = ref(false)
const savedAccount = uni.getStorageSync(REMEMBER_KEY)
if (savedAccount) {
  form.account = savedAccount
  remember.value = true
}

// 刷新验证码：生成唯一 key 拼到验证码地址
function refreshCaptcha() {
  key.value = 'cap_' + Date.now() + '_' + Math.floor(Math.random() * 10000)
  captchaUrl.value = captchaSrc(key.value)
}
refreshCaptcha()

async function onLogin() {
  try {
    await formRef.value?.validate()
  } catch (err: any) {
    const first = firstValidateError(err)
    loginError.value = first.message
    uni.showToast({ title: first.message, icon: 'none' })
    focusField(first.field)
    return
  }
  if (loading.value) return // 连点幂等守卫
  loginError.value = ''
  loading.value = true
  try {
    // 登录返回归一化后的 data：{ token:{token}, user }
    const res = await loginApi({
      account: form.account,
      password: form.password,
      key: key.value,
      code: form.code,
      tenantCode: TENANT_CODE,
    })
    const token = (res && res.token && res.token.token) || (res && res.token)
    if (!token) {
      loginError.value = '登录失败：服务未返回凭证，请稍后重试'
      uni.showToast({ title: loginError.value, icon: 'none' })
      refreshCaptcha()
      return
    }
    userStore.setToken(token)
    if (res.user) userStore.setUserInfo(res.user)
    if (remember.value) uni.setStorageSync(REMEMBER_KEY, form.account)
    else uni.removeStorageSync(REMEMBER_KEY)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      // 快递员首页是 tabBar，用 switchTab
      uni.switchTab({ url: '/pages/courier/home/index' })
    }, 600)
  } catch (e) {
    loginError.value = '账号、密码或验证码不正确，请核对后重试'
    uni.showToast({ title: loginError.value, icon: 'none' })
    refreshCaptcha()
    form.code = ''
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(160deg, var(--c-primary) 0%, var(--c-primary-dark) 100%);
}
.login-brand {
  position: relative;
  z-index: 1;
  padding-top: calc(var(--s-13) + env(safe-area-inset-top));
  display: flex;
  flex-direction: column;
  align-items: center;
}
.login-logo {
  width: var(--sz-840);
  max-width: 90vw;
  height: auto;
  filter: drop-shadow(0 var(--s-1) var(--s-4) rgba(0, 0, 0, 0.35));
}
.login-end {
  margin-top: var(--s-4);
  color: var(--c-primary-on);
  font-size: var(--f-body);
  font-weight: 600;
  letter-spacing: 2rpx;
  text-shadow: 0 2rpx var(--s-3) rgba(0, 0, 0, 0.4);
}
.login-slogan {
  margin-top: var(--s-2);
  color: rgba(255, 255, 255, 0.88);
  font-size: var(--f-aux);
  letter-spacing: 2rpx;
}
.login-card {
  position: relative;
  z-index: 1;
  margin: var(--s-11) var(--s-5) 0;
  background: rgba(255, 255, 255, 0.97);
  border-radius: var(--r-lg);
  padding: var(--s-9) var(--s-8) var(--s-8);
  box-shadow: 0 var(--s-6) var(--s-11) rgba(0, 0, 0, 0.3);
}
.login-title {
  font-size: var(--f-title);
  font-weight: 700;
  color: var(--c-text-1);
}
.login-sub {
  margin-top: var(--s-1);
  font-size: var(--f-aux);
  color: var(--c-text-3);
}
.field {
  margin-top: var(--s-7);
  height: var(--sz-92);
  background: var(--c-bg);
  border: 2rpx solid var(--c-border);
  border-radius: var(--r-md);
  padding: 0 var(--s-6);
  display: flex;
  align-items: center;
}
.field-captcha {
  padding-right: var(--s-3);
}
.captcha-input {
  flex: 1;
}
.captcha-img {
  width: var(--sz-200);
  height: var(--sz-72);
  border-radius: var(--r-sm);
  flex-shrink: 0;
}
.login-btn { margin-top: var(--s-10); }
.login-error {
  margin-top: var(--s-3);
  color: var(--c-error);
  text-align: center;
}
.field-remember {
  margin-top: var(--s-6);
  display: flex;
  align-items: center;
}
.remember-box {
  width: var(--sz-34);
  height: var(--sz-34);
  border-radius: 50%;
  border: 2rpx solid var(--c-border);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--s-3);
}
.remember-box.on {
  background: var(--c-primary);
  border-color: var(--c-primary);
}
.remember-text {
  font-size: var(--f-aux);
  color: var(--c-text-3);
}
.login-agree {
  margin-top: var(--s-8);
  text-align: center;
  color: var(--c-text-3);
  font-size: var(--f-tip);
}
.login-footer {
  position: relative;
  z-index: 1;
  margin-top: var(--s-9);
  padding-bottom: var(--s-10);
  text-align: center;
  color: rgba(255, 255, 255, 0.85);
  font-size: var(--f-tip);
  text-shadow: 0 1rpx var(--s-2) rgba(0, 0, 0, 0.35);
}
</style>
