import axios from 'axios'

export const TENANT = import.meta.env.VITE_TENANT_CODE || 'demo'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('member_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use((res) => {
  const body = res.data
  if (body && body.code !== 0) return Promise.reject(new Error(body.message || '请求失败'))
  return body.data
}, (err) => Promise.reject(err))

export const get = (url, params) => http.get(url, { params })
export const post = (url, data) => http.post(url, data)

export const pub = (path) => `/public/${TENANT}${path}`

export async function submitLead(payload) {
  return post(pub('/leads'), payload)
}

export async function memberLogin(phone, code) {
  return post(`/open/${TENANT}/member/login`, { phone, code })
}
