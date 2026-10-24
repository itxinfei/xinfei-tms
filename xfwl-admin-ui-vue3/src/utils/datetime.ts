/**
 * 后端时间字符串统一解析
 *
 * 后端实际存在两种序列化形态：
 * - xfwl-netty 的 LocalDateTime 未配 @JsonFormat，输出 ISO 形态 `2026-10-18T22:10:05`；
 * - 走 spring.jackson.date-format 的 java.util.Date 输出 `2026-10-18 22:10:05`。
 *
 * 历史坑：ISO 形态先做 `replace(/-/g, "/")` 会得到 `2026/10/18T22:10:05`，
 * Chrome/V8 解析为 NaN（调度工作台全部车辆因此被误判 GPS 离线，2026-10-24 定位修复）。
 * 本函数先直接解析原生形态，再仅对空格形态做斜杠兜底，两者都失败才返回 null。
 *
 * @returns 毫秒时间戳；入参为空或无法解析时返回 null
 */
export function parseBackendTime(
  value: string | number | Date | null | undefined
): number | null {
  if (value == null || value === "") return null;
  if (value instanceof Date) {
    return Number.isFinite(value.getTime()) ? value.getTime() : null;
  }
  if (typeof value === "number") return Number.isFinite(value) ? value : null;

  const text = String(value).trim();
  if (!text) return null;

  // ISO（含 T）与 V8 可直接解析的形态先走原生解析，禁止先 replace 破坏格式
  const direct = Date.parse(text);
  if (Number.isFinite(direct)) return direct;

  // 空格形态 `yyyy-MM-dd HH:mm:ss` 在部分内核支持不一致，转斜杠后再试一次
  if (text.includes(" ") && !text.includes("T")) {
    const slashed = Date.parse(text.replace(/-/g, "/"));
    if (Number.isFinite(slashed)) return slashed;
  }
  return null;
}
