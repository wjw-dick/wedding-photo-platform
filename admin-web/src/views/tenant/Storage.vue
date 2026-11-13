<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">对象存储</h2>
        <div class="page-sub">LOCAL / MinIO / 阿里云 OSS / 腾讯云 COS / AWS S3 五种驱动，密钥掩码展示</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="card">
      <el-table :data="configs" border stripe>
        <el-table-column prop="provider" label="驱动" width="140" />
        <el-table-column prop="endpoint" label="Endpoint" min-width="200" />
        <el-table-column prop="bucket" label="Bucket" width="150" />
        <el-table-column prop="region" label="Region" width="130" />
        <el-table-column prop="accessKey" label="AccessKey" width="140" />
        <el-table-column prop="secretKey" label="SecretKey" width="140" />
        <el-table-column label="当前" width="90">
          <template #default="{ row }"><el-tag v-if="row.active" type="success" size="small">使用中</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="edit(row)">配置</el-button>
            <el-button v-if="!row.active" link type="success" @click="activate(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert class="mt16" type="info" :closable="false" show-icon
        title="已保存的密钥仅显示掩码，重新保存时留空表示沿用原密钥；切换驱动需二次确认。" />
    </div>

    <el-dialog v-model="dialog" :title="`存储配置 · ${form.provider}`" width="560px">
      <el-form label-width="110px">
        <el-form-item label="Endpoint"><el-input v-model="form.endpoint" /></el-form-item>
        <el-form-item label="Bucket"><el-input v-model="form.bucket" /></el-form-item>
        <el-form-item label="Region"><el-input v-model="form.region" /></el-form-item>
        <el-form-item label="AccessKey"><el-input v-model="form.accessKey" placeholder="留空沿用原值" /></el-form-item>
        <el-form-item label="SecretKey"><el-input v-model="form.secretKey" show-password placeholder="留空沿用原值" /></el-form-item>
        <el-form-item label="访问域名"><el-input v-model="form.domain" /></el-form-item>
        <el-form-item label="PathStyle"><el-switch v-model="form.pathStyle" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button @click="test">连接测试</el-button>
        <el-button type="primary" @click="save">保存并启用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post } from '../../api'

const configs = ref([])
const activeProvider = ref('')
const dialog = ref(false)
const form = ref({})

async function load() {
  const d = await get('/tenant/storage')
  configs.value = d.configs
  activeProvider.value = d.activeProvider
}
function edit(row) {
  form.value = { provider: row.provider, endpoint: row.endpoint, bucket: row.bucket, region: row.region, accessKey: '', secretKey: '', domain: '', pathStyle: true }
  dialog.value = true
}
async function test() {
  const d = await post('/tenant/storage/test', form.value)
  ElMessage.success(d.message || '测试通过')
}
async function save() {
  const switching = form.value.provider !== activeProvider.value
  if (switching) await ElMessageBox.confirm('切换存储驱动后新文件写入新驱动，历史文件仍按原驱动读取。确认切换？', '二次确认', { type: 'warning' })
  await post('/tenant/storage/save', { provider: form.value.provider, config: form.value, confirmSwitch: switching })
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function activate(row) {
  await ElMessageBox.confirm('切换存储驱动需二次确认，确认继续？', '二次确认', { type: 'warning' })
  await post(`/tenant/storage/${row.provider}/activate`, { confirm: true })
  ElMessage.success('已切换')
  load()
}
onMounted(load)
</script>
