<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">订单中心</h2>
        <div class="page-sub">创建、锁档、收款、改期、取消、合同与发票全流程</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建订单</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="订单号 / 客户 / 电话" clearable style="width:220px" @keyup.enter="load" />
        <el-select v-model="query.status" clearable placeholder="订单状态" style="width:150px">
          <el-option v-for="s in statuses" :key="s" :label="dictLabel(s)" :value="s" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="order_no" label="订单号" width="165" />
        <el-table-column prop="customer_name" label="客户" width="110" />
        <el-table-column prop="customer_phone" label="电话" width="125" />
        <el-table-column prop="package_name" label="套系" min-width="130" show-overflow-tooltip />
        <el-table-column prop="store_name" label="门店" width="120" />
        <el-table-column prop="shoot_date" label="拍摄日期" width="115" />
        <el-table-column prop="total_amount" label="总额" width="100" />
        <el-table-column prop="paid_amount" label="已付" width="100" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }"><el-tag size="small" :type="tag(row.status)">{{ dictLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openPay(row)">收款</el-button>
            <el-button link type="warning" @click="openReschedule(row)">改期</el-button>
            <el-button link type="danger" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt16" layout="total, sizes, prev, pager, next" :total="total" :page-size="size" :current-page="page"
        :page-sizes="[10, 20, 50]" @current-change="(p) => { page = p; load() }" @size-change="(s) => { size = s; load() }" />
    </div>

    <el-drawer v-model="detailDrawer" title="订单详情" size="720px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.order.order_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ dictLabel(detail.order.status) }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.order.customer_name }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detail.order.customer_phone }}</el-descriptions-item>
          <el-descriptions-item label="套系">{{ detail.order.package_name }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ detail.order.store_name }}</el-descriptions-item>
          <el-descriptions-item label="总额">{{ detail.order.total_amount }}</el-descriptions-item>
          <el-descriptions-item label="已付">{{ detail.order.paid_amount }}</el-descriptions-item>
          <el-descriptions-item label="拍摄日期">{{ detail.order.shoot_date }}</el-descriptions-item>
          <el-descriptions-item label="渠道">{{ detail.order.channel_id || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div class="flex mt16">
          <el-button type="primary" size="small" @click="contract">生成合同 PDF</el-button>
          <el-button size="small" @click="refund">申请退款</el-button>
          <el-button size="small" @click="transition">推进状态</el-button>
        </div>
        <h4>订单明细</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="item_name" label="项目" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="amount" label="金额" width="100" />
        </el-table>
        <h4>状态流转</h4>
        <el-timeline>
          <el-timeline-item v-for="l in detail.statusLogs" :key="l.id" :timestamp="l.created_at">
            {{ dictLabel(l.from_status) || '—' }} → {{ dictLabel(l.to_status) }} {{ l.remark || '' }}
          </el-timeline-item>
        </el-timeline>
        <h4>支付记录</h4>
        <el-table :data="detail.payments" border size="small">
          <el-table-column prop="pay_no" label="支付号" min-width="140" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ dictLabel(row.pay_type) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ dictLabel(row.pay_status) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="payDialog" title="登记收款" width="440px">
      <el-form label-width="90px">
        <el-form-item label="金额"><el-input-number v-model="payForm.amount" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="payForm.payType" style="width:100%">
            <el-option label="定金" value="DEPOSIT" /><el-option label="尾款" value="BALANCE" /><el-option label="全款" value="FULL" />
          </el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="payForm.channel" style="width:100%">
            <el-option label="线下" value="OFFLINE" /><el-option label="微信" value="WECHAT" /><el-option label="支付宝" value="ALIPAY" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" @click="pay">确认收款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createDialog" title="新建订单" width="640px">
      <el-form label-width="100px">
        <el-form-item label="门店 ID"><el-input-number v-model="createForm.storeId" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="客户姓名"><el-input v-model="createForm.customerName" /></el-form-item>
        <el-form-item label="客户电话"><el-input v-model="createForm.customerPhone" /></el-form-item>
        <el-form-item label="套系 ID"><el-input-number v-model="createForm.packageId" :min="1" style="width:100%" /></el-form-item>
        <el-form-item label="拍摄日期"><el-date-picker v-model="createForm.shootDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="拍摄时段">
          <div class="flex">
            <el-time-select v-model="createForm.shootStart" start="06:00" step="00:30" end="20:00" placeholder="开始" />
            <el-time-select v-model="createForm.shootEnd" start="06:00" step="00:30" end="23:00" placeholder="结束" />
          </div>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="createForm.source" style="width:100%">
            <el-option label="到店" value="STORE" /><el-option label="官网" value="SITE" /><el-option label="渠道" value="CHANNEL" /><el-option label="电话" value="PHONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="createForm.remark" type="textarea" :rows="2" /></el-form-item>
        <el-alert type="info" :closable="false" show-icon title="创建时按套系资源自动锁档 30 分钟，未付定金由定时任务释放。" />
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建并锁档</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rescheduleDialog" title="订单改期" width="440px">
      <el-form label-width="90px">
        <el-form-item label="新日期"><el-date-picker v-model="rescheduleForm.shootDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="rescheduleForm.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rescheduleDialog = false">取消</el-button>
        <el-button type="primary" @click="doReschedule">确认改期</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { dictLabel } from '../../dict'

const statuses = ['LEAD', 'ARRIVED', 'CONFIRMED', 'SHOOTING', 'SELECTING', 'RETOUCHING', 'PRODUCING', 'DELIVERED', 'COMPLETED', 'CANCELLED']
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const query = reactive({ keyword: '', status: '' })
const detailDrawer = ref(false)
const detail = ref(null)
const payDialog = ref(false)
const payForm = reactive({ orderNo: '', amount: 0, payType: 'DEPOSIT', channel: 'OFFLINE' })
const createDialog = ref(false)
const createForm = reactive({})
const saving = ref(false)
const rescheduleDialog = ref(false)
const rescheduleForm = reactive({ id: null, shootDate: '', reason: '' })
const current = ref(null)

async function load() {
  loading.value = true
  try {
    const d = await get('/tenant/orders', { keyword: query.keyword || undefined, status: query.status || undefined, page: page.value, size: size.value })
    rows.value = d.records
    total.value = d.total
  } finally { loading.value = false }
}

async function openDetail(row) {
  current.value = row
  detail.value = await get(`/tenant/orders/${row.id}`)
  detailDrawer.value = true
}

function openPay(row) {
  current.value = row
  Object.assign(payForm, { orderNo: row.order_no, amount: Number(row.balance_amount || 0), payType: 'BALANCE', channel: 'OFFLINE' })
  payDialog.value = true
}

async function pay() {
  await post(`/tenant/orders/${payForm.orderNo}/pay`, { amount: payForm.amount, payType: payForm.payType, channel: payForm.channel })
  ElMessage.success('收款已登记')
  payDialog.value = false
  load()
}

function openCreate() {
  Object.assign(createForm, { storeId: 1, customerName: '', customerPhone: '', packageId: 1, shootDate: '', shootStart: '09:00', shootEnd: '12:00', source: 'STORE', remark: '' })
  createDialog.value = true
}

async function create() {
  saving.value = true
  try {
    const d = await post('/tenant/orders', { ...createForm, resourceIds: [], addons: [] })
    ElMessage.success(`订单 ${d.orderNo || d.order?.order_no || ''} 已创建`)
    createDialog.value = false
    load()
  } finally { saving.value = false }
}

function openReschedule(row) {
  current.value = row
  Object.assign(rescheduleForm, { id: row.id, shootDate: row.shoot_date, reason: '' })
  rescheduleDialog.value = true
}

async function doReschedule() {
  await post(`/tenant/order-extra/orders/${rescheduleForm.id}/reschedule`, { shootDate: rescheduleForm.shootDate, reason: rescheduleForm.reason })
  ElMessage.success('改期成功')
  rescheduleDialog.value = false
  load()
}

async function cancel(row) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', `取消订单 ${row.order_no}`, { type: 'warning' })
  await post(`/tenant/order-extra/orders/${row.id}/cancel`, { reason: value })
  ElMessage.success('订单已取消')
  load()
}

async function transition() {
  const order = detail.value.order
  const { value } = await ElMessageBox.prompt('目标状态（LEAD/ARRIVED/CONFIRMED/...）', '推进订单状态', { inputValue: order.status })
  await post(`/tenant/orders/${order.id}/transition`, { to: value, remark: '后台推进' })
  ElMessage.success('状态已更新')
  openDetail({ id: order.id })
  load()
}

async function refund() {
  const { value } = await ElMessageBox.prompt('退款金额', '申请退款', { inputValue: '0' })
  await post(`/tenant/orders/${detail.value.order.id}/refund`, { amount: value, reason: '客户申请' })
  ElMessage.success('退款申请已提交，待审核')
}

async function contract() {
  const d = await post(`/tenant/order-extra/orders/${detail.value.order.id}/contract`, {})
  ElMessageBox.alert(`合同已生成：${d.pdfUrl}`, '合同 PDF')
}

function tag(s) {
  if (['COMPLETED', 'DELIVERED'].includes(s)) return 'success'
  if (s === 'CANCELLED') return 'info'
  if (['LEAD', 'ARRIVED'].includes(s)) return 'warning'
  return ''
}

onMounted(load)
</script>
