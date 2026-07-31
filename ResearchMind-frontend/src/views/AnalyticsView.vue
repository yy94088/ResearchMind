<template>
  <div>
    <header class="page-header">
      <div><h2>研究分析</h2><p>从文献数据中发现趋势、热点与研究脉络</p></div>
      <div class="header-actions">
        <select v-model="range" class="range-select"><option>近 12 个月</option><option>近 6 个月</option><option>全部时间</option></select>
        <button class="primary-button" @click="exportReport"><AppIcon name="download" /> 导出分析报告</button>
      </div>
    </header>

    <section class="insight-banner">
      <span class="insight-icon"><AppIcon name="sparkles" /></span>
      <div><small>DATA INSIGHT</small><h3>{{ insight.title }}</h3><p>{{ insight.description }}</p></div>
      <button @click="router.push('/ai')">让 AI 深入分析 <span>→</span></button>
    </section>

    <section class="analysis-grid top-grid">
      <article class="panel wide-panel">
        <div class="panel-head"><div><h3>文献增长与阅读趋势</h3><p>按导入月份统计新增，按进度达到 100% 的月份统计完成阅读</p></div><div class="mini-legend"><span><i class="blue"></i>新增文献</span><span><i class="green"></i>完成阅读</span></div></div>
        <div ref="trendEl" class="large-chart"></div>
      </article>
      <article class="panel">
        <div class="panel-head"><div><h3>研究领域构成</h3><p>当前知识库分布</p></div></div>
        <div ref="donutEl" class="donut-chart"></div>
        <div class="donut-summary"><strong>{{ store.papers.length }}</strong><span>文献总数</span></div>
      </article>
    </section>

    <section class="analysis-grid middle-grid">
      <article class="panel keyword-panel">
        <div class="panel-head"><div><h3>高频关键词</h3><p>字号代表出现频率，颜色仅用于视觉区分</p></div><button class="panel-link" @click="shuffleCloud"><AppIcon name="refresh" /> 换一批</button></div>
        <div class="word-cloud">
          <button v-for="(word, index) in cloudWords" :key="`${word.name}-${cloudKey}`" :style="{ fontSize: `${word.size}px`, color: word.color, transform: `rotate(${word.rotate}deg)` }" @click="searchKeyword(word.name)">{{ word.name }}</button>
        </div>
      </article>
      <article class="panel author-panel">
        <div class="panel-head"><div><h3>活跃作者</h3><p>按文献关联与合作次数排序</p></div></div>
        <div class="author-list">
          <div v-for="(author, index) in activeAuthors" :key="author.name">
            <span class="rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
            <span class="author-avatar" :style="{ background: author.color }">{{ author.name.slice(0, 1) }}</span>
            <div><strong>{{ author.name }}</strong><small>{{ author.field }}</small></div>
            <span class="paper-number"><strong>{{ author.count }}</strong> 篇</span>
          </div>
          <p v-if="!activeAuthors.length" class="analysis-empty">导入带作者信息的文献后，这里会生成真实作者榜。</p>
        </div>
      </article>
    </section>

    <section class="panel evolution-panel">
      <div class="panel-head"><div><h3>关键词演化路径</h3><p>观察核心主题随时间的出现和热度变化</p></div><div class="mini-legend"><span><i class="hot"></i>热度上升</span><span><i class="steady"></i>保持稳定</span><span><i class="cool"></i>热度下降</span></div></div>
      <div ref="evolutionEl" class="evolution-chart"></div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import AppIcon from '../components/AppIcon.vue'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const router = useRouter()
const range = ref('近 12 个月')
const trendEl = ref()
const donutEl = ref()
const evolutionEl = ref()
const cloudKey = ref(0)
let charts = []

const palette = ['#3156d3','#7956cf','#0e9f78','#df7933','#3d8fb4','#d25d7b']
const paperAreaNames = (paper) => paper.areas?.length
  ? paper.areas.map((area) => area.name)
  : [paper.area || '未分类']
const tagCounts = computed(() => {
  const counts = {}
  store.papers.flatMap((paper) => paper.tags).forEach((tag) => { counts[tag] = (counts[tag] || 0) + 1 })
  return Object.entries(counts).sort((a,b) => b[1] - a[1])
})
const cloudWords = computed(() => tagCounts.value.slice(0, 20).map(([name, count], index) => ({
  name,
  size: 11 + count * 5 + (index % 4),
  color: palette[index % palette.length],
  rotate: [-5, 0, 3, 0, -2][(index + cloudKey.value) % 5]
})))
const activeAuthors=computed(()=>{
  const authors={}
  store.papers.forEach((paper)=>paper.authors.forEach((name)=>{
    const item=authors[name]||(authors[name]={name,count:0,areas:{}})
    item.count++;item.areas[paper.area]=(item.areas[paper.area]||0)+1
  }))
  return Object.values(authors).sort((a,b)=>b.count-a.count).slice(0,5).map((author,index)=>({
    name:author.name,
    count:author.count,
    field:Object.entries(author.areas).sort((a,b)=>b[1]-a[1])[0]?.[0]||'研究方向未分类',
    color:palette[index%palette.length]
  }))
})
const insight=computed(()=>{
  if(!store.papers.length)return {title:'知识库尚无可分析文献',description:'导入论文后，系统会根据真实领域、关键词和作者数据生成概览。'}
  const areas={}
  store.papers.forEach((paper)=>paperAreaNames(paper).forEach((area)=>{areas[area]=(areas[area]||0)+1}))
  const [topArea,areaCount]=Object.entries(areas).sort((a,b)=>b[1]-a[1])[0]
  const totalAreaLinks=Object.values(areas).reduce((sum,count)=>sum+count,0)||1
  const topTags=tagCounts.value.slice(0,3).map(([tag])=>tag)
  return {
    title:`当前文献主要集中在“${topArea}”`,
    description:`该领域占全部领域关联的 ${Math.round(areaCount/totalAreaLinks*100)}%；高频关键词为 ${topTags.join('、')||'尚未提取'}。以上结论仅根据当前知识库实时统计。`
  }
})
const trendData=computed(()=>{
  const monthCount=range.value==='近 6 个月'?6:range.value==='全部时间'?Math.min(24,Math.max(12,store.papers.length)):12
  const now=new Date();const months=[]
  for(let offset=monthCount-1;offset>=0;offset--){const date=new Date(now.getFullYear(),now.getMonth()-offset,1);months.push({key:`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`,label:`${date.getMonth()+1}月`})}
  return {labels:months.map((item)=>item.label),added:months.map((item)=>store.papers.filter((paper)=>paper.uploadDate?.startsWith(item.key)).length),read:months.map((item)=>store.papers.filter((paper)=>paper.read&&paper.lastReadTime?.startsWith(item.key)).length)}
})
const evolutionData=computed(()=>{
  const years=[...new Set(store.papers.map((paper)=>paper.year).filter(Boolean))].sort().slice(-6)
  const tags=tagCounts.value.slice(0,6).map(([tag])=>tag)
  const points=[]
  tags.forEach((tag,tagIndex)=>years.forEach((year,yearIndex)=>{
    const count=store.papers.filter((paper)=>paper.year===year&&paper.tags.includes(tag)).length
    if(count)points.push([yearIndex,tagIndex,Math.min(36,8+count*7),count])
  }))
  return {years:years.map(String),tags,points}
})

function initCharts() {
  const trend = echarts.init(trendEl.value)
  trend.setOption({
    grid:{left:42,right:24,top:28,bottom:33},tooltip:{trigger:'axis',borderWidth:0},xAxis:{type:'category',data:trendData.value.labels,axisLine:{lineStyle:{color:'#e7eaf0'}},axisTick:{show:false},axisLabel:{color:'#929cad',fontSize:9}},yAxis:{type:'value',minInterval:1,splitLine:{lineStyle:{color:'#eef1f5',type:'dashed'}},axisLabel:{color:'#9ba4b3',fontSize:9}},
    series:[{name:'新增文献',type:'bar',barWidth:12,data:trendData.value.added,itemStyle:{color:'#4565d2',borderRadius:[3,3,0,0]}},{name:'完成阅读',type:'line',smooth:true,symbol:'circle',symbolSize:5,data:trendData.value.read,lineStyle:{color:'#13a484',width:2.5},itemStyle:{color:'#13a484'}}]
  })
  const donut = echarts.init(donutEl.value)
  const areaCounts = {}
  store.papers.forEach((paper) => paperAreaNames(paper).forEach((area) => { areaCounts[area] = (areaCounts[area] || 0) + 1 }))
  donut.setOption({tooltip:{trigger:'item',borderWidth:0},series:[{type:'pie',radius:['61%','78%'],center:['50%','50%'],label:{show:true,formatter:'{b}\n{d}%',fontSize:8,color:'#68758a'},labelLine:{length:8,length2:7,lineStyle:{color:'#c8ced8'}},itemStyle:{borderColor:'#fff',borderWidth:3},data:Object.entries(areaCounts).map(([name,value],i)=>({name,value,itemStyle:{color:palette[i%palette.length]}}))}]})
  const evolution = echarts.init(evolutionEl.value)
  evolution.setOption({
    grid:{left:90,right:30,top:25,bottom:32},tooltip:{formatter:(params)=>`${evolutionData.value.tags[params.value[1]]} · ${evolutionData.value.years[params.value[0]]}：${params.value[3]} 篇`,borderWidth:0},xAxis:{type:'category',data:evolutionData.value.years,axisLine:{lineStyle:{color:'#e4e8ef'}},axisTick:{show:false},axisLabel:{fontSize:9,color:'#929cad'}},yAxis:{type:'category',data:evolutionData.value.tags,axisLine:{show:false},axisTick:{show:false},axisLabel:{fontSize:9,color:'#657187'}},
    series:[{type:'scatter',symbolSize:(value)=>value[2],data:evolutionData.value.points,itemStyle:{color:'#3156d3',opacity:.72}}]
  })
  charts = [trend, donut, evolution]
}
function resize() { charts.forEach((chart) => chart.resize()) }
function shuffleCloud() { cloudKey.value++ }
function searchKeyword(word) { router.push({ path:'/library', query:{ q:word } }) }
function exportReport() {
  const content = `ResearchMind 研究分析报告\n生成日期：${new Date().toLocaleDateString('zh-CN')}\n\n文献总量：${store.papers.length}\n研究领域：${store.areas.length}\n高频关键词：${tagCounts.value.slice(0,8).map(([tag])=>tag).join('、')||'无'}\n\n数据洞察：${insight.value.title}\n${insight.value.description}`
  const blob = new Blob([content],{type:'text/plain;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='ResearchMind-研究分析报告.txt';a.click();URL.revokeObjectURL(url);ElMessage.success('分析报告已导出')
}
onMounted(()=>{initCharts();window.addEventListener('resize',resize)})
watch([range,()=>store.papers],async()=>{await nextTick();charts.forEach((chart)=>chart.dispose());initCharts()},{deep:true})
onBeforeUnmount(()=>{window.removeEventListener('resize',resize);charts.forEach((chart)=>chart.dispose())})
</script>

<style scoped>
.range-select{height:38px;padding:0 30px 0 11px;border:1px solid #dfe4ed;border-radius:7px;color:#657188;background:#fff;font-size:10px}.insight-banner{display:flex;align-items:center;gap:16px;margin-bottom:17px;padding:18px 20px;border:1px solid #dfe5fb;border-radius:10px;background:linear-gradient(105deg,#f5f7ff,#f5f1fc);box-shadow:0 8px 22px rgba(48,65,126,.04)}.insight-icon{display:grid;width:42px;height:42px;flex:0 0 auto;place-items:center;border-radius:10px;color:#7358cc;background:#e9e3fb}.insight-icon .app-icon{width:20px}.insight-banner>div{flex:1}.insight-banner small{color:#7b64be;font-size:8px;font-weight:700;letter-spacing:.13em}.insight-banner h3{margin:4px 0;color:#403c5d;font-size:13px}.insight-banner p{margin:0;color:#77748b;font-size:9px}.insight-banner button{border:0;color:#5a48a0;background:transparent;font-size:10px;font-weight:600}.insight-banner button span{margin-left:5px}
.analysis-grid{display:grid;gap:16px}.top-grid{grid-template-columns:minmax(0,1.75fr) minmax(340px,.85fr)}.middle-grid{grid-template-columns:minmax(0,1.3fr) minmax(350px,.8fr);margin-top:16px}.large-chart{height:296px}.donut-chart{height:296px}.top-grid>.panel:last-child{position:relative}.donut-summary{position:absolute;top:174px;left:50%;display:flex;transform:translate(-50%,-50%);flex-direction:column;text-align:center;pointer-events:none}.donut-summary strong{color:#263249;font-size:24px}.donut-summary span{color:#9aa3b3;font-size:8px}.mini-legend{display:flex;gap:14px;color:#7e899c;font-size:9px}.mini-legend span{display:flex;align-items:center;gap:5px}.mini-legend i{width:7px;height:7px;border-radius:50%}.mini-legend .blue{background:#4565d2}.mini-legend .green{background:#13a484}.mini-legend .hot{background:#7956cf}.mini-legend .steady{background:#3156d3}.mini-legend .cool{background:#b6bfcc}
.word-cloud{display:flex;height:265px;align-content:center;align-items:center;justify-content:center;gap:15px 19px;padding:22px;flex-wrap:wrap;background:radial-gradient(circle at center,#fafbff,transparent 70%)}.word-cloud button{border:0;background:transparent;font-weight:600;transition:.15s}.word-cloud button:hover{transform:scale(1.12)!important}.author-list{padding:7px 20px 12px}.author-list>div{display:flex;align-items:center;gap:10px;padding:10px 0;border-bottom:1px solid #f0f2f5}.author-list>div:last-child{border:0}.rank{width:17px;color:#a0a8b7;text-align:center;font-size:9px}.rank.top{color:#e29a32;font-weight:700}.author-avatar{display:grid;width:30px;height:30px;place-items:center;border-radius:7px;color:#fff;font-size:10px}.author-list>div>div{flex:1}.author-list strong{display:block;color:#4c5970;font-size:10px}.author-list small{color:#959ead;font-size:8px}.paper-number{color:#9aa3b2;font-size:8px}.paper-number strong{display:inline;color:#536078;font-size:11px}.evolution-panel{margin-top:16px}.evolution-chart{height:255px}.analysis-empty{padding:30px;color:#99a2b1;text-align:center;font-size:9px}
</style>
