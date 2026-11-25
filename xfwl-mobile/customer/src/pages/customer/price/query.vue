<template>
  <view class="page">
    <u-navbar title="运费查询" :back="true" :borderBottom="false" bgColor="var(--c-primary)" placeholder />

    <u-form ref="formRef" :model="form" :rules="formRules" errorType="none">
      <!-- 地址信息：复用地址簿选择回传（与寄件页同一交互） -->
      <view class="card">
        <view class="card-title">地址信息</view>
        <FormField prop="sendAddress" :borderBottom="false">
          <view class="addr-row" id="field-sendAddress" @click="pickAddress('send')">
            <view class="addr-icon send">寄</view>
            <view class="addr-body">
              <view class="addr-name">{{ sendAddr ? sendAddr.fullAddress || '已选择' : '选择发件地址' }}</view>
              <view v-if="sendAddr" class="addr-contact">{{ maskName(sendAddr.name) }} {{ maskPhone(sendAddr.phoneNumber) }}</view>
            </view>
            <u-icon name="arrow-right" color="var(--c-text-3)" size="20" />
          </view>
        </FormField>
        <FormField prop="receiptAddress" :borderBottom="false">
          <view class="addr-row" id="field-receiptAddress" @click="pickAddress('receipt')">
            <view class="addr-icon recv">收</view>
            <view class="addr-body">
              <view class="addr-name">{{ recvAddr ? recvAddr.fullAddress || '已选择' : '选择收件地址' }}</view>
              <view v-if="recvAddr" class="addr-contact">{{ maskName(recvAddr.name) }} {{ maskPhone(recvAddr.phoneNumber) }}</view>
            </view>
            <u-icon name="arrow-right" color="var(--c-text-3)" size="20" />
          </view>
        </FormField>
      </view>

      <!-- 货物信息 -->
      <FormField prop="goodsWeight" :borderBottom="false">
        <view class="card" id="field-goodsWeight">
          <view class="card-title">货物信息</view>
          <view class="form-row">
            <text class="form-label">物品类型</text>
            <view class="picker-cell" @click="showGoodsPicker = true">
              <text class="picker-value" :class="{ placeholder: !goodsTypeName }">{{ goodsTypeName || '选择物品类型' }}</text>
              <u-icon name="arrow-down" color="var(--c-text-3)" size="18" />
            </view>
          </view>
          <view class="form-row">
            <text class="form-label">重量(kg)</text>
            <u-input v-model="form.goodsWeight" placeholder="请输入重量" border="none" type="digit" />
          </view>
        </view>
      </FormField>
    </u-form>

    <!-- 物品类型加载失败：不再静默吞掉，给可见状态与重试入口 -->
    <StatusBlock
      v-if="goodsLoadState === 'fail'"
      state="fail"
      message="物品类型加载失败，点重试重新加载"
      @retry="loadGoodsTypes"
    />

    <!-- 唯一底部固定层；占位交给全局 .page 的 padding-bottom -->
    <ActionBar>
      <u-button type="primary" shape="circle" :loading="loading" @click="handleQuery">查询运费</u-button>
    </ActionBar>

    <!-- 试算结果：加载中 / 失败（带重试） / 成功 三态统一出口 -->
    <view v-if="queried || loading" class="card price-card">
      <StatusBlock :state="priceState" :message="errorMsg" @retry="handleQuery">
        <view class="price-line">
          <text class="price-label">预估运费</text>
          <text class="price-value">¥{{ priceText }}</text>
        </view>
        <view class="price-tip">试算结果仅供参考，实际运费以下单计费为准</view>
      </StatusBlock>
    </view>

    <!-- 物品类型选择 -->
    <u-picker :show="showGoodsPicker" :columns="goodsColumns" @confirm="onGoodsConfirm" @cancel="showGoodsPicker = false" />
  </view>
</template>

<script setup>
// CST-03 运费查询：独立于寄件流程的快速试算页，仅调 totalPrice 试算，不产生订单数据
import { ref, reactive, computed, onUnmounted } from 'vue';
import { totalPrice } from '../../../common/api/mailing.js';
import { goodsTypeAll } from '../../../common/api/index.js';
import { maskName, maskPhone } from '../../../common/utils/mask.js';
import { firstValidateError, focusField } from '../../../common/ui-form.js';
import FormField from '../../../components/FormField.vue';
import StatusBlock from '../../../components/StatusBlock.vue';
import ActionBar from '../../../components/ActionBar.vue';

const sendAddr = ref(null);
const recvAddr = ref(null);
const goodsTypes = ref([]);
const goodsTypeName = ref('');
const goodsLoadState = ref('loading');
const loading = ref(false);
const queried = ref(false);
const showGoodsPicker = ref(false);
const formRef = ref(null);

const form = reactive({
  sendAddress: '', // 地址簿 id（与寄件页口径一致，禁传文本地址）
  receiptAddress: '',
  goodsName: '',
  goodsType: '',
  goodsWeight: '',
});

// 字段级校验：地址与重量都由 u-form 统一判空，页面不再手写一串 if
const formRules = {
  sendAddress: [{ required: true, message: '请选择发件地址', trigger: 'blur' }],
  receiptAddress: [{ required: true, message: '请选择收件地址', trigger: 'blur' }],
  goodsWeight: [
    { required: true, message: '请输入重量', trigger: 'blur' },
    {
      validator: (_r, v, cb) => (Number(v) > 0 ? cb() : cb(new Error('重量必须大于 0'))),
      trigger: 'blur',
    },
  ],
};

const price = ref(null);
const errorMsg = ref('');
const priceText = computed(() => (price.value !== null && price.value !== undefined ? price.value : '--'));
const priceState = computed(() => (loading.value ? 'loading' : errorMsg.value ? 'fail' : 'success'));

const goodsColumns = computed(() => [goodsTypes.value.map((g) => g.name || g.id || '未知类型')]);

const pickAddress = (role) => {
  uni.navigateTo({ url: `/pages/customer/address/list?selectMode=${role}` });
};

const onGoodsConfirm = (e) => {
  const name = (e.value || [])[0];
  const found = goodsTypes.value.find((g) => (g.name || g.id) === name);
  if (found) {
    form.goodsType = found.id;
    goodsTypeName.value = found.name || found.id;
  }
  showGoodsPicker.value = false;
};

const loadGoodsTypes = () => {
  goodsLoadState.value = 'loading';
  goodsTypeAll()
    .then((data) => {
      goodsTypes.value = Array.isArray(data) ? data : (data && data.items) || [];
      goodsLoadState.value = 'success';
    })
    .catch(() => {
      goodsLoadState.value = 'fail';
      uni.showToast({ title: '物品类型加载失败，可点重试', icon: 'none' });
    });
};
loadGoodsTypes();

const handleQuery = async () => {
  try {
    await formRef.value?.validate();
  } catch (err) {
    const first = firstValidateError(err);
    uni.showToast({ title: first.message, icon: 'none' });
    focusField(first.field);
    return;
  }
  if (loading.value) return; // 连点幂等守卫
  loading.value = true;
  queried.value = true;
  errorMsg.value = '';
  try {
    // 与寄件页同参结构：pickupType/payMethod 传默认值（仅参与计费口径，不产生订单）
    const data = await totalPrice({
      ...form,
      goodsWeight: Number(form.goodsWeight),
      pickUpTime: '',
      pickupType: 2, // 2 上门取件
      payMethod: 1, // 1 预结
    });
    // 后端口径：参数不全/地址解析失败时 amount="0" 且带 errorMsg；正常返回字符串金额
    if (data && data.amount !== undefined && data.amount !== '0') {
      price.value = data.amount;
      errorMsg.value = '';
    } else {
      price.value = null;
      errorMsg.value = (data && data.errorMsg) || '暂时无法试算，请检查地址与货物信息';
    }
  } catch (e) {
    price.value = null;
    errorMsg.value = '试算失败，请检查网络后重试';
  } finally {
    loading.value = false;
  }
};

// 地址簿选择回传（uni.$emit('addressSelected')），页面销毁时解除监听防重复回调
const onAddressSelected = (payload) => {
  if (payload && payload.role === 'send') {
    sendAddr.value = payload.item;
    form.sendAddress = payload.item.id;
  } else if (payload && payload.role === 'receipt') {
    recvAddr.value = payload.item;
    form.receiptAddress = payload.item.id;
  }
};
uni.$on('addressSelected', onAddressSelected);
onUnmounted(() => {
  uni.$off('addressSelected', onAddressSelected);
});
</script>

<style lang="scss" scoped>
.card-title {
  font-size: var(--f-sub);
  font-weight: 600;
  color: var(--c-text-1);
  margin-bottom: var(--s-3);
}
.addr-row {
  display: flex;
  align-items: center;
  padding: var(--s-3) 0;
}
.addr-icon {
  width: var(--s-11);
  height: var(--s-11);
  border-radius: 50%;
  color: var(--c-primary-on);
  font-size: var(--f-body);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--s-3);
}
.addr-icon.send {
  background: var(--c-primary);
}
.addr-icon.recv {
  background: var(--c-success);
}
.addr-body {
  flex: 1;
  min-width: 0;
}
.addr-name {
  font-size: var(--f-body);
  color: var(--c-text-1);
}
.addr-contact {
  margin-top: var(--s-1);
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.form-row {
  display: flex;
  align-items: center;
  padding: var(--s-3) 0;
}
.form-label {
  width: var(--sz-160);
  font-size: var(--f-body);
  color: var(--c-text-2);
}
.picker-cell {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.picker-value {
  font-size: var(--f-body);
  color: var(--c-text-1);
}
.picker-value.placeholder {
  color: var(--c-text-3);
}
.price-card {
  padding: var(--s-5) var(--s-4);
}
.price-line {
  display: flex;
  align-items: baseline;
  justify-content: center;
}
.price-label {
  font-size: var(--f-body);
  color: var(--c-text-2);
  margin-right: var(--s-3);
}
.price-value {
  font-size: var(--f-display);
  font-weight: 700;
  color: var(--c-primary);
}
.price-tip {
  margin-top: var(--s-2);
  text-align: center;
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
</style>
