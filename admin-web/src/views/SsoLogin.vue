<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">婚礼影像数字化运营平台</h1>
      <div class="login-sub">统一认证登录</div>
      <el-result v-if="!errorMsg" icon="info" title="正在完成登录" sub-title="校验统一认证票据并同步账号权限…" />
      <el-result v-else icon="error" title="统一认证登录失败" :sub-title="errorMsg" />
      <div class="login-tip" v-if="errorMsg">即将返回登录页，可使用账号密码登录。</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { setSession } from '../auth'
import { get } from '../api'

const route = useRoute()
const router = useRouter()
const errorMsg = ref('')

onMounted(async () => {
  const token = String(route.query.token || '')
  if (!token) {
    router.replace('/login')
    return
  }
  localStorage.setItem('token', token)
  try {
    const me = await get('/auth/me')
    setSession({ token, user: me }, false)
    router.replace('/dashboard')
  } catch (e) {
    localStorage.removeItem('token')
    errorMsg.value = '登录态校验未通过，请改用账号密码登录或联系管理员。'
    setTimeout(() => router.replace('/login'), 1800)
  }
})
</script>
