const api = require('../../utils/api')
Page({
  data: { list: [] },
  onLoad() { api.get(api.pub('/cases')).then((list) => this.setData({ list })) }
})
