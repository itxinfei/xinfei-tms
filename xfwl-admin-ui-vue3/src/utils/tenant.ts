import { appConfig } from "@/settings";
import { BUILTIN_TENANT_IDS } from "@/constants";

/**
 * 是否启用多租户
 */
export const isTenantEnabled = () => appConfig.tenantEnabled;

/**
 * 判断是否内置租户（平台虚拟租户 / 初始主租户）
 *
 * @description
 * 内置租户不参与套餐与菜单配置，且禁止停用/删除/改编码
 */
export const isBuiltinTenantId = (tenantId?: string | number) =>
  BUILTIN_TENANT_IDS.includes(Number(tenantId));
