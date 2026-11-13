const api = require('../../utils/api')
Page({
  data: { tenantCode: 'demo', username: '', password: '' },
  onInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  login() {
    api.login(this.data.tenantCode, this.data.username, this.data.password).then((d) => {
      wx.setStorageSync('token', d.token)
      wx.setStorageSync('user', d.user)
      wx.switchTab({ url: '/pages/index/index' })
    })
  }
})
