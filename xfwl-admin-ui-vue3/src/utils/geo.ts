/**
 * 地理距离工具（WGS84 经纬度）
 */

/** WGS84 平均地球半径（公里） */
const EARTH_RADIUS_KM = 6371.0088;

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/**
 * 大圆距离（Haversine 公式），单位公里。
 *
 * 用途：调度工作台「就近派车」——按车辆到装货网点的直线距离对候选车辆排序，
 * 帮助调度员优先选近车、减少空驶里程（业务诉求：合理安排车辆、减少车辆里程）。
 *
 * 注意：这是两点直线距离，**仅用于就近排序，不是计费/考核里程**；
 * 计费里程以后端线路档案 distance 与百度驾车规划为准。任一坐标非法返回 null。
 */
export function haversineKm(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number | null {
  if (![lat1, lng1, lat2, lng2].every(Number.isFinite)) return null;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** 就近距离展示：10 km 内保留 1 位小数，更远取整；无值占位 */
export function formatDistanceKm(km: number | null | undefined): string {
  if (km == null || !Number.isFinite(km)) return "—";
  return km < 10 ? `${km.toFixed(1)} km` : `${Math.round(km)} km`;
}
