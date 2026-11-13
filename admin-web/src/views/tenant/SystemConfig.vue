<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">消息与 SSO</h2>
        <div class="page-sub">消息通道、短信测试启用、SSO 条件映射与备份策略</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="消息通道" name="channels">
        <el-table :data="message.channels || []" border stripe>
          <el-table-column prop="channel" label="通道" width="130" />
          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <el-switch :model-value="!!row.enabled" @change="(v) => toggleChannel(row, v)" />
            </template>
          </el-table-column>
          <el-table-column prop="config_json" label="配置" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="短信渠道" name="sms">
        <el-table :data="message.sms || []" border stripe>
          <el-table-column prop="provider" label="服务商" width="140" />
          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <el-switch :model-value="!!row.enabled" @change="(v) => toggleSms(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="测试" width="120">
            <template #default="{ row }">
              <el-tag :type="row.test_ok ? 'success' : 'info'" size="small">{{ row.test_ok ? '已通过' : '未测试' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }"><el-button link type="primary" @click="testSms(row)">发送测试</el-button></template>
          </el-table-column>
        </el-table>
        <el-alert class="mt16" type="warning" :closable="false" show-icon
          title="未配置真实短信网关时，测试会写入本地日志并明确提示，不会伪装成真实发送。" />
      </el-tab-pane>

      <el-tab-pane label="消息模板" name="templates">
        <el-table :data="message.templates || []" border stripe>
          <el-table-column prop="node" label="触发节点" width="170" />
          <el-table-column prop="channel" label="通道" width="110" />
          <el-table-column prop="template_id" label="模板 ID" width="180" />
          <el-table-column prop="title" label="标题" min-width="150" />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
        <div class="mt16"><el-button @click="$router.push('/system/message-template')">维护模板</el-button></div>
      </el-tab-pane>

      <el-tab-pane label="SSO" name="sso">
        <el-form label-width="140px" style="max-width:640px">
          <el-form-item label="启用 SSO"><el-switch v-model="ssoForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
          <el-form-item label="协议">
            <el-select v-model="ssoForm.protocol" style="width:100%">
              <el-option label="CAS" value="CAS" /><el-option label="OAuth2" value="OAUTH2" /><el-option label="OIDC" value="OIDC" />
            </el-select>
          </el-form-item>
          <el-form-item label="Server URL"><el-input v-model="ssoForm.serverUrl" /></el-form-item>
          <el-form-item label="Service 回调"><el-input v-model="ssoForm.serviceCallback" placeholder="必须为 https 且与站点域名一致" /></el-form-item>
          <el-form-item label="条件字段"><el-input v-model="ssoForm.conditionField" /></el-form-item>
          <el-form-item label="账号映射"><el-input v-model="ssoForm.accountMapping" /></el-form-item>
          <el-form-item label="密钥"><el-input v-model="ssoForm.secret" show-password placeholder="留空沿用原密钥" /></el-form-item>
          <el-form-item><el-button type="primary" @click="saveSso">保存</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="测试与备份" name="ops">
        <div class="grid-2">
          <div class="card">
            <h3 style="margin-top:0">消息发送测试</h3>
            <el-form label-width="90px">
              <el-form-item label="通道">
                <el-select v-model="testForm.channel" style="width:100%">
                  <el-option label="站内信" value="SITE" /><el-option label="公众号" value="MP" /><el-option label="短信" value="SMS" />
                </el-select>
              </el-form-item>
              <el-form-item label="标题"><el-input v-model="testForm.title" /></el-form-item>
              <el-form-item label="内容"><el-input v-model="testForm.content" type="textarea" :rows="3" /></el-form-item>
              <el-form-item><el-button type="primary" @click="testMessage">发送测试</el-button></el-form-item>
            </el-form>
          </div>
          <div class="card">
            <h3 style="margin-top:0">备份策略</h3>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="数据库">{{ backup.db }}</el-descriptions-item>
              <el-descriptions-item label="备份">{{ backup.backup }}</el-descriptions-item>
              <el-descriptions-item label="对象存储">{{ backup.storage }}</el-descriptions-item>
              <el-descriptions-item label="说明">{{ backup.message }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post, put } from '../../api'

const tab = ref('channels')
const message = ref({})
const ssoForm = ref({})
const backup = ref({})
const testForm = reactive({ channel: 'SITE', title: '测试通知', content: '这是一条测试消息' })

async function load() {
  message.value = await get('/tenant/system/message')
  ssoForm.value = await get('/tenant/system/sso')
  backup.value = await get('/tenant/system/backup-info')
}
async function toggleChannel(row, v) {
  await put(`/tenant/system/message/channels/${row.channel}`, { enabled: v ? 1 : 0, configJson: row.config_json })
  ElMessage.success('已更新')
  load()
}
async function toggleSms(row, v) {
  await put(`/tenant/system/message/sms/${row.provider}`, { enabled: v ? 1 : 0 })
  ElMessage.success('已更新')
  load()
}
async function testSms(row) {
  const phone = prompt('测试手机号', '13800000000')
  if (!phone) return
  const d = await post(`/tenant/system/message/sms/${row.provider}/test`, { phone })
  ElMessage.success(d.message || '测试完成')
  load()
}
async function saveSso() {
  await put('/tenant/system/sso', ssoForm.value)
  ElMessage.success('SSO 配置已保存')
  load()
}
async function testMessage() {
  const d = await post('/tenant/cms/message/test', testForm)
  ElMessage.success(JSON.stringify(d))
}
onMounted(load)
</script>
