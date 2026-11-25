<script>
// 启动守卫：无 token 跳司机端登录页
// 修改点：由 courier 端改写为 driver，登录页路径改为 /pages/driver/login/index
export default {
  onLaunch: function () {
    const token = uni.getStorageSync('xfwl_token')
    if (!token) {
      uni.reLaunch({ url: '/pages/driver/login/index' })
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
