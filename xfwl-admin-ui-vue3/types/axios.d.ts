import "axios";

declare module "axios" {
  interface AxiosRequestConfig {
    // 匿名请求：不注入访问令牌，也不触发令牌续期
    anonymous?: boolean;
    // 静默鉴权：收到 401 时不弹"登录过期"、不跳登录页，直接 reject 交业务层 catch。
    // 仅用于启动态/后台探测类非关键请求（如授权状态），避免登录页被误踢。
    silentAuth?: boolean;
  }
}
