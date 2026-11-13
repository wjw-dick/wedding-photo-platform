const api = require('../../utils/api')
Page({
  data: { orders: [], coupons: [] },
  onLoad() {
    api.get('/member/orders', { page: 1, size: 20 }).then((d) => this.setData({ orders: d.records || d }))
    api.get('/member/coupons').then((coupons) => this.setData({ coupons }))
  }
})
