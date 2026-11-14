<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">多端入口</h2>
        <div class="page-sub">用户端 / 生产端 / 供应商端 / 渠道端小程序、公众号、H5 入口二维码</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="open()">新增入口</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="entry_name" label="入口名称" min-width="150" />
        <el-table-column label="端类型" width="140">
          <template #default="{ row }">{{ endLabel(row.end_type) }}</template>
        </el-table-column>
        <el-table-column prop="url" label="目标地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="store_name" label="门店" width="130" />
        <el-table-column prop="channel_name" label="渠道" width="130" />
        <el-table-column prop="scan_count" label="扫码数" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showQr(row)">二维码</el-button>
            <el-button link type="primary" @click="open(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑入口' : '新增入口'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="入口名称"><el-input v-model="form.entryName" /></el-form-item>
        <el-form-item label="端类型">
          <el-select v-model="form.endType" style="width:100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标地址"><el-input v-model="form.url" placeholder="留空自动按端类型与租户生成" /></el-form-item>
        <el-form-item label="门店 ID"><el-input-number v-model="form.storeId" :min="0" /></el-form-item>
        <el-form-item label="渠道 ID"><el-input-number v-model="form.channelId" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qrDialog" title="入口二维码" width="420px">
      <div v-if="qrUrl" style="text-align:center">
        <el-image :src="qrUrl" style="width:280px" />
        <p class="muted">{{ qrRow?.url }}</p>
        <el-button @click="downloadQr">下载 PNG</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { auth } from '../../auth'

const types = [
  { value: 'USER_MINI', label: '用户端小程序' },
  { value: 'STAFF_MINI', label: '生产端小程序' },
  { value: 'SUPPLIER_MINI', label: '供应商端小程序' },
  { value: 'CHANNEL_MINI', label: '渠道商端小程序' },
  { value: 'MP', label: '公众号' },
  { value: 'H5', label: 'H5 分享页' },
  { value: 'PC', label: 'PC 官网' }
]
const endLabel = (v) => types.find((t) => t.value === v)?.label || v
const rows = ref([])
const dialog = ref(false)
const form = ref({})
const qrDialog = ref(false)
const qrUrl = ref('')
const qrRow = ref(null)

async function load() { rows.value = await get('/tenant/entries') }
function open(row) {
  form.value = row ? { ...row, endType: row.end_type, entryName: row.entry_name } : { endType: 'USER_MINI', entryName: '', status: 1 }
  dialog.value = true
}
async function save() {
  await post('/tenant/entries', form.value)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function showQr(row) {
  qrRow.value = row
  const res = await fetch(`/api/tenant/entries/${row.id}/qr.png?size=320`, { headers: { Authorization: `Bearer ${auth.token}` } })
  if (!res.ok) return ElMessage.error('二维码生成失败')
  qrUrl.value = URL.createObjectURL(await res.blob())
  qrDialog.value = true
}
function downloadQr() {
  const a = document.createElement('a')
  a.href = qrUrl.value
  a.download = `entry-${qrRow.value.id}.png`
  a.click()
}

onMounted(load)
</script>
