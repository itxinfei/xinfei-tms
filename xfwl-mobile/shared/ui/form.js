/**
 * 表单校验辅助（唯一真源）
 * uview-plus 的 validate() 在不同端返回的 reject 结构不完全一致，
 * 这里统一取首个错误字段并把焦点滚到可视区。
 */

/** 从 validate() 的 rejection 里取 { field, message } */
export function firstValidateError(err) {
  const first = Array.isArray(err) ? err[0] : null
  if (!first) return { field: '', message: '请检查表单填写' }
  return {
    field: first.field || first.prop || '',
    message: first.message || '请检查表单填写',
  }
}

/** 把指定字段滚到可视区（页面需给对应容器加 id="field-<prop>"） */
export function focusField(prop, offset = 40) {
  if (!prop) return
  const q = uni.createSelectorQuery()
  q.select('#field-' + prop).boundingClientRect()
  q.selectViewport().scrollOffset()
  q.exec((res) => {
    const rect = res && res[0]
    const view = res && res[1]
    if (rect && view) {
      uni.pageScrollTo({ scrollTop: Math.max(0, view.scrollTop + rect.top - offset), duration: 200 })
    }
  })
}
