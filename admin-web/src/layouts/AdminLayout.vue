<template>
  <el-container style="height: 100%">
    <el-aside width="228px" style="background:#241b16">
      <div class="aside-brand">
        婚礼影像 SaaS
        <small>{{ auth.platform ? '平台运营端' : '租户管理后台' }}</small>
      </div>
      <el-menu
        :default-active="active"
        background-color="#241b16"
        text-color="#cfc3b8"
        active-text-color="#f0c89a"
        router
      >
        <template v-if="auth.platform">
          <el-menu-item index="/platform/dashboard"><el-icon><DataLine /></el-icon>平台看板</el-menu-item>
          <el-menu-item index="/platform/tenants"><el-icon><OfficeBuilding /></el-icon>租户管理</el-menu-item>
          <el-menu-item index="/platform/plans"><el-icon><Box /></el-icon>套餐与配额</el-menu-item>
          <el-menu-item index="/platform/bills"><el-icon><Money /></el-icon>账单与计费</el-menu-item>
          <el-menu-item index="/platform/announcements"><el-icon><Bell /></el-icon>平台公告</el-menu-item>
          <el-menu-item index="/platform/audits"><el-icon><Document /></el-icon>平台审计</el-menu-item>
          <el-menu-item index="/platform/admins"><el-icon><UserFilled /></el-icon>平台账号</el-menu-item>
        </template>
        <template v-else>
          <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon>工作台</el-menu-item>
          <el-menu-item index="/orders"><el-icon><Tickets /></el-icon>订单中心</el-menu-item>
          <el-menu-item index="/schedule"><el-icon><Calendar /></el-icon>档期日历</el-menu-item>
          <el-menu-item index="/production"><el-icon><Camera /></el-icon>生产交付</el-menu-item>
          <el-menu-item index="/crm"><el-icon><User /></el-icon>会员与线索</el-menu-item>
          <el-menu-item index="/channels"><el-icon><Connection /></el-icon>渠道分销</el-menu-item>
          <el-menu-item index="/suppliers"><el-icon><Van /></el-icon>供应商协同</el-menu-item>
          <el-menu-item index="/employees"><el-icon><Avatar /></el-icon>员工与业绩</el-menu-item>
          <el-menu-item index="/reports"><el-icon><PieChart /></el-icon>统计报表</el-menu-item>
          <el-menu-item index="/mp"><el-icon><ChatDotRound /></el-icon>公众号运营</el-menu-item>
          <el-menu-item index="/entries"><el-icon><Grid /></el-icon>多端入口</el-menu-item>
          <el-sub-menu index="catalog">
            <template #title><el-icon><Goods /></el-icon>产品与档期</template>
            <el-menu-item v-for="i in group('产品与档期')" :key="i.path" :index="i.path">{{ i.label }}</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="member">
            <template #title><el-icon><UserFilled /></el-icon>客户与运营</template>
            <el-menu-item v-for="i in group('客户与运营')" :key="i.path" :index="i.path">{{ i.label }}</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="content">
            <template #title><el-icon><EditPen /></el-icon>内容与公众号</template>
            <el-menu-item v-for="i in group('内容与公众号')" :key="i.path" :index="i.path">{{ i.label }}</el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="system">
            <template #title><el-icon><Setting /></el-icon>系统设置</template>
            <el-menu-item index="/system/params">系统参数</el-menu-item>
            <el-menu-item index="/system/rbac">角色与账号</el-menu-item>
            <el-menu-item index="/system/menu">菜单可视化</el-menu-item>
            <el-menu-item index="/system/storage">对象存储</el-menu-item>
            <el-menu-item index="/system/sso">消息与 SSO</el-menu-item>
            <el-menu-item index="/system/audit">审计日志</el-menu-item>
            <el-menu-item index="/cms/submits">官网留资</el-menu-item>
            <el-menu-item v-for="i in group('系统配置')" :key="i.path" :index="i.path">{{ i.label }}</el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #ebeef5">
        <div>
          <span style="font-weight:600">{{ route.meta.title || '工作台' }}</span>
          <span class="muted" style="margin-left:10px">{{ auth.platform ? '平台运营' : (auth.session?.tenant?.name || '') }}</span>
        </div>
        <div class="flex">
          <el-tag v-if="!auth.platform && auth.session?.tenant?.status" size="small" type="success">{{ auth.session.tenant.status }}</el-tag>
          <el-tag v-if="auth.platform" size="small" type="warning">{{ auth.user?.platformRole || auth.user?.role || 'PLATFORM' }}</el-tag>
          <span>{{ auth.user?.realName || auth.user?.username || auth.user?.user?.realName }}</span>
          <el-button link type="danger" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main style="padding:0;overflow:auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth, clearSession } from '../auth'
import { groups } from '../crud'

const route = useRoute()
const router = useRouter()
const active = computed(() => route.path)
const group = (title) => groups.find((g) => g.title === title)?.items || []

function logout() {
  clearSession()
  router.push('/login')
}
</script>
