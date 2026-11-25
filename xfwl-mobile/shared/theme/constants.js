/**
 * 原生组件专用色值常量（唯一真源）
 *
 * canvas 2D 的 fillStyle / strokeStyle、map 组件的 polyline / marker.label
 * 必须接收字面量色值字符串，读不到 CSS 变量；因此这些值不能写在页面里，
 * 统一由本模块导出。页面 R5（裸 hex）规则据此归零，值仍在真源。
 *
 * 与 shared/styles/tokens.scss 的 $c-* 一一对应，两处改色须同步：
 *   CANVAS_INK     -> $c-text-1   #1D2129
 *   CANVAS_SURFACE -> $c-surface  #FFFFFF
 *   MAP_SUCCESS    -> $c-success  #00B42A
 *   MAP_PRIMARY    -> $c-primary  #CC0029（承载白字 5.83:1，不用 #FF0033）
 *   MAP_LINE       -> $c-primary  #CC0029
 */

export const CANVAS_INK = '#1D2129'
export const CANVAS_SURFACE = '#FFFFFF'
export const MAP_SUCCESS = '#00B42A'
export const MAP_PRIMARY = '#CC0029'
export const MAP_LINE = '#CC0029'

/** uview-plus 属性用的 CSS 变量字符串；小程序端若不生效，改由主题变量注入 */
export const NAV_SURFACE = 'var(--c-surface)'
export const NAV_INK = 'var(--c-text-1)'
