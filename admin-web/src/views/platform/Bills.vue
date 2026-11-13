<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">账单与计费</h2>
        <div class="page-sub">按账期生成席位账单，差异入账，支持已付标记与账期导出</div>
      </div>
      <div class="flex">
        <el-button :icon="Download" @click="exportCsv">导出账期</el-button>
        <el-button type="primary" :icon="Money" :loading="generating" @click="generate">生成账单</el-button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model.number="query.tenantId" placeholder="租户 ID" clearable style="width:140px" />
        <el-date-picker v-model="query.period" type="month" value-format="YYYY-MM" placeholder="账期" style="width:150px" />
        <el-select v-model="query.status" clearable placeholder="状态" style="width:150px">
          <el-option v-for="s in ['UNPAID', 'PAID', 'OVERDUE']" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
      <el-table :data="rows" border stripe>
        <el-table-column prop="billId" label="账单号" width="90" />
        <el-table-column prop="tenantId" label="租户ID" width="90" />
        <el-table-column prop="billPeriod" label="账期" width="110" />
        <el-table-column prop="itemName" label="计费项" min-width="140" />
        <el-table-column prop="quantity" label="席位" width="80" />
        <el-table-column prop="unitPrice" label="单价" width="100" />
        <el-table-column prop="amount" label="金额" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status === 'PAID' ? 'success' : row.status === 'OVERDUE' ? 'danger' : 'warning'" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="paidAt" label="支付时间" width="170" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'PAID'" link type="primary" @click="paid(row)">标记已付</el-button>
            <span v-else class="muted">已完成</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>

    <div class="card mt16">
      <h3 style="margin-top:0">计费规则</h3>
      <p class="muted">套餐基础费用 + 超出席位按单价补差；同账期重复生成自动跳过，保证幂等。账单状态：UNPAID / PAID / OVERDUE。</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Money, Search } from '@element-plus/icons-vue'
import { download, get, post } from '../../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const generating = ref(false)
const query = reactive({ tenantId: null, period: '', status: '' })

async function load() {
  const d = await get('/platform/bills', {
    tenantId: query.tenantId || undefined,
    period: query.period || undefined,
    status: query.status || undefined,
    page: page.value,
    size: size.value
  })
  rows.value = d.records
  total.value = d.total
}

async function generate() {
  const { value } = await ElMessageBox.prompt('输入账期（YYYY-MM），留空使用当前月', '生成账单', {
    inputPlaceholder: new Date().toISOString().slice(0, 7),
    inputValue: query.period || ''
  })
  generating.value = true
  try {
    const d = await post('/platform/bills/generate', { period: value || undefined })
    ElMessage.success(`账期 ${d.period} 生成 ${d.created} 条账单`)
    query.period = d.period
    load()
  } finally {
    generating.value = false
  }
}

async function paid(row) {
  await ElMessageBox.confirm(`确认账单 #${row.billId} 已收款？`, '确认', { type: 'warning' })
  await post(`/platform/bills/${row.billId}/paid`)
  ElMessage.success('已标记为已付')
  load()
}

function exportCsv() {
  download(`/platform/bills/export${query.period ? `?period=${query.period}` : ''}`, `bills-${query.period || 'all'}.csv`)
}

onMounted(load)
</script>
