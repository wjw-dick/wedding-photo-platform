<template>
  <section class="section container" style="max-width:900px">
    <div class="section-head"><h2>会员中心</h2><span>订单 · 选片 · 交付 · 优惠券</span></div>

    <div v-if="!token" class="card card-body" style="max-width:420px">
      <p class="muted">使用微信授权手机号登录（本地开发可输入手机号与验证码 123456）。</p>
      <div class="form-row"><label>手机号</label><input v-model="phone" placeholder="13800000000" /></div>
      <div class="form-row"><label>验证码</label><input v-model="code" placeholder="123456" /></div>
      <button class="btn solid" @click="login">登录</button>
      <p v-if="error" style="color:#b43c3c">{{ error }}</p>
    </div>

    <template v-else>
      <div class="grid grid-2">
        <div class="card card-body">
          <h3>会员资料</h3>
          <p>姓名：{{ profile.real_name || '—' }}</p>
          <p>手机：{{ profile.phone || '—' }}</p>
          <p>积分：{{ profile.points ?? 0 }} · 储值：¥{{ profile.balance ?? 0 }}</p>
          <button class="btn" @click="logout">退出登录</button>
        </div>
        <div class="card card-body">
          <h3>我的优惠券</h3>
          <p v-for="c in coupons" :key="c.id">{{ c.coupon_name }} · {{ c.status }}</p>
          <p v-if="!coupons.length" class="muted">暂无优惠券</p>
        </div>
      </div>
      <h3 style="margin-top:32px">我的订单</h3>
      <div class="card card-body">
        <div v-for="o in orders" :key="o.id" style="display:flex;justify-content:space-between;border-bottom:1px solid var(--line);padding:12px 0">
          <div><b>{{ o.order_no }}</b><div class="muted">{{ o.package_name || '婚纱摄影' }} · {{ o.shoot_date || '待定' }}</div></div>
          <div style="text-align:right"><div class="price">¥{{ o.total_amount }}</div><span class="muted">{{ o.status }}</span></div>
        </div>
        <p v-if="!orders.length" class="muted">暂无订单</p>
      </div>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, memberLogin } from '../api'

const token = ref(localStorage.getItem('member_token') || '')
const phone = ref('')
const code = ref('123456')
const profile = ref({})
const orders = ref([])
const coupons = ref([])
const error = ref('')

async function login() {
  error.value = ''
  try {
    const d = await memberLogin(phone.value, code.value)
    token.value = d.token
    localStorage.setItem('member_token', d.token)
    load()
  } catch (e) { error.value = e.message || '登录失败' }
}
async function load() {
  try {
    profile.value = await get('/member/profile')
    const o = await get('/member/orders', { page: 1, size: 20 })
    orders.value = o.records || o
    coupons.value = await get('/member/coupons')
  } catch (e) { error.value = e.message }
}
function logout() {
  token.value = ''
  localStorage.removeItem('member_token')
}
onMounted(() => { if (token.value) load() })
</script>
