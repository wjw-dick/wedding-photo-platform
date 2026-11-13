const api = require('../../utils/api')
Page({
  data: { name: '', phone: '', requirement: '', city: '', done: false },
  onInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  submit() {
    if (!this.data.name || !this.data.phone) return wx.showToast({ title: '请填写姓名和手机号', icon: 'none' })
    api.post(api.pub('/leads'), {
      name: this.data.name,
      phone: this.data.phone,
      city: this.data.city,
      requirement: this.data.requirement,
      sourcePage: 'user-mini'
    }).then(() => {
      this.setData({ done: true, name: '', phone: '', requirement: '', city: '' })
      wx.showToast({ title: '预约成功' })
    })
  }
})
