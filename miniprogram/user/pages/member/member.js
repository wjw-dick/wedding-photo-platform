const api = require('../../utils/api')
Page({
  data: { token: '', profile: {} },
  onShow() { this.setData({ token: wx.getStorageSync('token') || '' }); if (this.data.token) this.load() },
  load() { api.get('/member/profile').then((profile) => this.setData({ profile })) },
  goLogin() { wx.navigateTo({ url: '/pages/login/login' }) },
  goOrders() { wx.navigateTo({ url: '/pages/orders/orders' }) },
  logout() { wx.removeStorageSync('token'); this.setData({ token: '', profile: {} }) }
})
