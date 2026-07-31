<template>
  <div>
    <header class="page-header">
      <div><h2>AI 阅读助手 <span class="ai-beta">BETA</span></h2><p>快速理解论文内容，提炼核心贡献与研究启发</p></div>
      <button class="secondary-button" :disabled="analyzing || !selectedPaper" @click="generateAnalysis(Boolean(analysis))">
        <AppIcon name="refresh" /> {{ analyzing ? 'DeepSeek 分析中…' : (analysis ? '重新生成解读' : '生成 AI 解读') }}
      </button>
    </header>

    <section class="ai-layout">
      <aside class="paper-selector panel">
        <div class="selector-head"><h3>选择文献</h3><span>{{ store.papers.length }} 篇</span></div>
        <label class="selector-search"><AppIcon name="search" /><input v-model="query" placeholder="搜索文献…" /></label>
        <div class="selector-list">
          <button v-for="paper in filteredPapers" :key="paper.id" :class="{ active: selectedId === paper.id }" @click="selectPaper(paper.id)">
            <span class="mini-pdf">PDF</span>
            <div><strong>{{ paper.title || paper.titleZh }}</strong><small>{{ paper.authors[0] || '未知作者' }} · {{ paper.year || '年份未知' }}</small></div>
            <i v-if="selectedId === paper.id"><AppIcon name="check" /></i>
          </button>
        </div>
      </aside>

      <main v-if="selectedPaper" class="ai-reader panel">
        <div class="reader-paper">
          <span class="reader-pdf">PDF</span>
          <div><small>{{ selectedPaper.area }}<template v-if="(selectedPaper.areas?.length || 1) > 1"> · 另有 {{ selectedPaper.areas.length - 1 }} 个关联领域</template></small><h3>{{ selectedPaper.title || selectedPaper.titleZh }}</h3><p>{{ selectedPaper.authors.join(' · ') || '未知作者' }} · {{ selectedPaper.journal || '来源未知' }} · {{ selectedPaper.year || '年份未知' }}</p></div>
          <button @click="router.push('/library')"><AppIcon name="file" /> 文献详情</button>
        </div>
        <nav class="reader-tabs">
          <button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key"><AppIcon :name="tab.icon" />{{ tab.label }}</button>
        </nav>
        <div class="reader-content">
          <div v-if="analysisLoading" class="analysis-empty">
            <span class="analysis-spinner"></span><h4>正在读取已有解读</h4>
          </div>
          <div v-else-if="activeTab !== 'notes' && !analysis" class="analysis-empty">
            <AppIcon name="sparkles" /><h4>尚未生成真实 AI 解读</h4>
            <p>点击下方按钮后，后端会把论文正文或摘要发送给 DeepSeek，并将结果保存到 MySQL。</p>
            <button :disabled="analyzing" @click="generateAnalysis(false)">{{ analyzing ? '正在分析，请稍候…' : '使用 DeepSeek V4 Flash 开始分析' }}</button>
          </div>
          <div v-else-if="activeTab === 'summary'" class="content-section">
            <div class="ai-generated"><AppIcon name="sparkles" /> DeepSeek 生成内容 <span>{{ analysisMeta }}</span></div>
            <div v-if="analysis.metadataFilledFields?.length" class="metadata-filled">
              <AppIcon name="check" />
              <div><strong>已自动补全文献信息</strong><p>{{ analysis.metadataFilledFields.join('、') }}。已有信息未被覆盖。</p></div>
            </div>
            <h4>一句话总结</h4><blockquote>{{ analysis.summary }}</blockquote>
            <h4>研究背景与问题</h4><p>{{ analysis.background }}</p>
            <h4>方法概览</h4><p>{{ analysis.methodOverview }}</p>
          </div>
          <div v-else-if="activeTab === 'contributions'" class="content-section">
            <div class="ai-generated"><AppIcon name="sparkles" /> 核心贡献提取 <span>{{ analysis.contributions.length }} 项</span></div>
            <div class="contribution-list">
              <article v-for="(item, index) in analysis.contributions" :key="item"><span>0{{ index + 1 }}</span><div><h4>{{ item }}</h4><p>{{ analysis.innovations[index] || '该项由 DeepSeek 根据论文资料提取。' }}</p></div></article>
            </div>
            <h4>创新性判断</h4><div class="innovation-meter"><span>{{ innovationLabel }}</span><i><b :style="{ width: `${analysis.innovationScore}%` }"></b></i><strong>{{ analysis.innovationScore }} / 100</strong></div>
            <h4>局限</h4><ul class="ai-list"><li v-for="item in analysis.limitations" :key="item">{{ item }}</li></ul>
            <h4>可延伸方向</h4><ul class="ai-list"><li v-for="item in analysis.futureDirections" :key="item">{{ item }}</li></ul>
          </div>
          <div v-else-if="activeTab === 'method'" class="content-section">
            <div class="ai-generated"><AppIcon name="graph" /> 方法脉络梳理</div>
            <div class="method-flow"><template v-for="(step, index) in analysis.methodSteps" :key="step"><i v-if="index">→</i><div><span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ step }}</strong><small>论文方法步骤</small></div></template></div>
            <h4>关键技术</h4><div class="tech-tags"><span v-for="tag in selectedPaper.tags" :key="tag">{{ tag }}</span></div>
            <h4>实验结论</h4><p>{{ analysis.experimentConclusion }}</p>
          </div>
          <div v-else class="content-section notes-content">
            <div class="ai-generated"><AppIcon name="edit" /> 阅读笔记 <span>{{ noteStatus }}</span></div>
            <textarea v-model="noteContent" :disabled="noteLoading" placeholder="记录你的思考、疑问和研究启发…"></textarea>
            <div class="note-prompts"><span>灵感提示</span><button @click="appendNote('这篇论文与我的研究问题有什么联系？')">研究关联</button><button @click="appendNote('可以复现或改进的实验设计：')">实验设计</button><button @click="appendNote('尚未解决的局限与开放问题：')">开放问题</button></div>
          </div>
        </div>
      </main>

      <aside v-if="selectedPaper" class="ai-chat panel">
        <div class="chat-head"><span><AppIcon name="sparkles" /></span><div><h3>向论文提问</h3><small><i></i> DeepSeek V4 Flash 已就绪</small></div></div>
        <div ref="chatBody" class="chat-body">
          <div class="message assistant"><span><AppIcon name="sparkles" /></span><div>你好，我会依据这篇论文的正文或摘要回答。你可以询问方法细节、实验结论、局限或复现建议。</div></div>
          <template v-for="message in messages" :key="message.id">
            <div class="message" :class="message.role">
              <span v-if="message.role === 'assistant'"><AppIcon name="sparkles" /></span>
              <div>{{ message.text }}</div>
            </div>
          </template>
          <div v-if="answering" class="message assistant"><span><AppIcon name="sparkles" /></span><div class="typing"><i></i><i></i><i></i></div></div>
        </div>
        <div class="quick-questions">
          <button :disabled="answering" @click="ask('这篇论文的主要局限是什么？')">主要局限</button>
          <button :disabled="answering" @click="ask('请给出三个可延伸的研究方向')">研究启发</button>
          <button :disabled="answering" @click="ask('如何复现这篇论文？')">复现建议</button>
        </div>
        <form class="chat-input" @submit.prevent="ask(input)">
          <textarea v-model="input" rows="2" placeholder="输入你想了解的问题…" @keydown.enter.exact.prevent="ask(input)"></textarea>
          <button type="submit" :disabled="!input.trim() || answering">↑</button>
        </form>
        <p class="chat-notice">AI 内容仅供科研参考，请结合原文判断</p>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { apiErrorMessage, http } from '../api/http'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const query = ref('')
const initialId = typeof route.query.paper === 'string' && store.papers.some((paper) => paper.id === route.query.paper) ? route.query.paper : store.papers[0]?.id
const selectedId = ref(initialId)
const activeTab = ref('summary')
const input = ref('')
const answering = ref(false)
const analyzing = ref(false)
const analysisLoading = ref(false)
const analysis = ref(null)
const messages = ref([])
const noteContent = ref('')
const noteLoading = ref(false)
const noteStatus = ref('从服务器自动保存')
let noteSaveTimer
let suppressNoteSave = false
const chatBody = ref()
const tabs = [{key:'summary',label:'智能摘要',icon:'sparkles'},{key:'contributions',label:'核心贡献',icon:'star'},{key:'method',label:'方法脉络',icon:'graph'},{key:'notes',label:'阅读笔记',icon:'edit'}]
const filteredPapers = computed(()=>store.papers.filter((paper)=>[paper.title,paper.titleZh,...paper.authors].join(' ').toLowerCase().includes(query.value.toLowerCase())))
const selectedPaper = computed(()=>store.papers.find((paper)=>paper.id===selectedId.value))
const innovationLabel = computed(()=>{
  const score=analysis.value?.innovationScore||0
  if(score>=80)return '显著创新'
  if(score>=60)return '渐进式创新'
  if(score>=40)return '局部创新'
  return '创新证据有限'
})
const analysisMeta = computed(()=>{
  if(!analysis.value)return ''
  const time=analysis.value.generatedAt ? new Date(analysis.value.generatedAt).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}) : ''
  return `${analysis.value.model} · ${analysis.value.tokenUsage} tokens${time?` · ${time}`:''}`
})

watch(noteContent,(content)=>{
  if(suppressNoteSave||!selectedId.value)return
  clearTimeout(noteSaveTimer)
  noteStatus.value='等待保存…'
  const paperId=selectedId.value
  noteSaveTimer=setTimeout(()=>saveNote(paperId,content),700)
})
watch(selectedId,(id,previousId)=>{
  messages.value=[];activeTab.value='summary';analysis.value=null
  clearTimeout(noteSaveTimer)
  if(previousId&&noteStatus.value==='等待保存…')saveNote(previousId,noteContent.value)
  if(id){loadAnalysis(id);loadNote(id)}
})
function selectPaper(id){selectedId.value=id;router.replace({path:'/ai',query:{paper:id}})}
async function loadAnalysis(id){
  analysisLoading.value=true
  try{
    const {data}=await http.get(`/ai/papers/${id}/analysis`)
    if(selectedId.value===id)analysis.value=data||null
  }catch(error){
    if(selectedId.value===id)ElMessage.error(apiErrorMessage(error,'读取 AI 解读失败'))
  }finally{
    if(selectedId.value===id)analysisLoading.value=false
  }
}
async function generateAnalysis(refresh=false){
  if(!selectedPaper.value||analyzing.value)return
  const paper={...selectedPaper.value}
  analyzing.value=true
  try{
    const {data}=await http.post(`/ai/papers/${paper.id}/analysis`,null,{params:{refresh},timeout:180000})
    if(selectedId.value===paper.id)analysis.value=data
    await store.loadPapers().catch(()=>{})
    store.loadActivitiesSafely()
    const filled=data.metadataFilledFields||[]
    ElMessage.success(filled.length
      ? `AI 解读已生成，并补全：${filled.join('、')}`
      : refresh?'DeepSeek 已重新生成解读':'DeepSeek 论文解读已生成并保存')
  }catch(error){
    ElMessage.error(apiErrorMessage(error,'DeepSeek 论文解读失败'))
  }finally{
    analyzing.value=false
  }
}
async function ask(question){
  const value=question?.trim();if(!value||answering.value)return
  const paper={...selectedPaper.value}
  messages.value.push({id:Date.now(),role:'user',text:value});input.value='';answering.value=true
  nextTick(scrollBottom)
  try{
    const {data}=await http.post(`/ai/papers/${paper.id}/questions`,{question:value},{timeout:180000})
    if(selectedId.value===paper.id)messages.value.push({id:Date.now()+1,role:'assistant',text:data.answer})
    store.loadActivitiesSafely()
  }catch(error){
    const message=apiErrorMessage(error,'DeepSeek 论文问答失败')
    if(selectedId.value===paper.id)messages.value.push({id:Date.now()+1,role:'assistant',text:`暂时无法回答：${message}`})
    ElMessage.error(message)
  }finally{
    answering.value=false
    nextTick(scrollBottom)
  }
}
function scrollBottom(){if(chatBody.value)chatBody.value.scrollTop=chatBody.value.scrollHeight}
function appendNote(text){noteContent.value=`${noteContent.value}${noteContent.value?'\n\n':''}${text}`}
async function loadNote(paperId){
  noteLoading.value=true;suppressNoteSave=true;noteStatus.value='正在读取…'
  try{
    const {data}=await http.get(`/papers/${paperId}/note`)
    if(selectedId.value===paperId){noteContent.value=data?.content||'';noteStatus.value=data?'已从服务器读取':'尚未创建笔记'}
  }catch(error){
    if(selectedId.value===paperId){noteContent.value='';noteStatus.value='读取失败';ElMessage.error(apiErrorMessage(error,'阅读笔记读取失败'))}
  }finally{
    if(selectedId.value===paperId)noteLoading.value=false
    await nextTick();suppressNoteSave=false
  }
}
async function saveNote(paperId,content){
  noteStatus.value='正在保存…'
  try{
    await http.put(`/papers/${paperId}/note`,{content})
    if(selectedId.value===paperId&&noteContent.value===content)noteStatus.value='已保存到服务器'
  }catch(error){
    if(selectedId.value===paperId)noteStatus.value='保存失败'
    ElMessage.error(apiErrorMessage(error,'阅读笔记保存失败'))
  }
}
onMounted(()=>{if(selectedPaper.value){loadAnalysis(selectedPaper.value.id);loadNote(selectedPaper.value.id)}})
onBeforeUnmount(()=>{clearTimeout(noteSaveTimer);if(selectedId.value&&noteStatus.value==='等待保存…')saveNote(selectedId.value,noteContent.value)})
</script>

<style scoped>
.ai-beta{display:inline-block;padding:2px 5px;border-radius:4px;color:#7154ca;background:#ece7fb;vertical-align:middle;font-size:7px;letter-spacing:.08em}.ai-layout{display:grid;height:calc(100vh - 151px);min-height:650px;grid-template-columns:235px minmax(460px,1fr) 315px;gap:13px}.paper-selector,.ai-reader,.ai-chat{overflow:hidden;box-shadow:none}.selector-head{display:flex;align-items:center;justify-content:space-between;padding:17px 15px 10px}.selector-head h3{margin:0;color:#39465d;font-size:12px}.selector-head span{color:#9ba4b3;font-size:8px}.selector-search{display:flex;height:32px;align-items:center;gap:7px;margin:0 12px 9px;padding:0 9px;border:1px solid #e2e6ed;border-radius:5px}.selector-search .app-icon{width:13px;color:#a1a9b7}.selector-search input{min-width:0;flex:1;border:0;outline:0;font-size:9px}.selector-list{height:calc(100% - 86px);overflow:auto}.selector-list button{display:flex;width:100%;align-items:flex-start;gap:9px;padding:11px 12px;border:0;border-left:3px solid transparent;background:#fff;text-align:left}.selector-list button:hover{background:#fafbfe}.selector-list button.active{border-left-color:#3156d3;background:#f2f5ff}.mini-pdf{display:grid;width:28px;height:34px;flex:0 0 auto;place-items:center;border-radius:4px;color:#cf5a5a;background:#fff0f0;font-size:6px;font-weight:700}.selector-list button>div{min-width:0;flex:1}.selector-list strong{display:-webkit-box;overflow:hidden;color:#536078;font-size:9px;line-height:1.5;-webkit-line-clamp:2;-webkit-box-orient:vertical}.selector-list small{display:block;margin-top:4px;overflow:hidden;color:#9ba4b3;font-size:7px;text-overflow:ellipsis;white-space:nowrap}.selector-list button>i{display:grid;width:16px;height:16px;place-items:center;border-radius:50%;color:#fff;background:#3156d3}.selector-list button>i .app-icon{width:9px}
.ai-reader{display:flex;flex-direction:column}.reader-paper{display:flex;align-items:center;gap:11px;padding:15px 18px;border-bottom:1px solid #e9ecf2}.reader-pdf{display:grid;width:37px;height:44px;place-items:center;border-radius:5px;color:#d05b5b;background:#fff0f0;font-size:8px;font-weight:700}.reader-paper>div{min-width:0;flex:1}.reader-paper small{color:#3156d3;font-size:8px}.reader-paper h3{overflow:hidden;margin:3px 0;color:#344158;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.reader-paper p{margin:0;color:#98a1b0;font-size:8px}.reader-paper>button{display:flex;height:30px;align-items:center;gap:5px;padding:0 8px;border:1px solid #e0e4ec;border-radius:5px;color:#68758a;background:#fff;font-size:8px}.reader-paper>button .app-icon{width:12px}.reader-tabs{display:flex;height:42px;padding:0 15px;border-bottom:1px solid #e9ecf2}.reader-tabs button{position:relative;display:flex;align-items:center;gap:5px;padding:0 13px;border:0;color:#8993a5;background:#fff;font-size:9px}.reader-tabs button.active{color:#3156d3;font-weight:600}.reader-tabs button.active:after{position:absolute;right:8px;bottom:-1px;left:8px;height:2px;background:#3156d3;content:''}.reader-tabs .app-icon{width:13px}.reader-content{overflow:auto;flex:1;padding:22px 25px}.ai-generated{display:flex;align-items:center;gap:6px;margin-bottom:20px;color:#7358c9;font-size:9px;font-weight:600}.ai-generated .app-icon{width:14px}.ai-generated span{margin-left:auto;color:#9ca4b3;font-size:7px;font-weight:400}.content-section h4{margin:19px 0 8px;color:#435067;font-size:10px}.content-section>p{margin:0;color:#657187;font-size:9px;line-height:1.9}.content-section blockquote{margin:0;padding:14px 16px;border-left:3px solid #6178d8;color:#4f5c73;background:#f6f8fd;font-size:10px;line-height:1.8}.contribution-list article{display:flex;gap:12px;padding:13px 0;border-bottom:1px solid #edf0f4}.contribution-list article>span{display:grid;width:29px;height:29px;place-items:center;border-radius:7px;color:#3156d3;background:#edf1ff;font-size:8px;font-weight:700}.contribution-list h4{margin:0 0 5px}.contribution-list p{margin:0;color:#8a94a5;font-size:8px}.innovation-meter{display:flex;align-items:center;gap:10px;color:#6d798d;font-size:9px}.innovation-meter>i{overflow:hidden;height:5px;flex:1;border-radius:5px;background:#edf0f4}.innovation-meter b{display:block;height:100%;background:linear-gradient(90deg,#3156d3,#7956cf)}.innovation-meter strong{color:#554879}.method-flow{display:flex;align-items:center;justify-content:space-between;margin:15px 0 25px}.method-flow div{display:flex;width:82px;height:80px;flex-direction:column;align-items:center;justify-content:center;border:1px solid #e3e7ef;border-radius:8px;background:#fafbfe}.method-flow div>span{color:#3156d3;font-size:8px;font-weight:700}.method-flow strong{margin:5px 0;color:#4f5c72;font-size:9px}.method-flow small{color:#a0a8b6;font-size:7px}.method-flow>i{color:#afb6c2;font-style:normal}.tech-tags{display:flex;gap:7px;flex-wrap:wrap}.tech-tags span{padding:5px 9px;border-radius:5px;color:#5c68a0;background:#eef1fb;font-size:8px}.notes-content{height:100%}.notes-content textarea{width:100%;height:calc(100% - 85px);padding:15px;border:1px solid #e0e5ed;border-radius:8px;outline:0;resize:none;color:#5e6b81;font-size:10px;line-height:1.8}.note-prompts{display:flex;align-items:center;gap:6px;margin-top:9px}.note-prompts span{color:#9aa3b2;font-size:8px}.note-prompts button{padding:4px 7px;border:1px solid #e0e4ec;border-radius:4px;color:#758197;background:#fff;font-size:7px}
.analysis-empty{display:flex;height:100%;min-height:280px;align-items:center;justify-content:center;flex-direction:column;color:#8d97a8;text-align:center}.analysis-empty>.app-icon{width:34px;color:#7659ca}.analysis-empty h4{margin:14px 0 5px;color:#4e5a70;font-size:12px}.analysis-empty p{max-width:330px;font-size:9px;line-height:1.8}.analysis-empty button{margin-top:12px;padding:9px 14px;border:0;border-radius:6px;color:#fff;background:#3156d3;font-size:9px}.analysis-empty button:disabled{background:#aeb8ca}.analysis-spinner{width:24px;height:24px;border:3px solid #e4e8f1;border-top-color:#3156d3;border-radius:50%;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.ai-list{margin:0;padding-left:18px;color:#657187;font-size:9px;line-height:1.9}
.metadata-filled{display:flex;align-items:flex-start;gap:9px;margin-bottom:17px;padding:11px 13px;border-radius:7px;color:#0e8b6b;background:#ecf8f3}.metadata-filled>.app-icon{width:14px;flex:0 0 auto}.metadata-filled strong{display:block;font-size:9px}.metadata-filled p{margin:3px 0 0;color:#68887e;font-size:8px}
.ai-chat{display:flex;flex-direction:column}.chat-head{display:flex;align-items:center;gap:9px;padding:16px;border-bottom:1px solid #e9ecf2}.chat-head>span{display:grid;width:33px;height:33px;place-items:center;border-radius:8px;color:#7154c9;background:#ece7fb}.chat-head .app-icon{width:16px}.chat-head h3{margin:0;color:#3f4c63;font-size:11px}.chat-head small{display:block;margin-top:3px;color:#939dad;font-size:7px}.chat-head small i{display:inline-block;width:5px;height:5px;margin-right:4px;border-radius:50%;background:#16aa82}.chat-body{overflow:auto;flex:1;padding:15px}.message{display:flex;margin-bottom:13px;gap:7px}.message>span{display:grid;width:25px;height:25px;flex:0 0 auto;place-items:center;border-radius:6px;color:#7257c9;background:#eee9fb}.message>span .app-icon{width:12px}.message>div{max-width:87%;padding:10px 11px;border-radius:4px 9px 9px;color:#657187;background:#f4f6fa;font-size:8px;line-height:1.7}.message.user{justify-content:flex-end}.message.user>div{border-radius:9px 4px 9px 9px;color:#fff;background:#3e5fcf}.typing{display:flex;gap:3px}.typing i{width:4px;height:4px;border-radius:50%;background:#8994a8;animation:pulse 1s infinite}.typing i:nth-child(2){animation-delay:.15s}.typing i:nth-child(3){animation-delay:.3s}@keyframes pulse{50%{opacity:.25}}
.quick-questions{display:flex;gap:5px;padding:0 12px 8px;flex-wrap:wrap}.quick-questions button{padding:5px 7px;border:1px solid #e2e6ed;border-radius:12px;color:#758197;background:#fff;font-size:7px}.chat-input{position:relative;margin:0 12px;padding:9px 38px 9px 10px;border:1px solid #dfe4ec;border-radius:7px}.chat-input textarea{width:100%;border:0;outline:0;resize:none;color:#556278;font-size:9px}.chat-input button{position:absolute;right:7px;bottom:7px;display:grid;width:27px;height:27px;place-items:center;border:0;border-radius:6px;color:#fff;background:#3156d3}.chat-input button:disabled{background:#b6bfce}.chat-notice{margin:7px 0 10px;color:#afb6c1;text-align:center;font-size:7px}
</style>
