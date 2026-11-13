<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">角色与账号</h2>
        <div class="page-sub">RBAC 角色、菜单授权、账号与门店数据范围</div>
      </div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="角色" name="roles">
        <div class="toolbar"><el-button type="primary" :icon="Plus" @click="openRole">新增角色</el-button></div>
        <el-table :data="roles" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="role_name" label="角色名称" width="160" />
          <el-table-column prop="role_code" label="编码" width="150" />
          <el-table-column prop="data_scope" label="数据范围" width="120" />
          <el-table-column prop="user_count" label="账号数" width="100" />
          <el-table-column prop="remark" label="说明" min-width="150" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="editMenus(row)">菜单授权</el-button>
              <el-button link type="danger" @click="removeRole(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="账号" name="users">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="账号 / 姓名 / 手机" clearable style="width:240px" @keyup.enter="loadUsers" />
          <el-button :icon="Search" @click="loadUsers">查询</el-button>
          <el-button type="primary" :icon="Plus" @click="openUser">新增账号</el-button>
        </div>
        <el-table :data="users" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="账号" width="140" />
          <el-table-column prop="real_name" label="姓名" width="110" />
          <el-table-column prop="phone" label="手机" width="130" />
          <el-table-column prop="roles" label="角色" min-width="150" />
          <el-table-column prop="store_name" label="门店" width="140" />
          <el-table-column prop="user_type" label="类型" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag :type="row.status ? 'success' : 'info'" size="small">{{ row.status ? '启用' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="warning" @click="reset(row)">重置密码</el-button>
              <el-button link type="primary" @click="assignRoles(row)">分配角色</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination class="mt16" layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="page"
          @current-change="(p) => { page = p; loadUsers() }" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="roleDialog" title="新增角色" width="480px">
      <el-form label-width="100px">
        <el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item>
        <el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" placeholder="如 STORE_MANAGER" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="roleForm.dataScope" style="width:100%">
            <el-option label="全部" value="ALL" /><el-option label="本门店" value="STORE" /><el-option label="仅本人" value="SELF" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="roleForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuDialog" :title="`菜单授权 · ${currentRole?.role_name || ''}`" width="520px">
      <el-tree ref="treeRef" :data="menuTree" show-checkbox node-key="id" :props="{ label: 'menu_name', children: 'children' }" default-expand-all />
      <template #footer>
        <el-button @click="menuDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMenus">保存授权</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialog" title="新增账号" width="520px">
      <el-form label-width="100px">
        <el-form-item label="账号"><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="userForm.phone" /></el-form-item>
        <el-form-item label="初始密码"><el-input v-model="userForm.password" show-password /></el-form-item>
        <el-form-item label="门店 ID"><el-input-number v-model="userForm.storeId" :min="0" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.roleIds" multiple style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.role_name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUser">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { del, get, post, put } from '../../api'

const tab = ref('roles')
const roles = ref([])
const users = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const roleDialog = ref(false)
const roleForm = ref({})
const userDialog = ref(false)
const userForm = ref({})
const menuDialog = ref(false)
const menuTree = ref([])
const currentRole = ref(null)
const treeRef = ref()

async function loadRoles() { roles.value = await get('/tenant/rbac/roles') }
async function loadUsers() {
  const d = await get('/tenant/rbac/users', { keyword: keyword.value || undefined, page: page.value, size: 20 })
  users.value = d.records; total.value = d.total
}
function openRole() { roleForm.value = { dataScope: 'ALL' }; roleDialog.value = true }
async function saveRole() {
  await post('/tenant/rbac/roles', roleForm.value)
  ElMessage.success('角色已创建')
  roleDialog.value = false
  loadRoles()
}
async function removeRole(row) {
  await ElMessageBox.confirm(`删除角色 ${row.role_name}？`, '确认', { type: 'warning' })
  await del(`/tenant/rbac/roles/${row.id}`)
  loadRoles()
}
async function editMenus(row) {
  currentRole.value = row
  menuTree.value = await get('/tenant/rbac/menus/tree')
  const checked = await get(`/tenant/rbac/roles/${row.id}/menus`)
  menuDialog.value = true
  await nextTick()
  treeRef.value?.setCheckedKeys(checked, false)
}
async function saveMenus() {
  const keys = [...treeRef.value.getCheckedKeys(), ...treeRef.value.getHalfCheckedKeys()]
  await put(`/tenant/rbac/roles/${currentRole.value.id}/menus`, keys)
  ElMessage.success('授权已保存')
  menuDialog.value = false
}
function openUser() { userForm.value = { password: 'Wp@123456', storeId: null, roleIds: [] }; userDialog.value = true }
async function saveUser() {
  const d = await post('/tenant/rbac/users', userForm.value)
  ElMessage.success(`账号已创建，初始密码：${d.initialPassword}`)
  userDialog.value = false
  loadUsers()
}
async function reset(row) {
  const { value } = await ElMessageBox.prompt('新密码', `重置 ${row.username}`, { inputValue: 'Wp@123456' })
  const d = await post(`/tenant/rbac/users/${row.id}/reset-password`, { password: value })
  ElMessage.success(`密码已重置：${d.initialPassword}`)
}
async function assignRoles(row) {
  const { value } = await ElMessageBox.prompt('角色 ID，逗号分隔', `分配角色 · ${row.username}`, { inputValue: '' })
  await put(`/tenant/rbac/users/${row.id}/roles`, value.split(',').map((x) => Number(x.trim())).filter(Boolean))
  ElMessage.success('角色已更新')
  loadUsers()
}

onMounted(() => { loadRoles(); loadUsers() })
</script>
