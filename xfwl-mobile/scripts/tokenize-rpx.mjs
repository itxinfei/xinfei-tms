#!/usr/bin/env node
/**
 * tokenize-rpx.mjs — 存量 rpx 字面量收敛到设计令牌唯一真源（可重复运行 / 幂等）
 *
 * 对应《80-移动三端页面设计优化整改TODO》P1/P2 的机械清理，配合 R7 卡口：
 *   - padding / margin / gap / top / right / shadow / filter 等「间距」→ 就近归入 4pt 标尺 --s-*
 *   - font-size → 就近归入字号阶梯 --f-*
 *   - border-radius → 就近归入圆角阶梯 --r-*
 *   - width / height / min-* / max-* / 组件 height 属性 / >96rpx 的大跨度留白
 *     → 交给 --sz-* 兼容档原值托管（强套 4pt 网格会把缩略图、画布、logo 拉变形）
 *   - 1rpx / 2rpx hairline 描边保留字面量（R7 白名单）
 *
 * 令牌定义只在 shared/styles/tokens.scss，页面不得再出现 rpx 字面量。
 * 用法：node scripts/tokenize-rpx.mjs [--dry]
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const DRY = process.argv.includes('--dry')
const ENDS = ['courier', 'customer', 'driver']
// 用户自己正在改的文件按项目约定跳过，不纳入任何批量迁移
const SKIP = new Set(['driver/src/pages/driver/task/track.vue'])

const S_SCALE = [[4, '--s-1'], [8, '--s-2'], [12, '--s-3'], [16, '--s-4'], [20, '--s-5'],
  [24, '--s-6'], [28, '--s-7'], [32, '--s-8'], [40, '--s-9'], [48, '--s-10'],
  [56, '--s-11'], [64, '--s-12'], [96, '--s-13']]
const F_SCALE = [[24, '--f-tip'], [26, '--f-aux'], [28, '--f-body'], [32, '--f-sub'],
  [40, '--f-title'], [48, '--f-hero'], [56, '--f-display']]
const R_SCALE = [[8, '--r-sm'], [16, '--r-md'], [24, '--r-lg'], [999, '--r-round']]
const S_EXACT = Object.fromEntries(S_SCALE.map(([n, t]) => [n, t]))
const DIMENSION = new Set(['width', 'height', 'min-width', 'max-width', 'min-height',
  'max-height', 'line-height', 'flex-basis', 'attr'])

/** 就近归入标尺；并列时取较小档，避免把间距撑大造成溢出 */
function nearest(scale, n) {
  let best = scale[0]
  for (const item of scale) {
    const d = Math.abs(item[0] - n)
    const bd = Math.abs(best[0] - n)
    if (d < bd || (d === bd && item[0] < best[0])) best = item
  }
  return best[1]
}

function tokenFor(prop, n) {
  if (prop === 'font-size') return nearest(F_SCALE, n)
  if (prop === 'border-radius') return nearest(R_SCALE, n)
  if (DIMENSION.has(prop)) return S_EXACT[n] || `--sz-${n}`
  if (n <= 96) return nearest(S_SCALE, n)
  return `--sz-${n}`
}

/** 从匹配位置往前找最近的 CSS 属性名；模板里 `height="88rpx"` 没有冒号，归为 attr */
function propAt(line, offset) {
  const before = line.slice(0, offset)
  const all = [...before.matchAll(/([A-Za-z][\w-]*)\s*:/g)]
  return all.length ? all[all.length - 1][1].toLowerCase() : 'attr'
}

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const abs = path.join(dir, ent.name)
    if (ent.isDirectory()) walk(abs, out)
    else if (ent.name.endsWith('.vue')) out.push(abs)
  }
  return out
}

let files = 0
let hits = 0
for (const end of ENDS) {
  for (const abs of walk(path.join(ROOT, end, 'src', 'pages'))) {
    const rel = path.relative(ROOT, abs).replace(/\\/g, '/')
    if (SKIP.has(rel)) continue
    const raw = fs.readFileSync(abs, 'utf8')
    const lines = raw.split('\n')
    let inScript = false
    let inHtmlComment = false
    let changed = false
    const next = lines.map((line) => {
      if (/<script\b/.test(line)) inScript = true
      if (inScript) {
        if (/<\/script>/.test(line)) inScript = false
        return line
      }
      const trimmed = line.trim()
      const wasComment = inHtmlComment
      if (inHtmlComment && /-->/.test(line)) inHtmlComment = false
      if (!inHtmlComment && /<!--/.test(line) && !/-->/.test(line)) inHtmlComment = true
      // 注释整行跳过：R7 已不把注释文字算违规，也不必改注释里的说明文字
      if (wasComment || inHtmlComment || /^(\/\/|\/\*|\*|<!--)/.test(trimmed)) return line
      const out = line.replace(/(-?)(\d+)rpx/g, (m, sign, digits, offset, whole) => {
        const n = Number(digits)
        if (n === 1 || n === 2) return m
        const token = tokenFor(propAt(whole, offset), n)
        hits++
        changed = true
        return sign ? `calc(var(${token}) * -1)` : `var(${token})`
      })
      return out
    })
    if (!changed) continue
    files++
    if (!DRY) fs.writeFileSync(abs, next.join('\n'), 'utf8')
  }
}
console.log(`${DRY ? '[DRY] ' : ''}tokenize-rpx: ${hits} 处 rpx → 令牌，${files} 个页面${SKIP.size ? `（跳过 ${[...SKIP].join(', ')}）` : ''}`)
