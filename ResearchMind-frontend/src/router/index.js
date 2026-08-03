import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import { hasAccessToken } from '../api/authStorage'

const LoginView = () => import('../views/LoginView.vue')
const DashboardView = () => import('../views/DashboardView.vue')
const LibraryView = () => import('../views/LibraryView.vue')
const GraphView = () => import('../views/GraphView.vue')
const AnalyticsView = () => import('../views/AnalyticsView.vue')
const AiView = () => import('../views/AiView.vue')
const TeamView = () => import('../views/TeamView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const AdminView = () => import('../views/AdminView.vue')
const NotFoundView = () => import('../views/NotFoundView.vue')

const routes = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '工作台' } },
      { path: 'library', name: 'library', component: LibraryView, meta: { title: '文献库' } },
      { path: 'graph', name: 'graph', component: GraphView, meta: { title: '知识图谱' } },
      { path: 'analytics', name: 'analytics', component: AnalyticsView, meta: { title: '研究分析' } },
      { path: 'ai', name: 'ai', component: AiView, meta: { title: 'AI 阅读助手' } },
      { path: 'team', name: 'team', component: TeamView, meta: { title: '团队空间' } },
      { path: 'profile', name: 'profile', component: ProfileView, meta: { title: '个人设置' } },
      { path: 'admin', name: 'admin', component: AdminView, meta: { title: '系统管理', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { public: true, title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const authenticated = hasAccessToken()
  if (!to.meta.public && !authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.admin) {
    try {
      const role = JSON.parse(localStorage.getItem('researchmind-session'))?.user?.role
      if (role !== 'ADMIN') return { name: 'dashboard' }
    } catch {
      return { name: 'dashboard' }
    }
  }
  if (to.name === 'login' && authenticated) return { name: 'dashboard' }
  document.title = `${to.meta.title || 'ResearchMind'} - ResearchMind`
  return true
})

export default router
