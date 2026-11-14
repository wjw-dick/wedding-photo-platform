<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">档期日历</h2>
        <div class="page-sub">资源时段、占用与锁档；Redis 锁 + 唯一索引双重防超卖</div>
      </div>
      <div class="flex">
        <el-button :icon="Plus" @click="openSlot">新增时段</el-button>
        <el-button type="primary" :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" style="width:260px" />
        <el-select v-model="type" clearable placeholder="资源类型" style="width:170px">
          <el-option v-for="t in types" :key="t" :label="dictLabel(t)" :value="t" />
        </el-select>
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
      <el-table :data="slots" border stripe>
        <el-table-column prop="slot_date" label="日期" width="120" />
        <el-table-column label="时段" width="150">
          <template #default="{ row }">{{ String(row.slot_start).slice(0, 5) }} - {{ String(row.slot_end).slice(0, 5) }}</template>
        </el-table-column>
        <el-table-column prop="resource_name" label="资源" width="150" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ dictLabel(row.resource_type) }}</template>
        </el-table-column>
        <el-table-column prop="store_name" label="门店" width="130" />
        <el-table-column label="容量" width="100">
          <template #default="{ row }">{{ row.used_count }} / {{ row.capacity }}</template>
        </el-table-column>
        <el-table-column prop="order_no" label="关联订单" width="160" />
        <el-table-column prop="customer_name" label="客户" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ dictLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.lock_key" link type="danger" @click="release(row)">释放锁</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card mt16">
      <h3 style="margin-top:0">档期资源</h3>
      <el-table :data="resources" border size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="resource_name" label="资源" width="150" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">{{ dictLabel(row.resource_type) }}</template>
        </el-table-column>
        <el-table-column prop="store_name" label="门店" width="140" />
        <el-table-column prop="employee_name" label="关联员工" width="120" />
        <el-table-column prop="capacity" label="容量" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) ? 'success' : 'info'" size="small">{{ Number(row.status) ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新增档期时段" width="460px">
      <el-form label-width="90px">
        <el-form-item label="资源">
          <el-select v-model="form.resourceId" style="width:100%">
            <el-option v-for="r in resources" :key="r.id" :label="`${r.resource_name} (${r.resource_type})`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.slotDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="开始"><el-time-select v-model="form.slotStart" start="06:00" step="00:30" end="22:00" /></el-form-item>
        <el-form-item label="结束"><el-time-select v-model="form.slotEnd" start="06:30" step="00:30" end="23:30" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="form.capacity" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="saveSlot">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { get, post } from '../../api'
import { dictLabel } from '../../dict'

const types = ['PHOTOGRAPHER', 'MAKEUP', 'STUDIO', 'CAMERA', 'DRESS']
const range = ref([new Date().toISOString().slice(0, 10), new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10)])
const type = ref('')
const slots = ref([])
const resources = ref([])
const dialog = ref(false)
const form = reactive({ resourceId: null, slotDate: '', slotStart: '09:00', slotEnd: '12:00', capacity: 1 })

async function load() {
  const [from, to] = range.value || []
  const d = await get('/tenant/schedule/calendar', { from, to, type: type.value || undefined })
  slots.value = d.slots
  resources.value = d.resources
}

function openSlot() {
  Object.assign(form, { resourceId: resources.value[0]?.id, slotDate: new Date().toISOString().slice(0, 10), slotStart: '09:00', slotEnd: '12:00', capacity: 1 })
  dialog.value = true
}

async function saveSlot() {
  await post('/tenant/schedule/slots', form)
  ElMessage.success('时段已创建')
  dialog.value = false
  load()
}

async function release(row) {
  await ElMessageBox.confirm('释放该时段锁？', '确认')
  await post(`/tenant/schedule/lock/${row.lock_key}/release`)
  ElMessage.success('已释放')
  load()
}

onMounted(load)
</script>
