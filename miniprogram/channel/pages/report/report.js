const api = require('../../utils/api')
Page({
  data: { customerName: '', phone: '', requirement: '', reports: [] },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    this.load()
  },
  load() { api.get('/channel-portal/reports', { page: 1, size: 20 }).then((d) => this.setData({ reports: d.records || d })) },
  onInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  submit() {
    if (!this.data.customerName || !this.data.phone) return wx.showToast({ title: '请填写客户信息', icon: 'none' })
    api.post('/channel-portal/reports', {
      customerName: this.data.customerName,
      phone: this.data.phone,
      requirement: this.data.requirement
    }).then(() => {
      wx.showToast({ title: '报单成功' })
      this.setData({ customerName: '', phone: '', requirement: '' })
      this.load()
    })
  }
})
