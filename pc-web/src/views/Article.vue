<template>
  <article class="section container" style="max-width:820px">
    <template v-if="article">
      <h1 style="font-weight:500;letter-spacing:2px">{{ article.title }}</h1>
      <p class="muted">{{ article.publish_at }} · {{ article.author || '光影纪' }}</p>
      <img v-if="article.cover_url" :src="article.cover_url" style="margin:24px 0" :alt="article.title" />
      <div style="line-height:2.1;font-size:16px" v-html="article.content"></div>
    </template>
    <p v-else class="muted">文章不存在或已下线。</p>
  </article>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get, pub } from '../api'
const route = useRoute()
const article = ref(null)
onMounted(async () => {
  try { article.value = await get(pub(`/articles/${route.params.id}`)) } catch (e) { article.value = null }
})
</script>
