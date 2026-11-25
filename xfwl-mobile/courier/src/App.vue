<script>
// 启动守卫：无 token 跳登录页
export default {
  onLaunch: function () {
    const token = uni.getStorageSync('xfwl_token')
    if (!token) {
      uni.reLaunch({ url: '/pages/courier/login/index' })
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
/* 按压态交给 uview-plus 按 type 取主题变量；不再用全局 !important 覆盖，
   否则 error / plain / info 按钮按压后会被刷成深红实心底（《80》2.3）。 */
</style>
