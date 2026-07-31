<template>
  <div>
    <header class="page-header"><div><h2>个人设置</h2><p>管理你的账户资料、偏好和数据</p></div></header>
    <section class="settings-layout">
      <nav class="settings-nav panel">
        <button v-for="item in sections" :key="item.key" :class="{ active: section === item.key }" @click="section = item.key"><AppIcon :name="item.icon" />{{ item.label }}</button>
      </nav>
      <main class="settings-content panel">
        <template v-if="section === 'profile'">
          <div class="settings-head"><h3>个人资料</h3><p>这些信息将展示在你的团队空间中</p></div>
          <div class="avatar-setting">
            <span><img v-if="store.avatarSrc" :src="store.avatarSrc" alt="个人头像" />{{ store.avatarSrc ? '' : (store.profile.name?.slice(0, 1) || '研') }}</span>
            <div><strong>个人头像</strong><p>支持真实 JPG、PNG 文件，文件不超过 2 MB，私有保存于 MinIO。</p><label class="avatar-upload">{{ avatarUploading ? '正在上传…' : '更换头像' }}<input type="file" accept="image/jpeg,image/png" :disabled="avatarUploading" @change="uploadAvatar" /></label><button v-if="store.avatarSrc" class="avatar-remove" :disabled="avatarUploading" @click="removeAvatar">移除头像</button></div>
          </div>
          <el-form :model="form" label-position="top" class="profile-form">
            <div class="form-grid"><el-form-item label="姓名"><el-input v-model="form.name" maxlength="50" /></el-form-item><el-form-item label="邮箱地址"><el-input v-model="form.email" type="email" maxlength="100" /></el-form-item></div>
            <div class="form-grid"><el-form-item label="所属机构"><el-input v-model="form.institution" maxlength="200" /></el-form-item><el-form-item label="主要研究方向"><el-input v-model="form.direction" maxlength="300" /></el-form-item></div>
            <el-form-item label="个人简介"><el-input v-model="form.bio" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
            <button class="primary-button" type="button" :disabled="savingProfile" @click="saveProfile">{{ savingProfile ? '正在保存…' : '保存修改' }}</button>
          </el-form>
        </template>
        <template v-else-if="section === 'security'">
          <div class="settings-head"><h3>安全设置</h3><p>定期更新密码以保护你的研究数据</p></div>
          <el-form label-position="top" class="security-form">
            <el-form-item label="当前密码" :error="currentPasswordError">
              <el-input v-model="password.current" type="password" autocomplete="current-password" maxlength="72" show-password @blur="verifyCurrentPassword(false)" />
              <small v-if="currentPasswordState === 'checking'" class="password-state">正在验证当前密码…</small>
              <small v-else-if="currentPasswordState === 'valid'" class="password-state valid">当前密码正确</small>
            </el-form-item>
            <el-form-item label="新密码"><el-input v-model="password.next" type="password" autocomplete="new-password" maxlength="72" show-password /></el-form-item>
            <el-form-item label="确认新密码"><el-input v-model="password.confirm" type="password" autocomplete="new-password" maxlength="72" show-password /></el-form-item>
            <button class="primary-button" type="button" :disabled="changingPassword" @click="changePassword">{{ changingPassword ? '正在更新…' : '更新密码' }}</button>
          </el-form>
          <div class="login-record">
            <h4>最近登录</h4>
            <div v-for="(record,index) in loginHistory" :key="record.id">
              <span><AppIcon name="check" /></span>
              <p><strong>{{ deviceLabel(record.userAgent) }}</strong><small>{{ record.ipAddress || '未知 IP' }} · {{ formatLoginTime(record.loginTime) }}</small></p>
              <em v-if="index === 0">最近</em>
            </div>
            <p v-if="!loginHistory.length" class="empty-record">暂无登录记录</p>
          </div>
        </template>
        <template v-else-if="section === 'preferences'">
          <div class="settings-head"><h3>使用偏好</h3><p>定制阅读与文献管理体验 · {{ preferenceStatus }}</p></div>
          <div class="preference-list">
            <label v-for="item in preferences" :key="item.key"><div><strong>{{ item.title }}</strong><p>{{ item.description }}</p></div><el-switch v-model="item.value" :disabled="preferenceLoading" @change="savePreferences" /></label>
          </div>
        </template>
        <template v-else>
          <div class="settings-head"><h3>数据管理</h3><p>备份个人数据或重新同步当前工作空间</p></div>
          <div class="data-actions">
            <article><span><AppIcon name="download" /></span><div><h4>导出个人数据备份</h4><p>下载资料、偏好、文献及阅读记录、阅读笔记和最近登录记录的 JSON 备份（不含 PDF 原文件）。</p></div><button class="secondary-button" :disabled="exportingData" @click="exportData">{{ exportingData ? '正在生成…' : '导出数据' }}</button></article>
            <article><span class="danger"><AppIcon name="refresh" /></span><div><h4>刷新服务器数据</h4><p>丢弃浏览器中的临时界面状态，重新读取服务器中的账户、文献、动态、头像、偏好和登录记录。</p></div><button class="danger-button" :disabled="refreshingData" @click="refreshData">{{ refreshingData ? '正在读取…' : '重新读取' }}</button></article>
          </div>
        </template>
      </main>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { useAppStore } from '../stores/app'
import { apiErrorMessage, http } from '../api/http'

const store=useAppStore();const section=ref('profile')
const sections=[{key:'profile',label:'个人资料',icon:'users'},{key:'security',label:'账户安全',icon:'check'},{key:'preferences',label:'使用偏好',icon:'settings'},{key:'data',label:'数据管理',icon:'download'}]
const form=reactive({...store.profile});const password=reactive({current:'',next:'',confirm:''})
const savingProfile=ref(false);const changingPassword=ref(false);const loginHistory=ref([])
const currentPasswordState=ref('idle');const currentPasswordError=ref('')
let passwordVerifySequence=0
const avatarUploading=ref(false)
const exportingData=ref(false);const refreshingData=ref(false)
const preferenceLoading=ref(false);const preferenceStatus=ref('保存在 Redis')
const preferences=reactive([
  {key:'resumeReading',title:'继续阅读时回到上次页码',description:'打开已阅读的 PDF 时自动定位到服务端保存的阅读位置',value:true},
  {key:'autoSaveReadingProgress',title:'自动记录阅读进度与时长',description:'仅在 PDF 成功显示、页面可见且窗口处于焦点时记录',value:true},
  {key:'confirmPaperDeletion',title:'删除文献前二次确认',description:'从文献库移除单篇或批量文献前显示确认对话框',value:true},
  {key:'defaultGridView',title:'文献库默认使用卡片视图',description:'进入“我的文献”时优先使用卡片视图，而不是列表视图',value:false}
])
let lastSavedPreferences={...store.preferences}
async function saveProfile(){
  const name=form.name?.trim()
  const email=form.email?.trim()
  if(!name)return ElMessage.warning('姓名不能为空')
  if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email))return ElMessage.warning('请输入有效的邮箱地址')
  savingProfile.value=true
  try{await store.updateProfile({...form,name,email});Object.assign(form,store.profile);ElMessage.success('个人资料已保存到服务器')}
  catch(error){ElMessage.error(apiErrorMessage(error,'个人资料保存失败'))}
  finally{savingProfile.value=false}
}
async function changePassword(){
  if(!password.current)return ElMessage.warning('请输入当前密码')
  if(password.next.length<8)return ElMessage.warning('新密码至少需要 8 位')
  if(password.next!==password.confirm)return ElMessage.warning('两次输入的新密码不一致')
  if(password.next===password.current)return ElMessage.warning('新密码不能与当前密码相同')
  if(currentPasswordState.value!=='valid'){
    const verified=await verifyCurrentPassword(true)
    if(!verified)return ElMessage.error(currentPasswordError.value||'当前密码不正确')
  }
  changingPassword.value=true
  try{
    await http.put('/auth/password',{currentPassword:password.current,newPassword:password.next})
    password.current='';password.next='';password.confirm='';ElMessage.success('密码已安全更新')
  }catch(error){
    if(error.response?.data?.code==='CURRENT_PASSWORD_INCORRECT'){
      currentPasswordState.value='invalid'
      currentPasswordError.value='当前密码不正确'
    }
    ElMessage.error(apiErrorMessage(error,'密码更新失败'))
  }
  finally{changingPassword.value=false}
}
async function verifyCurrentPassword(showEmptyError=false){
  const value=password.current
  if(!value){
    if(showEmptyError)currentPasswordError.value='请输入当前密码'
    return false
  }
  const sequence=++passwordVerifySequence
  currentPasswordState.value='checking';currentPasswordError.value=''
  try{
    await http.post('/auth/password/verify',{currentPassword:value})
    if(sequence!==passwordVerifySequence||value!==password.current)return false
    currentPasswordState.value='valid'
    return true
  }catch(error){
    if(sequence!==passwordVerifySequence||value!==password.current)return false
    currentPasswordState.value='invalid'
    currentPasswordError.value=apiErrorMessage(error,'当前密码验证失败')
    return false
  }
}
watch(()=>password.current,()=>{
  passwordVerifySequence++
  currentPasswordState.value='idle'
  currentPasswordError.value=''
})
async function exportData(){
  exportingData.value=true
  try{
    const {data,headers}=await http.get('/account/export',{responseType:'blob',timeout:30000})
    const disposition=headers['content-disposition']||''
    const matched=disposition.match(/filename="?([^";]+)"?/i)
    const fallback=`ResearchMind-data-backup-${new Date().toISOString().slice(0,10)}.json`
    const url=URL.createObjectURL(data)
    const anchor=document.createElement('a')
    anchor.href=url;anchor.download=matched?.[1]||fallback
    document.body.appendChild(anchor);anchor.click();anchor.remove()
    window.setTimeout(()=>URL.revokeObjectURL(url),0)
    ElMessage.success('个人数据备份已导出')
  }catch(error){ElMessage.error(apiErrorMessage(error,'个人数据备份导出失败'))}
  finally{exportingData.value=false}
}
async function refreshData(){
  refreshingData.value=true
  try{
    const restored=await store.restoreSession()
    if(!restored)throw new Error('SESSION_INVALID')
    await Promise.all([
      store.loadPapers(),
      store.loadActivities(),
      store.loadPreferences(),
      store.loadAvatar(),
      loadLoginHistory(true)
    ])
    Object.assign(form,store.profile)
    applyPreferences(store.preferences)
    lastSavedPreferences={...store.preferences}
    preferenceStatus.value='已从服务器读取'
    ElMessage.success('已重新读取服务器数据')
  }
  catch(error){ElMessage.error(apiErrorMessage(error,'服务器数据刷新失败'))}
  finally{refreshingData.value=false}
}
function deviceLabel(agent=''){
  const browser=agent.includes('Edg/')?'Edge':agent.includes('Chrome/')?'Chrome':agent.includes('Firefox/')?'Firefox':agent.includes('Safari/')?'Safari':'浏览器'
  const system=agent.includes('Windows')?'Windows':agent.includes('Android')?'Android':agent.includes('iPhone')||agent.includes('iPad')?'iOS':agent.includes('Mac OS')?'macOS':agent.includes('Linux')?'Linux':'未知系统'
  return `${browser} · ${system}`
}
function formatLoginTime(value){return value?new Date(value).toLocaleString('zh-CN'):'时间未知'}
async function loadLoginHistory(rethrow=false){
  try{const {data}=await http.get('/auth/login-history');loginHistory.value=data}
  catch(error){
    ElMessage.error(apiErrorMessage(error,'最近登录记录读取失败'))
    if(rethrow)throw error
  }
}
async function loadPreferences(){
  preferenceLoading.value=true
  try{
    const data=await store.loadPreferences()
    applyPreferences(data)
    lastSavedPreferences={...data}
    preferenceStatus.value='已从服务器读取'
  }catch(error){preferenceStatus.value='读取失败';ElMessage.error(apiErrorMessage(error,'使用偏好读取失败'))}
  finally{preferenceLoading.value=false}
}
async function savePreferences(){
  preferenceLoading.value=true;preferenceStatus.value='正在保存…'
  const values=Object.fromEntries(preferences.map((item)=>[item.key,item.value]))
  try{
    const saved=await store.savePreferences(values)
    applyPreferences(saved)
    lastSavedPreferences={...saved}
    preferenceStatus.value='已保存'
  }
  catch(error){
    applyPreferences(lastSavedPreferences)
    preferenceStatus.value='保存失败，已恢复原设置'
    ElMessage.error(apiErrorMessage(error,'使用偏好保存失败'))
  }
  finally{preferenceLoading.value=false}
}
function applyPreferences(values){
  preferences.forEach((item)=>{item.value=Boolean(values[item.key])})
}
async function uploadAvatar(event){
  const input=event.target;const file=input.files?.[0]
  if(!file)return
  if(!['image/jpeg','image/png'].includes(file.type)){ElMessage.error('请选择真实的 JPG 或 PNG 图片');input.value='';return}
  if(file.size>2*1024*1024){ElMessage.error('头像文件不能超过 2 MB');input.value='';return}
  avatarUploading.value=true
  try{await store.uploadAvatar(file);ElMessage.success('头像已保存到 MinIO')}
  catch(error){ElMessage.error(apiErrorMessage(error,'头像上传失败'))}
  finally{avatarUploading.value=false;input.value=''}
}
async function removeAvatar(){
  avatarUploading.value=true
  try{await store.removeAvatar();ElMessage.success('头像已移除')}
  catch(error){ElMessage.error(apiErrorMessage(error,'头像移除失败'))}
  finally{avatarUploading.value=false}
}
onMounted(()=>{loadLoginHistory();loadPreferences()})
</script>

<style scoped>
.settings-layout{display:grid;grid-template-columns:200px minmax(650px,820px);gap:15px;align-items:start}.settings-nav{display:flex;padding:8px;flex-direction:column;box-shadow:none}.settings-nav button{display:flex;height:41px;align-items:center;gap:10px;padding:0 11px;border:0;border-radius:6px;color:#6f7c91;background:#fff;font-size:10px}.settings-nav button.active{color:#3156d3;background:#eef2ff;font-weight:600}.settings-nav .app-icon{width:15px}.settings-content{min-height:570px;padding:25px 28px;box-shadow:none}.settings-head{padding-bottom:18px;border-bottom:1px solid #e9ecf2}.settings-head h3{margin:0;color:#344158;font-size:14px}.settings-head p{margin:5px 0 0;color:#959ead;font-size:9px}.avatar-setting{display:flex;align-items:center;gap:13px;padding:22px 0}.avatar-setting>span{display:grid;overflow:hidden;width:58px;height:58px;place-items:center;border-radius:14px;color:#fff;background:linear-gradient(145deg,#3d61dc,#263f9d);font-size:18px}.avatar-setting>span img{width:100%;height:100%;object-fit:cover}.avatar-setting strong{color:#4b5870;font-size:10px}.avatar-setting p{margin:4px 0 7px;color:#9aa3b2;font-size:8px}.avatar-upload,.avatar-remove{display:inline-block;padding:4px 7px;border:1px solid #dfe4ec;border-radius:4px;color:#66738a;background:#fff;font-size:8px;cursor:pointer}.avatar-upload input{display:none}.avatar-remove{margin-left:6px;color:#c85a5a}.profile-form{max-width:700px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}.security-form{max-width:430px;margin-top:22px}.password-state{display:block;margin-top:5px;color:#8b96a8;font-size:8px}.password-state.valid{color:#0e9f78}.login-record{margin-top:35px;padding-top:20px;border-top:1px solid #e9ecf2}.login-record h4{color:#4b5870;font-size:10px}.login-record>div{display:flex;align-items:center;gap:10px;padding:12px;border-radius:7px;background:#f7f9fc}.login-record>div>span{display:grid;width:29px;height:29px;place-items:center;border-radius:7px;color:#0e9f78;background:#e1f5ee}.login-record .app-icon{width:13px}.login-record p{display:flex;flex:1;flex-direction:column;margin:0}.login-record strong{color:#556278;font-size:9px}.login-record small{margin-top:3px;color:#98a1b0;font-size:7px}.login-record em{color:#0e9f78;font-size:8px;font-style:normal}.preference-list{margin-top:7px}.preference-list label{display:flex;align-items:center;justify-content:space-between;padding:18px 2px;border-bottom:1px solid #edf0f4}.preference-list strong{color:#4a576e;font-size:10px}.preference-list p{margin:4px 0 0;color:#98a1b0;font-size:8px}.data-actions article{display:flex;align-items:center;gap:13px;padding:20px 0;border-bottom:1px solid #edf0f4}.data-actions article>span{display:grid;width:40px;height:40px;place-items:center;border-radius:9px;color:#3156d3;background:#edf1ff}.data-actions article>span.danger{color:#d95a5a;background:#fff0f0}.data-actions .app-icon{width:17px}.data-actions article>div{flex:1}.data-actions h4{margin:0;color:#4c5970;font-size:10px}.data-actions p{margin:5px 0 0;color:#98a1b0;font-size:8px}.danger-button{height:35px;padding:0 12px;border:1px solid #efc5c5;border-radius:6px;color:#d55757;background:#fff7f7;font-size:9px}
</style>
