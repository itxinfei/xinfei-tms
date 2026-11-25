<template>
  <view class="page login-page">
    <!-- 品牌区：放大 logo + 端名 + slogan -->
    <view class="login-brand">
      <image class="login-logo" src="/static/logo-full-login.png" mode="widthFix" />
      <view class="login-end">心飞速运</view>
      <view class="login-slogan">心飞物流，让货运更简单</view>
    </view>

    <!-- 表单卡 -->
    <view class="login-card">
      <view class="login-title">欢迎回来</view>
      <view class="login-sub">请登录账号，开启高效物流服务</view>

      <u-form ref="formRef" :model="form" :rules="loginRules" errorType="none">
        <FormField prop="account" :borderBottom="false">
          <view class="field" id="field-account">
            <u-input v-model="form.account" placeholder="请输入账号" border="none" :clearable="true" prefixIcon="account" />
          </view>
        </FormField>
        <FormField prop="password" :borderBottom="false">
          <view class="field" id="field-password">
            <u-input v-model="form.password" placeholder="请输入密码" border="none" type="password" :clearable="true" prefixIcon="lock" />
          </view>
        </FormField>
        <FormField prop="code" :borderBottom="false">
          <view class="field field-captcha" id="field-code">
            <u-input v-model="form.code" placeholder="输入验证码" border="none" class="captcha-input" />
            <image class="captcha-img" :src="captchaSrc" mode="aspectFill" @click="refreshCaptcha" />
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
        :disabled="loading || !privacyAgreed"
        :customStyle="loginBtnStyle"
        @click="handleLogin"
      />
      <view v-if="loginError" class="login-error t-aux" role="alert">{{ loginError }}</view>
      <view v-if="!privacyAgreed" class="privacy-tip t-tip" @click="showPrivacy">
        需先同意《隐私政策》才能登录 · 点击重新阅读
      </view>

      <view class="login-agree">登录即代表同意《用户协议》和《隐私政策》</view>
    </view>

    <view class="login-footer">心飞速运</view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import FormField from '../../../components/FormField.vue';
import { firstValidateError, focusField } from '../../../common/ui-form.js';
import { useUserStore } from '../../../stores/user.js';
import { login, captchaUrl } from '../../../common/api/auth.js';
import { TENANT_CODE } from '../../../common/config';

const userStore = useUserStore();
const loading = ref(false);
const loginError = ref('');
const privacyAgreed = ref(uni.getStorageSync('cst_privacy_agreed') === '1');
const loginBtnStyle = { height: 'var(--s-13)' };
const key = ref('');
const captchaSrc = ref('');

const form = reactive({ account: '', password: '', code: '' });
const formRef = ref(null);
const loginRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
};

// 记住账号（《70》§3.4.1）：成功登录后保留账号，下次自动回填
const REMEMBER_KEY = 'xfwl_remember_account';
const remember = ref(false);
const savedAccount = uni.getStorageSync(REMEMBER_KEY);
if (savedAccount) {
  form.account = savedAccount;
  remember.value = true;
}

const refreshCaptcha = () => {
  key.value = 'k' + Date.now() + Math.floor(Math.random() * 1000);
  captchaSrc.value = captchaUrl(key.value);
};

const showPrivacy = () => {
  if (privacyAgreed.value) return;
  uni.showModal({
    title: '隐私政策提示',
    content: '为向您提供寄件、查件、轨迹等服务，我们需要收集您的手机号、地址、订单及位置信息（最小必要）。完整隐私政策见《用户协议》。拒绝授权将无法登录。',
    confirmText: '同意',
    cancelText: '暂不使用',
    success: (res) => {
      if (res.confirm) {
        uni.setStorageSync('cst_privacy_agreed', '1');
        privacyAgreed.value = true;
        loginError.value = '';
      } else {
        // 「暂不使用」真实拦截：登录按钮保持禁用，文案与行为一致
        privacyAgreed.value = false;
        uni.showToast({ title: '未同意隐私政策，暂不能登录', icon: 'none' });
      }
    }
  });
};

const handleLogin = async () => {
  if (!privacyAgreed.value) {
    showPrivacy();
    return;
  }
  try {
    await formRef.value?.validate();
  } catch (err) {
    const first = firstValidateError(err);
    loginError.value = first.message;
    uni.showToast({ title: first.message, icon: 'none' });
    focusField(first.field);
    return;
  }
  if (loading.value) return; // 连点幂等守卫
  loginError.value = '';
  loading.value = true;
  try {
    const data = await login({
      account: form.account,
      password: form.password,
      key: key.value,
      code: form.code,
      tenantCode: TENANT_CODE,
    });
    // JWT 在 data.token（后端返回字符串；兼容历史两层写法）
    const jwt = (data && data.token && data.token.token) || (data && data.token);
    if (!jwt) {
      loginError.value = '登录失败：服务未返回凭证，请稍后重试';
      uni.showToast({ title: loginError.value, icon: 'none' });
      refreshCaptcha();
      form.code = '';
      return;
    }
    userStore.setToken(jwt);
    userStore.setUserInfo(data.user || null);
    if (remember.value) uni.setStorageSync(REMEMBER_KEY, form.account);
    else uni.removeStorageSync(REMEMBER_KEY);
    uni.showToast({ title: '登录成功', icon: 'success' });
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/customer/home/index' });
    }, 500);
  } catch (err) {
    const msg = String((err && (err.message || err.msg)) || '');
    loginError.value = /验证码|captcha/i.test(msg)
      ? '验证码错误或已过期，请重新输入'
      : /网络|network|timeout|请求失败|请求超时/i.test(msg)
        ? '网络异常，请检查连接后重试'
        : '账号或密码不正确，请核对后重试';
    uni.showToast({ title: loginError.value, icon: 'none' });
    refreshCaptcha();
    form.code = '';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  showPrivacy();
  if (userStore.isLogin) {
    uni.reLaunch({ url: '/pages/customer/home/index' });
    return;
  }
  refreshCaptcha();
});
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
  color: var(--c-primary-on);
  font-size: var(--f-aux);
  letter-spacing: 2rpx;
}
.login-card {
  position: relative;
  z-index: 1;
  margin: var(--s-11) var(--s-5) 0;
  background: var(--c-surface);
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
.privacy-tip {
  margin-top: var(--s-3);
  color: var(--c-warning);
  text-align: center;
  text-decoration: underline;
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
  color: var(--c-primary-on);
  font-size: var(--f-tip);
  text-shadow: 0 1rpx var(--s-2) rgba(0, 0, 0, 0.35);
}
</style>
