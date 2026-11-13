<template>
  <section class="section container">
    <div class="section-head"><h2>套系选购</h2><span>按风格、场景与预算自由筛选</span></div>
    <div class="tabs">
      <button :class="{ on: !keyword }" @click="keyword = ''; load()">全部</button>
      <input v-model="keyword" placeholder="搜索套系名称" style="width:220px;margin-left:auto" @keyup.enter="load" />
      <button class="btn solid" style="padding:8px 18px" @click="load">搜索</button>
    </div>
    <div v-if="loading" class="muted">加载中…</div>
    <div v-else class="grid grid-3">
      <router-link v-for="p in list" :key="p.id" :to="`/packages/${p.id}`" class="card">
        <img v-if="p.cover_url" :src="p.cover_url" class="cover" :alt="p.name" />
        <div v-else class="placeholder">LUMIÈRE</div>
        <div class="card-body">
          <h3>{{ p.name }}</h3>
          <p class="muted">{{ p.description || `${p.scene_count || 2} 场景 · ${p.dress_count || 3} 套服装 · ${p.retouch_count || 20} 张精修` }}</p>
          <div class="price">¥{{ p.price }}</div>
        </div>
      </router-link>
    </div>
    <p v-if="!loading && !list.length" class="muted">当前没有符合条件的套系。</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, pub } from '../api'

const list = ref([])
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try { list.value = await get(pub('/packages'), { keyword: keyword.value || undefined }) }
  finally { loading.value = false }
}
onMounted(load)
</script>
