<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">渠道分销</h2>
        <div class="page-sub">报单归属保护、审核转线索、佣金与结算、两级下级</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="渠道商" name="channels">
        <el-table :data="channels" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="channel_no" label="编号" width="140" />
          <el-table-column prop="channel_name" label="名称" min-width="150" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ dictLabel(row.channel_type) }}</template>
          </el-table-column>
          <el-table-column prop="contact_name" label="联系人" width="110" />
          <el-table-column prop="contact_phone" label="电话" width="130" />
          <el-table-column prop="commission_rate" label="佣金比例" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="report(row)">代报单</el-button>
              <el-button link type="primary" @click="account(row)">开账号</el-button>
              <el-button link type="primary" @click="sub(row)">下级</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="channelTotal" :page-size="20" :current-page="channelPage"
          @current-change="(p) => { channelPage = p; loadChannels() }" />
      </el-tab-pane>

      <el-tab-pane label="渠道报单" name="reports">
        <el-table :data="reports" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="report_no" label="报单号" width="160" />
          <el-table-column prop="channel_name" label="渠道" width="140" />
          <el-table-column prop="customer_name" label="客户" width="110" />
          <el-table-column prop="phone" label="电话" width="130" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="created_at" label="报单时间" width="170" />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING'" link type="success" @click="audit(row, true)">通过</el-button>
              <el-button v-if="row.status === 'PENDING'" link type="danger" @click="audit(row, false)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="reportTotal" :page-size="20" :current-page="reportPage"
          @current-change="(p) => { reportPage = p; loadReports() }" />
      </el-tab-pane>

      <el-tab-pane label="佣金与结算" name="settle">
        <div class="toolbar">
          <el-input v-model.number="settleForm.targetId" placeholder="渠道 ID" style="width:120px" />
          <el-date-picker v-model="settleForm.period" type="month" value-format="YYYY-MM" placeholder="结算周期" style="width:150px" />
          <el-button type="primary" @click="generateSettlement">生成结算单</el-button>
        </div>
        <el-table :data="settlements" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="settlement_no" label="结算单号" width="170" />
          <el-table-column prop="target_name" label="对象" width="150" />
          <el-table-column prop="period" label="周期" width="110" />
          <el-table-column prop="amount" label="金额" width="110" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'PAID'" link type="primary" @click="paySettlement(row)">打款</el-button>
            </template>
          </el-table-column>
        </el-table>
        <h4>佣金明细</h4>
        <el-table :data="commissions" border size="small">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="order_no" label="订单号" width="160" />
          <el-table-column prop="channel_name" label="渠道" width="140" />
          <el-table-column prop="amount" label="佣金" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="created_at" label="时间" width="170" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { dictLabel } from '../../dict'

const tab = ref('channels')
const channels = ref([])
const channelTotal = ref(0)
const channelPage = ref(1)
const reports = ref([])
const reportTotal = ref(0)
const reportPage = ref(1)
const settlements = ref([])
const commissions = ref([])
const settleForm = reactive({ targetType: 'CHANNEL', targetId: null, period: new Date().toISOString().slice(0, 7) })

async function loadChannels() {
  const d = await get('/tenant/channel/channels', { page: channelPage.value, size: 20 })
  channels.value = d.records; channelTotal.value = d.total
}
async function loadReports() {
  const d = await get('/tenant/channel/reports', { page: reportPage.value, size: 20 })
  reports.value = d.records; reportTotal.value = d.total
}
async function loadSettle() {
  settlements.value = await get('/tenant/channel/settlements', { targetType: 'CHANNEL' })
  const c = await get('/tenant/channel/commissions', { page: 1, size: 50 })
  commissions.value = c.records
}

async function report(row) {
  const { value: name } = await ElMessageBox.prompt('客户姓名', '渠道代报单')
  const { value: phone } = await ElMessageBox.prompt('客户电话', '渠道代报单')
  await post(`/tenant/channel/channels/${row.id}/report`, { customerName: name, phone, remark: '后台代报' })
  ElMessage.success('报单已提交')
  loadReports()
}
async function account(row) {
  const { value: username } = await ElMessageBox.prompt('登录账号', '开通渠道账号', { inputValue: `channel${row.id}` })
  const { value: password } = await ElMessageBox.prompt('初始密码', '开通渠道账号', { inputValue: 'Ch@123456' })
  await post(`/tenant/channel/channels/${row.id}/account`, { username, password })
  ElMessage.success('账号已创建')
}
async function sub(row) {
  const list = await get(`/tenant/channel/channels/${row.id}/sub`)
  ElMessageBox.alert(list.length ? list.map((x) => `${x.channel_name} · ${x.commission_rate}`).join('\n') : '暂无下级渠道', '下级渠道')
}
async function audit(row, approve) {
  await post(`/tenant/channel/reports/${row.id}/audit`, { approve, remark: approve ? '审核通过' : '信息不完整' })
  ElMessage.success('已处理')
  loadReports()
}
async function generateSettlement() {
  await post('/tenant/channel/settlements', settleForm)
  ElMessage.success('结算单已生成')
  loadSettle()
}
async function paySettlement(row) {
  await ElMessageBox.confirm(`确认结算单 ${row.settlement_no} 已打款？`, '确认')
  await post(`/tenant/channel/settlements/${row.id}/paid`)
  loadSettle()
}

onMounted(() => { loadChannels(); loadReports(); loadSettle() })
</script>
