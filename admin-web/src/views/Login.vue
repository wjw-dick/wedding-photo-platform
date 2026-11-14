<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">婚礼影像数字化运营平台</h1>
      <div class="login-sub">平台运营端 / 租户管理后台</div>
      <el-tabs v-model="mode" stretch>
        <el-tab-pane label="租户登录" name="tenant" />
        <el-tab-pane label="平台登录" name="platform" />
      </el-tabs>
      <el-form label-position="top" @keyup.enter="submit">
        <el-form-item v-if="mode === 'tenant'" label="租户编码">
          <el-input v-model="tenantCode" placeholder="demo" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password placeholder="admin123" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="submit">
          登录
        </el-button>
        <template v-if="mode === 'tenant' && ssoEnabled">
          <el-divider content-position="center">或</el-divider>
          <el-button style="width:100%" @click="ssoLogin">{{ ssoButtonText }}</el-button>
        </template>
      </el-form>
      <div class="login-tip">
        演示账号：平台 admin / admin123；租户 demo / admin / admin123<br />
        生产环境请通过 HTTPS 访问，并立即修改默认密码。
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginPlatform, loginTenant } from '../auth'
import { get } from '../api'

const route = useRoute()
const router = useRouter()
const mode = ref('tenant')
const tenantCode = ref(String(route.query.tenantCode || 'demo'))
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)
const ssoEnabled = ref(false)
const ssoButtonText = ref('统一认证登录（CAS）')

if (route.query.sso_error) {
  ElMessage.error(decodeURIComponent(String(route.query.sso_error)))
}

async function loadSso() {
  ssoEnabled.value = false
  if (mode.value !== 'tenant' || !tenantCode.value) return
  try {
    const data = await get(`/public/${tenantCode.value}/sso/config`)
    ssoEnabled.value = !!data && data.enabled === 1
    if (data && data.buttonText) ssoButtonText.value = data.buttonText
  } catch (e) {
    ssoEnabled.value = false
  }
}

function ssoLogin() {
  window.location.href = `/api/public/${tenantCode.value}/sso/login`
}

watch([mode, tenantCode], loadSso, { immediate: true })

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'platform') {
      await loginPlatform(username.value, password.value)
      router.push('/platform/dashboard')
    } else {
      await loginTenant(tenantCode.value, username.value, password.value)
      router.push('/dashboard')
    }
    ElMessage.success('登录成功')
  } finally {
    loading.value = false
  }
}
</script>
