App({
  globalData: { tenant: 'demo' },
  onLaunch() { this.globalData.token = wx.getStorageSync('token') || '' }
})
