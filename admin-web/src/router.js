import { createRouter, createWebHashHistory } from 'vue-router'
import { auth } from './auth'
import { groups } from './crud'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('./layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: () => (auth.platform ? '/platform/dashboard' : '/dashboard') },

      // 平台端
      { path: 'platform/dashboard', component: () => import('./views/platform/Dashboard.vue'), meta: { platform: true, title: '平台看板' } },
      { path: 'platform/tenants', component: () => import('./views/platform/Tenants.vue'), meta: { platform: true, title: '租户管理' } },
      { path: 'platform/plans', component: () => import('./views/platform/Plans.vue'), meta: { platform: true, title: '套餐与配额' } },
      { path: 'platform/bills', component: () => import('./views/platform/Bills.vue'), meta: { platform: true, title: '账单与计费' } },
      { path: 'platform/announcements', component: () => import('./views/platform/Announcements.vue'), meta: { platform: true, title: '平台公告' } },
      { path: 'platform/audits', component: () => import('./views/platform/PlatformAudit.vue'), meta: { platform: true, title: '平台审计' } },
      { path: 'platform/admins', component: () => import('./views/platform/Admins.vue'), meta: { platform: true, title: '平台账号' } },

      // 租户端
      { path: 'dashboard', component: () => import('./views/tenant/Workbench.vue'), meta: { title: '工作台' } },
      { path: 'orders', component: () => import('./views/tenant/Orders.vue'), meta: { title: '订单中心' } },
      { path: 'schedule', component: () => import('./views/tenant/Schedule.vue'), meta: { title: '档期日历' } },
      { path: 'production', component: () => import('./views/tenant/Production.vue'), meta: { title: '生产交付' } },
      { path: 'crm', component: () => import('./views/tenant/Crm.vue'), meta: { title: '会员与线索' } },
      { path: 'channels', component: () => import('./views/tenant/Channels.vue'), meta: { title: '渠道分销' } },
      { path: 'suppliers', component: () => import('./views/tenant/Suppliers.vue'), meta: { title: '供应商协同' } },
      { path: 'employees', component: () => import('./views/tenant/Employees.vue'), meta: { title: '员工与业绩' } },
      { path: 'reports', component: () => import('./views/tenant/Reports.vue'), meta: { title: '统计报表' } },
      { path: 'mp', component: () => import('./views/tenant/Mp.vue'), meta: { title: '公众号运营' } },
      { path: 'entries', component: () => import('./views/tenant/Entries.vue'), meta: { title: '多端入口' } },
      { path: 'system/params', component: () => import('./views/tenant/Params.vue'), meta: { title: '系统参数' } },
      { path: 'system/rbac', component: () => import('./views/tenant/Rbac.vue'), meta: { title: '角色与账号' } },
      { path: 'system/storage', component: () => import('./views/tenant/Storage.vue'), meta: { title: '对象存储' } },
      { path: 'system/menu', component: () => import('./views/tenant/MenuDesigner.vue'), meta: { title: '菜单可视化' } },
      { path: 'system/sso', component: () => import('./views/tenant/SystemConfig.vue'), meta: { title: '消息与 SSO' } },
      { path: 'system/audit', component: () => import('./views/tenant/Audit.vue'), meta: { title: '审计日志' } },
      { path: 'cms/submits', component: () => import('./views/tenant/LeadSubmits.vue'), meta: { title: '官网留资' } },

      ...groups.flatMap((g) => g.items.map((item) => ({
        path: item.path.replace(/^\//, ''),
        component: () => import('./views/tenant/GenericCrud.vue'),
        meta: { title: item.label, table: item.table }
      })))
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!auth.token) return '/login'
  if (to.meta.platform && !auth.platform) return '/dashboard'
  if (!to.meta.platform && auth.platform && to.path !== '/') return '/platform/dashboard'
  return true
})

export default router
