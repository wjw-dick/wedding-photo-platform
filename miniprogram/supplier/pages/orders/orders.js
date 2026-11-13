const api = require('../../utils/api')
Page({
  data: { list: [] },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    this.load()
  },
  load() { api.get('/supplier/orders', { page: 1, size: 30 }).then((d) => this.setData({ list: d.records || d })) },
  action(e) {
    const { id, action } = e.currentTarget.dataset
    api.post(`/supplier/orders/${id}/action`, { action }).then(() => { wx.showToast({ title: '已提交' }); this.load() })
  },
  ship(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '填写物流单号',
      editable: true,
      success: (res) => {
        if (res.confirm && res.content) {
          api.post(`/supplier/orders/${id}/action`, { action: 'SHIPPED', trackingCompany: '顺丰', trackingNo: res.content })
            .then(() => { wx.showToast({ title: '已发货' }); this.load() })
        }
      }
    })
  }
})
