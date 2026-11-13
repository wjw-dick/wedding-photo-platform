<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">菜单可视化</h2>
        <div class="page-sub">公众号 3×5、小程序 tabBar 2~5、H5 底部 5 项的服务端校验</div>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="grid-2">
      <div class="card">
        <h3 style="margin-top:0">菜单配置</h3>
        <el-table :data="configs" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="config_name" label="名称" min-width="150" />
          <el-table-column prop="end_type" label="端" width="130" />
          <el-table-column prop="item_count" label="项数" width="80" />
          <el-table-column prop="published" label="已发布" width="90" />
          <el-table-column label="操作" width="130">
            <template #default="{ row }"><el-button link type="primary" @click="select(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </div>
      <div class="card">
        <h3 style="margin-top:0">菜单结构 {{ selected ? `· ${selected.config_name}` : '' }}</h3>
        <el-tree :data="items" node-key="id" :props="{ label: 'title', children: 'children' }" default-expand-all />
        <div v-if="selected" class="mt16 flex">
          <el-button type="primary" @click="save">保存并发布</el-button>
          <el-button @click="reset">重置为首页</el-button>
          <el-button v-if="selected.end_type === 'MP'" @click="publishMp">发布到公众号</el-button>
        </div>
      </div>
    </div>

    <div class="card mt16">
      <h3 style="margin-top:0">JSON 编辑</h3>
      <el-input v-model="json" type="textarea" :rows="12" class="mono" placeholder="按 [{title,item_type,path,children}] 结构编辑" />
      <p class="muted">保存时后端按端类型校验层级与数量，非法结构会返回明确错误。</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { get, post, put } from '../../api'

const configs = ref([])
const selected = ref(null)
const items = ref([])
const json = ref('[]')

async function load() {
  configs.value = await get('/tenant/menu-designer')
  if (configs.value.length && !selected.value) select(configs.value[0])
}
async function select(row) {
  selected.value = row
  items.value = await get(`/tenant/menu-designer/${row.id}/items`)
  json.value = JSON.stringify(strip(items.value), null, 2)
}
function strip(list) {
  return list.map((i) => ({ title: i.title, item_type: i.item_type, path: i.path, sort_no: i.sort_no, visible: i.visible, children: strip(i.children || []) }))
}
async function save() {
  let data
  try { data = JSON.parse(json.value) } catch (e) { return ElMessage.error('JSON 格式错误') }
  await put(`/tenant/menu-designer/${selected.value.id}/items`, data)
  ElMessage.success('已保存并发布')
  select(selected.value)
  load()
}
async function reset() {
  await ElMessageBox.confirm('重置后仅保留首页，确认？', '确认', { type: 'warning' })
  await post(`/tenant/menu-designer/${selected.value.id}/reset`)
  select(selected.value)
}
async function publishMp() {
  const d = await post(`/tenant/menu-designer/${selected.value.id}/publish-mp`)
  ElMessage.success(d.notice || '已发布')
}
onMounted(load)
</script>
