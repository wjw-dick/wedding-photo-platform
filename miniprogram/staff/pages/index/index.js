const api = require('../../utils/api')
Page({
  data: { tasks: {}, today: 0, week: 0 },
  onShow() {
    if (!wx.getStorageSync('token')) return wx.reLaunch({ url: '/pages/login/login' })
    api.get('/tenant/employee/my/tasks').then((tasks) => {
      this.setData({ tasks, today: (tasks.today || []).length, week: (tasks.week || []).length })
    }).catch(() => {})
  }
})
