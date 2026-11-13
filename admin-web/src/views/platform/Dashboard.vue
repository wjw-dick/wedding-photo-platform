<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">平台看板</h2>
        <div class="page-sub">租户规模、试用转化、到期预警与收入趋势</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value }}</div>
      </div>
    </div>
    <div class="card mt16">
      <h3 style="margin-top:0">租户状态分布</h3>
      <div ref="chartEl" class="chart" />
    </div>
    <div class="card mt16">
      <h3 style="margin-top:0">最近平台操作</h3>
      <el-table :data="expiring" border>
        <el-table-column prop="operator_name" label="操作人" width="140" />
        <el-table-column prop="action" label="动作" />
        <el-table-column prop="target_tenant_id" label="租户ID" width="100" />
        <el-table-column prop="result" label="结果" width="100" />
        <el-table-column prop="created_at" label="时间" width="180" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { get } from '../../api'

const stats = ref([])
const expiring = ref([])
const chartEl = ref()
let chart

async function load() {
  const d = await get('/platform/dashboard')
  stats.value = [
    { label: '租户总数', value: d.tenantTotal ?? 0 },
    { label: '正常租户', value: d.activeTenants ?? 0 },
    { label: '试用租户', value: d.trialTenants ?? 0 },
    { label: '30 天内到期', value: d.expiringIn30Days ?? 0 },
    { label: '试用转化率', value: (d.trialConversion ?? 0) + '%' }
  ]
  expiring.value = d.recentAudits || []
  render({
    ACTIVE: d.activeTenants || 0,
    TRIAL: d.trialTenants || 0,
    OVERDUE: d.overdueTenants || 0
  })
}

function render(dist) {
  if (!chartEl.value) return
  chart = chart || echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      data: Object.entries(dist).map(([name, value]) => ({ name, value }))
    }]
  })
}

onMounted(load)
</script>
