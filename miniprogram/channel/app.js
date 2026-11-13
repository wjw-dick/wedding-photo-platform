App({ onLaunch() { this.globalData = { token: wx.getStorageSync('token') || '' } } })
