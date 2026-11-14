<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">平台公告</h2>
        <div class="page-sub">面向全部租户或指定租户发布公告，支持定向与已读统计</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="open()">发布公告</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="范围" width="110">
          <template #default="{ row }">{{ targetMap[row.target_type] || dictLabel(row.target_type) }}</template>
        </el-table-column>
        <el-table-column prop="target_value" label="目标" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ dictLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="publish_at" label="发布时间" width="170" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="stats(row)">已读</el-button>
            <el-button link type="primary" @click="open(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="size" :current-page="page"
        @current-change="(p) => { page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑公告' : '发布公告'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="form.targetType" style="width:100%">
            <el-option label="全部租户" value="ALL" />
            <el-option label="指定套餐" value="PLAN" />
            <el-option label="指定租户" value="TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.targetType !== 'ALL'" label="目标值">
          <el-input v-model="form.targetValue" placeholder="套餐 ID 或租户 ID，多个用逗号分隔" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="PUBLISHED">立即发布</el-radio>
            <el-radio value="DRAFT">存为草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="7" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { del, get, post, put } from '../../api'
import { dictLabel } from '../../dict'

const targetMap = { ALL: '全部租户', PLAN: '指定套餐', TENANT: '指定租户' }

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const dialog = ref(false)
const form = ref({})

async function load() {
  const d = await get('/platform/announcements', { page: page.value, size: size.value })
  rows.value = d.records
  total.value = d.total
}

function open(row) {
  form.value = row
    ? { id: row.id, title: row.title, content: row.content, targetType: row.target_type, targetValue: row.target_value, status: row.status }
    : { targetType: 'ALL', targetValue: '', status: 'PUBLISHED' }
  dialog.value = true
}

async function save() {
  if (!form.value.title) return ElMessage.warning('请填写标题')
  if (form.value.id) await put(`/platform/announcements/${form.value.id}`, form.value)
  else await post('/platform/announcements', form.value)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`删除公告「${row.title}」？`, '确认', { type: 'warning' })
  await del(`/platform/announcements/${row.id}`)
  load()
}

async function stats(row) {
  const d = await get(`/platform/announcements/${row.id}/read-stats`)
  ElMessageBox.alert(`已读 ${d.read} / 租户总数 ${d.total}`, `「${row.title}」已读统计`)
}

onMounted(load)
</script>
