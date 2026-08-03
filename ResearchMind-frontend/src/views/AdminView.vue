<template>
  <div>
    <header class="page-header">
      <div><h2>系统管理</h2><p>用户权限、运行数据与安全审计</p></div>
      <div class="header-actions">
        <button class="secondary-button" :disabled="maintaining" @click="cleanup">
          <AppIcon name="trash" /> {{ maintaining ? '正在清理…' : '清理过期上传' }}
        </button>
        <button class="primary-button" :disabled="loading" @click="loadAll"><AppIcon name="refresh" /> 刷新</button>
      </div>
    </header>

    <section class="admin-stats">
      <article v-for="stat in stats" :key="stat.label">
        <span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small>
      </article>
    </section>

    <section class="admin-grid">
      <article class="panel users-panel">
        <div class="panel-head">
          <div><h3>用户与权限</h3><p>最多显示最近注册的 200 个匹配账户</p></div>
          <label class="admin-search"><AppIcon name="search" /><input v-model.trim="query" placeholder="姓名、用户名或邮箱" @keyup.enter="loadUsers" /></label>
        </div>
        <div class="admin-table user-table">
          <div class="table-head"><span>用户</span><span>资源</span><span>角色</span><span>状态</span></div>
          <div v-for="user in users" :key="user.id" class="table-row">
            <div class="user-cell"><strong>{{ user.realName }}</strong><span>{{ user.username }} · {{ user.email }}</span><small>{{ user.institution || '未填写机构' }} · {{ formatTime(user.createTime) }} 注册</small></div>
            <div><strong>{{ user.paperCount }} 篇</strong><small>{{ user.teamCount }} 个团队</small></div>
            <select :value="user.role" :disabled="user.id === store.profile.id" @change="updateRole(user, $event.target.value)">
              <option value="USER">科研用户</option><option value="MANAGER">团队管理员</option><option value="ADMIN">系统管理员</option>
            </select>
            <button class="status-toggle" :class="user.status.toLowerCase()" :disabled="user.id === store.profile.id" @click="toggleStatus(user)">{{ statusLabel(user.status) }}</button>
          </div>
          <p v-if="!users.length && !loading" class="empty-row">没有匹配的用户</p>
        </div>
      </article>

      <article class="panel audit-panel">
        <div class="panel-head"><div><h3>审计日志</h3><p>登录与关键业务操作</p></div></div>
        <div class="audit-list">
          <div v-for="item in audit" :key="item.id">
            <i :class="{ failed: !item.success }"></i>
            <div><strong>{{ item.actor }} · {{ item.action }}</strong><span>{{ item.module }}{{ item.ipAddress ? ` · ${item.ipAddress}` : '' }}</span></div>
            <time>{{ formatTime(item.occurredAt) }}</time>
          </div>
          <p v-if="!audit.length && !loading" class="empty-row">暂无审计记录</p>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { apiErrorMessage, http } from '../api/http'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const loading = ref(false)
const maintaining = ref(false)
const query = ref('')
const users = ref([])
const audit = ref([])
const overview = ref({ totalUsers: 0, activeUsers: 0, disabledUsers: 0, totalPapers: 0, totalTeams: 0, pendingUploads: 0, operationsToday: 0 })
const stats = computed(() => [
  { label: '用户总量', value: overview.value.totalUsers, note: `${overview.value.activeUsers} 个启用账户` },
  { label: '文献总量', value: overview.value.totalPapers, note: '全站有效文献' },
  { label: '团队总量', value: overview.value.totalTeams, note: '协作空间' },
  { label: '待维护项', value: overview.value.pendingUploads, note: `${overview.value.operationsToday} 次今日操作` }
])

async function loadUsers() {
  const { data } = await http.get('/admin/users', { params: { query: query.value } })
  users.value = data
}
async function loadAll() {
  loading.value = true
  try {
    const [overviewResult, auditResult] = await Promise.all([
      http.get('/admin/overview'),
      http.get('/admin/audit', { params: { limit: 100 } }),
      loadUsers()
    ])
    overview.value = overviewResult.data
    audit.value = auditResult.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '系统管理数据读取失败'))
  } finally {
    loading.value = false
  }
}
async function updateRole(user, role) {
  try {
    const { data } = await http.put(`/admin/users/${user.id}/role`, { role })
    Object.assign(user, data)
    ElMessage.success('用户角色已更新')
    await loadAudit()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '用户角色更新失败'))
    await loadUsers()
  }
}
async function toggleStatus(user) {
  const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(`${status === 'DISABLED' ? '禁用' : '启用'}账户 ${user.realName}？`, '账户状态', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' })
    const { data } = await http.put(`/admin/users/${user.id}/status`, { status })
    Object.assign(user, data)
    ElMessage.success('账户状态已更新')
    await Promise.all([loadOverview(), loadAudit()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '账户状态更新失败'))
  }
}
async function cleanup() {
  maintaining.value = true
  try {
    const { data } = await http.post('/admin/maintenance/cleanup')
    ElMessage.success(`已清理 ${data.removedUploads} 条过期上传`)
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '数据维护失败'))
  } finally {
    maintaining.value = false
  }
}
async function loadOverview() { overview.value = (await http.get('/admin/overview')).data }
async function loadAudit() { audit.value = (await http.get('/admin/audit', { params: { limit: 100 } })).data }
function statusLabel(status) { return ({ ACTIVE: '已启用', DISABLED: '已禁用', PENDING: '待激活' })[status] || status }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '从未' }

onMounted(loadAll)
</script>

<style scoped>
.admin-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px}.admin-stats article{display:flex;min-height:104px;flex-direction:column;padding:18px;border:1px solid #e5e9f0;border-radius:8px;background:#fff}.admin-stats span{color:#7c8799;font-size:10px}.admin-stats strong{margin:7px 0 3px;color:#253149;font-size:24px}.admin-stats small{color:#9aa3b2;font-size:9px}.admin-grid{display:grid;grid-template-columns:minmax(650px,1.5fr) minmax(330px,.8fr);gap:16px}.users-panel,.audit-panel{overflow:hidden;box-shadow:none}.admin-search{display:flex;width:220px;height:33px;align-items:center;gap:7px;padding:0 9px;border:1px solid #dfe4ec;border-radius:6px}.admin-search .app-icon{width:14px;color:#9aa3b2}.admin-search input{min-width:0;flex:1;border:0;outline:0;font-size:9px}.table-head,.table-row{display:grid;grid-template-columns:minmax(300px,1.8fr) 90px 125px 75px;align-items:center;gap:10px;padding:0 18px}.table-head{height:38px;color:#929bad;background:#f8f9fb;font-size:8px;font-weight:600}.table-row{min-height:76px;border-top:1px solid #edf0f4;color:#5c697f;font-size:9px}.table-row:first-of-type{border-top:0}.table-row>div:not(.user-cell){display:flex;flex-direction:column;gap:4px}.table-row small{display:block;color:#9aa3b2;font-size:8px}.user-cell strong{display:block;color:#3f4d65;font-size:10px}.user-cell span{display:block;overflow:hidden;margin:3px 0;color:#748097;text-overflow:ellipsis;white-space:nowrap}.table-row select{height:29px;border:1px solid #dfe4ec;border-radius:5px;color:#59667d;background:#fff;font-size:8px}.status-toggle{height:27px;border:0;border-radius:12px;color:#087d63;background:#e3f5ef;font-size:8px}.status-toggle.disabled{color:#b44747;background:#fff0f0}.status-toggle.pending{color:#9a6a1d;background:#fff5df}.status-toggle:disabled,.table-row select:disabled{cursor:not-allowed;opacity:.55}.audit-list{max-height:620px;overflow:auto;padding:4px 17px}.audit-list>div{display:flex;align-items:flex-start;gap:9px;padding:12px 0;border-bottom:1px solid #edf0f4}.audit-list i{width:7px;height:7px;margin-top:4px;flex:0 0 auto;border-radius:50%;background:#10a68a}.audit-list i.failed{background:#db5656}.audit-list>div>div{min-width:0;flex:1}.audit-list strong{display:block;color:#4d5a71;font-size:9px;line-height:1.5}.audit-list span{display:block;margin-top:3px;color:#929bad;font-size:8px}.audit-list time{color:#a0a8b6;font-size:7px;white-space:nowrap}.empty-row{padding:28px;color:#9aa3b2;text-align:center;font-size:9px}
</style>
