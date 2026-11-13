import { reactive } from 'vue'
import { post } from './api'

export const auth = reactive({
  token: localStorage.getItem('token') || '',
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  session: JSON.parse(localStorage.getItem('session') || 'null'),
  platform: localStorage.getItem('platform') === '1'
})

export function setSession(data, platform) {
  auth.token = data.token
  auth.user = data.user || data
  auth.session = data
  auth.platform = platform
  localStorage.setItem('token', data.token)
  localStorage.setItem('user', JSON.stringify(auth.user || data))
  localStorage.setItem('session', JSON.stringify(data))
  localStorage.setItem('platform', platform ? '1' : '0')
}

export function clearSession() {
  auth.token = ''
  auth.user = null
  auth.session = null
  auth.platform = false
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('session')
  localStorage.removeItem('platform')
}

export async function loginPlatform(username, password) {
  const data = await post('/platform/auth/login', { username, password })
  setSession(data, true)
  return data
}

export async function loginTenant(tenantCode, username, password) {
  const data = await post('/auth/login', { tenantCode, username, password })
  setSession(data, false)
  return data
}
