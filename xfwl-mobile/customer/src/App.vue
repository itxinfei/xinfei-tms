<script>
// 心飞物流TMS 客户端 App 入口
export default {
  onLaunch: function () {
    // 登录守卫：无 token 引导回登录页（与 driver/courier 一致）
    const token = uni.getStorageSync('xfwl_token');
    if (!token) {
      uni.reLaunch({ url: '/pages/customer/login/index' });
    }
  },
  onShow: function () {},
  onHide: function () {},
}
</script>

<style lang="scss">
/* uview-plus 全局样式 */
@import "uview-plus/index.scss";
/* 设计令牌唯一真源：xfwl-mobile/shared/styles/tokens.scss，经薄壳注入一次 */
@import "@/common/styles/variables.scss";
/* 按压态交给 uview-plus 按 type 取主题变量；不再用全局 !important 覆盖。 */
</style>
