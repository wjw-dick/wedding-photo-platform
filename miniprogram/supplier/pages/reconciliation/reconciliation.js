const api = require('../../utils/api')
Page({
  data: { data: {} },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    const period = new Date().toISOString().slice(0, 7)
    api.get('/supplier/reconciliation', { period }).then((data) => this.setData({ data }))
  }
})
