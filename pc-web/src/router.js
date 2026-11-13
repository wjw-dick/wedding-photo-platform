import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', component: () => import('./views/Home.vue'), meta: { title: '首页' } },
  { path: '/packages', component: () => import('./views/Packages.vue'), meta: { title: '套系' } },
  { path: '/packages/:id', component: () => import('./views/PackageDetail.vue'), meta: { title: '套系详情' } },
  { path: '/cases', component: () => import('./views/Cases.vue'), meta: { title: '客片案例' } },
  { path: '/travel', component: () => import('./views/Travel.vue'), meta: { title: '旅拍线路' } },
  { path: '/articles', component: () => import('./views/Articles.vue'), meta: { title: '资讯' } },
  { path: '/articles/:id', component: () => import('./views/Article.vue'), meta: { title: '文章' } },
  { path: '/contact', component: () => import('./views/Contact.vue'), meta: { title: '预约咨询' } },
  { path: '/member', component: () => import('./views/Member.vue'), meta: { title: '会员中心' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.afterEach((to) => {
  document.title = `${to.meta.title || '光影纪'} · 光影纪婚纱摄影`
})

export default router
