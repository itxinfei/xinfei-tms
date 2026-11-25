#!/usr/bin/env node
/**
 * guard-ui.mjs — 移动三端 UI 静态卡口（R1–R14、R17，共 15 条；R15/R16 归体验侧静态候选池未落地）
 *
 * 设计约束（对应《80-移动三端页面设计优化整改TODO》§3、§4）：
 *  1. 每条规则是纯函数：输入 = 单个文件的源码文本，输出 = 违规数组；不读外部状态、不靠推断。
 *  2. 规则常量全部显式列出（反馈标记、rpx 白名单、分页参数变体），可被自检脚本逐条证伪。
 *  3. 支持基线闸门：--baseline N 时，违规总数 <= N 视为通过（允许存量），新增违规即失败。
 *     这样流水线现在就能接入，不必等全仓清零。
 *
 * 用法：
 *   node scripts/guard-ui.mjs                    # 文本报告
 *   node scripts/guard-ui.mjs --json            # 机器可读
 *   node scripts/guard-ui.mjs --baseline 519    # CI 卡口（<=519 通过）
 *   node scripts/guard-ui.mjs --require-baseline # CI 用：缺基线快照直接失败，不静默放行
 *   node scripts/guard-ui.mjs --root <dir>      # 指定扫描根（注入测试用）
 *   node scripts/guard-ui.mjs --write-baseline  # 写 scripts/guard-baseline.json
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
export const ROOT = path.resolve(HERE, '..')
export const APPS = ['courier', 'customer', 'driver']
export const BASELINE_FILE = path.join(HERE, 'guard-baseline.json')

/* ------------------------------------------------------------------ *
 * 显式规则常量（自检脚本会对每一组做正例 / 反例断言）
 * ------------------------------------------------------------------ */

/** R1：catch 块内出现任一标记，视为「有用户可见反馈 / 有错误态」，否则为静默 catch */
export const FEEDBACK_TOKENS = [
  /uni\.showToast\b/,
  /uni\.showModal\b/,
  /\bshowToast\s*\(/,
  /\btoast\s*\(/,
  /\berror(?:Msg|Text|Message)?\.value\s*=/,
  /\bsetError\s*\(/,
  /\b(?:status|loadStatus)\.value\s*=\s*['"](?:error|fail|nomore)['"]/,
  /\bmsg\.value\s*=/,
  /\bmessage\.value\s*=/,
  /\buni\.\$emit\s*\(/,
]

/** R2：URL 含 login 视为「启动守卫 / 登出清栈」，属合理的一次性清栈，不计违规 */
export const REASONABLE_CLEAR_STACK = /login/i

/** R7：唯一允许的字面 rpx —— 1rpx/2rpx 仅用于 hairline 描边；其余必须走 var(--s-*) 等令牌 */
export const RPX_ALLOWLIST = new Set([1, 2])

/** R8：手写底部占位（正确实现应走 var(--fixed-bottom-h) 或组件 :placeholder） */
export const HAND_PLACEHOLDER = /(?:height|min-height|padding-bottom|padding-top|margin-bottom)\s*:\s*[^;]*(?:140|160)rpx/i

/**
 * R11：分页参数「本地命名」分裂。
 * 口径修正（2026-11-08）：后端接口契约本来就不同 —— 地址簿用 pageSize（驼峰），
 * 其余接口用 pagesize（全小写），前端统一用 const pageSize 持有值、按接口填键是正确写法。
 * 旧版把 `const pageSize` + `pagesize:` 判成「分裂」，实测 7 个文件全是这类假阳性。
 * 现在只拦两种真分裂：① 同一文件本地声明了 ≥2 种页容量/页码变量名；
 * ② 同一个请求对象里同时出现 ≥2 种页容量键。
 */
export const PAGE_SIZE_LOCAL_NAMES = new Set(['pagesize', 'size', 'limit', 'perpage', 'rows'])
export const PAGE_NUM_LOCAL_NAMES = new Set(['pagenum', 'pageno', 'current', 'page', 'pageindex'])
export const canonPageName = (s) => String(s).toLowerCase().replace(/[_-]/g, '')

/** R9：判定「提交动作」的函数名 */
export const SUBMIT_HANDLER = /submit|login|confirm|save|send|pay/i

/* ------------------------------------------------------------------ *
 * 纯函数工具
 * ------------------------------------------------------------------ */

export function lineOf(text, index) {
  let line = 1
  for (let i = 0; i < index && i < text.length; i++) if (text[i] === '\n') line++
  return line
}

/** 取 SFC 中某个 tag 的所有段落（含绝对区间，供「是否落在 <style> 内」判定） */
export function sectionRanges(raw, tag) {
  const re = new RegExp(`<${tag}\\b([^>]*)>([\\s\\S]*?)<\\/${tag}>`, 'gi')
  const out = []
  let m
  while ((m = re.exec(raw))) {
    out.push({
      tag,
      attrs: m[1] || '',
      body: m[2],
      start: m.index,
      end: m.index + m[0].length,
      bodyStart: m.index + m[0].indexOf(m[2]),
      line: lineOf(raw, m.index),
    })
  }
  return out
}

export function inAnyRange(index, ranges) {
  return ranges.some(r => index >= r.bodyStart && index < r.end)
}

/**
 * 把注释内容替换为等长空格（保留换行），使行号与字符下标不变。
 * 用于「裸 hex / !important」这类只应判定真实代码、不应把注释里的示例文字算违规的规则。
 * `//` 仅在行首或空白后生效，避免把 http:// 这类字符串误判为注释。
 */
export function blankComments(src) {
  return src
    // HTML 注释同样要剥离：Vue 模板里的「主按钮 ≥96rpx」等设计说明曾把 R7 误报成违规
    .replace(/<!--[\s\S]*?-->/g, m => m.replace(/[^\n]/g, ' '))
    .replace(/\/\*[\s\S]*?\*\//g, m => m.replace(/[^\n]/g, ' '))
    // 必须带 m：否则 ^ 只匹配全文开头，行首的 // 注释根本不会被剥离（2026-11-08 实测修正）
    .replace(/(^|[^\S\n])\/\/[^\n]*/gm, (m, p1) => p1 + m.slice(p1.length).replace(/[^\n]/g, ' '))
}

/** 花括号配平取块体。SFC 的 catch 块规模小，配平足以给出确定性结果 */
export function blockBody(raw, openIndex) {
  let depth = 0
  for (let i = openIndex; i < raw.length; i++) {
    const ch = raw[i]
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) return raw.slice(openIndex + 1, i)
    }
  }
  return raw.slice(openIndex + 1)
}

/** 构造规则上下文（纯函数） */
export function makeContext(raw, rel = '<fixture>') {
  const templates = sectionRanges(raw, 'template')
  const scripts = sectionRanges(raw, 'script')
  const styles = sectionRanges(raw, 'style')
  return {
    raw,
    rel,
    templates,
    scripts,
    styles,
    template: templates.map(s => s.body).join('\n'),
    script: scripts.map(s => s.body).join('\n'),
    style: styles.map(s => s.body).join('\n'),
  }
}

function v(ctx, rule, index, message, detail = '') {
  return { rule, file: ctx.rel, line: lineOf(ctx.raw, index), message, detail }
}

/* ------------------------------------------------------------------ *
 * R1–R14、R17 规则表：每条都是 (ctx) => violation[]（R15/R16 为体验侧静态候选，本轮不落地避免假阳性击穿闸门）
 * ------------------------------------------------------------------ */

export const RULES = [
  {
    id: 'R1',
    name: 'catch 块无可见反馈 / 无 error 态',
    test(ctx) {
      const out = []
      const re = /catch\s*(?:\([^)]*\))?\s*\{/g
      let m
      while ((m = re.exec(ctx.raw))) {
        const open = m.index + m[0].lastIndexOf('{')
        const body = blockBody(ctx.raw, open)
        if (!FEEDBACK_TOKENS.some(t => t.test(body))) {
          out.push(v(ctx, 'R1', m.index, '静默 catch：块内无任何可见反馈或错误态', body.trim().slice(0, 72)))
        }
      }
      // Promise 形式 .catch(() => {})：上面的正则匹配不到箭头函数，实测曾漏报 3 处。
      // 只覆盖「箭头函数 + 花括号块体」这一种最常见的静默写法。
      const pre = /\.catch\s*\(\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{/g
      while ((m = pre.exec(ctx.raw))) {
        const open = m.index + m[0].lastIndexOf('{')
        const body = blockBody(ctx.raw, open)
        if (!FEEDBACK_TOKENS.some(t => t.test(body))) {
          out.push(v(ctx, 'R1', m.index, '静默 Promise catch：.catch(() => {}) 吞掉错误且无可见反馈', body.trim().slice(0, 72)))
        }
      }
      return out
    },
  },
  {
    id: 'R2',
    name: 'uni.reLaunch 用于标签页互相切换（应走 switchTab）',
    test(ctx) {
      const out = []
      // 只拦「底部标签互相切换」：handler 名 goHome/goList/goProfile/goMailingList/goTab*。
      // 登录成功、下单成功、签收返回这类一次性清栈是合理用法，不在本规则范围（对齐《80》§4 原文）。
      const re = /\b(go(?:Home|List|Profile|MailingList|Tab\w*))\s*=\s*\([^)]*\)\s*=>\s*uni\.reLaunch\s*\(\s*\{[^}]*?url:\s*['"]([^'"]+)['"]/g
      let m
      while ((m = re.exec(ctx.raw))) {
        if (REASONABLE_CLEAR_STACK.test(m[2])) continue
        out.push(v(ctx, 'R2', m.index, `${m[1]} 用 reLaunch 切标签会清空页面栈，应改 uni.switchTab`, m[2]))
      }
      return out
    },
  },
  {
    id: 'R3',
    name: '普通属性内 Mustache（Vue 不插值）',
    test(ctx) {
      const out = []
      // 注意：}} 之后仍可能有文字再闭合引号（如 text="已选 {{ n }} 件"），不能要求 }} 紧贴引号
      const re = /(?<![:@\w-])([a-zA-Z][\w-]*)="[^"]*\{\{[\s\S]*?\}\}[^"]*"/g
      let m
      while ((m = re.exec(ctx.raw))) {
        if (m[1].startsWith('v-')) continue
        out.push(v(ctx, 'R3', m.index, `普通属性 ${m[1]} 内出现 {{ }}，会字面显示`, m[0].trim().slice(0, 80)))
      }
      return out
    },
  },
  {
    id: 'R4',
    name: '同屏底部固定层 > 1',
    test(ctx) {
      // 固定层是三处来源相加：全局 .action-bar（variables.scss 内 position:fixed;bottom:0）
      // + 组件 <u-tabbar :fixed="true"> + 页面自定义 fixed-bottom 规则（如 .fab-apply）
      const layers = []
      const actionBars = ctx.template.match(/class="[^"]*\baction-bar\b[^"]*"/g) || []
      actionBars.forEach(() => layers.push('view.action-bar'))
      // 2.4 之后固定层收敛为 <ActionBar> 组件；组件同样是「一层」，必须继续计入 R4
      const actionBarComps = ctx.template.match(/<ActionBar\b/g) || []
      actionBarComps.forEach(() => layers.push('ActionBar'))
      const tabbars = ctx.template.match(/<u-tabbar\b[^>]*?:fixed\s*=\s*"true"[^>]*>/gi) || []
      tabbars.forEach(() => layers.push('u-tabbar[fixed]'))
      for (const s of ctx.styles) {
        const re = /([^{}]+)\{([^{}]*)\}/g
        let m
        while ((m = re.exec(s.body))) {
          const selector = m[1].trim()
          // 页面内对 .action-bar / .bar-fixed 的重复定义与模板元素同一个，不重复计数
          if (/action-bar|bar-fixed/.test(selector)) continue
          // bottom 可以是 0，也可以是 calc(var(--window-bottom) + 24rpx)（悬浮 FAB），两者都是底部固定层
          if (/position\s*:\s*fixed/i.test(m[2]) && /\bbottom\s*:/i.test(m[2])) layers.push(selector)
        }
      }
      if (layers.length > 1) {
        return [v(ctx, 'R4', 0, `同屏底部固定层 ${layers.length} 个（应唯一）`, layers.join(' | '))]
      }
      return []
    },
  },
  {
    id: 'R5',
    name: '页面源码内裸 hex（模板属性 / style / script）',
    test(ctx) {
      if (!ctx.rel.endsWith('.vue')) return [] // scss/json 是令牌真源，由 2.1 收敛，不按页面口径计数
      const out = []
      const code = blankComments(ctx.raw)
      const re = /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b/g
      let m
      while ((m = re.exec(code))) {
        out.push(v(ctx, 'R5', m.index, '裸 hex 色值，应走 var(--c-*)', m[0]))
      }
      return out
    },
  },
  {
    id: 'R6',
    name: '!important',
    test(ctx) {
      const out = []
      const code = blankComments(ctx.raw)
      const re = /!important/g
      let m
      while ((m = re.exec(code))) out.push(v(ctx, 'R6', m.index, '写死 !important，按压态应进主题变量'))
      return out
    },
  },
  {
    id: 'R7',
    name: 'rpx 字面量不在白名单（应走设计令牌）',
    test(ctx) {
      if (!ctx.rel.endsWith('.vue')) return []
      const out = []
      // 只判真实代码：组件说明注释里出现「140rpx / 160rpx」这类示例文字不算违规
      const code = blankComments(ctx.raw)
      const re = /(\d+)rpx/g
      let m
      while ((m = re.exec(code))) {
        const n = Number(m[1])
        if (!RPX_ALLOWLIST.has(n)) out.push(v(ctx, 'R7', m.index, `${m[0]} 不在白名单 {1rpx,2rpx}，应走 var(--s-*)`, m[0]))
      }
      return out
    },
  },
  {
    id: 'R8',
    name: '手写固定层占位高度',
    test(ctx) {
      const out = []
      // ① 模板里的空 spacer：<view style="height: 140rpx"></view> —— 这才是真实的手写占位。
      //    样式里的正方形缩略图（照片 160rpx × 160rpx）不是占位，旧口径会误报，已修正。
      const inline = /<view\b[^>]*style\s*=\s*["'][^"']*(?:height|min-height)\s*:\s*(?:140|160)rpx[^"']*["'][^>]*>\s*<\/view>/gi
      let m
      while ((m = inline.exec(ctx.raw))) {
        out.push(v(ctx, 'R8', m.index, '手写占位，应走 var(--fixed-bottom-h)', m[0].trim()))
      }
      // ② 样式里用作底部让位的 padding / margin；不再把盒子的 height 一概当占位。
      for (const s of ctx.styles) {
        const re = /[^\n;{}]*(?:padding-bottom|padding-top|margin-bottom|margin-top)\s*:\s*[^;]*(?:140|160)rpx[^;]*/gi
        let n
        while ((n = re.exec(s.body))) {
          out.push(v(ctx, 'R8', s.bodyStart + n.index, '手写占位，应走 var(--fixed-bottom-h)', n[0].trim()))
        }
      }
      return out
    },
  },
  {
    id: 'R9',
    name: '有表单 + 提交动作但无 <u-form>',
    test(ctx) {
      const hasInput = /<u-input\b|<input\b|placeholder=/.test(ctx.template)
      const hasSubmit = /@click\s*=\s*"([^"]+)"/g
      let hasSubmitHandler = false
      let m
      while ((m = hasSubmit.exec(ctx.template))) if (SUBMIT_HANDLER.test(m[1])) hasSubmitHandler = true
      const hasForm = /<u-form\b|<form\b/.test(ctx.template)
      if (hasInput && hasSubmitHandler && !hasForm) {
        return [v(ctx, 'R9', 0, '页面含表单与提交动作但无 <u-form>，字段校验靠手写 if')]
      }
      return []
    },
  },
  {
    id: 'R10',
    name: '@change 直连请求且无防抖',
    test(ctx) {
      const handlers = []
      const re = /@change\s*=\s*"([^"(@]+)/g
      let m
      while ((m = re.exec(ctx.template))) handlers.push(m[1].trim())
      if (!handlers.length) return []
      if (/\bdebounce\b|防抖/.test(ctx.script)) return []
      if (!/\bawait\b/.test(ctx.script)) return []
      return handlers.map(h => v(ctx, 'R10', 0, `@change="${h}" 所在文件无防抖且存在 await 请求`, h))
    },
  },
  {
    id: 'R11',
    name: '分页参数本地命名混用',
    test(ctx) {
      const out = []
      const script = ctx.script
      // ① 本地声明的分页变量名（含 ref() 形式），同类出现 ≥2 种才算分裂
      const declRe = /(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*(?::[^=]+)?=/g
      const decls = []
      let m
      while ((m = declRe.exec(script))) decls.push(canonPageName(m[1]))
      const sizeNames = new Set(decls.filter(n => PAGE_SIZE_LOCAL_NAMES.has(n)))
      const numNames = new Set(decls.filter(n => PAGE_NUM_LOCAL_NAMES.has(n)))
      if (sizeNames.size > 1) out.push(v(ctx, 'R11', 0, `页容量本地变量命名分裂：${[...sizeNames].join(' / ')}`))
      if (numNames.size > 1) out.push(v(ctx, 'R11', 0, `页码本地变量命名分裂：${[...numNames].join(' / ')}`))
      // ② 同一个请求对象里同时出现 ≥2 种页容量键，才是真的混用
      const objRe = /\{[^{}]*\}/g
      let o
      while ((o = objRe.exec(script))) {
        // 请求键按原始拼写判重：pagesize 与 pageSize 同时出现就是混用，不能先归一化合并
        const rawKeys = [...o[0].matchAll(/([A-Za-z_$][\w$]*)\s*:/g)].map(x => x[1])
        const sizeKeys = new Set(rawKeys.filter(k => PAGE_SIZE_LOCAL_NAMES.has(canonPageName(k))))
        if (sizeKeys.size > 1) out.push(v(ctx, 'R11', o.index, `同一请求对象混用页容量键：${[...sizeKeys].join(' / ')}`))
      }
      return out
    },
  },
  {
    id: 'R12',
    name: '<style> 缺 scoped',
    test(ctx) {
      // App.vue 的全局样式本就必须全局生效，不按页面作用域规则判定
      if (ctx.rel.endsWith('App.vue')) return []
      return ctx.styles
        .filter(s => !/\bscoped\b/.test(s.attrs))
        .map(s => v(ctx, 'R12', s.start, '<style> 未加 scoped，样式会跨页泄漏', s.attrs.trim()))
    },
  },
  {
    id: 'R13',
    name: '编译期主题文件写死色值（应走共享真源 --u-*）',
    test(ctx) {
      // uview-plus 3.8.130 的 $u-* 均为 var(--u-*, fallback)，
      // 运行时值由 shared/styles/tokens.scss 的 page { --u-* } 提供。
      // 三端 uni.scss / 薄壳 variables.scss 只允许 @import 与 @include，不允许出现 hex。
      if (!/(?:^|\/)(?:uni|variables)\.scss$/.test(ctx.rel)) return []
      const out = []
      const code = blankComments(ctx.raw)
      const re = /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b/g
      let m
      while ((m = re.exec(code))) {
        out.push(v(ctx, 'R13', m.index, '编译期主题写死色值，色值唯一真源是 shared/styles/tokens.scss', m[0]))
      }
      return out
    },
  },
  {
    id: 'R14',
    name: '品牌亮色承载文字（对比度不足 AA）',
    test(ctx) {
      // 视觉负责人 Phase 1 定稿口径：#FF0033 / var(--c-primary-bright) 白底约 3.96:1，
      // 只能作 ≤1% 面积点缀，不得承载文字；正例为全局 .accent-dot，令牌定义行不判。
      // 工程精确化：只判「文字色声明」——模板 color= 属性、模板内联 style 的 color:、
      // <style> 规则里的 color:。脚本里的 color: '#FF0033' 是 map polyline / canvas
      // 绘制色，属原生组件常量，由 R5 + shared/theme/constants.js 约束，不在此判。
      if (/(?:^|\/)(?:tokens\.scss|constants\.js|uni\.scss|variables\.scss)$/.test(ctx.rel)) return []
      const code = blankComments(ctx.raw)
      const BRIGHT = /#FF0033\b|var\(\s*--c-primary-bright\s*\)/i
      const re = /(^|[;{\s])(?::)?color\s*(?::|=)\s*(?:"([^"]*)"|'([^']*)'|([^;}"']+))/g
      const out = []
      let m
      while ((m = re.exec(code))) {
        const value = m[2] ?? m[3] ?? m[4] ?? ''
        if (!BRIGHT.test(value)) continue
        const at = m.index + m[1].length
        if (inAnyRange(at, ctx.scripts)) continue // 原生组件绘制色
        const inStyle = ctx.styles.some(s => at >= s.bodyStart && at < s.end)
        if (inStyle) {
          // .accent-dot 是唯一真源里明确放行的点缀类（小面积、非正文承载）
          const before = code.slice(0, at)
          const open = before.lastIndexOf('{')
          const close = before.lastIndexOf('}')
          const selector = open > close ? before.slice(Math.max(close + 1, open - 120), open) : ''
          if (/\.accent-dot\b/.test(selector)) continue
        }
        out.push(v(ctx, 'R14', at, '品牌亮色 #FF0033 白底约 3.96:1，不得承载文字；点缀请用 .accent-dot', value.trim().slice(0, 48)))
      }
      return out
    },
  },
  {
    // 视觉负责人 Phase 1 定稿的静态候选（原误称 R15 → 改 R17，避免与体验侧 R15/R16 撞号）：
    // #FF0033 / var(--c-primary-bright) 落在 background / background-color，作为承载白字的底色
    // 约 3.96:1 不达标。与 R14 有意不一致：R14 的 .accent-dot 白名单为「包含」式，R17 为「恰为」式；
    // 模板内联 style 无选择器、不走白名单，命中即报。裸 hex 由 R5 管，不重复计。
    id: 'R17',
    name: '品牌亮色作背景承载白字（对比度不足 AA）',
    test(ctx) {
      if (/(?:^|\/)(?:tokens\.scss|constants\.js|uni\.scss|variables\.scss)$/.test(ctx.rel)) return []
      const code = blankComments(ctx.raw)
      const BRIGHT = /#FF0033\b|var\(\s*--c-primary-bright\s*\)/i
      const re = /(^|[;{\s"'])(?:background|background-color)\s*:\s*(?:"([^"]*)"|'([^']*)'|([^;}"']+))/g
      const out = []
      let m
      while ((m = re.exec(code))) {
        const value = m[2] ?? m[3] ?? m[4] ?? ''
        if (!BRIGHT.test(value)) continue
        const at = m.index + m[1].length
        if (inAnyRange(at, ctx.scripts)) continue // 原生组件绘制色
        const block = ctx.styles.find(s => at >= s.bodyStart && at < s.end)
        if (block) {
          // .accent-dot 白名单取「恰为」式（与 R14 的「包含」式有意不一致）；
          // 从样式块正文起点到本规则 { 之间的文本即选择器，避免把 <style> 标签算进去
          const selector = code.slice(block.bodyStart, code.lastIndexOf('{', at)).trim()
          if (selector === '.accent-dot') continue
        }
        out.push(v(ctx, 'R17', at, '品牌亮色 #FF0033 作背景承载白字约 3.96:1 不达标；底色用 $c-primary (#CC0029)', value.trim().slice(0, 48)))
      }
      return out
    },
  },
]

export function runRules(ctx) {
  const out = []
  for (const rule of RULES) out.push(...rule.test(ctx).map(x => ({ ...x, name: rule.name })))
  return out
}

/* ------------------------------------------------------------------ *
 * 文件收集与附加统计（不计入违规，用于回填基线表）
 * ------------------------------------------------------------------ */

export function collectFiles(root = ROOT) {
  const files = []
  for (const app of APPS) {
    const base = path.join(root, app, 'src')
    const walk = dir => {
      if (!fs.existsSync(dir)) return
      for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, ent.name)
        if (ent.isDirectory()) walk(p)
        else if (/\.(vue|scss|json)$/.test(ent.name)) {
          if (ent.name.endsWith('.vue') || ent.name === 'uni.scss' || ent.name === 'variables.scss' || ent.name === 'pages.json') {
            files.push(p)
          }
        }
      }
    }
    walk(base)
  }
  return files.sort()
}

/** 只把「规则适用」的文件送进规则：vue 全量，scss/json 仅参与统计 */
export function scan(root = ROOT) {
  const files = collectFiles(root)
  const violations = []
  const stats = {
    filesScanned: 0,
    vuePages: 0,
    catchTotal: 0,
    // 全文件口径（含 App.vue / uni.scss / variables.scss / pages.json）
    hex6All: 0,
    hex3All: 0,
    rpxAll: 0,
    // 页面口径（三端 src/pages/**/*.vue）——与《80》第二节基线表同一口径
    hex6Pages: 0,
    rpxPages: 0,
    reLaunchCustomerPages: 0,
    ariaHits: 0,
    styleWithoutScopedPages: 0,
    pagesWithoutStates: [],
  }
  for (const abs of files) {
    const rel = path.relative(root, abs).replace(/\\/g, '/')
    const raw = fs.readFileSync(abs, 'utf8')
    const isVue = abs.endsWith('.vue')
    const isPage = isVue && rel.includes('/pages/')
    stats.filesScanned++
    stats.catchTotal += (raw.match(/catch\s*(?:\([^)]*\))?\s*\{/g) || []).length
    stats.hex6All += (raw.match(/#[0-9a-fA-F]{6}\b/g) || []).length
    stats.hex3All += (raw.match(/#[0-9a-fA-F]{3}\b/g) || []).length
    stats.rpxAll += (raw.match(/\d+rpx/g) || []).length
    stats.ariaHits += (raw.match(/\baria-|\brole=|\btabindex=|:focus/g) || []).length
    if (isPage) {
      stats.vuePages++
      stats.hex6Pages += (raw.match(/#[0-9a-fA-F]{6}\b/g) || []).length
      stats.rpxPages += (raw.match(/\d+rpx/g) || []).length
      if (rel.startsWith('customer/')) {
        stats.reLaunchCustomerPages += (raw.match(/uni\.reLaunch\s*\(/g) || []).length
      }
    }
    if (!isVue) continue
    const ctx = makeContext(raw, rel)
    if (isPage && !/u-skeleton|u-empty|u-loadmore|StatusBlock|loading\.value|error\.value|loadStatus/.test(ctx.template + ctx.script)) {
      stats.pagesWithoutStates.push(rel)
    }
    if (!rel.endsWith('App.vue')) {
      stats.styleWithoutScopedPages += ctx.styles.filter(s => !/\bscoped\b/.test(s.attrs)).length
    }
    violations.push(...runRules(ctx))
  }
  return { violations, stats, byRule: summarize(violations) }
}

export function summarize(violations) {
  const byRule = Object.fromEntries(RULES.map(r => [r.id, 0]))
  for (const x of violations) byRule[x.rule] = (byRule[x.rule] || 0) + 1
  return byRule
}

export function formatReport(result) {
  const { violations, stats, byRule } = result
  const lines = []
  lines.push(`移动三端 UI 静态卡口 · ${RULES[0].id}–${RULES[RULES.length - 1].id}`)
  lines.push('='.repeat(64))
  for (const r of RULES) lines.push(`  ${r.id.padEnd(4)} ${String(byRule[r.id]).padStart(4)}  ${r.name}`)
  lines.push('-'.repeat(64))
  lines.push(`  违规合计 ${violations.length}  |  扫描 ${stats.filesScanned} 文件 / ${stats.vuePages} 页面`)
  lines.push('')
  lines.push('附加统计（回填基线表用，不计入违规）')
  lines.push('  [页面口径 · 三端 src/pages/**/*.vue]')
  lines.push(`    6 位 hex ${stats.hex6Pages}  |  rpx 字面量 ${stats.rpxPages}  |  catch 块 ${stats.catchTotal}`)
  lines.push(`    客户端 reLaunch 调用点 ${stats.reLaunchCustomerPages}  |  无 scoped 的 style 段 ${stats.styleWithoutScopedPages}`)
  lines.push(`    aria/role/tabindex/focus ${stats.ariaHits}  |  完全无状态要素页面 ${stats.pagesWithoutStates.length}`)
  lines.push('  [全文件口径 · 含 App.vue / uni.scss / variables.scss / pages.json]')
  lines.push(`    6 位 hex ${stats.hex6All}  |  短 hex ${stats.hex3All}  |  rpx 字面量 ${stats.rpxAll}`)
  return lines.join('\n')
}

/* ------------------------------------------------------------------ *
 * CLI
 * ------------------------------------------------------------------ */

function isMain() {
  const entry = process.argv[1] ? path.resolve(process.argv[1]) : ''
  return entry === path.resolve(fileURLToPath(import.meta.url))
}

if (isMain()) {
  const args = process.argv.slice(2)
  const rootArg = args.indexOf('--root')
  const scanRoot = rootArg >= 0 && args[rootArg + 1] ? path.resolve(args[rootArg + 1]) : ROOT
  const result = scan(scanRoot)
  const json = args.includes('--json')

  if (args.includes('--write-baseline')) {
    const payload = {
      generatedAt: new Date().toISOString(),
      total: result.violations.length,
      byRule: result.byRule,
      stats: { ...result.stats, pagesWithoutStates: result.stats.pagesWithoutStates.length },
    }
    fs.writeFileSync(BASELINE_FILE, JSON.stringify(payload, null, 2) + '\n', 'utf8')
    console.log(`基线快照已写入 ${path.relative(ROOT, BASELINE_FILE)}（违规 ${payload.total}）`)
  }

  if (json) {
    console.log(JSON.stringify(result, null, 2))
  } else {
    console.log(formatReport(result))
    console.log('')
    console.log('违规明细（前 40 条，--json 看全量）')
    for (const x of result.violations.slice(0, 40)) {
      console.log(`  [${x.rule}] ${x.file}:${x.line} ${x.message}${x.detail ? ' :: ' + x.detail : ''}`)
    }
    if (result.violations.length > 40) console.log(`  ... 其余 ${result.violations.length - 40} 条`)
  }

  let baseline = null
  const i = args.indexOf('--baseline')
  if (i >= 0 && args[i + 1]) baseline = Number(args[i + 1])
  else if (fs.existsSync(BASELINE_FILE)) {
    try { baseline = JSON.parse(fs.readFileSync(BASELINE_FILE, 'utf8')).total } catch { baseline = null }
  }

  if (baseline === null && args.includes('--require-baseline')) {
    if (!json) console.error('\n闸门：FAIL — 缺少 scripts/guard-baseline.json，无法判定是否新增违规。请先运行 guard:baseline 并提交快照。')
    process.exit(1)
  }

  if (baseline !== null && Number.isFinite(baseline)) {
    const ok = result.violations.length <= baseline
    if (!json) console.log(`\n闸门：违规 ${result.violations.length} / 基线 ${baseline} → ${ok ? 'PASS（无新增违规）' : 'FAIL（出现新增违规）'}`)
    process.exit(ok ? 0 : 1)
  }
  process.exit(0)
}
