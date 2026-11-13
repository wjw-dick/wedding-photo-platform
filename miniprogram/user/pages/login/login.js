const api = require('../../utils/api')
Page({
  data: { phone: '' },
  onInput(e) { this.setData({ phone: e.detail.value }) },
  login() {
    if (!/^1\d{10}$/.test(this.data.phone)) return wx.showToast({ title: '请输入正确手机号', icon: 'none' })
    api.memberLogin(this.data.phone).then((d) => {
      wx.setStorageSync('token', d.token)
      wx.setStorageSync('member', d.member || d)
      wx.showToast({ title: '登录成功' })
      wx.navigateBack()
    })
  }
})
