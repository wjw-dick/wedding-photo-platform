<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">会员与线索</h2>
        <div class="page-sub">线索跟进、分配、转订单；会员积分、储值、优惠券</div>
      </div>
      <el-button v-if="tab === 'leads'" type="primary" :icon="Plus" @click="openLead">新建线索</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="线索池" name="leads">
        <div class="toolbar">
          <el-input v-model="leadQuery.keyword" placeholder="姓名 / 电话" clearable style="width:220px" @keyup.enter="loadLeads" />
          <el-select v-model="leadQuery.status" clearable placeholder="状态" style="width:150px">
            <el-option v-for="s in leadStatuses" :key="s" :label="dictLabel(s)" :value="s" />
          </el-select>
          <el-button :icon="Search" @click="loadLeads">查询</el-button>
          <el-button :icon="Bell" @click="reminders">跟进提醒</el-button>
        </div>
        <el-table :data="leads" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="customer_name" label="客户" width="110" />
          <el-table-column prop="phone" label="电话" width="130" />
          <el-table-column label="来源" width="110">
            <template #default="{ row }">{{ dictLabel(row.source) }}</template>
          </el-table-column>
          <el-table-column prop="intent_package" label="意向套系" width="130" show-overflow-tooltip />
          <el-table-column prop="owner_name" label="跟进人" width="110" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="next_follow_at" label="下次跟进" width="165" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="follow(row)">跟进</el-button>
              <el-button link type="primary" @click="assign(row)">分配</el-button>
              <el-button link type="success" @click="convert(row)">转订单</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="leadTotal" :page-size="20" :current-page="leadPage"
          @current-change="(p) => { leadPage = p; loadLeads() }" />
      </el-tab-pane>

      <el-tab-pane label="会员" name="members">
        <div class="toolbar">
          <el-input v-model="memberQuery.keyword" placeholder="姓名 / 手机 / 会员号" clearable style="width:240px" @keyup.enter="loadMembers" />
          <el-button :icon="Search" @click="loadMembers">查询</el-button>
        </div>
        <el-table :data="members" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="member_no" label="会员号" width="150" />
          <el-table-column prop="real_name" label="姓名" width="110" />
          <el-table-column prop="phone" label="手机" width="130" />
          <el-table-column prop="level_name" label="等级" width="110" />
          <el-table-column prop="points" label="积分" width="90" />
          <el-table-column prop="balance" label="储值" width="100" />
          <el-table-column prop="created_at" label="注册时间" width="170" />
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="memberDetail(row)">详情</el-button>
              <el-button link type="success" @click="points(row)">调积分</el-button>
              <el-button link type="warning" @click="balance(row)">调储值</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="memberTotal" :page-size="20" :current-page="memberPage"
          @current-change="(p) => { memberPage = p; loadMembers() }" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="leadDialog" title="新建线索" width="560px">
      <el-form label-width="100px">
        <el-form-item label="客户姓名"><el-input v-model="leadForm.customerName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="leadForm.phone" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="leadForm.source" style="width:100%">
            <el-option label="官网" value="SITE" /><el-option label="公众号" value="MP" /><el-option label="到店" value="STORE" />
            <el-option label="渠道" value="CHANNEL" /><el-option label="电话" value="PHONE" /><el-option label="小程序" value="MINI" />
          </el-select>
        </el-form-item>
        <el-form-item label="意向套系"><el-input v-model="leadForm.intentPackage" /></el-form-item>
        <el-form-item label="预算"><el-input-number v-model="leadForm.budget" :min="0" style="width:100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="leadForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="leadDialog = false">取消</el-button>
        <el-button type="primary" @click="saveLead">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberDrawer" title="会员详情" width="600px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="会员号">{{ detail.member?.member_no }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.member?.real_name }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ detail.member?.phone }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ detail.member?.level_name }}</el-descriptions-item>
          <el-descriptions-item label="积分">{{ detail.member?.points }}</el-descriptions-item>
          <el-descriptions-item label="储值">{{ detail.member?.balance }}</el-descriptions-item>
        </el-descriptions>
        <h4>订单</h4>
        <el-table :data="detail.orders || []" border size="small">
          <el-table-column prop="order_no" label="订单号" /><el-table-column prop="total_amount" label="金额" width="100" />
        </el-table>
        <h4>最近优惠券</h4>
        <el-table :data="detail.coupons || []" border size="small">
          <el-table-column prop="coupon_name" label="券" /><el-table-column label="状态" width="100">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Plus, Search } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { dictLabel } from '../../dict'

const leadStatuses = ['NEW', 'FOLLOWING', 'VISITED', 'CONVERTED', 'INVALID']
const tab = ref('leads')
const leads = ref([])
const leadTotal = ref(0)
const leadPage = ref(1)
const leadQuery = reactive({ keyword: '', status: '' })
const members = ref([])
const memberTotal = ref(0)
const memberPage = ref(1)
const memberQuery = reactive({ keyword: '' })
const leadDialog = ref(false)
const leadForm = reactive({})
const memberDrawer = ref(false)
const detail = ref(null)

async function loadLeads() {
  const d = await get('/tenant/crm/leads', { keyword: leadQuery.keyword || undefined, status: leadQuery.status || undefined, page: leadPage.value, size: 20 })
  leads.value = d.records; leadTotal.value = d.total
}
async function loadMembers() {
  const d = await get('/tenant/crm/members', { keyword: memberQuery.keyword || undefined, page: memberPage.value, size: 20 })
  members.value = d.records; memberTotal.value = d.total
}

function openLead() {
  Object.assign(leadForm, { customerName: '', phone: '', source: 'STORE', intentPackage: '', budget: 0, remark: '' })
  leadDialog.value = true
}
async function saveLead() {
  await post('/tenant/crm/leads', leadForm)
  ElMessage.success('线索已创建')
  leadDialog.value = false
  loadLeads()
}
async function follow(row) {
  const { value } = await ElMessageBox.prompt('跟进内容', `跟进 ${row.customer_name}`, { inputType: 'textarea' })
  await post(`/tenant/crm/leads/${row.id}/follow`, { followType: 'PHONE', content: value })
  ElMessage.success('已记录跟进')
  loadLeads()
}
async function assign(row) {
  const { value } = await ElMessageBox.prompt('跟进人（员工 ID）', '分配线索')
  await post(`/tenant/crm/leads/${row.id}/assign`, { ownerId: value })
  ElMessage.success('已分配')
  loadLeads()
}
async function convert(row) {
  const { value } = await ElMessageBox.prompt('套系 ID', '转为订单')
  await post(`/tenant/crm/leads/${row.id}/convert`, { packageId: value, amount: row.budget || 0 })
  ElMessage.success('已转为订单')
  loadLeads()
}
async function reminders() {
  const list = await get('/tenant/crm/leads/reminders')
  ElMessageBox.alert(list.length ? list.map((x) => `${x.customer_name} · ${x.next_follow_at}`).join('\n') : '暂无待跟进提醒', '跟进提醒')
}

async function memberDetail(row) {
  detail.value = await get(`/tenant/crm/members/${row.id}`)
  memberDrawer.value = true
}
async function points(row) {
  const { value } = await ElMessageBox.prompt('调整积分（可为负）', `会员 ${row.real_name}`, { inputValue: '0' })
  await post(`/tenant/crm/members/${row.id}/points`, { points: value, reason: '后台调整' })
  ElMessage.success('积分已调整')
  loadMembers()
}
async function balance(row) {
  const { value } = await ElMessageBox.prompt('调整储值金额（可为负）', `会员 ${row.real_name}`, { inputValue: '0' })
  await post(`/tenant/crm/members/${row.id}/balance`, { amount: value, changeType: 'ADJUST', remark: '后台调整' })
  ElMessage.success('储值已调整')
  loadMembers()
}

onMounted(() => { loadLeads(); loadMembers() })
</script>
