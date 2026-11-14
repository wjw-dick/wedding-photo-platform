<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">供应商协同</h2>
        <div class="page-sub">账号开通、月度对账、资质到期提醒</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="供应商" name="suppliers">
        <el-table :data="suppliers" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="supplier_no" label="编号" width="140" />
          <el-table-column prop="supplier_name" label="名称" min-width="160" />
          <el-table-column label="品类" width="110">
            <template #default="{ row }">{{ dictLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column label="评级" width="90">
            <template #default="{ row }">{{ dictLabel(row.rating) }}</template>
          </el-table-column>
          <el-table-column prop="payment_days" label="账期" width="90" />
          <el-table-column prop="contact_name" label="联系人" width="110" />
          <el-table-column prop="contact_phone" label="电话" width="130" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }"><el-button link type="primary" @click="account(row)">开通账号</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page"
          @current-change="(p) => { page = p; loadSuppliers() }" />
      </el-tab-pane>

      <el-tab-pane label="月度对账" name="statement">
        <div class="toolbar">
          <el-date-picker v-model="period" type="month" value-format="YYYY-MM" placeholder="账期" style="width:160px" />
          <el-button :icon="Search" @click="loadStatement">查询</el-button>
          <span class="muted">应结合供应商端“确认对账”流程，双方数据一致后进入付款。</span>
        </div>
        <el-table :data="statement.items || []" border stripe>
          <el-table-column prop="supplier_name" label="供应商" min-width="160" />
          <el-table-column prop="order_count" label="制作单数" width="110" />
          <el-table-column prop="amount" label="应结金额" width="120" />
          <el-table-column prop="paid_amount" label="已付金额" width="120" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="资质到期" name="expiring">
        <el-table :data="expiring.items || []" border stripe>
          <el-table-column prop="supplier_name" label="供应商" min-width="160" />
          <el-table-column prop="qual_type" label="资质类型" width="150" />
          <el-table-column prop="expire_date" label="到期日" width="130" />
          <el-table-column prop="days_left" label="剩余天数" width="110" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { dictLabel } from '../../dict'

const tab = ref('suppliers')
const suppliers = ref([])
const total = ref(0)
const page = ref(1)
const period = ref(new Date().toISOString().slice(0, 7))
const statement = ref({})
const expiring = ref({})

async function loadSuppliers() {
  const d = await get('/tenant/supplier/suppliers', { page: page.value, size: 20 })
  suppliers.value = d.records; total.value = d.total
}
async function loadStatement() { statement.value = await get('/tenant/supplier/statement', { period: period.value }) }
async function loadExpiring() { expiring.value = await get('/tenant/supplier/qualifications/expiring') }

async function account(row) {
  const { value: username } = await ElMessageBox.prompt('登录账号', '开通供应商账号', { inputValue: `supplier${row.id}` })
  const { value: password } = await ElMessageBox.prompt('初始密码', '开通供应商账号', { inputValue: 'Sp@123456' })
  await post(`/tenant/supplier/suppliers/${row.id}/account`, { username, password })
  ElMessage.success('账号已创建')
}

function load() { loadSuppliers(); loadStatement(); loadExpiring() }
onMounted(load)
</script>
