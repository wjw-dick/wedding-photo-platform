<template>
  <header class="site-header">
    <div class="container nav">
      <router-link to="/" class="brand">光影纪<small>LUMIÈRE WEDDING</small></router-link>
      <nav class="nav-links">
        <router-link to="/packages">套系</router-link>
        <router-link to="/cases">客片</router-link>
        <router-link to="/travel">旅拍</router-link>
        <router-link to="/articles">资讯</router-link>
        <router-link to="/contact">预约</router-link>
        <router-link to="/member">会员</router-link>
      </nav>
    </div>
  </header>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in"><component :is="Component" /></transition>
  </router-view>
  <footer class="site-footer">
    <div class="container footer-grid">
      <div>
        <div class="brand" style="color:#fff">光影纪</div>
        <p>把爱情拍成电影。城市轻旅拍 / 婚礼跟拍 / 全家福。<br />营业时间 09:00 - 21:00，全年无休。</p>
      </div>
      <div>
        <b>快速导航</b>
        <p><router-link to="/packages">套系选购</router-link><br /><router-link to="/cases">客片案例</router-link><br /><router-link to="/contact">在线预约</router-link></p>
      </div>
      <div>
        <b>联系我们</b>
        <p>400-000-0000<br />上海市静安区示例路 88 号<br />hello@example.com</p>
      </div>
    </div>
  </footer>
</template>

<script setup>
</script>
