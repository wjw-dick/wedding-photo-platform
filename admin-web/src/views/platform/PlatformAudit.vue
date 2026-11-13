<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">平台审计</h2>
        <div class="page-sub">平台侧操作留痕，仅可查询，不提供修改与删除入口</div>
      </div>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model.number="query.tenantId" placeholder="租户 ID" clearable style="width:140px" />
        <el-input v-model="query.action" placeholder="动作关键字" clearable style="width:180px" />
        <el-button :icon="Search" @click="load">查询</el-button>
      </div>
      <el-table :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="operator_name" label="操作人" width="130" />
        <el-table-column prop="module" label="模块" width="130" />
        <el-table-column prop="action" label="动作" width="150" />
        <el-table-column label="目标" width="150">
          <template #default="{ row }">{{ row.target_type }}:{{ row.target_id }}</template>
        </el-table-column>
        <el-table-column prop="target_tenant_id" label="租户ID" width="90" />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="cost_ms" label="耗时(ms)" width="100" />
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="detail = row">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
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
        <el-descriptions-item label="异常信息"><pre class="code-block">{{ detail.error_msg }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { get } from '../../api'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const query = reactive({ tenantId: null, action: '' })
const detail = ref(null)
const drawer = computed({ get: () => !!detail.value, set: (v) => { if (!v) detail.value = null } })

async function load() {
  const d = await get('/platform/audits', {
    tenantId: query.tenantId || undefined,
    action: query.action || undefined,
    page: page.value,
    size: size.value
  })
  rows.value = d.records
  total.value = d.total
}

onMounted(load)
</script>
