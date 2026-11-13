const api = require('../../utils/api')
Page({
  data: { list: [], date: '' },
  onLoad() {
    const d = new Date()
    const date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    this.setData({ date })
    this.load()
  },
  load() { api.get('/tenant/production/shoot-tasks', { date: this.data.date }).then((list) => this.setData({ list })) },
  checkin(e) {
    const id = e.currentTarget.dataset.id
    wx.getLocation({
      type: 'gcj02',
      success: (loc) => api.post(`/tenant/production/shoot-tasks/${id}/checkin`, { longitude: loc.longitude, latitude: loc.latitude, address: '现场打卡' })
        .then(() => { wx.showToast({ title: '打卡成功' }); this.load() }),
      fail: () => wx.showToast({ title: '需要位置权限', icon: 'none' })
    })
  },
  finish(e) {
    api.post(`/tenant/production/shoot-tasks/${e.currentTarget.dataset.id}/finish`).then(() => { wx.showToast({ title: '已完成' }); this.load() })
  }
})
