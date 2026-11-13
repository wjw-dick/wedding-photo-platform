<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">官网留资</h2>
        <div class="page-sub">PC 官网 / 移动端表单留资，一键转入 CRM 线索池</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="status" clearable placeholder="状态" style="width:150px" @change="load">
          <el-option label="待处理" value="NEW" /><el-option label="已转线索" value="CONVERTED" />
        </el-select>
      </div>
      <el-table :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="city" label="城市" width="110" />
        <el-table-column prop="requirement" label="需求" min-width="180" show-overflow-tooltip />
        <el-table-column prop="source_page" label="来源页" width="150" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="created_at" label="提交时间" width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'CONVERTED'" link type="success" @click="convert(row)">转线索</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post } from '../../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('NEW')

async function load() {
  const d = await get('/tenant/cms/lead-submits', { status: status.value || undefined, page: page.value, size: 20 })
  rows.value = d.records; total.value = d.total
}
async function convert(row) {
  const { value } = await ElMessageBox.prompt('门店 ID（可留空）', '转为 CRM 线索', { inputValue: '1' })
  await post(`/tenant/cms/lead-submits/${row.id}/convert`, { storeId: value || null })
  ElMessage.success('已转入线索池')
  load()
}
onMounted(load)
</script>
