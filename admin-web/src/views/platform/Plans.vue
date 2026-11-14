<template>
  <div class="page">
    <div class="page-header">
      <div><h2 class="page-title">套餐与配额</h2><div class="page-sub">席位、公众号、存储、短信、API 五类配额</div></div>
      <el-button type="primary" :icon="Plus" @click="open()">新增套餐</el-button>
    </div>
    <div class="card">
      <el-table :data="rows" border stripe>
        <el-table-column prop="plan_id" label="ID" width="70" />
        <el-table-column prop="plan_name" label="套餐名称" />
        <el-table-column prop="price" label="价格" width="110" />
        <el-table-column label="周期" width="90">
          <template #default="{ row }">{{ cycleMap[row.billing_cycle] || dictLabel(row.billing_cycle) }}</template>
        </el-table-column>
        <el-table-column prop="seat_limit" label="席位" width="80" />
        <el-table-column prop="mp_account_limit" label="公众号" width="90" />
        <el-table-column prop="storage_quota_gb" label="存储GB" width="90" />
        <el-table-column prop="sms_quota" label="短信" width="90" />
        <el-table-column prop="api_quota" label="API" width="110" />
        <el-table-column prop="trial_days" label="试用天数" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ dictLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-dialog v-model="dialog" :title="form.plan_id ? '编辑套餐' : '新增套餐'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="名称"><el-input v-model="form.plan_name" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" /></el-form-item>
        <el-form-item label="计费周期">
          <el-select v-model="form.billing_cycle"><el-option label="年" value="YEAR" /><el-option label="月" value="MONTH" /></el-select>
        </el-form-item>
        <el-form-item label="席位上限"><el-input-number v-model="form.seat_limit" :min="1" /></el-form-item>
        <el-form-item label="公众号上限"><el-input-number v-model="form.mp_account_limit" :min="0" /></el-form-item>
        <el-form-item label="存储 GB"><el-input-number v-model="form.storage_quota_gb" :min="1" /></el-form-item>
        <el-form-item label="短信条数"><el-input-number v-model="form.sms_quota" :min="0" /></el-form-item>
        <el-form-item label="API 调用量"><el-input-number v-model="form.api_quota" :min="0" /></el-form-item>
        <el-form-item label="试用天数"><el-input-number v-model="form.trial_days" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" active-value="ACTIVE" inactive-value="DISABLED" /></el-form-item>
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
import { del, get, post, put } from '../../api'
import { dictLabel } from '../../dict'

const cycleMap = { YEAR: '年付', MONTH: '月付', QUARTER: '季付' }

const rows = ref([])
const dialog = ref(false)
const form = ref({})

async function load() { rows.value = await get('/platform/plans') }
function open(row) {
  form.value = row ? { ...row } : { billing_cycle: 'YEAR', status: 'ACTIVE', price: 0, seat_limit: 5, mp_account_limit: 1, storage_quota_gb: 100, sms_quota: 1000, api_quota: 100000, trial_days: 14 }
  dialog.value = true
}
async function save() {
  if (form.value.plan_id) await put(`/platform/plans/${form.value.plan_id}`, form.value)
  else await post('/platform/plans', form.value)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}
async function remove(row) {
  await ElMessageBox.confirm('删除套餐？已开通租户不受影响。', '确认')
  await del(`/platform/plans/${row.plan_id}`)
  load()
}
onMounted(load)
</script>
