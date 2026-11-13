const BASE = 'http://localhost:18090/api'
const TENANT = 'demo'

function request(path, method = 'GET', data) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE + path,
      method,
      data,
      header: {
        'content-type': 'application/json',
        Authorization: wx.getStorageSync('token') ? `Bearer ${wx.getStorageSync('token')}` : ''
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.reLaunch({ url: '/pages/login/login' })
          return reject(new Error('登录已过期'))
        }
        if (body.code !== 0) {
          wx.showToast({ title: body.message || '请求失败', icon: 'none' })
          return reject(new Error(body.message || '请求失败'))
        }
        resolve(body.data)
      },
      fail(err) { wx.showToast({ title: '网络异常', icon: 'none' }); reject(err) }
    })
  })
}

module.exports = {
  TENANT,
  get: (p, d) => request(p, 'GET', d),
  post: (p, d) => request(p, 'POST', d),
  login: (tenantCode, username, password) => request('/auth/login', 'POST', { tenantCode, username, password })
}
