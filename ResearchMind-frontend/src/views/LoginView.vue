<template>
  <div class="login-page">
    <section class="login-visual">
      <div class="visual-grid"></div>
      <div class="login-brand">
        <span class="login-logo"><AppIcon name="graph" /></span>
        <div><strong>ResearchMind</strong><small>科研知识管理平台</small></div>
      </div>
      <div class="visual-copy">
        <span class="eyebrow">RESEARCH, CONNECTED.</span>
        <h1>让每一篇文献<br />成为知识网络的一部分</h1>
        <p>集中管理科研资料，用智能分析发现关联，<br />从海量阅读中提炼真正有价值的洞察。</p>
        <div class="visual-metrics">
          <div><strong>PDF</strong><span>真实解析与私有存储</span></div>
          <div><strong>DeepSeek</strong><span>论文解读与问答</span></div>
          <div><strong>MySQL</strong><span>账户与科研数据持久化</span></div>
        </div>
      </div>
      <div class="network-art" aria-hidden="true">
        <span v-for="node in nodes" :key="node.id" class="network-node" :style="node.style">
          {{ node.label }}
        </span>
        <i class="network-line line-1"></i><i class="network-line line-2"></i>
        <i class="network-line line-3"></i><i class="network-line line-4"></i>
        <i class="network-line line-5"></i>
      </div>
      <p class="visual-footer">© 2026 ResearchMind · 让科研更专注</p>
    </section>

    <section class="login-panel">
      <div class="login-box">
        <div class="login-heading">
          <span class="mobile-logo"><AppIcon name="graph" /></span>
          <h2>{{ heading.title }}</h2>
          <p>{{ heading.description }}</p>
        </div>

        <form v-if="mode === 'login'" @submit.prevent="submitLogin">
          <label class="field-label">用户名或邮箱</label>
          <div class="login-field" :class="{ focused: focused === 'account' }">
            <span>@</span>
            <input v-model.trim="loginForm.account" autocomplete="username" placeholder="请输入用户名或邮箱" required @focus="focused = 'account'" @blur="focused = ''" />
          </div>
          <label class="field-label">
            <span>登录密码</span>
            <button type="button" @click="forgotPassword">忘记密码？</button>
          </label>
          <div class="login-field" :class="{ focused: focused === 'password' }">
            <span>⌁</span>
            <input v-model="loginForm.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入登录密码" required @focus="focused = 'password'" @blur="focused = ''" />
            <button type="button" class="eye-button" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button>
          </div>
          <label class="remember-row"><input v-model="loginForm.remember" type="checkbox" /> <span>保持登录状态</span></label>
          <button class="login-submit" type="submit" :disabled="submitting">
            {{ submitting ? '正在进入工作空间…' : '登录 ResearchMind' }} <span>→</span>
          </button>
        </form>

        <form v-else-if="mode === 'register'" @submit.prevent="submitRegister">
          <label class="field-label">用户名</label>
          <div class="login-field"><span>人</span><input v-model.trim="registerForm.username" autocomplete="username" pattern="[a-zA-Z0-9_]+" minlength="3" maxlength="50" placeholder="字母、数字或下划线" required /></div>
          <label class="field-label">姓名</label>
          <div class="login-field"><span>名</span><input v-model.trim="registerForm.realName" maxlength="50" placeholder="你的姓名" required /></div>
          <label class="field-label">邮箱地址</label>
          <div class="login-field"><span>@</span><input v-model.trim="registerForm.email" type="email" autocomplete="email" placeholder="name@example.com" required /></div>
          <label class="field-label">设置密码</label>
          <div class="login-field"><span>⌁</span><input v-model="registerForm.password" type="password" autocomplete="new-password" placeholder="8–72 位字符" minlength="8" maxlength="72" required /></div>
          <button class="login-submit" type="submit" :disabled="submitting">
            {{ submitting ? '正在创建账户…' : '创建账户并开始使用' }} <span>→</span>
          </button>
        </form>

        <form v-else @submit.prevent="submitPasswordReset">
          <label class="field-label">新密码</label>
          <div class="login-field"><span>⌁</span><input v-model="resetForm.password" type="password" autocomplete="new-password" placeholder="8–72 位字符" minlength="8" maxlength="72" required /></div>
          <label class="field-label">确认新密码</label>
          <div class="login-field"><span>⌁</span><input v-model="resetForm.confirm" type="password" autocomplete="new-password" placeholder="再次输入新密码" minlength="8" maxlength="72" required /></div>
          <button class="login-submit" type="submit" :disabled="submitting">
            {{ submitting ? '正在更新密码…' : '确认重置密码' }} <span>→</span>
          </button>
        </form>

        <div class="login-divider"><span>或</span></div>
        <button class="status-button" type="button" :disabled="checkingBackend" @click="checkBackend">
          <AppIcon name="sparkles" /> {{ checkingBackend ? '正在连接真实后端…' : '检查后端连接状态' }}
        </button>
        <p class="mode-switch">
          {{ mode === 'login' ? '还没有账户？' : '准备登录？' }}
          <button type="button" @click="switchMode">
            {{ mode === 'login' ? '免费注册' : '返回登录' }}
          </button>
        </p>
      </div>
      <p class="terms">登录即表示你同意《服务条款》和《隐私政策》</p>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { useAppStore } from '../stores/app'
import { apiErrorMessage, http } from '../api/http'

const router = useRouter()
const route = useRoute()
const store = useAppStore()
const resetToken = typeof route.query.resetToken === 'string' ? route.query.resetToken : ''
const mode = ref(resetToken ? 'reset' : 'login')
const focused = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const checkingBackend = ref(false)
const loginForm = reactive({ account: '', password: '', remember: true })
const registerForm = reactive({ username: '', realName: '', email: '', password: '' })
const resetForm = reactive({ password: '', confirm: '' })
const heading = computed(() => ({
  login: { title: '欢迎回来', description: '登录你的工作空间，继续探索知识' },
  register: { title: '创建研究账户', description: '只需一分钟，即可建立你的科研知识库' },
  reset: { title: '设置新密码', description: '重置链接只能使用一次，并将在 15 分钟后过期' }
})[mode.value])

const nodes = [
  { id: 1, label: 'GNN', style: 'left:12%;top:18%' },
  { id: 2, label: 'NLP', style: 'left:58%;top:8%' },
  { id: 3, label: 'RAG', style: 'left:68%;top:55%' },
  { id: 4, label: 'XAI', style: 'left:23%;top:68%' },
  { id: 5, label: 'AI', style: 'left:43%;top:36%;width:64px;height:64px;background:#526fe1;color:#fff' }
]

function goAfterLogin() {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
  router.replace(target)
}

async function submitLogin() {
  submitting.value = true
  try {
    await store.login(loginForm.account, loginForm.password, loginForm.remember)
    ElMessage.success('登录成功，欢迎回来')
    goAfterLogin()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '用户名或密码错误'))
  } finally {
    submitting.value = false
  }
}

async function checkBackend() {
  checkingBackend.value = true
  try {
    const { data } = await http.get('/system/status')
    ElMessage.success(`后端正常 · MySQL ${data.database} · Redis ${data.redis} · MinIO ${data.objectStorage}`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '后端状态检查失败'))
  } finally {
    checkingBackend.value = false
  }
}

async function submitRegister() {
  submitting.value = true
  try {
    await store.register({ ...registerForm })
    ElMessage.success('账户创建成功，已自动登录')
    goAfterLogin()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '账户创建失败'))
  } finally {
    submitting.value = false
  }
}

async function forgotPassword() {
  try {
    const { value: email } = await ElMessageBox.prompt(
      '请输入注册邮箱，系统将发送一封 15 分钟有效的重置邮件。',
      '找回密码',
      { inputType: 'email', inputPlaceholder: 'name@example.com', confirmButtonText: '发送邮件', cancelButtonText: '取消', inputPattern: /^[^@\s]+@[^@\s]+\.[^@\s]+$/, inputErrorMessage: '请输入有效邮箱地址' }
    )
    const { data } = await http.post('/auth/password-reset/request', { email })
    ElMessage.success(data.message)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '密码重置邮件发送失败'))
  }
}

async function submitPasswordReset() {
  if (!resetToken) return ElMessage.error('密码重置链接缺少令牌')
  if (resetForm.password !== resetForm.confirm) return ElMessage.warning('两次输入的新密码不一致')
  submitting.value = true
  try {
    await http.post('/auth/password-reset/confirm', { token: resetToken, newPassword: resetForm.password })
    ElMessage.success('密码已重置，请使用新密码登录')
    mode.value = 'login'
    router.replace('/login')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '密码重置失败'))
  } finally {
    submitting.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  if (route.query.resetToken) router.replace('/login')
}
</script>

<style scoped>
.login-page { display: grid; min-height: 100vh; grid-template-columns: minmax(560px, 1.12fr) minmax(500px, .88fr); background: #fff; }
.login-visual { position: relative; overflow: hidden; padding: 48px 58px; color: #fff; background: linear-gradient(145deg, #101b40 0%, #172961 55%, #233d8f 100%); }
.visual-grid { position: absolute; inset: 0; opacity: .12; background-image: linear-gradient(rgba(255,255,255,.2) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.2) 1px, transparent 1px); background-size: 48px 48px; mask-image: linear-gradient(to bottom, black, transparent 85%); }
.login-brand { position: relative; z-index: 3; display: flex; align-items: center; gap: 12px; }
.login-logo, .mobile-logo { display: grid; width: 40px; height: 40px; place-items: center; border: 1px solid rgba(255,255,255,.2); border-radius: 10px; background: rgba(255,255,255,.09); }
.login-brand div { display: flex; flex-direction: column; }
.login-brand strong { font-size: 19px; }
.login-brand small { margin-top: 1px; color: #96a8d8; font-size: 9px; letter-spacing: .1em; }
.visual-copy { position: relative; z-index: 3; margin-top: 17vh; }
.eyebrow { color: #91a8ff; font-size: 10px; font-weight: 700; letter-spacing: .22em; }
.visual-copy h1 { margin: 17px 0; font-size: clamp(32px, 3.2vw, 50px); font-weight: 650; line-height: 1.3; letter-spacing: -1.7px; }
.visual-copy p { color: #aebce0; font-size: 14px; line-height: 1.9; }
.visual-metrics { display: flex; gap: 42px; margin-top: 42px; }
.visual-metrics div { display: flex; flex-direction: column; gap: 4px; }
.visual-metrics strong { font-size: 18px; }
.visual-metrics span { color: #8192bd; font-size: 9px; }
.network-art { position: absolute; right: 4%; bottom: 5%; width: 300px; height: 260px; opacity: .48; }
.network-node { position: absolute; z-index: 2; display: grid; width: 47px; height: 47px; place-items: center; border: 1px solid rgba(158,178,255,.44); border-radius: 50%; color: #aebdff; background: rgba(19,35,81,.85); font-size: 9px; }
.network-line { position: absolute; z-index: 1; height: 1px; transform-origin: left; background: rgba(135,157,235,.45); }
.line-1 { left: 53px; top: 61px; width: 109px; transform: rotate(23deg); }.line-2 { left: 157px; top: 108px; width: 103px; transform: rotate(-31deg); }
.line-3 { left: 161px; top: 120px; width: 111px; transform: rotate(45deg); }.line-4 { left: 70px; top: 193px; width: 111px; transform: rotate(-45deg); }
.line-5 { left: 52px; top: 65px; width: 133px; transform: rotate(72deg); }
.visual-footer { position: absolute; bottom: 27px; left: 58px; color: #6475a4; font-size: 9px; }
.login-panel { position: relative; display: grid; place-items: center; padding: 50px; }
.login-box { width: 100%; max-width: 390px; }
.login-heading { margin-bottom: 33px; }
.login-heading h2 { margin: 0 0 8px; color: #17223a; font-size: 29px; letter-spacing: -.7px; }
.login-heading p { margin: 0; color: #8a95a8; font-size: 12px; }
.mobile-logo { display: none; margin-bottom: 20px; color: var(--primary); background: var(--primary-soft); }
.field-label { display: flex; align-items: center; justify-content: space-between; margin: 17px 0 7px; color: #48556d; font-size: 11px; font-weight: 600; }
.field-label button { border: 0; color: var(--primary); background: transparent; font-size: 10px; }
.login-field { display: flex; height: 46px; align-items: center; gap: 10px; padding: 0 13px; border: 1px solid #dfe4ed; border-radius: 7px; transition: .18s; }
.login-field.focused { border-color: #7990e5; box-shadow: 0 0 0 3px rgba(49,86,211,.08); }
.login-field > span { width: 17px; color: #9aa5b8; text-align: center; font-size: 12px; }
.login-field input { min-width: 0; flex: 1; border: 0; outline: 0; color: #263249; font-size: 12px; }
.login-field input::placeholder { color: #aeb6c5; }
.eye-button { border: 0; color: #8b95a6; background: transparent; font-size: 9px; }
.remember-row { display: flex; align-items: center; gap: 6px; margin: 15px 0 20px; color: #758096; font-size: 10px; }
.remember-row input { accent-color: var(--primary); }
.login-submit, .status-button { display: flex; width: 100%; height: 46px; align-items: center; justify-content: center; gap: 9px; border-radius: 7px; font-size: 12px; font-weight: 600; }
.login-submit { border: 0; color: #fff; background: linear-gradient(100deg, #3156d3, #4266dc); box-shadow: 0 8px 22px rgba(49,86,211,.22); }
.login-submit:disabled { opacity: .7; cursor: wait; }
.login-submit span { font-size: 18px; font-weight: 400; }
.login-divider { display: flex; align-items: center; gap: 12px; margin: 24px 0; color: #a3abba; font-size: 9px; }
.login-divider::before,.login-divider::after { height: 1px; flex: 1; background: #e8ebf1; content: ''; }
.status-button { border: 1px solid #dfe4ed; color: #4f5e78; background: #fff; }
.status-button .app-icon { width: 17px; color: #7257d4; }
.mode-switch { margin-top: 27px; color: #8791a2; text-align: center; font-size: 10px; }
.mode-switch button { border: 0; color: var(--primary); background: transparent; font-weight: 600; }
.terms { position: absolute; bottom: 24px; color: #acb3bf; font-size: 9px; }
@media(max-width: 1180px) { .login-page { grid-template-columns: 1fr; }.login-visual { display:none }.mobile-logo{display:grid}.login-panel{min-height:100vh}.login-box{max-width:430px} }
</style>
