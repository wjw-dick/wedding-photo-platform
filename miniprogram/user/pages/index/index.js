const api = require('../../utils/api')

Page({
  data: { home: {}, loading: true },
  onLoad() { this.load() },
  onPullDownRefresh() { this.load().then(() => wx.stopPullDownRefresh()) },
  load() {
    return api.get(api.pub('/home')).then((home) => this.setData({ home, loading: false })).catch(() => this.setData({ loading: false }))
  },
  goPackages() { wx.switchTab({ url: '/pages/packages/packages' }) },
  goContact() { wx.navigateTo({ url: '/pages/contact/contact' }) },
  goCase() { wx.switchTab({ url: '/pages/cases/cases' }) }
})
