<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">租户管理</h2>
        <div class="page-sub">开通、停用、续期、配额与数据导出</div>
      </div>
      <div class="flex">
        <el-button :icon="Download" @click="exportCsv">导出</el-button>
        <el-button type="primary" :icon="Plus" @click="startCreate">开通租户</el-button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="租户编码 / 名称" clearable style="width:240px" @keyup.enter="load" />
        <el-select v-model="status" clearable placeholder="状态" style="width:160px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="dictLabel(s)" :value="s" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="tenantCode" label="编码" width="120" />
        <el-table-column prop="tenantName" label="租户名称" min-width="160" />
        <el-table-column label="行业" width="140">
          <template #default="{ row }">{{ dictLabel(row.industry) }}</template>
        </el-table-column>
        <el-table-column prop="planName" label="套餐" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="tag(row.status)" size="small">{{ dictLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="expireAt" label="到期时间" width="170" />
        <el-table-column label="席位" width="100">
          <template #default="{ row }">{{ row.usedSeat }} / {{ row.seatLimit }}</template>
        </el-table-column>
        <el-table-column label="公众号" width="100">
          <template #default="{ row }">{{ row.usedMp }} / {{ row.mpAccountLimit }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="quota(row)">配额</el-button>
              <el-button link type="primary" @click="renew(row)">续期</el-button>
              <el-button v-if="row.status !== 'SUSPENDED'" link type="warning" @click="changeStatus(row, 'SUSPENDED')">停用</el-button>
              <el-button v-else link type="success" @click="changeStatus(row, 'ACTIVE')">恢复</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt16" layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="(p) => { page = p; load() }"
      />
    </div>

    <el-drawer v-model="drawer" title="租户开通向导" size="620px" destroy-on-close>
      <el-steps :active="step" finish-status="success" simple style="margin-bottom:22px">
        <el-step title="基本信息" /><el-step title="套餐配额" /><el-step title="主管理员" /><el-step title="完成" />
      </el-steps>
      <el-form v-if="step === 0" label-width="110px">
        <el-form-item label="租户编码"><el-input v-model="form.tenantCode" placeholder="小写字母/数字，如 demo2" /></el-form-item>
        <el-form-item label="租户名称"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="行业">
          <el-select v-model="form.industry" style="width:100%">
            <el-option label="婚纱摄影" value="WEDDING_PHOTO" />
            <el-option label="婚礼跟拍" value="WEDDING_VIDEO" />
            <el-option label="综合影像" value="IMAGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
      </el-form>
      <el-form v-else-if="step === 1" label-width="110px">
        <el-form-item label="套餐">
          <el-select v-model="form.planId" style="width:100%">
            <el-option v-for="p in plans" :key="p.plan_id" :label="`${p.plan_name} · ¥${p.price}`" :value="p.plan_id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试用">
          <el-switch v-model="form.trial" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
          title="开通时自动生成默认角色、菜单、参数、门店、语言、消息通道与演示数据，并创建 tenants/{id}/ 存储前缀。" />
      </el-form>
      <el-form v-else-if="step === 2" label-width="110px">
        <el-form-item label="管理员账号"><el-input v-model="form.adminUsername" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="form.adminPassword" show-password /></el-form-item>
        <el-alert type="warning" :closable="false" show-icon title="密码仅在开通成功时展示一次，请立即转交客户并强制修改。" />
      </el-form>
      <div v-else>
        <el-result icon="success" title="租户开通成功" :sub-title="`${created.tenantName || ''} 已创建`">
          <template #extra>
            <div class="card" style="text-align:left">
              <p><b>租户编码：</b>{{ created.tenantCode }}</p>
              <p><b>管理员账号：</b>{{ form.adminUsername }}</p>
              <p><b>初始密码：</b><span class="mono danger">{{ form.adminPassword }}</span>（仅展示一次）</p>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button v-if="step > 0 && step < 3" @click="step--">上一步</el-button>
        <el-button v-if="step < 2" type="primary" @click="step++">下一步</el-button>
        <el-button v-if="step === 2" type="primary" :loading="saving" @click="create">确认开通</el-button>
        <el-button v-if="step === 3" type="primary" @click="finish">完成</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="quotaDialog" title="调整配额" width="460px">
      <el-form label-width="110px">
        <el-form-item label="席位上限"><el-input-number v-model="quotaForm.seatLimit" :min="1" /></el-form-item>
        <el-form-item label="公众号上限"><el-input-number v-model="quotaForm.mpLimit" :min="0" /></el-form-item>
        <el-form-item label="存储 GB"><el-input-number v-model="quotaForm.storageGb" :min="1" /></el-form-item>
        <el-form-item label="短信条数"><el-input-number v-model="quotaForm.smsQuota" :min="0" /></el-form-item>
        <el-form-item label="API 调用量"><el-input-number v-model="quotaForm.apiQuota" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialog = false">取消</el-button>
        <el-button type="primary" @click="saveQuota">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Download } from '@element-plus/icons-vue'
import { del, download, get, post, put } from '../../api'
import { dictLabel } from '../../dict'

const statuses = ['TRIAL', 'ACTIVE', 'OVERDUE', 'SUSPENDED', 'EXPIRED']
const rows = ref([])
const plans = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const drawer = ref(false)
const step = ref(0)
const saving = ref(false)
const created = ref({})
const quotaDialog = ref(false)
const quotaForm = ref({ tenantId: null })
const form = ref({ industry: 'WEDDING_PHOTO', trial: true, adminUsername: 'admin', adminPassword: 'admin123' })

async function load() {
  loading.value = true
  try {
    const data = await get('/platform/tenants', { keyword: keyword.value, status: status.value, page: page.value, size: size.value })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function startCreate() {
  plans.value = await get('/platform/plans')
  form.value = { industry: 'WEDDING_PHOTO', trial: true, adminUsername: 'admin', adminPassword: 'admin123', planId: plans.value[0]?.plan_id }
  step.value = 0
  created.value = {}
  drawer.value = true
}

async function create() {
  saving.value = true
  try {
    created.value = await post('/platform/tenants', form.value)
    step.value = 3
    load()
  } finally {
    saving.value = false
  }
}

function finish() {
  drawer.value = false
  load()
}

async function changeStatus(row, s) {
  await post(`/platform/tenants/${row.tenantId}/status`, { status: s })
  ElMessage.success('状态已更新')
  load()
}

async function renew(row) {
  const { value } = await ElMessageBox.prompt('输入新的到期时间（YYYY-MM-DDTHH:mm:ss）', '续期', {
    inputValue: new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 19)
  })
  await post(`/platform/tenants/${row.tenantId}/renew`, { expireAt: value })
  ElMessage.success('续期成功')
  load()
}

function quota(row) {
  quotaForm.value = {
    tenantId: row.tenantId, seatLimit: row.seatLimit, mpLimit: row.mpAccountLimit,
    storageGb: row.storageQuotaGb, smsQuota: 1000, apiQuota: 100000
  }
  quotaDialog.value = true
}

async function saveQuota() {
  const { tenantId, ...body } = quotaForm.value
  await put(`/platform/tenants/${tenantId}/quota`, body)
  ElMessage.success('配额已保存并留痕')
  quotaDialog.value = false
}

async function remove(row) {
  await ElMessageBox.confirm(`删除租户 ${row.tenantName}？业务数据将归档且平台留痕。`, '二次确认', { type: 'warning' })
  await del(`/platform/tenants/${row.tenantId}`)
  ElMessage.success('已删除')
  load()
}

function exportCsv() {
  download('/platform/tenants/export', 'tenants.csv')
}

function tag(s) {
  return { ACTIVE: 'success', TRIAL: 'info', OVERDUE: 'warning', SUSPENDED: 'danger', EXPIRED: 'danger' }[s] || 'info'
}

onMounted(load)
</script>
