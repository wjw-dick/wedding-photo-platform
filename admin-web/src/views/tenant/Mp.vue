<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">公众号运营</h2>
        <div class="page-sub">多公众号接入、标签/粉丝/模板同步、菜单发布与素材、草稿</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="open">接入公众号</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="账号与凭证" name="accounts">
        <el-table :data="accounts" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="account_name" label="账号名称" min-width="160" />
          <el-table-column prop="app_id" label="AppID" width="180" />
          <el-table-column prop="server_url" label="服务器回调" min-width="240" show-overflow-tooltip />
          <el-table-column prop="app_secret_encrypted" label="AppSecret" width="140" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="sync(row, 'tags')">同步标签</el-button>
              <el-button link type="primary" @click="sync(row, 'fans')">同步粉丝</el-button>
              <el-button link type="primary" @click="sync(row, 'templates')">同步模板</el-button>
              <el-button link type="warning" @click="clearQuota(row)">清配额</el-button>
              <el-button link type="primary" @click="open(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert class="mt16" type="info" :closable="false" show-icon
          title="未配置真实 AppID/AppSecret 时，同步接口会明确返回失败原因，不会伪造成功数据。" />
      </el-tab-pane>

      <el-tab-pane label="菜单发布" name="menu">
        <el-form label-width="120px" style="max-width:520px">
          <el-form-item label="菜单配置 ID"><el-input-number v-model="menuConfigId" :min="1" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="publishMenu">发布到公众号</el-button>
            <el-button @click="$router.push('/system/menu')">打开菜单可视化</el-button>
          </el-form-item>
        </el-form>
        <p class="muted">公众号一级菜单最多 3 个、二级最多 5 个；发布后需重新进入公众号查看。</p>
      </el-tab-pane>

      <el-tab-pane label="运营子页" name="subpages">
        <el-space wrap>
          <el-button v-for="i in subpages" :key="i.path" @click="$router.push(i.path)">{{ i.label }}</el-button>
        </el-space>
        <p class="muted mt16">标签、粉丝、消息、模板消息、自动回复、素材、图文草稿均在左侧“内容与公众号”分组中维护。</p>
      </el-tab-pane>

      <el-tab-pane label="二维码工具" name="qrcode">
        <el-input v-model="qrContent" placeholder="输入链接或文本" style="max-width:480px" />
        <el-button class="mt16" type="primary" @click="makeQr">生成二维码</el-button>
        <div v-if="qrUrl" class="mt16"><el-image :src="qrUrl" style="width:220px" /></div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" title="接入公众号" width="640px">
      <el-form label-width="130px">
        <el-form-item label="账号名称"><el-input v-model="form.accountName" /></el-form-item>
        <el-form-item label="AppID"><el-input v-model="form.appId" /></el-form-item>
        <el-form-item label="AppSecret"><el-input v-model="form.appSecret" show-password placeholder="留空表示不修改" /></el-form-item>
        <el-form-item label="Token"><el-input v-model="form.token" /></el-form-item>
        <el-form-item label="EncodingAESKey"><el-input v-model="form.encodingAesKey" /></el-form-item>
        <el-form-item label="网页授权域名"><el-input v-model="form.oauthDomain" /></el-form-item>
        <el-form-item label="JS 安全域名"><el-input v-model="form.jsDomain" /></el-form-item>
        <el-form-item label="关联小程序 AppID"><el-input v-model="form.miniAppId" /></el-form-item>
        <el-form-item label="小程序 Secret"><el-input v-model="form.miniAppSecret" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { auth } from '../../auth'

const tab = ref('accounts')
const accounts = ref([])
const dialog = ref(false)
const form = ref({})
const menuConfigId = ref(1)
const qrContent = ref('https://example.com/wedding')
const qrUrl = ref('')
const subpages = [
  { path: '/mp/tag', label: '标签管理' },
  { path: '/mp/user', label: '粉丝管理' },
  { path: '/mp/message', label: '消息管理' },
  { path: '/mp/message-template', label: '模板消息' },
  { path: '/mp/auto-reply', label: '自动回复' },
  { path: '/mp/material', label: '素材管理' },
  { path: '/mp/draft', label: '图文草稿' }
]

async function load() { accounts.value = await get('/tenant/mp/accounts') }
function open(row) {
  form.value = row ? { accountName: row.account_name, appId: row.app_id, ...row } : { accountName: '', appId: '' }
  dialog.value = true
}
async function save() {
  await post('/tenant/mp/accounts', form.value)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function sync(row, type) {
  try {
    const d = await post(`/tenant/mp/accounts/${row.id}/sync/${type}`, {})
    ElMessage.success(`同步完成：${JSON.stringify(d)}`)
  } catch (e) { /* 错误提示由拦截器展示 */ }
}
async function clearQuota(row) {
  await ElMessageBox.confirm('清零本月接口配额计数？', '确认', { type: 'warning' })
  await post(`/tenant/mp/accounts/${row.id}/clear-quota`, { confirm: true })
  ElMessage.success('已清零')
}
async function publishMenu() {
  const account = accounts.value[0]
  if (!account) return ElMessage.warning('请先接入公众号')
  const d = await post(`/tenant/mp/accounts/${account.id}/publish-menu`, { configId: menuConfigId.value })
  ElMessage.success(d.notice || '发布成功')
}
function makeQr() {
  const token = auth.token
  fetch(`/api/tenant/mp/qrcode?content=${encodeURIComponent(qrContent.value)}`, { headers: { Authorization: `Bearer ${token}` } })
    .then((r) => r.blob())
    .then((b) => { qrUrl.value = URL.createObjectURL(b) })
}

onMounted(load)
</script>
