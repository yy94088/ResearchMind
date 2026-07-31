<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed }">
    <aside class="sidebar">
      <router-link class="brand" to="/dashboard">
        <span class="brand-mark">
          <span class="brand-node node-a"></span>
          <span class="brand-node node-b"></span>
          <span class="brand-node node-c"></span>
          <span class="brand-line line-a"></span>
          <span class="brand-line line-b"></span>
        </span>
        <span v-if="!collapsed" class="brand-copy">
          <strong>ResearchMind</strong>
          <small>科研知识管理平台</small>
        </span>
      </router-link>

      <nav class="side-nav">
        <template v-for="group in navGroups" :key="group.title">
          <p v-if="!collapsed" class="nav-heading">{{ group.title }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            :title="collapsed ? item.label : ''"
          >
            <AppIcon :name="item.icon" />
            <span v-if="!collapsed">{{ item.label }}</span>
            <span v-if="item.badge && !collapsed" class="nav-badge">{{ item.badge }}</span>
          </router-link>
        </template>
      </nav>

      <div class="sidebar-bottom">
        <div v-if="!collapsed" class="storage-card">
          <div class="storage-head"><span>MinIO 私有存储</span><strong>{{ storedPdfCount }}</strong></div>
          <div class="storage-track"><span :style="{ width: `${storageCoverage}%` }"></span></div>
          <small>{{ storedPdfCount }} / {{ store.papers.length }} 篇文献带 PDF 原文</small>
        </div>
        <button class="collapse-button" type="button" @click="collapsed = !collapsed">
          <AppIcon name="chevron" />
          <span v-if="!collapsed">收起导航</span>
        </button>
      </div>
    </aside>

    <div class="main-column">
      <header class="topbar">
        <div class="topbar-title">
          <h1>{{ route.meta.title }}</h1>
          <span>{{ today }}</span>
        </div>
        <div class="topbar-actions">
          <label class="global-search">
            <AppIcon name="search" />
            <input v-model="globalQuery" placeholder="搜索文献、作者或关键词" @keyup.enter="search" />
            <kbd>⌘ K</kbd>
          </label>
          <button class="icon-button notification-button" type="button" title="查看团队邀请" @click="router.push('/team')">
            <AppIcon name="bell" />
          </button>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-button" type="button">
              <span class="avatar"><img v-if="store.avatarSrc" :src="store.avatarSrc" alt="个人头像" />{{ store.avatarSrc ? '' : (store.profile.name?.slice(0, 1) || '研') }}</span>
              <span class="user-copy">
                <strong>{{ store.profile.name }}</strong>
                <small>{{ store.profile.role }}</small>
              </span>
              <span class="down-arrow">⌄</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人设置</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="page-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const globalQuery = ref('')
const storedPdfCount = computed(() => store.papers.filter((paper) => paper.fileAvailable).length)
const storageCoverage = computed(() => store.papers.length ? Math.round(storedPdfCount.value / store.papers.length * 100) : 0)

const navGroups = computed(() => [
  {
    title: '工作空间',
    items: [
      { to: '/dashboard', icon: 'home', label: '工作台' },
      { to: '/library', icon: 'book', label: '我的文献', badge: store.papers.length },
      { to: '/graph', icon: 'graph', label: '知识图谱' },
      { to: '/analytics', icon: 'chart', label: '研究分析' }
    ]
  },
  {
    title: '智能工具',
    items: [
      { to: '/ai', icon: 'sparkles', label: 'AI 阅读助手' },
      { to: '/team', icon: 'users', label: '团队空间' }
    ]
  },
  {
    title: '账户',
    items: [{ to: '/profile', icon: 'settings', label: '个人设置' }]
  }
])

const today = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())

function search() {
  if (!globalQuery.value.trim()) return
  router.push({ path: '/library', query: { q: globalQuery.value.trim() } })
  globalQuery.value = ''
}

async function handleUserCommand(command) {
  if (command === 'profile') router.push('/profile')
  if (command === 'logout') {
    await store.logout()
    ElMessage.success('已安全退出')
    router.replace('/login')
  }
}
</script>
