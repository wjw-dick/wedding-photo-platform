<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">生产交付</h2>
        <div class="page-sub">拍摄派工、修图池、制作外发、交付留存与礼服租赁</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="拍摄任务" name="shoot">
        <el-table :data="shoot" border stripe>
          <el-table-column prop="task_no" label="任务号" width="160" />
          <el-table-column prop="shoot_date" label="日期" width="115" />
          <el-table-column prop="customer_name" label="客户" width="120" />
          <el-table-column prop="gather_time" label="集合" width="90" />
          <el-table-column prop="gather_place" label="地点" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" link type="primary" @click="dispatch(row)">派工</el-button>
              <el-button v-if="row.status === 'DISPATCHED'" link type="success" @click="checkin(row)">打卡</el-button>
              <el-button v-if="row.status === 'CHECKED_IN'" link type="warning" @click="finish(row)">完成拍摄</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="修图池" name="retouch">
        <el-table :data="retouch" border stripe>
          <el-table-column prop="id" label="任务ID" width="90" />
          <el-table-column prop="order_no" label="订单号" width="160" />
          <el-table-column prop="customer_name" label="客户" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="created_at" label="创建时间" width="180" />
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <el-button v-if="!row.editor_id" link type="primary" @click="claim(row)">领取</el-button>
              <el-button v-if="row.status === 'CLAIMED'" link type="success" @click="submitRetouch(row)">提交</el-button>
              <el-button v-if="row.status === 'SUBMITTED'" link type="success" @click="doneRetouch(row)">验收完成</el-button>
              <el-button v-if="row.status === 'SUBMITTED'" link type="warning" @click="rework(row)">退回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="制作单" name="product">
        <el-table :data="products" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="order_no" label="订单号" width="160" />
          <el-table-column prop="supplier_name" label="供应商" width="150" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="tracking_no" label="物流单号" width="150" />
          <el-table-column label="操作" width="300">
            <template #default="{ row }">
              <el-button v-if="!row.supplier_id" link type="primary" @click="assign(row)">分配供应商</el-button>
              <el-button link type="success" @click="productAction(row, 'PRODUCING')">生产中</el-button>
              <el-button link type="primary" @click="productAction(row, 'SHIPPED')">发货</el-button>
              <el-button link type="success" @click="productAction(row, 'DONE')">完成</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="交付单" name="delivery">
        <el-table :data="deliveries" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="order_no" label="订单号" width="160" />
          <el-table-column prop="delivery_type" label="方式" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="retain_end" label="留存截止" width="130" />
          <el-table-column label="操作" width="300">
            <template #default="{ row }">
              <el-button link type="primary" @click="notify(row)">通知客户</el-button>
              <el-button link type="success" @click="confirm(row)">确认交付</el-button>
              <el-button link type="warning" @click="extend(row)">延长留存</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="礼服租赁" name="dress">
        <el-table :data="dresses" border stripe>
          <el-table-column prop="dress_no" label="编号" width="140" />
          <el-table-column prop="dress_name" label="名称" width="160" />
          <el-table-column prop="category" label="品类" width="110" />
          <el-table-column prop="size" label="尺码" width="90" />
          <el-table-column prop="rent_price" label="租金" width="100" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.status === 'AVAILABLE'" link type="primary" @click="rent(row)">租出</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post } from '../../api'

const tab = ref('shoot')
const shoot = ref([])
const retouch = ref([])
const products = ref([])
const deliveries = ref([])
const dresses = ref([])

async function load() {
  const [a, b, c, d, e] = await Promise.all([
    get('/tenant/production/shoot-tasks'),
    get('/tenant/production/retouch/pool'),
    get('/tenant/production/products'),
    get('/tenant/production/deliveries'),
    get('/tenant/production/dresses')
  ])
  shoot.value = a; retouch.value = b; products.value = c; deliveries.value = d; dresses.value = e
}

async function dispatch(row) {
  const { value } = await ElMessageBox.prompt('摄影师 ID（可留空）', '派工', { inputValue: '1' })
  await post(`/tenant/production/shoot-tasks/${row.id}/dispatch`, { photographerId: value || null })
  ElMessage.success('已派工')
  load()
}

async function checkin(row) {
  await post(`/tenant/production/shoot-tasks/${row.id}/checkin`, { longitude: 116.397, latitude: 39.908, address: '门店' })
  ElMessage.success('已打卡')
  load()
}

async function finish(row) {
  await post(`/tenant/production/shoot-tasks/${row.id}/finish`)
  ElMessage.success('拍摄已完成')
  load()
}

async function claim(row) {
  await post(`/tenant/production/retouch/${row.id}/claim`, { editorId: 1 })
  ElMessage.success('已领取')
  load()
}
async function submitRetouch(row) { await post(`/tenant/production/retouch/${row.id}/submit`); ElMessage.success('已提交'); load() }
async function doneRetouch(row) { await post(`/tenant/production/retouch/${row.id}/done`); ElMessage.success('已验收'); load() }
async function rework(row) {
  const { value } = await ElMessageBox.prompt('退回原因', '退回重修')
  await post(`/tenant/production/retouch/${row.id}/rework`, { reason: value })
  load()
}

async function assign(row) {
  const { value } = await ElMessageBox.prompt('供应商 ID', '分配供应商', { inputValue: '1' })
  await post(`/tenant/production/products/${row.id}/assign`, { supplierId: value })
  ElMessage.success('已分配')
  load()
}
async function productAction(row, action) {
  await post(`/tenant/production/products/${row.id}/action`, { action })
  ElMessage.success('已更新')
  load()
}

async function notify(row) { await post(`/tenant/production/deliveries/${row.id}/notify`, { channels: 'SMS,SITE' }); ElMessage.success('已发送通知'); load() }
async function confirm(row) { await post(`/tenant/production/deliveries/${row.id}/confirm`); ElMessage.success('已确认'); load() }
async function extend(row) {
  const { value } = await ElMessageBox.prompt('延长天数', '延长留存', { inputValue: '30' })
  await post(`/tenant/production/deliveries/${row.id}/extend`, { days: value })
  load()
}

async function rent(row) {
  await post(`/tenant/production/dresses/${row.id}/rent`, { orderId: 1, startDate: new Date().toISOString().slice(0, 10), endDate: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10) })
  ElMessage.success('已租出')
  load()
}

onMounted(load)
</script>
