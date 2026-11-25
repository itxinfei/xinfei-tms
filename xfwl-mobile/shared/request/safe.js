/**
 * 请求层「三值返回」与可复用工具（唯一真源）
 *
 * 背景：三端现有 request 签名分裂（courier/driver 为位置参数、customer 为对象参数），
 * 且业务失败一律 reject + 全局 toast。本模块不改签名、不重复弹窗，只在调用侧把
 * Promise 归一化成 success / fail / empty 三值，让页面不必把失败伪装成 0 / null / '--'。
 *
 * 说明：现有 request 已在网络/业务失败时统一提示，requestSafe 只负责状态与可诊断文案，
 * 不再二次 showToast；页面按 result.message 渲染内联错误态或重试入口。
 */

export const RequestState = Object.freeze({
  SUCCESS: 'success',
  FAIL: 'fail',
  EMPTY: 'empty',
})

/**
 * 把任意请求 Promise 转成三值结果，永不 reject。
 * @param {Promise|Function} task 请求 Promise 或返回 Promise 的函数
 * @param {Object} opts { empty: (data) => boolean, fallback: string }
 * @returns {Promise<{state:string, data:any, message:string}>}
 */
export async function requestSafe(task, opts = {}) {
  const { empty = () => false, fallback = '数据暂不可用，请重试' } = opts
  try {
    const data = await (typeof task === 'function' ? task() : task)
    if (empty(data)) return { state: RequestState.EMPTY, data, message: '' }
    return { state: RequestState.SUCCESS, data, message: '' }
  } catch (e) {
    const raw = (e && (e.msg || e.message)) || ''
    return { state: RequestState.FAIL, data: null, message: raw || fallback }
  }
}

/** 300ms 防抖；连点 / 连续输入只发最后一次 */
export function debounce(fn, wait = 300) {
  let t = null
  return (...args) => {
    if (t) clearTimeout(t)
    t = setTimeout(() => {
      t = null
      fn(...args)
    }, wait)
  }
}

/** in-flight 去重：同一 key 的请求未结束前复用同一个 Promise */
export function createInFlight() {
  const pending = new Map()
  return function runInFlight(key, task) {
    if (pending.has(key)) return pending.get(key)
    const p = Promise.resolve()
      .then(task)
      .finally(() => pending.delete(key))
    pending.set(key, p)
    return p
  }
}

/**
 * 分页参数归一化：后端契约本来就不同，前端统一用 page/pageSize 持有值，
 * 由 style 决定请求键拼写，避免在各页手写 pagesize / pageSize 造成混乱。
 * @param {'lower'|'camel'} style lower=pagesize（多数接口），camel=pageSize（地址簿）
 */
export function pageParams(page, pageSize, style = 'lower') {
  return { page, [style === 'camel' ? 'pageSize' : 'pagesize']: pageSize }
}

/** 从分页响应里取列表与总数，兼容裸数组 / {items,counts} / {list,total} */
export function pagePayload(res) {
  if (Array.isArray(res)) return { items: res, counts: res.length }
  if (!res || typeof res !== 'object') return { items: [], counts: 0 }
  return {
    items: res.items || res.list || res.records || [],
    counts: res.counts ?? res.total ?? 0,
  }
}
