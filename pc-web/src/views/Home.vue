<template>
  <div>
    <section class="hero">
      <div class="hero-inner container">
        <h1>把爱情拍成电影</h1>
        <p>城市轻旅拍 · 婚礼跟拍 · 双影像记录</p>
        <div style="margin-top:34px">
          <router-link class="btn solid" to="/contact">立即预约</router-link>
          <router-link class="btn" style="margin-left:12px;border-color:#fff;color:#fff" to="/packages">浏览套系</router-link>
        </div>
      </div>
    </section>

    <section class="section container">
      <div class="section-head"><h2>精选套系</h2><router-link to="/packages" class="muted">全部套系 →</router-link></div>
      <div class="grid grid-3">
        <router-link v-for="p in home.packages?.slice(0, 6)" :key="p.id" :to="`/packages/${p.id}`" class="card">
          <img v-if="p.cover_url" :src="p.cover_url" class="cover" :alt="p.name" />
          <div v-else class="placeholder">LUMIÈRE</div>
          <div class="card-body">
            <h3>{{ p.name }}</h3>
            <p class="muted">{{ p.description || `${p.scene_count || 2} 场景 · ${p.dress_count || 3} 套服装` }}</p>
            <div class="price">¥{{ p.price }}</div>
          </div>
        </router-link>
      </div>
      <p v-if="!home.packages?.length" class="muted">暂未发布套系，请先在后台上架。</p>
    </section>

    <section class="section" style="background:var(--cream)">
      <div class="container">
        <div class="section-head"><h2>客片案例</h2><router-link to="/cases" class="muted">更多客片 →</router-link></div>
        <div class="grid grid-3">
          <div v-for="c in home.cases?.slice(0, 6)" :key="c.id" class="card">
            <img v-if="c.cover_url" :src="c.cover_url" class="cover" :alt="c.title" />
            <div v-else class="placeholder">{{ c.title }}</div>
            <div class="card-body"><h3>{{ c.title }}</h3></div>
          </div>
        </div>
      </div>
    </section>

    <section class="section container">
      <div class="section-head"><h2>旅拍线路</h2><router-link to="/travel" class="muted">全部线路 →</router-link></div>
      <div class="grid grid-3">
        <div v-for="r in home.routes?.slice(0, 3)" :key="r.id" class="card">
          <div class="placeholder">{{ r.destination }}</div>
          <div class="card-body">
            <h3>{{ r.route_name }}</h3>
            <p class="muted">{{ r.days }} 天 · {{ r.destination }}</p>
            <div class="price">¥{{ r.price }}</div>
          </div>
        </div>
      </div>
    </section>

    <section class="section" style="background:#241b16;color:#f4ece2">
      <div class="container grid grid-2">
        <div>
          <h2 style="font-weight:500;letter-spacing:4px">为什么选择光影纪</h2>
          <p style="line-height:2.1;color:#c9bcae">双机位拍摄、独立修图团队、48 小时选片、云端交付永久可下载。<br />所有客片均在获得肖像授权后展示，支持随时撤回。</p>
        </div>
        <div class="grid grid-2">
          <div v-for="s in stores" :key="s.id" style="border:1px solid #4a3f36;padding:18px">
            <b>{{ s.store_name }}</b>
            <p style="color:#b3a596;font-size:13px;line-height:1.8">{{ s.address }}<br />{{ s.phone }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="section container">
      <div class="section-head"><h2>最新资讯</h2><router-link to="/articles" class="muted">全部资讯 →</router-link></div>
      <div class="grid grid-3">
        <router-link v-for="a in home.articles?.slice(0, 3)" :key="a.id" :to="`/articles/${a.id}`" class="card">
          <div class="card-body">
            <h3>{{ a.title }}</h3>
            <p class="muted">{{ a.summary }}</p>
            <span class="muted" style="font-size:12px">{{ a.publish_at }}</span>
          </div>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, pub } from '../api'

const home = ref({})
const stores = ref([])

onMounted(async () => {
  try {
    home.value = await get(pub('/home'))
    stores.value = home.value.stores || []
  } catch (e) {
    home.value = {}
  }
})
</script>
