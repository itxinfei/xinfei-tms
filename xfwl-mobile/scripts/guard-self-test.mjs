#!/usr/bin/env node
/**
 * guard-self-test.mjs — R1–R14、R17 正例 / 反例自检（共 29 项断言）
 *
 * 对应《80-移动三端页面设计优化整改TODO》§3 任务 1.2：
 *  - 14 条规则各配 1 个正例（必须 0 命中）、1 个反例（必须 >=1 命中）；
 *  - 故意注入一条静默 catch，必须被 R1 拦下（防假阴性 / 漏报）；
 *  - 把反例改成合法写法后不得误报（防假阳性 / 冤枉）；
 *  - 任何断言失败 exit 1，可直接在流水线里挡住规则退化。
 *
 * 用法：node scripts/guard-self-test.mjs
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { RULES, makeContext, runRules, summarize, FEEDBACK_TOKENS, RPX_ALLOWLIST } from './guard-ui.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const PROJECT_ROOT = path.resolve(HERE, '..')

/** 统一骨架：正例默认带上合规的 style / script，避免被其它规则误伤（本脚本只按规则 id 断言） */
const sfc = (template, script, style = '.ok { color: var(--c-text-1); }', styleAttrs = ' scoped lang="scss"') =>
  `<template>\n${template}\n</template>\n<script setup>\n${script}\n</script>\n<style${styleAttrs}>\n${style}\n</style>\n`

const cases = {
  R1: {
    good: sfc('<view />', "try { await api() } catch (e) { uni.showToast({ title: '加载失败' }) }"),
    bad: sfc('<view />', 'try { await api() } catch (e) { price.value = null }'),
  },
  R2: {
    // 登录成功后一次性清栈是合理用法，函数名不是 goXxx，不得误报
    good: sfc('<view />', "const afterLogin = () => uni.reLaunch({ url: '/pages/customer/home/index' })"),
    bad: sfc('<view />', "const goList = () => uni.reLaunch({ url: '/pages/customer/mailing/list' })"),
  },
  R3: {
    good: sfc(`<u-button :text="'已选 ' + picked.size" />`, 'const picked = ref(new Set())'),
    bad: sfc('<u-button text="批量完成（已选 {{ picked.size }}）" />', 'const picked = ref(new Set())'),
  },
  R4: {
    good: sfc('<view class="bar" />', 'const a = 1', '.bar { position: fixed; bottom: 0; }'),
    bad: sfc('<view class="bar" />', 'const a = 1', '.bar { position: fixed; bottom: 0; } .fab { position: fixed; bottom: 0; }'),
  },
  R5: {
    good: sfc('<view />', 'const a = 1', '.a { color: var(--c-text-1); }'),
    bad: sfc('<view />', 'const a = 1', '.a { color: #FF0033; }'),
  },
  R6: {
    good: sfc('<view />', 'const a = 1', '.a { color: var(--c-primary-dark); }'),
    bad: sfc('<view />', 'const a = 1', '.a { color: var(--c-primary-dark) !important; }'),
  },
  R7: {
    good: sfc('<view />', 'const a = 1', '.a { border: 1rpx solid var(--line); }'),
    bad: sfc('<view />', 'const a = 1', '.a { padding: 48rpx; }'),
  },
  R8: {
    // 正方形缩略图（照片 160rpx）不是占位，不得误报；真正要拦的是模板里的空 spacer
    good: sfc('<view />', 'const a = 1', '.photo-item { width: 160rpx; height: 160rpx; }\n.a { padding-bottom: var(--fixed-bottom-h); }'),
    bad: sfc('<view style="height: 140rpx"></view>', 'const a = 1', '.a { padding-bottom: var(--fixed-bottom-h); }'),
  },
  R9: {
    good: sfc('<u-form><u-input v-model="v" placeholder="请输入" /><u-button @click="onSubmit" /></u-form>', 'const onSubmit = () => {}'),
    bad: sfc('<u-input v-model="v" placeholder="请输入" /><u-button @click="onSubmit" />', 'const onSubmit = () => {}'),
  },
  R10: {
    good: sfc('<u-input @change="onFormChange" />', 'const debounce = (f) => f\nconst onFormChange = debounce(async () => { await calcPrice() })'),
    bad: sfc('<u-input @change="onFormChange" />', 'const onFormChange = async () => { await calcPrice() }'),
  },
  R11: {
    // 后端契约差异：本地统一 const pageSize，按接口填 pagesize / pageSize 键 —— 不是分裂
    good: sfc('<view />', 'const pageSize = 10; const q = { page: 1, pagesize: pageSize }; const picked = ref(new Set()); console.log(picked.size)'),
    // 真分裂：同文件并存两种页容量本地变量名
    bad: sfc('<view />', 'const pageSize = 10; const limit = 20; const q = { page: 1, pagesize: pageSize }'),
  },
  R12: {
    good: sfc('<view />', 'const a = 1'),
    bad: sfc('<view />', 'const a = 1', '.ok { color: var(--c-text-1); }', ' lang="scss"'),
  },
  R13: {
    // 规则按文件名生效，fixture 需伪装成端内 uni.scss
    rel: 'courier/src/uni.scss',
    good: '@import "uview-plus/theme.scss";\n/* 主题色由 page { --u-* } 运行时变量提供 */\n',
    bad: '@import "uview-plus/theme.scss";\n$u-primary: #FF0033;\n',
  },
  R14: {
    // .accent-dot 是唯一真源里放行的 ≤1% 点缀类；自定义属性定义行不算文字承载
    good: sfc('<view />', 'const a = 1', '.accent-dot { color: var(--c-primary-bright); }\n--c-primary-bright: #FF0033;'),
    bad: sfc('<view />', 'const a = 1', '.brand-dot { color: var(--c-primary-bright); }'),
  },
  R17: {
    // 亮底白字：brand-dot 用 background 承载白字约 3.96:1 不达标；accent-dot「恰为」放行
    good: sfc('<view />', 'const a = 1', '.accent-dot { background: var(--c-primary-bright); }'),
    bad: sfc('<view />', 'const a = 1', '.a { background: var(--c-primary-bright); }'),
  },
}

const failures = []
const rows = []

// 默认 fixture 以 .vue 结尾（R5/R7 按「页面 .vue」口径生效）；
// 按文件名生效的规则（如 R13 只管 uni.scss）用 case.rel 指定路径。
const hits = (raw, ruleId, rel = 'fixture.vue') => runRules(makeContext(raw, rel)).filter(x => x.rule === ruleId).length
const ok = (cond, label) => { if (!cond) failures.push(label) }

for (const rule of RULES) {
  const c = cases[rule.id]
  if (!c) { failures.push(`${rule.id} 缺正例/反例`); rows.push([rule.id, 'MISSING', '-', '-']); continue }
  const g = hits(c.good, rule.id, c.rel)
  const b = hits(c.bad, rule.id, c.rel)
  ok(g === 0, `${rule.id} 正例误报（命中 ${g}，应为 0）`)
  ok(b >= 1, `${rule.id} 反例漏报（命中 ${b}，应 >=1）`)
  rows.push([rule.id, g === 0 && b >= 1 ? 'PASS' : 'FAIL', g, b])
}

/* --- 防假阴性：故意注入一条静默 catch，R1 必须精确多拦 1 条 --- */
const clean = cases.R1.good
const injected = clean.replace('</script>', "try { await other() } catch (e) { /* 静默 */ }\n</script>")
const before = hits(clean, 'R1')
const after = hits(injected, 'R1')
ok(before === 0 && after === before + 1, `注入静默 catch 未被 R1 精确拦下（before ${before} / after ${after}）`)
rows.push(['INJECT', before === 0 && after === before + 1 ? 'PASS' : 'FAIL', before, after])

/* --- 防假阳性：把 R1 反例改成合法写法后，R1 必须归零 --- */
const repaired = cases.R1.bad.replace('price.value = null', "uni.showToast({ title: '计算失败' })")
const repairedHits = hits(repaired, 'R1')
ok(repairedHits === 0, `反例改成合法写法后仍误报（R1 命中 ${repairedHits}）`)
rows.push(['REPAIR', repairedHits === 0 ? 'PASS' : 'FAIL', repairedHits, '-'])

/* --- 规则常量自身的确定性 --- */
ok(FEEDBACK_TOKENS.some(t => t.test("uni.showToast({ title: 'x' })")), '反馈标记表未覆盖 uni.showToast')
ok(!FEEDBACK_TOKENS.some(t => t.test('price.value = null')), '反馈标记表把「伪装成 null」误判为有反馈')
ok(RPX_ALLOWLIST.has(1) && RPX_ALLOWLIST.has(2) && !RPX_ALLOWLIST.has(48), 'rpx 白名单不是 {1,2}')
rows.push(['CONSTANTS', failures.some(f => f.includes('反馈标记') || f.includes('白名单')) ? 'FAIL' : 'PASS', '-', '-'])

/* --- 注释剥离：注释中的示例文字不得误报，真实代码不得漏报 --- */
const commentOnly = '/* .u-button--active { color: #FF0033 !important; } */\n/* $u-primary: #FF0033; */\n'
const realCode = '.a { color: #FF0033 !important; }\n'
const commentsOk =
  hits(commentOnly, 'R6') === 0 && hits(commentOnly, 'R5') === 0 &&
  hits(commentOnly, 'R13', 'courier/src/uni.scss') === 0 &&
  hits(realCode, 'R6') === 1 && hits(realCode, 'R5') === 1
ok(commentsOk, '注释剥离失败：注释里的 !important / hex 被误报，或真实样式未被拦下')
rows.push(['COMMENTS', commentsOk ? 'PASS' : 'FAIL', '-', '-'])

/* --- 行注释剥离：// 里的示例文字不得误报，真实代码不得漏报 --- */
const lineCommentOnly = '.a { color: var(--c-text-1); }\n// 手写 140rpx 占位、#FF0033 !important 示例\n'
const lineRealCode = '.a { padding: 140rpx; color: #FF0033 !important; }\n'
const lineCommentsOk =
  hits(lineCommentOnly, 'R7') === 0 && hits(lineCommentOnly, 'R6') === 0 && hits(lineCommentOnly, 'R5') === 0 &&
  hits(lineRealCode, 'R7') === 1 && hits(lineRealCode, 'R6') === 1 && hits(lineRealCode, 'R5') === 1
ok(lineCommentsOk, '行注释剥离失败：// 示例文字被误报，或真实代码未被拦下')
rows.push(['LINE-COMMENTS', lineCommentsOk ? 'PASS' : 'FAIL', hits(lineCommentOnly, 'R7'), hits(lineRealCode, 'R7')])

/* --- HTML 注释剥离：模板注释里的尺寸示例不得误报 --- */
const htmlCommentOnly = sfc('<!-- 作业端主按钮 ≥96rpx，删掉装饰 u-tabs -->\n<view />', 'const a = 1')
const htmlRealCode = sfc('<view />', 'const a = 1', '.a { height: 96rpx; }')
const htmlCommentsOk = hits(htmlCommentOnly, 'R7') === 0 && hits(htmlRealCode, 'R7') === 1
ok(htmlCommentsOk, `HTML 注释剥离失败：注释示例 ${hits(htmlCommentOnly, 'R7')} / 真实代码 ${hits(htmlRealCode, 'R7')}`)
rows.push(['HTML-COMMENTS', htmlCommentsOk ? 'PASS' : 'FAIL', hits(htmlCommentOnly, 'R7'), hits(htmlRealCode, 'R7')])

/* --- Promise 静默 catch 不得漏报 --- */
const promiseSilent = sfc('<view />', 'api().then(ok).catch(() => {})')
const promiseLoud = sfc('<view />', "api().then(ok).catch(() => { uni.showToast({ title: '加载失败' }) })")
const r1PromiseOk = hits(promiseSilent, 'R1') >= 1 && hits(promiseLoud, 'R1') === 0
ok(r1PromiseOk, `Promise catch 口径错误：静默 ${hits(promiseSilent, 'R1')} / 有反馈 ${hits(promiseLoud, 'R1')}`)
rows.push(['R1-PROMISE', r1PromiseOk ? 'PASS' : 'FAIL', hits(promiseSilent, 'R1'), hits(promiseLoud, 'R1')])

/* --- R4：抽取成 <ActionBar> 组件后仍必须算一层，不能被规则漏掉 --- */
const componentBar = sfc('<ActionBar><u-button text="提交" /></ActionBar>', 'const a = 1')
const doubleComponentBar = sfc('<ActionBar><u-button text="提交" /></ActionBar><view class="fab" />', 'const a = 1', '.fab { position: fixed; bottom: 0; }')
const r4ComponentOk = hits(componentBar, 'R4') === 0 && hits(doubleComponentBar, 'R4') === 1
ok(r4ComponentOk, `R4 组件口径错误：单层 ${hits(componentBar, 'R4')} / 双层 ${hits(doubleComponentBar, 'R4')}`)
rows.push(['R4-COMPONENT', r4ComponentOk ? 'PASS' : 'FAIL', hits(componentBar, 'R4'), hits(doubleComponentBar, 'R4')])

/* --- R11 口径：后端契约差异不报、本地命名分裂与请求键混用必须报 --- */
const contractDiff = sfc('<view />', 'const pageSize = 10; const q = { page: 1, pagesize: pageSize }')
const doubleKey = sfc('<view />', 'const pageSize = 10; const q = { pagesize: pageSize, pageSize: pageSize }')
const localSplit = sfc('<view />', 'const pageSize = 10; const limit = 20')
const r11Scope =
  hits(contractDiff, 'R11') === 0 &&
  hits(doubleKey, 'R11') >= 1 &&
  hits(localSplit, 'R11') >= 1
ok(r11Scope, `R11 口径错误：契约差异 ${hits(contractDiff, 'R11')} / 双键 ${hits(doubleKey, 'R11')} / 本地分裂 ${hits(localSplit, 'R11')}`)
rows.push(['R11-SCOPE', r11Scope ? 'PASS' : 'FAIL', hits(contractDiff, 'R11'), hits(localSplit, 'R11')])

/* --- R8 口径：正方形缩略图不报、模板空 spacer 必须报 --- */
const thumb = sfc('<view />', 'const a = 1', '.photo-item { width: 160rpx; height: 160rpx; }')
const spacer = sfc('<view style="height: 140rpx"></view>', 'const a = 1')
const r8Scope = hits(thumb, 'R8') === 0 && hits(spacer, 'R8') === 1
ok(r8Scope, `R8 口径错误：缩略图 ${hits(thumb, 'R8')} / 空 spacer ${hits(spacer, 'R8')}`)
rows.push(['R8-SCOPE', r8Scope ? 'PASS' : 'FAIL', hits(thumb, 'R8'), hits(spacer, 'R8')])

/* --- R14 口径：绘制色 / 注释 / 令牌定义 / 点缀类不误报，文字色必须报 --- */
const r14Polyline = sfc('<view />', "const p = { color: '#FF0033', width: 6 }")
const r14Comment = sfc('<view />', 'const a = 1', '/* 点缀色 #FF0033 仅用于圆点 */\n.a { color: var(--c-primary); }')
const r14TplAttr = sfc('<text color="#FF0033">x</text>', 'const a = 1')
const r14Scope =
  hits(r14Polyline, 'R14') === 0 &&
  hits(r14Comment, 'R14') === 0 &&
  hits(cases.R14.good, 'R14') === 0 &&
  hits(r14TplAttr, 'R14') >= 1 &&
  hits(cases.R14.bad, 'R14') >= 1
ok(r14Scope, `R14 口径错误：绘制色 ${hits(r14Polyline, 'R14')} / 注释 ${hits(r14Comment, 'R14')} / 点缀类 ${hits(cases.R14.good, 'R14')} / 模板属性 ${hits(r14TplAttr, 'R14')} / 品牌点 ${hits(cases.R14.bad, 'R14')}`)
rows.push(['R14-SCOPE', r14Scope ? 'PASS' : 'FAIL', hits(r14Polyline, 'R14'), hits(r14TplAttr, 'R14')])

/* --- R17 口径：.accent-dot 恰为式放行、内联 style 无白名单、绘制色/边框不误报 --- */
const r17Inline = sfc('<view style="background: var(--c-primary-bright)">x</view>', 'const a = 1')
const r17Accent = sfc('<view />', 'const a = 1', '.accent-dot { background: var(--c-primary-bright); }')
const r17Border = sfc('<view />', 'const a = 1', '.a { border-color: var(--c-primary-bright); }')
const r17Script = sfc('<view />', "const p = { backgroundColor: '#FF0033' }")
const r17Scope =
  hits(r17Inline, 'R17') >= 1 &&
  hits(r17Accent, 'R17') === 0 &&
  hits(r17Border, 'R17') === 0 &&
  hits(r17Script, 'R17') === 0 &&
  hits(cases.R17.good, 'R17') === 0 &&
  hits(cases.R17.bad, 'R17') >= 1
ok(r17Scope, `R17 口径错误：内联 ${hits(r17Inline, 'R17')} / accent恰为 ${hits(r17Accent, 'R17')} / 边框 ${hits(r17Border, 'R17')} / 脚本 ${hits(r17Script, 'R17')} / 正例 ${hits(cases.R17.good, 'R17')} / 反例 ${hits(cases.R17.bad, 'R17')}`)
rows.push(['R17-SCOPE', r17Scope ? 'PASS' : 'FAIL', hits(r17Inline, 'R17'), hits(cases.R17.bad, 'R17')])

/* --- 原生组件色值常量必须与设计令牌真源同步（双真源靠注释，用断言锁住） --- */
const constantsSrc = fs.readFileSync(path.join(PROJECT_ROOT, 'shared/theme/constants.js'), 'utf8')
const tokensSrc = fs.readFileSync(path.join(PROJECT_ROOT, 'shared/styles/tokens.scss'), 'utf8')
const exportedInk = [...constantsSrc.matchAll(/export const (\w+)\s*=\s*'#([0-9a-fA-F]{6})'/g)]
const syncOk = exportedInk.length >= 5 && exportedInk.every(([, , hex]) =>
  new RegExp('^\\$c-[\\w-]+\\s*:\\s*#' + hex + '\\b', 'im').test(tokensSrc))
ok(syncOk, `constants.js 与 tokens.scss 色值不同步（导出 ${exportedInk.length} 个 hex 常量）`)
rows.push(['CONSTANTS-SYNC', syncOk ? 'PASS' : 'FAIL', exportedInk.length, '-'])

/* --- 汇总函数计数正确性 --- */
const sample = [
  { rule: 'R1' }, { rule: 'R1' }, { rule: 'R5' },
]
const s = summarize(sample)
ok(s.R1 === 2 && s.R5 === 1 && s.R12 === 0, 'summarize 计数不正确')
rows.push(['SUMMARIZE', s.R1 === 2 && s.R5 === 1 && s.R12 === 0 ? 'PASS' : 'FAIL', s.R1, s.R5])

console.log('R1–R14 正例 / 反例自检')
console.log('='.repeat(58))
console.log('  编号      结果   正例命中  反例命中')
for (const [id, status, g, b] of rows) {
  console.log(`  ${id.padEnd(10)} ${status.padEnd(6)} ${String(g).padStart(6)} ${String(b).padStart(9)}`)
}
console.log('-'.repeat(58))
if (failures.length) {
  console.log(`FAIL：${failures.length} 项`)
  for (const f of failures) console.log(`  - ${f}`)
  process.exit(1)
}
console.log(`PASS：${rows.length} 项断言全绿（${RULES.length} 条规则 + 注入 / 修复 / 常量 / 计数）`)
process.exit(0)
