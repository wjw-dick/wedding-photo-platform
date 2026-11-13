Page({
  data: { user: {} },
  onShow() { this.setData({ user: wx.getStorageSync('user') || {} }) },
  logout() { wx.removeStorageSync('token'); wx.removeStorageSync('user'); wx.reLaunch({ url: '/pages/login/login' }) }
})
