const api = require('../../utils/api')
Page({
  data: { list: [], qualType: '', expireDate: '' },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    this.load()
  },
  load() { api.get('/supplier/qualifications').then((list) => this.setData({ list })) },
  onInput(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }) },
  add() {
    if (!this.data.qualType) return wx.showToast({ title: '请填写资质类型', icon: 'none' })
    api.post('/supplier/qualifications', { qualType: this.data.qualType, expireDate: this.data.expireDate, fileUrl: '' })
      .then(() => { wx.showToast({ title: '已提交' }); this.setData({ qualType: '', expireDate: '' }); this.load() })
  }
})
