<template>
  <section class="section container">
    <div class="section-head"><h2>客片案例</h2><span>均已获得客户肖像授权</span></div>
    <div class="grid grid-3">
      <div v-for="c in list" :key="c.id" class="card">
        <img v-if="c.cover_url" :src="c.cover_url" class="cover" :alt="c.title" />
        <div v-else class="placeholder">{{ c.title }}</div>
        <div class="card-body"><h3>{{ c.title }}</h3><p class="muted">{{ c.content }}</p></div>
      </div>
    </div>
    <p v-if="!list.length" class="muted">暂无已授权公开的客片。</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, pub } from '../api'
const list = ref([])
onMounted(async () => { try { list.value = await get(pub('/cases')) } catch (e) { list.value = [] } })
</script>
