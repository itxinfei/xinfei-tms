<template>
  <view class="page">
    <!-- 三态统一：加载中 / 失败（带诊断文案与重试）/ 无车 / 正常 -->
    <StatusBlock
      :state="carState"
      :message="errorMsg"
      :empty-text="noBind ? '当前无在途任务，暂无关联车辆' : '未获取到车辆信息'"
      :retryable="carState === 'fail'"
      @retry="loadInfo"
    >
      <view class="card">
        <view class="card-title">车辆信息</view>
        <view class="row">
          <text class="label">车牌号</text>
          <!-- 修改点：车牌脱敏展示 -->
          <text class="value">{{ maskPlate(car.licensePlate) }}</text>
        </view>
        <view class="row">
          <text class="label">品牌</text>
          <text class="value">{{ car.brand || '—' }}</text>
        </view>
        <view class="row">
          <text class="label">车型</text>
          <!-- 后端 CarInfoDTO 字段名为 carModel（非 model） -->
          <text class="value">{{ car.carModel || '—' }}</text>
        </view>
        <view class="row">
          <text class="label">载重</text>
          <!-- 后端 load 为字符串，空串时展示 —，避免出现孤立的 "kg" -->
          <text class="value">{{ car.load ? car.load + ' kg' : '—' }}</text>
        </view>
        <view class="row" v-if="hasMonitor">
          <text class="label">监控状态</text>
          <!-- 修改点：在线/离线/故障，离线故障明显告警 -->
          <u-tag
            v-if="monitorView.type !== 'offline'"
            :text="monitorView.text"
            :type="monitorView.type"
            size="mini"
          />
          <u-tag v-else :text="monitorView.text" type="error" size="mini" />
        </view>
      </view>

      <!-- 修改点：离线或故障时明显告警并提示禁任务（后端补监控状态字段前 hasMonitor 恒 false，不误报） -->
      <u-alert
        v-if="hasMonitor && (monitorView.type === 'offline' || monitorView.type === 'fault')"
        type="error"
        :description="monitorView.type === 'fault' ? '车辆故障，禁止执行运输任务' : '监控离线，禁止执行运输任务'"
        showIcon
      ></u-alert>
    </StatusBlock>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { carInfo } from '@/common/api/driver'
import { onLoad } from '@dcloudio/uni-app'
import { requestSafe, RequestState } from '@/common/request/safe'
import StatusBlock from '@/components/StatusBlock.vue'

const id = ref('')
const car = ref<any>(null)
const loading = ref(false)
const error = ref(false)
const errorMsg = ref('')
// 入口未传车辆 id：司机当前无在途任务（车辆随运输任务关联，非常驻绑定）
const noBind = ref(false)

// 车牌脱敏：粤B****9
function maskPlate(plate?: string): string {
  if (!plate) return ''
  const s = String(plate)
  if (s.length <= 4) return s
  return s.slice(0, 2) + '****' + s.slice(-1)
}

// 监控状态原始值：CarInfoDTO 当前未返回该字段，在线/离线以后由车辆状态数据源（见《76》B8）补齐
const monitorRaw = computed(() => car.value?.monitorStatus ?? car.value?.status)
// 无状态字段时整行与禁任务告警均不渲染，避免对所有车辆恒显“监控离线”误报
const hasMonitor = computed(() => monitorRaw.value != null && monitorRaw.value !== '')

// 监控状态：在线 success / 离线 offline / 故障 fault
const monitorView = computed(() => {
  const v = monitorRaw.value
  // 容错：后端可能返回字符串或数字
  if (v === 'ONLINE' || v === 1 || v === 'online') return { text: '在线', type: 'success' }
  if (v === 'FAULT' || v === 3 || v === 'fault') return { text: '故障', type: 'fault' }
  return { text: '离线', type: 'offline' }
})

// 三态出口：加载中 / 失败（带诊断文案与重试）/ 无车（无重试意义）/ 正常
const carState = computed(() =>
  loading.value ? 'loading'
    : noBind.value ? 'empty'
      : error.value ? 'fail'
        : !car.value ? 'empty'
          : 'success'
)

async function loadInfo() {
  if (!id.value) return
  loading.value = true
  error.value = false
  errorMsg.value = ''
  // 三值返回：失败不再被吞成空对象，统一由 StatusBlock 展示诊断文案与重试
  const result = await requestSafe(() => carInfo(id.value), {
    fallback: '车辆信息加载失败，请检查网络后重试',
  })
  if (result.state === RequestState.FAIL) {
    error.value = true
    errorMsg.value = result.message
  } else {
    car.value = result.data || null
  }
  loading.value = false
}

onLoad((opt: any) => {
  // id 为车辆 id（由入口从 /user/profile.truckId 传入）；未传＝无在途任务关联车辆
  id.value = opt.id || opt.carId || ''
  if (!id.value) {
    noBind.value = true
    return
  }
  loadInfo()
})
</script>

<style lang="scss" scoped>
.card-title {
  font-size: var(--f-sub);
  color: var(--c-text-1);
  font-weight: bold;
  margin-bottom: var(--s-3);
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--s-2) 0;
}
.label {
  font-size: var(--f-body);
  color: var(--c-text-3);
}
.value {
  font-size: var(--f-body);
  color: var(--c-text-1);
}
</style>
