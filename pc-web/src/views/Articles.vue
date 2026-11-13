<template>
  <section class="section container">
    <div class="section-head"><h2>资讯</h2><span>拍摄攻略与品牌动态</span></div>
    <div class="grid grid-2">
      <router-link v-for="a in list" :key="a.id" :to="`/articles/${a.id}`" class="card">
        <div class="card-body">
          <h3>{{ a.title }}</h3>
          <p class="muted">{{ a.summary }}</p>
          <span class="muted" style="font-size:12px">{{ a.publish_at }}</span>
        </div>
      </router-link>
    </div>
    <p v-if="!list.length" class="muted">暂无已发布文章。</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, pub } from '../api'
const list = ref([])
onMounted(async () => { try { list.value = await get(pub('/articles')) } catch (e) { list.value = [] } })
</script>
