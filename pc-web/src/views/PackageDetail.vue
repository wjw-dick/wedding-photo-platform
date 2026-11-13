<template>
  <section v-if="pkg" class="section container">
    <div class="grid grid-2" style="align-items:start">
      <div>
        <img v-if="pkg.cover_url" :src="pkg.cover_url" class="cover" :alt="pkg.name" />
        <div v-else class="placeholder" style="aspect-ratio:4/3">LUMIÈRE</div>
      </div>
      <div>
        <h1 style="font-weight:500;letter-spacing:3px">{{ pkg.name }}</h1>
        <p class="price" style="font-size:30px">¥{{ pkg.price }}</p>
        <p class="muted">{{ pkg.description }}</p>
        <ul style="line-height:2;padding-left:18px">
          <li>拍摄时长：{{ pkg.shoot_duration || '—' }}</li>
          <li>服装套数：{{ pkg.dress_count || '—' }}</li>
          <li>场景数：{{ pkg.scene_count || '—' }}</li>
          <li>精修张数：{{ pkg.retouch_count || '—' }}</li>
          <li>交付周期：{{ pkg.delivery_days || '—' }} 天</li>
          <li>定金：¥{{ pkg.deposit }}</li>
        </ul>
        <h3>包含项目</h3>
        <div v-for="i in pkg.items || []" :key="i.item_name" class="muted">· {{ i.item_name }} {{ i.item_value || '' }}</div>
        <h3 style="margin-top:24px">可选加购</h3>
        <div class="grid grid-2">
          <div v-for="a in pkg.addons || []" :key="a.id" style="border:1px solid var(--line);padding:12px">
            <b>{{ a.addon_name }}</b><div class="price">¥{{ a.base_price }}</div>
          </div>
        </div>
        <router-link class="btn solid" style="margin-top:26px" to="/contact">预约拍摄</router-link>
      </div>
    </div>
  </section>
  <section v-else class="section container"><p class="muted">套系不存在或已下架。</p></section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get, pub } from '../api'

const route = useRoute()
const pkg = ref(null)
onMounted(async () => {
  try { pkg.value = await get(pub(`/packages/${route.params.id}`)) } catch (e) { pkg.value = null }
})
</script>
