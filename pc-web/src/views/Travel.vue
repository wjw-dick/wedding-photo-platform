<template>
  <section class="section container">
    <div class="section-head"><h2>旅拍线路</h2><span>目的地 · 天数 · 一价全含</span></div>
    <div class="grid grid-3">
      <div v-for="r in list" :key="r.id" class="card">
        <div class="placeholder">{{ r.destination }}</div>
        <div class="card-body">
          <h3>{{ r.route_name }}</h3>
          <p class="muted">{{ r.days }} 天 · {{ r.destination }}</p>
          <div class="price">¥{{ r.price }}</div>
        </div>
      </div>
    </div>
    <p v-if="!list.length" class="muted">暂无旅拍线路。</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, pub } from '../api'
const list = ref([])
onMounted(async () => { try { list.value = await get(pub('/routes')) } catch (e) { list.value = [] } })
</script>
