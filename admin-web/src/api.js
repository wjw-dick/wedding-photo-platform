import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => {
    if (res.config.responseType === 'blob') return res
    const body = res.data
    if (body && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '网络错误'
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!location.hash.includes('/login')) location.hash = '#/login'
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export const get = (url, params) => http.get(url, { params })
export const post = (url, data) => http.post(url, data)
export const put = (url, data) => http.put(url, data)
export const del = (url) => http.delete(url)

export async function download(url, filename) {
  const res = await http.get(url, { responseType: 'blob' })
  const blob = new Blob([res.data])
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename || 'export.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}

export default http
