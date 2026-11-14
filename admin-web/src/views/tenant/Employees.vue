<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">员工与业绩</h2>
        <div class="page-sub">排班、提成规则、订单提成、门店与员工业绩</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="员工档案" name="employees">
        <el-table :data="employees" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="employee_no" label="工号" width="120" />
          <el-table-column prop="real_name" label="姓名" width="110" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">{{ dictLabel(row.role_type) }}</template>
          </el-table-column>
          <el-table-column prop="phone" label="电话" width="130" />
          <el-table-column prop="store_name" label="门店" width="140" />
          <el-table-column prop="hire_date" label="入职" width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page"
          @current-change="(p) => { page = p; loadEmployees() }" />
      </el-tab-pane>

      <el-tab-pane label="排班" name="schedules">
        <div class="toolbar">
          <el-date-picker v-model="scheduleDate" type="date" value-format="YYYY-MM-DD" placeholder="日期" style="width:160px" />
          <el-button type="primary" @click="addSchedule">新增排班</el-button>
        </div>
        <el-table :data="schedules" border stripe>
          <el-table-column prop="work_date" label="日期" width="120" />
          <el-table-column prop="real_name" label="员工" width="120" />
          <el-table-column prop="store_name" label="门店" width="150" />
          <el-table-column label="班次" width="120">
            <template #default="{ row }">{{ dictLabel(row.shift) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="提成规则" name="rules">
        <el-table :data="rules" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="rule_name" label="规则" width="160" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ ruleType(row.rule_type) }}</template>
          </el-table-column>
          <el-table-column label="基数" width="140">
            <template #default="{ row }">{{ dictLabel(row.base_type) }}</template>
          </el-table-column>
          <el-table-column prop="rate" label="比例" width="100" />
          <el-table-column prop="fixed_amount" label="固定金额" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="提成明细" name="commissions">
        <el-table :data="commissions" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="order_no" label="订单号" width="160" />
          <el-table-column prop="employee_name" label="员工" width="120" />
          <el-table-column prop="rule_name" label="规则" width="150" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ dictLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="created_at" label="时间" width="170" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="业绩" name="performance">
        <div class="stat-grid">
          <div class="stat-card"><div class="stat-label">总业绩</div><div class="stat-value">{{ performance.totalAmount ?? 0 }}</div></div>
          <div class="stat-card"><div class="stat-label">订单数</div><div class="stat-value">{{ performance.orderCount ?? 0 }}</div></div>
          <div class="stat-card"><div class="stat-label">客单价</div><div class="stat-value">{{ performance.avgAmount ?? 0 }}</div></div>
        </div>
        <h4>门店业绩</h4>
        <el-table :data="performance.stores || []" border size="small">
          <el-table-column prop="store_name" label="门店" /><el-table-column prop="order_count" label="订单数" width="110" />
          <el-table-column prop="amount" label="金额" width="120" />
        </el-table>
        <h4>员工业绩</h4>
        <el-table :data="performance.employees || []" border size="small">
          <el-table-column prop="real_name" label="员工" /><el-table-column prop="order_count" label="订单数" width="110" />
          <el-table-column prop="amount" label="金额" width="120" />
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
import { dictLabel } from '../../dict'

const ruleTypeMap = { SALES: '销售提成', PHOTO: '拍摄提成', EDITOR: '修片提成', ADDON: '加购提成', CHANNEL: '渠道提成' }
const ruleType = (v) => ruleTypeMap[v] || dictLabel(v)

const tab = ref('employees')
const employees = ref([])
const total = ref(0)
const page = ref(1)
const schedules = ref([])
const scheduleDate = ref(new Date().toISOString().slice(0, 10))
const rules = ref([])
const commissions = ref([])
const performance = ref({})

async function loadEmployees() {
  const d = await get('/tenant/employee', { page: page.value, size: 20 })
  employees.value = d.records; total.value = d.total
}
async function loadSchedules() { schedules.value = await get('/tenant/employee/schedules', { from: scheduleDate.value, to: scheduleDate.value }) }
async function loadRules() { rules.value = await get('/tenant/employee/rules') }
async function loadCommissions() {
  const d = await get('/tenant/employee/commissions', { page: 1, size: 50 })
  commissions.value = d.records
}
async function loadPerformance() { performance.value = await get('/tenant/employee/performance') }

async function addSchedule() {
  const { value: employeeId } = await ElMessageBox.prompt('员工 ID', '新增排班', { inputValue: '1' })
  const { value: shift } = await ElMessageBox.prompt('班次（MORNING/AFTERNOON/ALL/REST）', '新增排班', { inputValue: 'ALL' })
  await post('/tenant/employee/schedules', { employeeId, workDate: scheduleDate.value, shift })
  ElMessage.success('排班已保存')
  loadSchedules()
}

function load() { loadEmployees(); loadSchedules(); loadRules(); loadCommissions(); loadPerformance() }
onMounted(load)
</script>
