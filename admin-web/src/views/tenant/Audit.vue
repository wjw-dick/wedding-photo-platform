<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">审计日志</h2>
        <div class="page-sub">只读留痕，支持筛选与 CSV 导出；不提供修改与删除</div>
      </div>
      <el-button :icon="Download" @click="exportCsv">导出</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.module" placeholder="模块" clearable style="width:150px" />
        <el-input v-model="query.operator" placeholder="操作人" clearable style="width:150px" />
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" style="width:250px" />
        <el-button :icon="Search" @click="load">查询</el-button>
        <span class="muted">审计写入仅由后端拦截器完成</span>
      </div>
      <el-table :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="operator_name" label="操作人" width="130" />
        <el-table-column prop="module" label="模块" width="130" />
        <el-table-column prop="action" label="动作" width="150" />
        <el-table-column label="目标" width="150">
          <template #default="{ row }">{{ row.target_type }}:{{ row.target_id }}</template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="cost_ms" label="耗时(ms)" width="100" />
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="detail = row">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>

    <el-drawer v-model="drawer" title="审计详情" size="520px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="操作人">{{ detail.operator_name }}</el-descriptions-item>
        <el-descriptions-item label="模块 / 动作">{{ detail.module }} / {{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ detail.target_type }}:{{ detail.target_id }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.result }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.created_at }}</el-descriptions-item>
        <el-descriptions-item label="请求参数"><pre class="code-block">{{ detail.request_json }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, Search } from '@element-plus/icons-vue'
import { download, get } from '../../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const range = ref([])
const query = reactive({ module: '', operator: '' })
const detail = ref(null)
const drawer = computed({ get: () => !!detail.value, set: (v) => { if (!v) detail.value = null } })

async function load() {
  const [from, to] = range.value || []
  const d = await get('/tenant/audits', {
    module: query.module || undefined,
    operator: query.operator || undefined,
    from: from || undefined,
    to: to || undefined,
    page: page.value,
    size: 20
  })
  rows.value = d.records; total.value = d.total
}
function exportCsv() {
  const [from, to] = range.value || []
  download(`/tenant/audits/export?from=${from || ''}&to=${to || ''}`, 'audits.csv')
}
onMounted(load)
</script>
