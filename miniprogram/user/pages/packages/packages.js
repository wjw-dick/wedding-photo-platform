const api = require('../../utils/api')
Page({
  data: { list: [] },
  onLoad() { this.load() },
  load() { api.get(api.pub('/packages')).then((list) => this.setData({ list })) }
})
