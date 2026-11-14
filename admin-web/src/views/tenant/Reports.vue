<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">统计报表</h2>
        <div class="page-sub">9 类经营指标 + 异步导出任务（RabbitMQ 消费，对象存储下载）</div>
      </div>
      <div class="flex">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" style="width:260px" />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="s in cards" :key="s.label" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value }}</div>
      </div>
    </div>

    <div class="grid-2 mt16">
      <div class="card"><h3 style="margin-top:0">收入趋势</h3><div ref="revenueEl" class="chart" /></div>
      <div class="card"><h3 style="margin-top:0">门店业绩</h3><div ref="storeEl" class="chart" /></div>
    </div>

    <div class="grid-2 mt16">
      <div class="card">
        <h3 style="margin-top:0">渠道贡献</h3>
        <el-table :data="overview.channelContribution || []" border size="small">
          <el-table-column prop="channel_name" label="渠道" />
          <el-table-column prop="orders" label="订单" width="90" />
          <el-table-column prop="amount" label="金额" width="110" />
          <el-table-column prop="commission" label="佣金" width="110" />
        </el-table>
      </div>
      <div class="card">
        <h3 style="margin-top:0">员工业绩</h3>
        <el-table :data="overview.employeePerformance || []" border size="small">
          <el-table-column prop="real_name" label="员工" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ dictLabel(row.role_type) }}</template>
          </el-table-column>
          <el-table-column prop="items" label="提成笔数" width="100" />
          <el-table-column prop="commission" label="提成" width="110" />
        </el-table>
      </div>
    </div>

    <div class="card mt16">
      <div class="page-header" style="margin-bottom:12px">
        <h3 style="margin:0">导出任务</h3>
        <div class="flex">
          <el-select v-model="reportType" style="width:150px">
            <el-option label="订单明细" value="orders" />
            <el-option label="收入汇总" value="revenue" />
            <el-option label="经营汇总" value="summary" />
          </el-select>
          <el-button type="primary" @click="createTask">创建导出</el-button>
        </div>
      </div>
      <el-table :data="tasks" border size="small">
        <el-table-column prop="task_no" label="任务号" width="170" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ reportTypeMap[row.report_type] || dictLabel(row.report_type) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ dictLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="error_msg" label="错误" min-width="150" show-overflow-tooltip />
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DONE'" link type="primary" @click="downloadTask(row)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { dictLabel } from '../../dict'

const reportTypeMap = { orders: '订单明细', revenue: '收入汇总', summary: '经营汇总' }

const range = ref([new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10), new Date().toISOString().slice(0, 10)])
const overview = ref({})
const tasks = ref([])
const reportType = ref('orders')
const revenueEl = ref()
const storeEl = ref()
let revenueChart, storeChart

const cards = computed(() => {
  const o = overview.value.orders || {}
  const s = overview.value.schedule || {}
  const d = overview.value.delivery || {}
  return [
    { label: '订单数', value: o.orderCount ?? 0 },
    { label: '订单总额', value: o.totalAmount ?? 0 },
    { label: '已收金额', value: o.paidAmount ?? 0 },
    { label: '退款金额', value: o.refundAmount ?? 0 },
    { label: '退款率', value: (overview.value.refundRate ?? 0) + '%' },
    { label: '档期利用率', value: (s.utilizationRate ?? 0) + '%' },
    { label: '交付单数', value: d.delivered ?? 0 },
    { label: '平均交付天数', value: d.avgDays ?? 0 }
  ]
})

async function load() {
  const [from, to] = range.value || []
  overview.value = await get('/tenant/reports/overview', { from, to })
  const t = await get('/tenant/reports/tasks', { page: 1, size: 20 })
  tasks.value = t.records
  await nextTick()
  renderCharts()
}

function renderCharts() {
  if (revenueEl.value) {
    revenueChart = revenueChart || echarts.init(revenueEl.value)
    revenueChart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: (overview.value.revenueByDay || []).map((x) => x.day) },
      yAxis: { type: 'value' },
      series: [{ type: 'line', smooth: true, areaStyle: {}, data: (overview.value.revenueByDay || []).map((x) => x.paid) }]
    })
  }
  if (storeEl.value) {
    storeChart = storeChart || echarts.init(storeEl.value)
    storeChart.setOption({
      tooltip: {},
      xAxis: { type: 'category', data: (overview.value.storePerformance || []).map((x) => x.store_name) },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: (overview.value.storePerformance || []).map((x) => x.amount) }]
    })
  }
}

async function createTask() {
  const [from, to] = range.value || []
  const d = await post('/tenant/reports/tasks', { reportType: reportType.value, params: { from, to } })
  ElMessage.success(`导出任务 ${d.taskNo} 已创建`)
  setTimeout(load, 1500)
}

async function downloadTask(row) {
  const d = await get(`/tenant/reports/tasks/${row.id}/download`)
  if (d.url) window.open(d.url, '_blank')
  else ElMessage.warning('文件尚未生成')
}

onMounted(load)
</script>
