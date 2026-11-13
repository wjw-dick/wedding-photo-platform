<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">平台账号</h2>
        <div class="page-sub">平台管理员、运营、财务三类角色，与租户账号完全隔离</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="open">新增账号</el-button>
    </div>

    <div class="card">
      <el-table :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="账号" width="160" />
        <el-table-column prop="real_name" label="姓名" width="140" />
        <el-table-column label="角色" width="170">
          <template #default="{ row }"><el-tag size="small">{{ row.role }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'" size="small">{{ row.status ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="last_login_at" label="最近登录" width="180" />
        <el-table-column prop="created_at" label="创建时间" width="180" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link :type="row.status ? 'danger' : 'success'" @click="toggle(row)">{{ row.status ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新增平台账号" width="480px">
      <el-form label-width="100px">
        <el-form-item label="账号"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="form.password" show-password /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="平台管理员" value="PLATFORM_ADMIN" />
            <el-option label="平台运营" value="PLATFORM_OPERATOR" />
            <el-option label="平台财务" value="PLATFORM_FINANCE" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon title="平台账号不可登录租户后台，租户账号也不可登录平台端。" />
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { get, post } from '../../api'

const rows = ref([])
const dialog = ref(false)
const form = ref({})

async function load() { rows.value = await get('/platform/admins') }

function open() {
  form.value = { username: '', realName: '', password: 'Admin@123456', role: 'PLATFORM_OPERATOR' }
  dialog.value = true
}

async function save() {
  if (!form.value.username) return ElMessage.warning('请填写账号')
  await post('/platform/admins', form.value)
  ElMessage.success('账号已创建')
  dialog.value = false
  load()
}

async function toggle(row) {
  await ElMessageBox.confirm(`${row.status ? '停用' : '启用'}账号 ${row.username}？`, '确认', { type: 'warning' })
  await post(`/platform/admins/${row.id}/status`, { status: row.status ? 0 : 1 })
  load()
}

onMounted(load)
</script>
