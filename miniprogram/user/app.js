App({
  globalData: { tenant: 'demo', token: '', member: null },
  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.member = wx.getStorageSync('member') || null
  }
})
