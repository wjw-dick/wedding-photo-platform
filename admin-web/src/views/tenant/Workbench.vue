<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">工作台</h2>
        <div class="page-sub">{{ today }} · 今日拍摄、待办与异常一屏掌握</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value }}</div>
      </div>
    </div>

    <div class="grid-2 mt16">
      <div class="card">
        <h3 style="margin-top:0">今日拍摄安排</h3>
        <el-table :data="data.todaySchedules || []" border size="small" empty-text="今日暂无拍摄">
          <el-table-column prop="gather_time" label="集合" width="90" />
          <el-table-column prop="customer_name" label="客户" width="110" />
          <el-table-column prop="gather_place" label="地点" show-overflow-tooltip />
          <el-table-column prop="order_no" label="订单号" width="150" />
        </el-table>
      </div>
      <div class="card">
        <h3 style="margin-top:0">门店业绩 Top5</h3>
        <el-table :data="data.storeTop5 || []" border size="small">
          <el-table-column prop="store_name" label="门店" />
          <el-table-column prop="order_count" label="订单数" width="90" />
          <el-table-column prop="amount" label="金额" width="110" />
        </el-table>
      </div>
    </div>

    <div class="card mt16">
      <h3 style="margin-top:0">异常订单</h3>
      <el-table :data="data.exceptions || []" border size="small" empty-text="暂无异常">
        <el-table-column prop="order_no" label="订单号" width="160" />
        <el-table-column prop="customer_name" label="客户" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="abnormal_status" label="异常标记" width="140" />
        <el-table-column prop="updated_at" label="更新时间" width="180" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { get } from '../../api'

const data = ref({})
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
const stats = computed(() => [
  { label: '今日拍摄', value: (data.value.todaySchedules || []).length },
  { label: '待确认订单', value: data.value.pendingOrderConfirm ?? 0 },
  { label: '待派工', value: data.value.pendingDispatch ?? 0 },
  { label: '待选片', value: data.value.pendingSelection ?? 0 },
  { label: '待交付', value: data.value.pendingDelivery ?? 0 },
  { label: '留存期预警', value: data.value.retentionWarnings ?? 0 },
  { label: '待结算渠道', value: data.value.pendingChannelSettle ?? 0 }
])

async function load() { data.value = await get('/tenant/workbench') }
onMounted(load)
</script>
