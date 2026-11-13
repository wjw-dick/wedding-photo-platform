const api = require('../../utils/api')
Page({
  data: { data: {}, subs: [] },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    api.get('/channel-portal/dashboard').then((data) => {
      this.setData({ data })
      if (data.channelId) api.get('/channel-portal/sub').then((subs) => this.setData({ subs }))
    }).catch(() => {})
  }
})
