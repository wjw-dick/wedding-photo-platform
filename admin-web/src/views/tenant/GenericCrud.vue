<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ title }}</h2>
        <div class="page-sub">表 {{ table }} · 租户数据自动隔离</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索名称/电话/编号" clearable style="width:260px" @keyup.enter="load" />
        <el-button :icon="Search" @click="load">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column
          v-for="c in columns"
          :key="c.key"
          :prop="c.key"
          :label="c.label"
          :min-width="c.width || 130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <el-tag v-if="c.type === 'switch'" :type="Number(row[c.key]) ? 'success' : 'info'" size="small">
              {{ Number(row[c.key]) ? '启用' : '停用' }}
            </el-tag>
            <span v-else>{{ display(row[c.key]) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="mt16"
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="(p) => { page = p; load() }"
        @size-change="(s) => { size = s; page = 1; load() }"
      />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑' : '新增'" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item v-for="f in fields" :key="f.key" :label="f.label">
          <el-switch v-if="f.type === 'switch'" v-model="form[f.key]" :active-value="1" :inactive-value="0" />
          <el-input-number v-else-if="f.type === 'number'" v-model="form[f.key]" :precision="2" style="width:100%" />
          <el-date-picker
            v-else-if="f.type === 'date'"
            v-model="form[f.key]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
          <el-date-picker
            v-else-if="f.type === 'datetime'"
            v-model="form[f.key]"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width:100%"
          />
          <el-select v-else-if="f.type?.startsWith('select:')" v-model="form[f.key]" clearable style="width:100%">
            <el-option v-for="o in f.type.slice(7).split(',')" :key="o" :label="o" :value="o" />
          </el-select>
          <el-input
            v-else
            v-model="form[f.key]"
            :type="f.type === 'textarea' ? 'textarea' : 'text'"
            :rows="f.type === 'textarea' ? 4 : 1"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { del, get, post, put } from '../../api'
import { CRUD } from '../../crud'

const route = useRoute()
const table = computed(() => route.meta.table)
const config = computed(() => CRUD[table.value] || { fields: [] })
const title = computed(() => config.value.title || route.meta.title)
const fields = computed(() => {
  const given = config.value.fields || []
  return given.map(([key, label, type]) => ({ key, label, type: type || 'text' }))
})
const columns = computed(() => fields.value.filter((f) => f.type !== 'textarea').slice(0, 8))

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const form = ref({})

async function load() {
  loading.value = true
  try {
    const data = await get(`/tenant/crud/${table.value}`, { page: page.value, size: size.value, keyword: keyword.value })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reset() {
  keyword.value = ''
  page.value = 1
  load()
}

function openCreate() {
  const f = { id: null }
  fields.value.forEach((x) => {
    f[x.key] = x.type === 'switch' ? 1 : (x.type === 'number' ? 0 : '')
  })
  form.value = f
  dialog.value = true
}

function openEdit(row) {
  form.value = { ...row }
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    const body = { ...form.value }
    delete body.id
    delete body.tenant_id
    delete body.created_at
    delete body.updated_at
    delete body.deleted
    if (form.value.id) {
      await put(`/tenant/crud/${table.value}/${form.value.id}`, body)
    } else {
      await post(`/tenant/crud/${table.value}`, body)
    }
    ElMessage.success('保存成功')
    dialog.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除 #${row.id}？该操作将写入审计日志。`, '删除确认', { type: 'warning' })
  await del(`/tenant/crud/${table.value}/${row.id}`)
  ElMessage.success('已删除')
  load()
}

function display(v) {
  if (v === null || v === undefined) return ''
  const s = String(v)
  return s.length > 80 ? s.slice(0, 80) + '…' : s
}

watch(() => route.meta.table, () => { page.value = 1; keyword.value = ''; load() })
onMounted(load)
</script>
