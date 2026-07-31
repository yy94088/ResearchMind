<template>
  <div>
    <header class="page-header">
      <div><h2>团队空间</h2><p>与研究伙伴共享资料、批注和知识发现</p></div>
      <button v-if="team && canManage" class="primary-button" @click="inviteVisible = true"><AppIcon name="plus" /> 邀请成员</button>
    </header>

    <section v-if="invitations.length" class="panel invitation-panel">
      <div><strong>你有 {{ invitations.length }} 个待处理的团队邀请</strong><p>接受后即可进入团队空间；当前版本每个账户只能加入一个团队。</p></div>
      <article v-for="invitation in invitations" :key="invitation.teamId">
        <span><AppIcon name="users" /></span>
        <div><h4>{{ invitation.teamName }}</h4><p>{{ invitation.institution || '未填写机构' }} · {{ invitation.inviterName }} 邀请你担任{{ roleLabel(invitation.role) }}</p></div>
        <button class="secondary-button" @click="decideInvitation(invitation,false)">拒绝</button>
        <button class="primary-button" @click="decideInvitation(invitation,true)">接受</button>
      </article>
    </section>

    <section v-if="loading" class="panel team-empty"><span class="analysis-spinner"></span><h3>正在读取团队空间</h3></section>
    <section v-else-if="!team" class="panel team-empty">
      <AppIcon name="users" /><h3>还没有加入团队</h3><p>创建一个真实团队空间，或让团队管理员通过你的注册邮箱发送站内邀请。</p>
      <button class="primary-button" @click="createVisible = true"><AppIcon name="plus" /> 创建团队</button>
    </section>

    <template v-else>
    <section class="team-overview">
      <article class="team-card panel">
        <div class="team-avatar"><AppIcon name="users" /></div>
        <div><small>当前团队 · {{ roleLabel(team.currentUserRole) }}</small><h3>{{ team.name }}</h3><p>{{ team.institution || '未填写机构' }} · 创建于 {{ formatDate(team.createdAt) }}</p></div>
        <button v-if="team.currentUserRole === 'OWNER'" class="secondary-button" @click="openSettings"><AppIcon name="settings" /> 团队设置</button>
      </article>
      <article class="mini-stat panel"><span><AppIcon name="users" /></span><div><strong>{{ acceptedMemberCount }}</strong><small>正式成员</small></div></article>
      <article class="mini-stat panel"><span class="green"><AppIcon name="book" /></span><div><strong>{{ team.sharedPaperCount }}</strong><small>共享文献</small></div></article>
      <article class="mini-stat panel"><span class="orange"><AppIcon name="edit" /></span><div><strong>{{ team.annotationCount }}</strong><small>协作批注</small></div></article>
    </section>

    <section class="team-grid">
      <article class="panel members-panel">
        <div class="panel-head"><div><h3>团队成员</h3><p>管理成员与访问权限</p></div><label class="member-search"><AppIcon name="search" /><input v-model="query" placeholder="搜索成员" /></label></div>
        <div class="member-table-head"><span>成员</span><span>角色</span><span>个人文献</span><span>状态</span><span></span></div>
        <div v-for="member in filteredMembers" :key="member.id" class="member-row">
          <div class="member-info"><span :style="{ background: avatarColor(member.id) }">{{ member.name.slice(0,1) }}</span><div><strong>{{ member.name }} <em v-if="member.id === store.profile.id">你</em></strong><small>{{ member.email }}</small></div></div>
          <div><select :value="member.role" :disabled="!canManage || member.role === 'OWNER' || member.joinStatus !== 'ACCEPTED'" @change="updateRole(member,$event.target.value)"><option value="OWNER">团队所有者</option><option value="MANAGER">团队管理员</option><option value="MEMBER">研究成员</option><option value="GUEST">访客</option></select></div>
          <span><strong>{{ member.paperCount }}</strong> 篇</span>
          <span><i v-if="member.joinStatus === 'ACCEPTED'" class="online"></i>{{ statusLabel(member.joinStatus) }}</span>
          <el-dropdown v-if="canManage && member.role !== 'OWNER'" @command="(command) => memberCommand(command, member)"><button class="more-button"><AppIcon name="more" /></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="remove" divided>移出团队</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          <span v-else></span>
        </div>
        <div v-if="!filteredMembers.length" class="table-empty">没有匹配的成员</div>
      </article>

      <aside class="panel collaboration-panel">
        <div class="panel-head"><div><h3>协作动态</h3><p>最近 7 天</p></div></div>
        <div class="collab-list">
          <article v-for="item in team.activities" :key="item.id">
            <span :style="{ background: avatarColor(String(item.id)) }">{{ item.operatorName.slice(0, 1) }}</span>
            <div><p><strong>{{ item.operatorName }}</strong> {{ item.operation }}</p><small>{{ formatDateTime(item.occurredAt) }}</small></div>
          </article>
          <p v-if="!team.activities.length" class="table-empty">暂无团队动态</p>
        </div>
      </aside>
    </section>

    <section class="panel shared-panel">
      <div class="panel-head"><div><h3>共享专题库</h3><p>按研究主题组织团队文献</p></div><button v-if="team.currentUserRole !== 'GUEST'" class="panel-link" @click="collectionVisible = true"><AppIcon name="plus" /> 新建专题</button></div>
      <div class="collection-grid">
        <article v-for="collection in team.collections" :key="collection.id" @click="openCollection(collection)">
          <div class="collection-icon" :style="{ color: collection.color || '#3156d3', background: `${collection.color || '#3156d3'}18` }"><AppIcon name="book" /></div>
          <div><h4>{{ collection.name }}</h4><p>{{ collection.description || '暂无专题简介' }}</p><span>{{ collection.paperCount }} 篇文献 · 创建于 {{ formatDate(collection.createdAt) }}</span></div>
          <AppIcon name="chevron" />
        </article>
        <p v-if="!team.collections.length" class="table-empty">还没有专题，创建后可继续组织团队文献。</p>
      </div>
    </section>
    </template>

    <el-dialog v-model="inviteVisible" width="500px" title="邀请团队成员">
      <el-form :model="inviteForm" label-position="top">
        <el-form-item label="注册邮箱"><el-input v-model.trim="inviteForm.email" type="email" placeholder="对方已注册 ResearchMind 的邮箱" /></el-form-item>
        <el-form-item label="团队角色"><el-select v-model="inviteForm.role" style="width:100%"><el-option label="研究成员" value="MEMBER" /><el-option label="访客" value="GUEST" /><el-option label="团队管理员" value="MANAGER" /></el-select></el-form-item>
      </el-form>
      <template #footer><button class="secondary-button" @click="inviteVisible = false">取消</button><button class="primary-button" :disabled="!inviteForm.email || submitting" @click="sendInvite">{{ submitting ? '正在发送…' : '发送站内邀请' }}</button></template>
    </el-dialog>

    <el-dialog v-model="createVisible" width="500px" title="创建团队">
      <el-form :model="teamForm" label-position="top">
        <el-form-item label="团队名称"><el-input v-model.trim="teamForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="所属机构"><el-input v-model.trim="teamForm.institution" maxlength="200" /></el-form-item>
        <el-form-item label="团队简介"><el-input v-model="teamForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><button class="secondary-button" @click="createVisible = false">取消</button><button class="primary-button" :disabled="!teamForm.name || submitting" @click="createTeam">{{ submitting ? '正在创建…' : '创建团队' }}</button></template>
    </el-dialog>

    <el-dialog v-model="settingsVisible" width="500px" title="团队设置">
      <el-form :model="teamForm" label-position="top">
        <el-form-item label="团队名称"><el-input v-model.trim="teamForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="所属机构"><el-input v-model.trim="teamForm.institution" maxlength="200" /></el-form-item>
        <el-form-item label="团队简介"><el-input v-model="teamForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><button class="secondary-button" @click="settingsVisible = false">取消</button><button class="primary-button" :disabled="!teamForm.name || submitting" @click="saveSettings">保存设置</button></template>
    </el-dialog>

    <el-dialog v-model="collectionVisible" width="500px" title="新建共享专题">
      <el-form :model="collectionForm" label-position="top">
        <el-form-item label="专题名称"><el-input v-model.trim="collectionForm.name" maxlength="150" /></el-form-item>
        <el-form-item label="专题简介"><el-input v-model="collectionForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
        <el-form-item label="封面颜色"><el-color-picker v-model="collectionForm.color" /></el-form-item>
      </el-form>
      <template #footer><button class="secondary-button" @click="collectionVisible = false">取消</button><button class="primary-button" :disabled="!collectionForm.name || submitting" @click="createCollection">创建专题</button></template>
    </el-dialog>

    <el-dialog v-model="paperManagerVisible" width="620px" :title="`管理专题文献 · ${selectedCollection?.name || ''}`">
      <p class="collection-help">选择你拥有的文献加入专题；其他成员已经分享的内容不会被移除。</p>
      <el-checkbox-group v-model="selectedPaperIds" class="collection-paper-list">
        <el-checkbox v-for="paper in store.papers" :key="paper.id" :value="paper.id">
          <strong>{{ paper.title || paper.titleZh }}</strong><small>{{ paper.authors.slice(0,2).join('、') || '未知作者' }} · {{ paper.year || '年份未知' }}</small>
        </el-checkbox>
      </el-checkbox-group>
      <p v-if="!store.papers.length" class="table-empty">你的文献库为空，请先导入文献。</p>
      <template #footer><button class="secondary-button" @click="paperManagerVisible = false">取消</button><button class="primary-button" :disabled="submitting" @click="saveCollectionPapers">{{ submitting ? '正在保存…' : '保存专题文献' }}</button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { useAppStore } from '../stores/app'
import { apiErrorMessage, http } from '../api/http'

const store=useAppStore();const query=ref('')
const team=ref(null);const invitations=ref([]);const loading=ref(true);const submitting=ref(false)
const inviteVisible=ref(false);const createVisible=ref(false);const settingsVisible=ref(false);const collectionVisible=ref(false);const paperManagerVisible=ref(false)
const inviteForm=reactive({email:'',role:'MEMBER'})
const teamForm=reactive({name:'',institution:'',description:''})
const collectionForm=reactive({name:'',description:'',color:'#3156d3'})
const selectedCollection=ref(null);const selectedPaperIds=ref([])
const canManage=computed(()=>['OWNER','MANAGER'].includes(team.value?.currentUserRole))
const acceptedMemberCount=computed(()=>team.value?.members.filter((member)=>member.joinStatus==='ACCEPTED').length||0)
const filteredMembers=computed(()=>(team.value?.members||[]).filter((member)=>`${member.name} ${member.email}`.toLowerCase().includes(query.value.toLowerCase())))
async function loadWorkspace(){
  loading.value=true
  try{
    const [workspaceResult,invitationResult]=await Promise.all([http.get('/teams/current'),http.get('/teams/invitations')])
    team.value=workspaceResult.data||null;invitations.value=invitationResult.data
  }catch(error){ElMessage.error(apiErrorMessage(error,'团队空间读取失败'))}
  finally{loading.value=false}
}
async function sendInvite(){
  submitting.value=true
  try{const {data}=await http.post(`/teams/${team.value.id}/members`,inviteForm);team.value=data;inviteVisible.value=false;ElMessage.success(`站内邀请已发送至 ${inviteForm.email}`);inviteForm.email='';inviteForm.role='MEMBER'}
  catch(error){ElMessage.error(apiErrorMessage(error,'团队邀请失败'))}
  finally{submitting.value=false}
}
async function memberCommand(command,member){
  if(command!=='remove')return
  try{await ElMessageBox.confirm(`确定将 ${member.name} 移出团队吗？`,'成员管理',{type:'warning',confirmButtonText:'确认移出',cancelButtonText:'取消'});const {data}=await http.delete(`/teams/${team.value.id}/members/${member.id}`);team.value=data;ElMessage.success('成员已移出团队')}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(apiErrorMessage(error,'移出成员失败'))}
}
async function updateRole(member,role){
  try{const {data}=await http.put(`/teams/${team.value.id}/members/${member.id}/role`,{role});team.value=data;ElMessage.success('成员角色已更新')}
  catch(error){ElMessage.error(apiErrorMessage(error,'角色更新失败'));await loadWorkspace()}
}
async function createTeam(){await submitTeam('post','/teams',createVisible,'团队已创建')}
async function saveSettings(){await submitTeam('put',`/teams/${team.value.id}`,settingsVisible,'团队设置已保存')}
async function submitTeam(method,url,dialog,message){
  submitting.value=true
  try{const {data}=await http[method](url,teamForm);team.value=data;dialog.value=false;ElMessage.success(message)}
  catch(error){ElMessage.error(apiErrorMessage(error,message+'失败'))}
  finally{submitting.value=false}
}
function openSettings(){Object.assign(teamForm,{name:team.value.name,institution:team.value.institution||'',description:team.value.description||''});settingsVisible.value=true}
async function createCollection(){
  submitting.value=true
  try{const {data}=await http.post(`/teams/${team.value.id}/collections`,collectionForm);team.value=data;collectionVisible.value=false;Object.assign(collectionForm,{name:'',description:'',color:'#3156d3'});ElMessage.success('共享专题已创建')}
  catch(error){ElMessage.error(apiErrorMessage(error,'专题创建失败'))}
  finally{submitting.value=false}
}
function openCollection(collection){
  selectedCollection.value=collection
  selectedPaperIds.value=[...(collection.currentUserPaperIds||[])]
  paperManagerVisible.value=true
}
async function saveCollectionPapers(){
  if(!selectedCollection.value)return
  submitting.value=true
  try{const {data}=await http.put(`/teams/${team.value.id}/collections/${selectedCollection.value.id}/papers`,{paperIds:selectedPaperIds.value});team.value=data;paperManagerVisible.value=false;ElMessage.success('专题文献已保存')}
  catch(error){ElMessage.error(apiErrorMessage(error,'专题文献保存失败'))}
  finally{submitting.value=false}
}
async function decideInvitation(invitation,accepted){
  try{await http.put(`/teams/${invitation.teamId}/invitation`,{accepted});ElMessage.success(accepted?'已加入团队':'已拒绝邀请');await loadWorkspace()}
  catch(error){ElMessage.error(apiErrorMessage(error,'邀请处理失败'))}
}
function roleLabel(role){return {OWNER:'所有者',MANAGER:'管理员',MEMBER:'研究成员',GUEST:'访客'}[role]||role}
function statusLabel(status){return {ACCEPTED:'已加入',PENDING:'待接受',REJECTED:'已拒绝'}[status]||status}
function formatDate(value){return value?new Date(value).toLocaleDateString('zh-CN',{year:'numeric',month:'long'}):'时间未知'}
function formatDateTime(value){return value?new Date(value).toLocaleString('zh-CN'):'时间未知'}
function avatarColor(value=''){return ['#3156d3','#7956cf','#0e9f78','#e27a32'][[...value].reduce((sum,char)=>sum+char.charCodeAt(0),0)%4]}
onMounted(loadWorkspace)
</script>

<style scoped>
.team-overview{display:grid;grid-template-columns:minmax(430px,1.8fr) repeat(3,minmax(150px,.55fr));gap:14px}.team-card{display:flex;align-items:center;gap:13px;padding:17px}.team-avatar{display:grid;width:45px;height:45px;place-items:center;border-radius:10px;color:#fff;background:linear-gradient(145deg,#3156d3,#263f9d)}.team-card>div:nth-child(2){min-width:0;flex:1}.team-card small{color:#97a0b0;font-size:8px}.team-card h3{margin:3px 0;color:#3b485f;font-size:13px}.team-card p{margin:0;color:#9aa3b1;font-size:8px}.team-card .secondary-button{height:32px;padding:0 10px;font-size:8px}.mini-stat{display:flex;align-items:center;gap:12px;padding:17px}.mini-stat>span{display:grid;width:38px;height:38px;place-items:center;border-radius:8px;color:#3156d3;background:#edf1ff}.mini-stat>span.green{color:#0e9f78;background:#e9f8f3}.mini-stat>span.orange{color:#df7933;background:#fff2e8}.mini-stat .app-icon{width:17px}.mini-stat div{display:flex;flex-direction:column}.mini-stat strong{color:#344158;font-size:19px}.mini-stat small{color:#929bad;font-size:8px}.team-grid{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(320px,.65fr);gap:14px;margin-top:14px}.member-search{display:flex;width:180px;height:30px;align-items:center;gap:6px;padding:0 8px;border:1px solid #e1e5ed;border-radius:5px}.member-search .app-icon{width:12px;color:#9ba4b3}.member-search input{min-width:0;flex:1;border:0;outline:0;font-size:8px}.member-table-head,.member-row{display:grid;grid-template-columns:minmax(220px,1.3fr) 125px 90px 100px 35px;align-items:center;padding:0 18px}.member-table-head{height:37px;color:#969fad;background:#f8f9fb;font-size:8px;font-weight:600}.member-row{min-height:65px;border-top:1px solid #edf0f4;color:#738095;font-size:9px}.member-info{display:flex;align-items:center;gap:10px}.member-info>span{display:grid;width:32px;height:32px;place-items:center;border-radius:8px;color:#fff;font-size:10px}.member-info strong{display:block;color:#4b5870;font-size:9px}.member-info em{margin-left:4px;padding:1px 4px;border-radius:3px;color:#3156d3;background:#edf1ff;font-size:6px;font-style:normal}.member-info small{display:block;margin-top:3px;color:#9ca5b3;font-size:7px}.member-row select{max-width:112px;padding:5px;border:1px solid #e1e5ed;border-radius:4px;color:#6e7b8f;background:#fff;font-size:8px}.member-row>span strong{color:#58657b}.online{display:inline-block;width:5px;height:5px;margin-right:4px;border-radius:50%;background:#13a681}.more-button{display:grid;width:28px;height:28px;place-items:center;border:0;color:#98a2b1;background:transparent}.more-button .app-icon{width:14px}.collab-list{padding:6px 18px}.collab-list article{display:flex;gap:10px;padding:11px 0;border-bottom:1px solid #eff1f5}.collab-list article:last-child{border:0}.collab-list article>span{display:grid;width:28px;height:28px;flex:0 0 auto;place-items:center;border-radius:7px;color:#fff;font-size:9px}.collab-list p{margin:0;color:#778399;font-size:8px}.collab-list p strong{color:#4f5c73}.collab-list a{display:block;overflow:hidden;max-width:210px;margin:4px 0;color:#3156d3;font-size:8px;text-overflow:ellipsis;white-space:nowrap;cursor:pointer}.collab-list small{color:#a4acb8;font-size:7px}.shared-panel{margin-top:14px}.collection-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:15px}.collection-grid article{display:grid;grid-template-columns:42px 1fr 15px;align-items:center;gap:11px;padding:14px;border:1px solid #e5e8ef;border-radius:8px;cursor:pointer;transition:.15s}.collection-grid article:hover{border-color:#bec9ee;box-shadow:0 7px 17px rgba(24,42,80,.06)}.collection-icon{display:grid;width:42px;height:42px;place-items:center;border-radius:9px}.collection-icon .app-icon{width:18px}.collection-grid h4{margin:0;color:#48556c;font-size:10px}.collection-grid p{margin:4px 0;color:#8d97a8;font-size:8px}.collection-grid div>span{color:#a1a9b6;font-size:7px}.collection-grid>article>.app-icon{width:13px;color:#a1a9b6}.team-empty{display:flex;min-height:360px;align-items:center;justify-content:center;flex-direction:column;color:#8d97a8;text-align:center}.team-empty>.app-icon{width:45px;color:#3156d3}.team-empty h3{margin:16px 0 6px;color:#48556c}.team-empty p{max-width:430px;font-size:10px;line-height:1.8}.team-empty button{margin-top:12px}.analysis-spinner{width:28px;height:28px;border:3px solid #e4e8f1;border-top-color:#3156d3;border-radius:50%;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.invitation-panel{margin-bottom:14px;padding:16px 18px}.invitation-panel>div>strong{color:#3f4c63;font-size:11px}.invitation-panel>div>p{margin:4px 0 10px;color:#929cad;font-size:8px}.invitation-panel article{display:flex;align-items:center;gap:10px;padding:11px;border-radius:7px;background:#f7f9fc}.invitation-panel article>span{display:grid;width:34px;height:34px;place-items:center;border-radius:8px;color:#3156d3;background:#e9eeff}.invitation-panel article>div{flex:1}.invitation-panel h4{margin:0;color:#48556c;font-size:10px}.invitation-panel article p{margin:4px 0 0;color:#8d97a8;font-size:8px}.invitation-panel button{height:31px}.table-empty{grid-column:1/-1;padding:20px;color:#99a2b1;text-align:center;font-size:9px}.collection-help{margin-top:0;color:#7f8a9d;font-size:9px}.collection-paper-list{display:flex;max-height:360px;overflow:auto;flex-direction:column;border:1px solid #e5e8ef;border-radius:8px}.collection-paper-list :deep(.el-checkbox){height:auto;margin:0;padding:11px 13px;border-bottom:1px solid #edf0f4}.collection-paper-list :deep(.el-checkbox__label){display:flex;min-width:0;flex-direction:column}.collection-paper-list strong{overflow:hidden;max-width:500px;color:#4d5a71;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.collection-paper-list small{margin-top:3px;color:#99a2b1;font-size:7px}
</style>
