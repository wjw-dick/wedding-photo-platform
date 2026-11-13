const api = require('../../utils/api')
Page({
  data: { commissions: [], settlements: [] },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    api.get('/channel-portal/commissions', { page: 1, size: 20 }).then((d) => this.setData({ commissions: d.records || d }))
    api.get('/channel-portal/settlements').then((settlements) => this.setData({ settlements }))
  }
})
