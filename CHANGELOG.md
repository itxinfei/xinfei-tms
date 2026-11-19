# 更新日志

按时间倒序。这里只记对人有用的事：能力变化、破坏性改动、依赖和安全升级。逐条提交流水看 `git log`。

## 2026-11

- 文档：README 补齐常见问题、贡献指南、安全策略与 Issue 模板；中英双版顶部加 star/fork 徽章
- 文档：Nacos 命名空间更正为 `xinfei-tms`
- 仓库统一更名为 xf 系列，README 内所有链接同步
- 新增 `.gitattributes`，Markdown 统一 LF 行尾

## 2026-10

- 技术栈：JDK 25 + Spring Boot 4.1.1 + Vue 3 + Sa-Token 多租户 + XXL-Job
- 依赖对齐 Boot 4：MyBatis-Plus boot4 starter 3.5.17、springdoc-openapi 3.1.1、Sa-Token boot4 1.46.0、Druid boot4 1.2.28
- 安全升级：commons-io 2.17.0、httpclient 4.5.14
- 业务：签收凭证持久化，三端回单展示
- 业务：租户启停开关接线，禁止停用或删除平台内置租户
- 业务：车辆与司机档案在线状态、车辆证照编辑
- 修复：未登录时授权状态探测误报 401、附件上传返回值形态导致照片凭证丢失
- 修复：移动三端可访问性断点（异常上报、路线、绩效入口）

## 2026-09

- 项目立项：四端骨架、13 个微服务、全链路业务闭环

## 2023-12

- 仓库建立

---

## 后续计划

轨迹链路（Kafka + TDengine）、智能排线（OR-Tools）、回单 OCR、动态 ETA，排序和取舍见 README「十二、里程碑」。
