<template>
  <view class="page">
    <view class="card info-card">
      <text class="info-no">运单号：{{ orderNumber || tranOrderId }}</text>
    </view>

    <!-- 拍照回单（选填，最多3张；上传前烧录时间+GPS水印） -->
    <view class="card">
      <text class="block-title">现场照片（选填，最多3张）</text>
      <view class="photo-list">
        <view class="photo-item" v-for="(p, idx) in photos" :key="idx">
          <image :src="p.url" class="photo-img" mode="aspectFill" aria-label="预览照片" @click="previewPhoto(idx)"></image>
          <u-icon name="close-circle" class="photo-del" aria-label="删除照片" @click="removePhoto(idx)"></u-icon>
        </view>
        <view class="photo-add" v-if="photos.length < 3" @click="onChoosePhoto">
          <u-icon name="camera" size="44" color="var(--c-text-3)"></u-icon>
          <text class="photo-add-text">拍照</text>
        </view>
      </view>
      <text class="wm-hint">照片将自动叠加拍摄时间与定位水印{{ gpsText ? '：' + gpsText : '（定位中/失败时仅叠加时间）' }}</text>
    </view>

    <!-- 手写签名（签收必签；导出图片烧录水印） -->
    <view class="card">
      <text class="block-title">客户签名{{ signatureId ? '（已保存）' : '' }}</text>
      <view class="sign-wrap">
        <canvas
          type="2d"
          id="signCanvas"
          class="sign-canvas"
          disable-scroll
          @touchstart="onTouchStart"
          @touchmove="onTouchMove"
          @touchend="onTouchEnd"
        ></canvas>
      </view>
      <view class="sign-actions">
        <u-button text="清除" shape="circle" @click="clearSign"></u-button>
        <u-button type="primary" :text="signatureId ? '重新保存签名' : '保存签名'" shape="circle" :loading="savingSign" @click="saveSign"></u-button>
      </view>
    </view>

    <!-- 备注 / 拒收原因（签收选填，拒收必填） -->
    <u-form ref="formRef" :model="formModel" :rules="formRules" errorType="none">
      <FormField prop="remark" :borderBottom="false">
        <view class="card" id="field-remark">
          <text class="block-title">备注</text>
          <u--textarea v-model="remark" placeholder="签收备注选填；如拒收，请在此填写拒收原因（必填）" :maxlength="200"></u--textarea>
        </view>
      </FormField>
    </u-form>

    <!-- 底部操作：拒收 / 签收 -->
    <ActionBar>
      <u-button type="error" text="拒收" shape="circle" :loading="submitting" @click="onDeliver(DELIVERED_REJECT)"></u-button>
      <u-button type="primary" text="确认签收" shape="circle" :loading="submitting" @click="onDeliver(DELIVERED_SIGN)"></u-button>
    </ActionBar>

    <!-- 离屏合成画布（烧录用，移出可视区；不能 display:none，否则位图尺寸为0） -->
    <canvas type="2d" id="wmCanvas" class="offscreen-canvas"></canvas>
  </view>
</template>

<script setup lang="ts">
// 电子签收回单：现场照片(选填≤3)+手写签名(签收必填)+备注(拒收必填)；
// 照片与签名图上传前统一经离屏 canvas 烧录「时间+GPS」水印，提交 URL 凭证（B2）
import { ref, computed } from 'vue'
import ActionBar from '@/components/ActionBar.vue'
import {
  attachmentUpload,
  delivered,
  DELIVERED_SIGN,
  DELIVERED_REJECT,
} from '@/common/api/courier'
import { wgs84ToGcj02 } from '@/common/utils/coord'
import { CANVAS_INK, CANVAS_SURFACE } from '@/common/theme.js'
import FormField from '@/components/FormField.vue'
import { firstValidateError, focusField } from '@/common/ui-form.js'
import { onLoad, onReady, onUnload } from '@dcloudio/uni-app'

const MAX_PHOTOS = 3
// 烧录图片最大边（像素），防止手机原图超出 H5 canvas 尺寸/面积上限导出失败
const MAX_EDGE = 2048

const tranOrderId = ref('')
const orderNumber = ref('')
// 照片上传后 {id=url=file-server匿名viewURL}
const photos = ref<{ id: string; url: string }[]>([])
// 签名图上传后的 URL（空=未保存签名）
const signatureId = ref('')
const remark = ref('')
const formRef = ref<any>(null)
const formModel = computed(() => ({ remark: remark.value }))
// 签收时备注选填、拒收时必填：条件规则读 rejectMode，不把业务状态散进手写 if
const rejectMode = ref(false)
const formRules = {
  remark: [{
    validator: (_r: any, value: any, callback: any) => {
      if (rejectMode.value && !String(value || '').trim()) callback(new Error('请填写拒收原因'))
      else callback()
    },
    trigger: 'blur',
  }],
}
const gpsText = ref('')
const submitting = ref(false)
const savingSign = ref(false)
const photoBusy = ref(false)

// GCJ-02 坐标（与项目坐标口径一致）；定位失败不阻断，水印仅缺坐标行
let lng: number | null = null
let lat: number | null = null

// ===== 签名画布 =====
let signCtx: any = null
let signCanvas: any = null
let signCssW = 0
let signCssH = 0
let drawing = false
let hasSign = false // 当前画布上有无笔迹（未保存的也算）
let lastPoint = { x: 0, y: 0 }

// ===== 离屏水印合成画布 =====
let wmCtx: any = null
let wmCanvas: any = null

onLoad((options: any) => {
  tranOrderId.value = options?.tranOrderId || ''
  orderNumber.value = options?.orderNumber || ''
  getGps()
})

onReady(() => {
  initSignCanvas()
  initWmCanvas()
})

onUnload(() => {
  signCtx = null
  signCanvas = null
  wmCtx = null
  wmCanvas = null
})

function getGps() {
  uni.getLocation({
    type: 'wgs84',
    success: (res) => {
      const gcj = wgs84ToGcj02(res.longitude, res.latitude)
      lng = gcj.lng
      lat = gcj.lat
      gpsText.value = `经度 ${lng.toFixed(5)} 纬度 ${lat.toFixed(5)}`
    },
    fail: () => {
      lng = null
      lat = null
      gpsText.value = ''
    },
  })
}

function nowText(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// 水印内容：第一行品牌+时间，定位成功时第二行打 GCJ02 坐标；
// 定位失败按拍板口径降级为只烧录时间一行（不把"定位失败"状态写进证据图，不阻断签收）
function watermarkLines(): string[] {
  const head = `心飞物流 ${nowText()}`
  if (lng === null || lat === null) return [head]
  return [head, `经度 ${lng.toFixed(5)} 纬度 ${lat.toFixed(5)}`]
}

function initSignCanvas() {
  uni.createSelectorQuery()
    .select('#signCanvas')
    .fields({ node: true, size: true })
    .exec((res: any) => {
      if (!res || !res[0]) return
      const dpr = uni.getSystemInfoSync().pixelRatio || 2
      signCssW = res[0].width
      signCssH = res[0].height
      signCanvas = res[0].node
      signCanvas.width = signCssW * dpr
      signCanvas.height = signCssH * dpr
      signCtx = signCanvas.getContext('2d')
      signCtx.scale(dpr, dpr)
      signCtx.strokeStyle = CANVAS_INK
      signCtx.lineWidth = 4
      signCtx.lineCap = 'round'
      signCtx.lineJoin = 'round'
    })
}

function initWmCanvas() {
  uni.createSelectorQuery()
    .select('#wmCanvas')
    .fields({ node: true })
    .exec((res: any) => {
      if (!res || !res[0]) return
      wmCanvas = res[0].node
      wmCtx = wmCanvas.getContext('2d')
    })
}

// ===== 拍照：烧录水印 → 上传 =====
function onChoosePhoto() {
  if (photoBusy.value || savingSign.value) return
  uni.chooseImage({
    count: MAX_PHOTOS - photos.value.length,
    sourceType: ['camera', 'album'],
    success: async (res) => {
      photoBusy.value = true
      uni.showLoading({ title: '处理中...', mask: true })
      let fail = 0
      try {
        for (const filePath of res.tempFilePaths) {
          if (photos.value.length >= MAX_PHOTOS) break
          try {
            const stamped = await composePhoto(filePath)
            const att = await attachmentUpload(stamped)
            if (att?.id) photos.value.push({ id: att.id, url: att.url })
          } catch (e) {
            uni.showToast({ title: "照片处理失败，可补拍后再签收", icon: "none" })
            // 单张失败不影响其余，但计数并提示，避免快递员误以为照片已带上
            fail++
          }
        }
        if (fail > 0) {
          uni.showToast({ title: `${fail} 张照片处理失败，可补拍后再签收`, icon: 'none' })
        }
      } finally {
        photoBusy.value = false
        uni.hideLoading()
      }
    },
  })
}

function removePhoto(idx: number) {
  photos.value.splice(idx, 1)
}

function previewPhoto(idx: number) {
  uni.previewImage({
    current: idx,
    urls: photos.value.map((p) => p.url),
  })
}

// 跨端加载图片到 canvas 可绘制对象。
// H5 端 SelectorQuery fields({node:true}) 取到的是原生 HTMLCanvasElement，uni-h5 运行时
// 既无 canvas.createImage()，uni.canvasToTempFilePath 也只认 canvas-id（不认 node），
// 故 H5 走浏览器原生 Image/toDataURL；App/小程序保留 node API。
function loadCanvasImage(src: string): Promise<any> {
  // #ifdef H5
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('照片读取失败'))
    img.src = src
  })
  // #endif
  // #ifndef H5
  return new Promise((resolve, reject) => {
    const img = wmCanvas.createImage()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('照片读取失败'))
    img.src = src
  })
  // #endif
}

// 跨端导出 JPEG：H5 直接 toDataURL（uni.uploadFile 对 data:image/...;base64 有专门转换分支）；
// App/小程序走 node 版 canvasToTempFilePath 出临时文件
function exportJpeg(canvas: any): Promise<string> {
  // #ifdef H5
  return Promise.resolve(canvas.toDataURL('image/jpeg', 0.92))
  // #endif
  // #ifndef H5
  return new Promise((resolve, reject) => {
    uni.canvasToTempFilePath({
      canvas,
      fileType: 'jpg',
      quality: 0.92,
      success: (r) => resolve(r.tempFilePath),
      fail: () => reject(new Error('图片导出失败')),
    })
  })
  // #endif
}

// 照片 → 等比缩放到 MAX_EDGE 内 → 底部水印条 → 导出 JPEG（H5=dataURL / 其它端=临时文件，均可直接上传）
async function composePhoto(srcPath: string): Promise<string> {
  const img = await loadCanvasImage(srcPath)
  let w = img.width
  let h = img.height
  const scale = Math.min(1, MAX_EDGE / Math.max(w, h))
  w = Math.round(w * scale)
  h = Math.round(h * scale)
  wmCanvas.width = w
  wmCanvas.height = h
  wmCtx.drawImage(img, 0, 0, w, h)
  drawWatermark(wmCtx, w, h)
  return exportJpeg(wmCanvas)
}

// 统一水印绘制：底部半透明黑条 + 白字（一或两行；字号/条高随图宽等比）
function drawWatermark(ctx: any, w: number, h: number) {
  const fontSize = Math.max(12, Math.round(w * 0.024))
  const lineH = Math.round(fontSize * 1.4)
  const padX = Math.round(fontSize * 0.5)
  const lines = watermarkLines()
  const barH = lineH * lines.length + Math.round(fontSize * 0.5)
  const top = h - barH
  ctx.fillStyle = 'rgba(0,0,0,0.45)'
  ctx.fillRect(0, top, w, barH)
  ctx.fillStyle = CANVAS_SURFACE
  ctx.font = `${fontSize}px sans-serif`
  ctx.textBaseline = 'top'
  lines.forEach((line, i) => {
    ctx.fillText(line, padX, top + Math.round(fontSize * 0.25) + i * lineH)
  })
}

// ===== 手写签名 =====
function getPos(e: any) {
  const t = e.touches[0] || e.changedTouches[0]
  return { x: t.x, y: t.y }
}
function onTouchStart(e: any) {
  if (!signCtx) return
  drawing = true
  lastPoint = getPos(e)
}
function onTouchMove(e: any) {
  if (!signCtx || !drawing) return
  // 不让笔迹画出画布
  const p = getPos(e)
  signCtx.beginPath()
  signCtx.moveTo(lastPoint.x, lastPoint.y)
  signCtx.lineTo(p.x, p.y)
  signCtx.stroke()
  lastPoint = p
  hasSign = true
}
function onTouchEnd() {
  drawing = false
}
function clearSign() {
  if (!signCtx) return
  signCtx.clearRect(0, 0, signCssW, signCssH)
  hasSign = false
}

// 导出签名：白底位图 + 笔迹 + 水印条 → 上传；重签覆盖旧签名
async function saveSign() {
  if (!wmCtx || !wmCanvas) {
    uni.showToast({ title: '画布未就绪，请稍后重试', icon: 'none' })
    return
  }
  if (!hasSign) {
    uni.showToast({ title: '请先手写签名', icon: 'none' })
    return
  }
  if (savingSign.value) return
  // 与照片处理共用同一离屏 canvas，双保险互斥（showLoading mask 之外再拦一次）
  if (photoBusy.value) {
    uni.showToast({ title: '照片处理中，请稍候', icon: 'none' })
    return
  }
  savingSign.value = true
  try {
    const w = signCanvas.width
    const h = signCanvas.height
    wmCanvas.width = w
    wmCanvas.height = h
    // 白底（透明 PNG 上盖水印观感差，签名图统一白底 JPEG）
    wmCtx.fillStyle = CANVAS_SURFACE
    wmCtx.fillRect(0, 0, w, h)
    wmCtx.drawImage(signCanvas, 0, 0)
    // 签名位图本身按 dpr 放大，水印绘制坐标用位图尺寸（字号随位图宽等比）
    drawWatermark(wmCtx, w, h)
    const tempPath = await exportJpeg(wmCanvas)
    const att = await attachmentUpload(tempPath)
    if (!att?.id) throw new Error('签名上传失败')
    signatureId.value = att.id
    uni.showToast({ title: '签名已保存', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '签名保存失败', icon: 'none' })
  } finally {
    savingSign.value = false
  }
}

// ===== 签收 / 拒收 =====
async function onDeliver(status: string) {
  if (!tranOrderId.value) {
    uni.showToast({ title: '缺少运单标识', icon: 'none' })
    return
  }
  if (photoBusy.value || savingSign.value) {
    uni.showToast({ title: '图片仍在处理，请稍候', icon: 'none' })
    return
  }
  if (status === DELIVERED_SIGN && !signatureId.value) {
    uni.showToast({ title: '请手写签名并保存', icon: 'none' })
    return
  }
  rejectMode.value = status === DELIVERED_REJECT
  try {
    await formRef.value?.validate()
  } catch (err: any) {
    const first = firstValidateError(err)
    uni.showToast({ title: first.message, icon: 'none' })
    focusField(first.field)
    return
  }
  const remarkTrim = remark.value.trim()
  if (submitting.value) return
  submitting.value = true
  try {
    await delivered(tranOrderId.value, status, {
      signPictures: photos.value.map((p) => p.id).filter(Boolean),
      signSignature: signatureId.value || undefined,
      signRemark: remarkTrim || undefined,
    })
    uni.showToast({ title: status === DELIVERED_SIGN ? '签收成功' : '已拒收', icon: 'success' })
    // 成功后保持按钮禁用（不复位 submitting），防返回延迟窗口内重复妥投/重复发 MQ
    setTimeout(goBackAfterDeliver, 600)
  } catch (e) {
    uni.showToast({ title: "签收操作失败，请重试", icon: "none" })
    // request 封装已统一提示；仅失败时放行重试
    submitting.value = false
  }
}

// 正常返回任务详情；H5 刷新/分享链接直入导致页面栈为空时，重定向到任务列表
function goBackAfterDeliver() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/courier/task/list' })
  }
}
</script>

<style lang="scss" scoped>
.info-card {
  padding: var(--s-4);
}
.info-no {
  font-size: var(--f-body);
  color: var(--c-text-1);
  font-weight: bold;
}
.block-title {
  display: block;
  font-size: var(--f-sub);
  color: var(--c-text-1);
  font-weight: bold;
  margin-bottom: var(--s-3);
}
.wm-hint {
  display: block;
  margin-top: var(--s-2);
  font-size: var(--f-tip);
  color: var(--c-text-3);
}
.photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--s-3);
}
.photo-item {
  position: relative;
  width: var(--sz-180);
  height: var(--sz-180);
}
.photo-img {
  width: var(--sz-180);
  height: var(--sz-180);
  border-radius: var(--r-sm);
}
.photo-del {
  position: absolute;
  top: calc(var(--s-2) * -1);
  right: calc(var(--s-2) * -1);
  background: var(--c-surface);
  border-radius: var(--r-round);
}
.photo-add {
  width: var(--sz-180);
  height: var(--sz-180);
  border: 2rpx dashed var(--c-border);
  border-radius: var(--r-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.photo-add-text {
  font-size: var(--f-tip);
  color: var(--c-text-3);
  margin-top: var(--s-1);
}
.sign-wrap {
  position: relative;
  width: 100%;
  height: var(--sz-360);
  border: 2rpx dashed var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
}
.sign-canvas {
  width: 100%;
  height: var(--sz-360);
  /* 阻止画布上手指滑动带动页面滚动，保证竖笔连续 */
  touch-action: none;
}
.sign-actions {
  display: flex;
  gap: var(--s-3);
  margin-top: var(--s-3);
}
.action-bar {
  display: flex;
  gap: var(--s-3);
}
.offscreen-canvas {
  position: fixed;
  left: -9999px;
  top: 0;
  width: 10px;
  height: 10px;
}
</style>
