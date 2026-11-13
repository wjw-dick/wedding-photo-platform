<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">系统参数</h2>
        <div class="page-sub">金额、比例、时长等后台可配；保存后清缓存并广播刷新</div>
      </div>
      <el-button type="primary" :loading="saving" @click="save">保存全部</el-button>
    </div>

    <div v-for="(items, group) in grouped" :key="group" class="card mb16">
      <h3 style="margin-top:0">{{ group }}</h3>
      <el-form label-width="240px" style="max-width:760px">
        <el-form-item v-for="p in items" :key="p.param_key" :label="p.param_key">
          <el-input v-model="edits[p.param_key]" />
          <div class="muted">{{ p.remark }}</div>
        </el-form-item>
      </el-form>
    </div>
    <el-empty v-if="!Object.keys(grouped).length" description="暂无参数" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { get, put } from '../../api'

const grouped = ref({})
const edits = ref({})
const saving = ref(false)

async function load() {
  grouped.value = await get('/tenant/params')
  const flat = {}
  Object.values(grouped.value).flat().forEach((p) => { flat[p.param_key] = p.param_value })
  edits.value = flat
}
async function save() {
  saving.value = true
  try {
    await put('/tenant/params', edits.value)
    ElMessage.success('参数已保存，缓存已刷新')
    load()
  } finally { saving.value = false }
}
onMounted(load)
</script>
