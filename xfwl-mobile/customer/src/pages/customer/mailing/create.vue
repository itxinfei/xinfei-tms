<template>
  <view class="page">
    <u-navbar
      title="我要寄件"
      :back="true"
      :borderBottom="false"
      bgColor="var(--c-surface)"
      title-color="var(--c-text-1)"
      left-icon-color="var(--c-text-1)"
      placeholder
    />

    <u-form :model="form" :rules="rules" ref="formRef" :errorType="['message']">
      <!-- 地址：内联弹层选择，不再离开下单页 -->
      <view class="card">
        <view class="card-title t-sub">地址信息</view>
        <u-form-item prop="sendAddress" :borderBottom="false">
          <view id="field-sendAddress" class="addr-row" @click="openPicker('send')">
            <view class="addr-icon send">寄</view>
            <view class="addr-body">
              <view class="addr-name t-body">{{ sendAddr ? sendAddr.fullAddress || sendAddr.address || '已选择' : '选择发件地址' }}</view>
              <view v-if="sendAddr" class="addr-contact t-tip">{{ maskName(sendAddr.name) }} {{ maskPhone(sendAddr.phoneNumber) }}</view>
            </view>
            <u-icon name="arrow-right" color="var(--c-text-3)" size="20" />
          </view>
        </u-form-item>
        <u-form-item prop="receiptAddress" :borderBottom="false">
          <view id="field-receiptAddress" class="addr-row" @click="openPicker('receipt')">
            <view class="addr-icon recv">收</view>
            <view class="addr-body">
              <view class="addr-name t-body">{{ recvAddr ? recvAddr.fullAddress || recvAddr.address || '已选择' : '选择收件地址' }}</view>
              <view v-if="recvAddr" class="addr-contact t-tip">{{ maskName(recvAddr.name) }} {{ maskPhone(recvAddr.phoneNumber) }}</view>
            </view>
            <u-icon name="arrow-right" color="var(--c-text-3)" size="20" />
          </view>
        </u-form-item>
        <view v-if="againHint" class="again-hint t-tip" role="status">
          再来一单：原地址 {{ againHint.sender || '—' }} → {{ againHint.receiver || '—' }}
          <text v-if="againNeedPick">，地址簿中未找到完全匹配项，请重新选择</text>
        </view>
      </view>

      <!-- 取件信息 -->
      <view class="card">
        <view class="card-title t-sub">取件信息</view>
        <u-form-item :borderBottom="false">
          <view class="form-row">
            <text class="form-label t-body">取件方式</text>
            <u-radio-group v-model="form.pickupType" activeColor="var(--c-primary)" shape="circle">
              <u-radio :name="1" label="网点自寄" />
              <u-radio :name="2" label="上门取件" />
            </u-radio-group>
          </view>
        </u-form-item>
        <u-form-item prop="pickUpTime" :borderBottom="false">
          <view id="field-pickUpTime" class="form-row" @click="showTimePicker = true">
            <text class="form-label t-body">取件时间</text>
            <view class="time-picker">
              <text class="time-value t-body">{{ form.pickUpTime || '选择时间段（如 09:00-18:00）' }}</text>
              <u-icon name="clock" color="var(--c-text-3)" size="18" />
            </view>
          </view>
        </u-form-item>
      </view>

      <!-- 货物信息 -->
      <view class="card">
        <view class="card-title t-sub">货物信息</view>
        <u-form-item prop="goodsType" :borderBottom="false">
          <view id="field-goodsType" class="form-row" @click="showGoodsPicker = true">
            <text class="form-label t-body">物品类型</text>
            <view class="time-picker">
              <text class="time-value t-body">{{ goodsTypeName || (goodsError ? '类型加载失败 · 点此重试' : '选择物品类型') }}</text>
              <u-icon name="arrow-down" color="var(--c-text-3)" size="18" />
            </view>
          </view>
        </u-form-item>
        <u-form-item prop="goodsName" :borderBottom="false">
          <view id="field-goodsName" class="form-row">
            <text class="form-label t-body">物品名称</text>
            <u-input v-model="form.goodsName" placeholder="如：文件、衣物" border="none" />
          </view>
        </u-form-item>
        <u-form-item prop="goodsWeight" :borderBottom="false">
          <view id="field-goodsWeight" class="form-row">
            <text class="form-label t-body">重量(kg)</text>
            <u-input v-model="form.goodsWeight" placeholder="请输入重量" border="none" type="digit" />
          </view>
        </u-form-item>
      </view>

      <!-- 费用：三态，不把失败伪装成 -- -->
      <view class="card">
        <view class="card-title t-sub">费用</view>
        <u-form-item :borderBottom="false">
          <view class="form-row">
            <text class="form-label t-body">付款方式</text>
            <u-radio-group v-model="form.payMethod" activeColor="var(--c-primary)" shape="circle">
              <u-radio :name="1" label="预结" />
              <u-radio :name="2" label="到付" />
            </u-radio-group>
          </view>
        </u-form-item>
        <view class="price-row" aria-live="polite">
          <view class="price-left">
            <text class="price-label t-aux">预估运费</text>
            <text class="price-value t-title">{{ priceText }}</text>
          </view>
          <u-button v-if="priceState === 'fail'" size="mini" :plain="true" text="重试" @click="calcPrice" />
        </view>
        <view v-if="priceState === 'fail'" class="price-error t-tip" role="alert">{{ priceError }}</view>
      </view>
    </u-form>

    <ActionBar>
      <u-button
        type="primary"
        shape="circle"
        :text="submitting ? '提交中…' : '提交订单'"
        :loading="submitting"
        :disabled="submitting"
        :customStyle="submitStyle"
        @click="handleSubmit"
      />
    </ActionBar>
    <view v-if="errorMsg" class="err-banner t-aux" role="alert">{{ errorMsg }}</view>

    <!-- 取件时间段 / 物品类型 -->
    <u-picker :show="showTimePicker" :columns="timeColumns" @confirm="onTimeConfirm" @cancel="showTimePicker = false" />
    <u-picker :show="showGoodsPicker" :columns="goodsColumns" @confirm="onGoodsConfirm" @cancel="showGoodsPicker = false" />

    <!-- 地址内联选择弹层 -->
    <u-popup :show="showAddrPicker" mode="bottom" :round="16" :closeable="true" @close="showAddrPicker = false">
      <view class="picker-panel">
        <text class="picker-title t-sub">{{ pickRole === 'send' ? '选择发件地址' : '选择收件地址' }}</text>
        <view v-if="addrLoading" class="picker-state t-aux">地址加载中…</view>
        <view v-else-if="addrError" class="picker-state t-aux" @click="loadAddrOptions">{{ addrError }} · 点此重试</view>
        <view v-else-if="!addrOptions.length" class="picker-state t-aux">地址簿为空，请先新增地址</view>
        <scroll-view v-else scroll-y class="picker-list">
          <view v-for="item in addrOptions" :key="item.id" class="picker-item" @click="chooseAddr(item)">
            <text class="t-body">{{ item.fullAddress || item.address || '未命名地址' }}</text>
            <text class="t-tip">{{ maskName(item.name) }} {{ maskPhone(item.phoneNumber) }}</text>
          </view>
        </scroll-view>
        <view class="picker-foot">
          <text class="t-aux" @click="goAddressBook">管理地址簿</text>
          <text class="t-aux" @click="showAddrPicker = false">取消</text>
        </view>
      </view>
    </u-popup>
  </view>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import ActionBar from '../../../components/ActionBar.vue';
import { onShow, onLoad, onUnload } from '@dcloudio/uni-app';
import { totalPrice, createOrder } from '../../../common/api/mailing.js';
import { goodsTypeAll } from '../../../common/api/index.js';
import { page as addressPage } from '../../../common/api/address.js';
import { maskName, maskPhone } from '../../../common/utils/mask.js';
import { firstValidateError, focusField } from '../../../common/ui-form.js';
import { debounce } from '../../../common/request/safe.js';

const formRef = ref(null);
const sendAddr = ref(null);
const recvAddr = ref(null);
const goodsTypes = ref([]);
const goodsTypeName = ref('');
const goodsError = ref('');
const price = ref(null);
const priceState = ref('idle'); // idle / loading / ok / fail
const priceError = ref('');
const submitting = ref(false);
const errorMsg = ref('');
const showTimePicker = ref(false);
const showGoodsPicker = ref(false);

// 地址内联弹层
const showAddrPicker = ref(false);
const pickRole = ref('send');
const addrOptions = ref([]);
const addrLoading = ref(false);
const addrError = ref('');

// 再来一单
const againHint = ref(null);
const againNeedPick = ref(false);

const form = reactive({
  sendAddress: '',
  receiptAddress: '',
  pickUpTime: '',
  pickupType: 2, // 1网点自寄 2上门取件
  payMethod: 1, // 1预结 2到付
  goodsType: '',
  goodsName: '',
  goodsWeight: '',
});

const rules = {
  sendAddress: [{ required: true, message: '请选择发件地址', trigger: ['change', 'blur'] }],
  receiptAddress: [{ required: true, message: '请选择收件地址', trigger: ['change', 'blur'] }],
  pickUpTime: [{ required: true, message: '请选择取件时间', trigger: ['change', 'blur'] }],
  goodsType: [{ required: true, message: '请选择物品类型', trigger: ['change', 'blur'] }],
  goodsName: [{ required: true, message: '请填写物品名称', trigger: ['change', 'blur'] }],
  goodsWeight: [{ required: true, message: '请填写物品重量', trigger: ['change', 'blur'] }],
};

const submitStyle = { height: 'var(--s-13)', minHeight: 'var(--s-13)', fontSize: 'var(--f-sub)' };

const priceText = computed(() => {
  if (priceState.value === 'loading') return '计算中…';
  if (priceState.value === 'ok') return `¥${price.value}`;
  if (priceState.value === 'fail') return '暂无法计算';
  return '填写完整后自动计算';
});

const timeColumns = computed(() => {
  const hours = [];
  for (let h = 8; h <= 20; h += 1) hours.push(h < 10 ? `0${h}:00` : `${h}:00`);
  return [hours.slice(0, hours.length - 1), hours.slice(1)];
});
const goodsColumns = computed(() => [goodsTypes.value.map((g) => g.name || g.id || '未知类型')]);

// 300ms 防抖统一走 shared/request/safe.js，不再各页自写一份
let priceSeq = 0;
const calcPrice = async () => {
  if (!form.sendAddress || !form.receiptAddress || !form.goodsWeight) {
    priceState.value = 'idle';
    return;
  }
  const seq = ++priceSeq;
  priceState.value = 'loading';
  priceError.value = '';
  try {
    const data = await totalPrice({ ...form });
    if (seq !== priceSeq) return; // 丢弃过期响应
    price.value = data && data.amount !== undefined ? data.amount : null;
    priceState.value = price.value !== null ? 'ok' : 'fail';
    if (priceState.value === 'fail') priceError.value = '报价服务未返回金额，请点重试';
  } catch (e) {
    if (seq !== priceSeq) return;
    priceState.value = 'fail';
    priceError.value = '运费暂无法计算，请检查网络后重试';
    uni.showToast({ title: priceError.value, icon: 'none' });
  }
};
const scheduleCalc = debounce(calcPrice, 300);

// 物品类型 / 取件方式 / 付款方式 / 取件时间 / 物品名称 / 重量 / 地址 任一变化都重算
watch(
  () => [
    form.sendAddress, form.receiptAddress, form.pickupType, form.payMethod,
    form.pickUpTime, form.goodsType, form.goodsName, form.goodsWeight,
  ],
  () => scheduleCalc()
);

const onTimeConfirm = (e) => {
  const values = e.value || [];
  if (values.length === 2 && values[0] && values[1]) form.pickUpTime = `${values[0]}-${values[1]}`;
  showTimePicker.value = false;
};
const onGoodsConfirm = (e) => {
  if (goodsError.value) { loadGoodsTypes(); return; }
  const name = (e.value || [])[0];
  const found = goodsTypes.value.find((g) => (g.name || g.id) === name);
  if (found) {
    form.goodsType = found.id;
    goodsTypeName.value = found.name || found.id;
  }
  showGoodsPicker.value = false;
};

const openPicker = (role) => {
  pickRole.value = role;
  showAddrPicker.value = true;
  loadAddrOptions();
};
const loadAddrOptions = async () => {
  addrLoading.value = true;
  addrError.value = '';
  try {
    const data = await addressPage({ page: 1, pageSize: 50 });
    addrOptions.value = (data && data.items) || [];
  } catch (e) {
    addrOptions.value = [];
    addrError.value = '地址簿加载失败';
    uni.showToast({ title: '地址簿加载失败，请点此重试', icon: 'none' });
  } finally {
    addrLoading.value = false;
  }
};
const chooseAddr = (item) => {
  if (pickRole.value === 'send') {
    sendAddr.value = item;
    form.sendAddress = item.id;
  } else {
    recvAddr.value = item;
    form.receiptAddress = item.id;
  }
  showAddrPicker.value = false;
  errorMsg.value = '';
};
const goAddressBook = () => {
  showAddrPicker.value = false;
  uni.navigateTo({ url: `/pages/customer/address/list?selectMode=${pickRole.value}` });
};
// 兼容从地址簿页返回的老通路；与 onUnload 的 $off 成对
const onAddressSelected = (payload) => {
  if (!payload || !payload.item) return;
  if (payload.role === 'send') {
    sendAddr.value = payload.item;
    form.sendAddress = payload.item.id;
  } else if (payload.role === 'receipt') {
    recvAddr.value = payload.item;
    form.receiptAddress = payload.item.id;
  }
};
uni.$on('addressSelected', onAddressSelected);
onUnload(() => uni.$off('addressSelected', onAddressSelected));

const loadGoodsTypes = async () => {
  goodsError.value = '';
  try {
    const data = await goodsTypeAll();
    goodsTypes.value = data || [];
  } catch (e) {
    goodsTypes.value = [];
    goodsError.value = '物品类型加载失败';
    uni.showToast({ title: '物品类型加载失败，请点类型行重试', icon: 'none' });
  }
};

// 字段级校验失败时把首个错误字段滚到可视区（实现来自 shared/ui/form.js）
const handleSubmit = async () => {
  if (submitting.value) return; // 连点幂等守卫
  errorMsg.value = '';
  try {
    await formRef.value.validate();
  } catch (err) {
    const first = firstValidateError(err);
    uni.showToast({ title: first.message, icon: 'none' });
    focusField(first.field);
    return;
  }
  const weight = Number(form.goodsWeight);
  if (!Number.isFinite(weight) || weight <= 0) {
    uni.showToast({ title: '重量需为大于 0 的数字', icon: 'none' });
    focusField('goodsWeight');
    return;
  }
  submitting.value = true;
  try {
    await createOrder({ ...form });
    uni.showToast({ title: '下单成功', icon: 'success' });
    setTimeout(() => {
      // 运单列表已是原生 tabBar 页，必须用 switchTab，不能 reLaunch 清栈
      uni.switchTab({ url: '/pages/customer/mailing/list' });
    }, 600);
  } catch (e) {
    errorMsg.value = '下单失败，请核对信息后重试；重复提交不会产生第二单';
    uni.showToast({ title: '下单失败，请稍后重试', icon: 'none' });
  } finally {
    submitting.value = false;
  }
};

// 再来一单：详情页传的是地址文本而不是 id，先在地址簿里反查 id，再触发重算
const matchAddr = (text) => {
  if (!text) return null;
  const t = String(text).replace(/\s+/g, '');
  return addrOptions.value.find((a) => {
    const cand = [a.fullAddress, a.address].filter(Boolean).map((v) => String(v).replace(/\s+/g, ''));
    return cand.some((v) => v === t || (v && t && (v.includes(t) || t.includes(v))));
  }) || null;
};
const prefillAgain = async (sender, receiver) => {
  if (!sender && !receiver) return;
  againHint.value = { sender, receiver };
  await loadAddrOptions();
  const s = matchAddr(sender);
  const r = matchAddr(receiver);
  againNeedPick.value = !(s && r);
  if (s) {
    sendAddr.value = s;
    form.sendAddress = s.id;
  }
  if (r) {
    recvAddr.value = r;
    form.receiptAddress = r.id;
  }
  calcPrice();
};

onShow(() => {
  loadGoodsTypes();
});

onLoad((options) => {
  if (!options || options.again !== '1') return;
  const decode = (v) => {
    if (!v) return '';
    try {
      return decodeURIComponent(v);
    } catch (e) {
      errorMsg.value = '历史地址参数解析失败，请手动选择地址';
      return '';
    }
  };
  prefillAgain(decode(options.senderAddress), decode(options.receiverAddress));
});
</script>

<style lang="scss" scoped>
.card-title { margin-bottom: var(--s-4); }
.addr-row {
  display: flex;
  align-items: center;
  width: 100%;
  padding: var(--s-4) 0;
  border-bottom: 1rpx solid var(--c-border);
}
.addr-icon {
  width: var(--s-11);
  height: var(--s-11);
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--f-body);
  font-weight: var(--fw-semibold);
  color: var(--c-primary-on);
  margin-right: var(--s-4);
  flex-shrink: 0;
}
.addr-icon.send { background: var(--c-success); }
.addr-icon.recv { background: var(--c-primary); }
.addr-body { flex: 1; min-width: 0; }
.addr-contact { margin-top: var(--s-1); }
.again-hint { margin-top: var(--s-3); color: var(--c-text-3); }
.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: var(--s-4) 0;
  border-bottom: 1rpx solid var(--c-border);
}
.form-label { width: var(--s-13); color: var(--c-text-2); flex-shrink: 0; }
.time-picker { flex: 1; display: flex; align-items: center; justify-content: space-between; color: var(--c-text-1); }
.price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--s-6);
}
.price-left { display: flex; flex-direction: column; }
.price-value { color: var(--c-primary); font-weight: var(--fw-semibold); margin-top: var(--s-1); }
.price-error { color: var(--c-error); margin-top: var(--s-2); }
.err-banner {
  margin-top: var(--s-4);
  padding: var(--s-3) var(--s-4);
  background: var(--c-fill-1);
  color: var(--c-error);
  border-radius: var(--r-sm);
}
.picker-panel { padding: var(--s-6) var(--s-5) var(--s-8); }
.picker-title { display: block; margin-bottom: var(--s-4); }
.picker-state { padding: var(--s-9) 0; text-align: center; }
.picker-list { height: calc(var(--s-13) * 4); }
.picker-item {
  display: flex;
  flex-direction: column;
  padding: var(--s-4) 0;
  border-bottom: 1rpx solid var(--c-border);
}
.picker-foot { display: flex; justify-content: space-between; margin-top: var(--s-5); }
</style>
