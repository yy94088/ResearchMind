<template>
  <div>
    <section class="welcome-row">
      <div>
        <h2>{{ greeting }}，{{ store.profile.name.slice(0, 1) }}老师</h2>
        <p>你的知识库本周新增了 <strong>{{ weeklyPaperCount }} 篇</strong> 文献，当前形成 <strong>{{ graphLinkCount }} 条</strong> 可计算关联。</p>
      </div>
      <div class="header-actions">
        <button class="secondary-button" @click="router.push('/analytics')"><AppIcon name="chart" /> 查看研究报告</button>
        <button class="primary-button" @click="uploadVisible = true"><AppIcon name="upload" /> 导入新文献</button>
      </div>
    </section>

    <section class="stat-grid">
      <article v-for="stat in stats" :key="stat.label" class="stat-card" :title="stat.description" :style="{ '--stat-color': stat.color, '--stat-soft': stat.soft }">
        <div class="stat-card-head"><span class="stat-label">{{ stat.label }}</span><span class="stat-icon"><AppIcon :name="stat.icon" /></span></div>
        <div class="stat-value">{{ stat.value }}</div>
        <div class="stat-note"><em>{{ stat.change }}</em>{{ stat.note }}</div>
      </article>
    </section>

    <section class="dashboard-main">
      <article class="panel trend-panel">
        <div class="panel-head">
          <div><h3>知识库增长趋势</h3><p>近 8 个月的文献收录与阅读情况</p></div>
          <select v-model="trendRange"><option>近 8 个月</option><option>近 6 个月</option></select>
        </div>
        <div ref="trendChart" class="trend-chart"></div>
      </article>

      <article class="panel focus-panel">
        <div class="panel-head"><div><h3>研究领域分布</h3><p>按文献数量统计</p></div><button class="panel-link" @click="router.push('/analytics')">详情 <AppIcon name="chevron" /></button></div>
        <div ref="areaChart" class="area-chart"></div>
        <div class="area-legend">
          <div v-for="item in areaData" :key="item.name"><i :style="{ background: item.itemStyle.color }"></i><span>{{ item.name }}</span><strong>{{ item.value }}%</strong></div>
        </div>
      </article>
    </section>

    <section class="dashboard-bottom">
      <article class="panel recent-panel">
        <div class="panel-head"><div><h3>最近文献</h3><p>继续你的阅读和整理</p></div><button class="panel-link" @click="router.push('/library')">查看全部 <AppIcon name="chevron" /></button></div>
        <div class="recent-list">
          <div v-for="paper in store.papers.slice(0, 4)" :key="paper.id" class="recent-item" @click="openPaper(paper)">
            <span class="paper-type">PDF</span>
            <div class="recent-copy">
              <strong class="ellipsis">{{ paper.title }}</strong>
              <span>{{ paper.authors.slice(0, 2).join(' · ') }} · {{ paper.year }}</span>
              <div><span v-for="tag in paper.tags.slice(0, 2)" :key="tag" class="tag">{{ tag }}</span></div>
            </div>
            <div class="reading-progress"><strong>{{ paper.progress }}%</strong><span><i :style="{ width: `${paper.progress}%` }"></i></span></div>
            <button class="row-arrow"><AppIcon name="chevron" /></button>
          </div>
          <p v-if="!store.papers.length" class="dashboard-empty">还没有文献，点击右上角导入第一篇 PDF。</p>
        </div>
      </article>

      <article class="panel activity-panel">
        <div class="panel-head"><div><h3>最近动态</h3><p>知识库中的最新变化</p></div></div>
        <div class="activity-list">
          <div v-for="activity in store.activities.slice(0, 5)" :key="activity.id" class="activity-item">
            <span class="activity-icon" :class="activity.type"><AppIcon :name="activityIcon(activity.type)" /></span>
            <div><strong>{{ activity.title }}</strong><span>{{ activity.detail }}</span></div>
            <time>{{ formatActivityTime(activity.occurredAt) }}</time>
          </div>
          <p v-if="!store.activities.length" class="dashboard-empty">完成文献导入、AI 解读或团队操作后，真实动态会显示在这里。</p>
        </div>
      </article>
    </section>

    <PaperUploadDialog v-model="uploadVisible" />
    <PaperDetailDialog v-model="detailVisible" :paper="selectedPaper" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import AppIcon from '../components/AppIcon.vue'
import PaperUploadDialog from '../components/PaperUploadDialog.vue'
import PaperDetailDialog from '../components/PaperDetailDialog.vue'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const router = useRouter()
const trendChart = ref()
const areaChart = ref()
const trendRange = ref('近 8 个月')
const uploadVisible = ref(false)
const detailVisible = ref(false)
const selectedPaper = ref(null)
let trendInstance
let areaInstance

const stats = computed(() => [
  { label: '文献总量', value: store.papers.length, change: `+${weeklyPaperCount.value}`, note: '本周新增', description: '当前账户文献库中未删除的文献数量。', icon: 'book', color: '#3156d3', soft: '#edf1ff' },
  { label: '已读文献', value: store.readPapers.length, change: `${store.papers.length ? Math.round(store.readPapers.length / store.papers.length * 100) : 0}%`, note: '阅读完成率', description: '网页阅读器已渲染到 PDF 最后一页的文献数量；完成率 = 已读文献 ÷ 文献总量。', icon: 'check', color: '#0e9f78', soft: '#e9f8f3' },
  { label: '知识节点', value: graphNodeCount.value, change: graphLinkCount.value, note: '当前关联', description: '按当前文献、作者、关键词与研究领域实时估算的图谱规模。', icon: 'graph', color: '#7956cf', soft: '#f1edfb' },
  { label: '累计阅读时间', value: formatReadingDuration(totalReadSeconds.value), change: readPageCount.value, note: '约合已读页数', description: '所有 PDF 阅读器的有效停留时间之和；仅在阅读器加载完成、标签页可见且浏览器窗口处于焦点时累计。', icon: 'clock', color: '#dd7a32', soft: '#fff2e8' }
])

const palette=['#3156d3','#7556cf','#12a68a','#e8873d','#3d8fb4','#b9c1cf']
const paperAreaNames=(paper)=>paper.areas?.length?paper.areas.map((area)=>area.name):[paper.area||'未分类']
const startOfWeek=computed(()=>{const date=new Date();const day=(date.getDay()+6)%7;date.setHours(0,0,0,0);date.setDate(date.getDate()-day);return date})
const weeklyPaperCount=computed(()=>store.papers.filter((paper)=>paper.uploadDate&&new Date(`${paper.uploadDate}T00:00:00`)>=startOfWeek.value).length)
const graphNodeCount=computed(()=>{
  const authors=new Set(store.papers.flatMap((paper)=>paper.authors))
  const tags=new Set(store.papers.flatMap((paper)=>paper.tags))
  const areas=new Set(store.papers.flatMap(paperAreaNames).filter(Boolean))
  return store.papers.length+authors.size+tags.size+areas.size
})
const graphLinkCount=computed(()=>store.papers.reduce((count,paper)=>count+paper.authors.length+paper.tags.length+paperAreaNames(paper).length,0))
const readPageCount=computed(()=>Math.round(store.papers.reduce((sum,paper)=>sum+Number(paper.pages||0)*Number(paper.progress||0)/100,0)))
const totalReadSeconds=computed(()=>store.papers.reduce((sum,paper)=>sum+Number(paper.totalReadSeconds||0),0))
const formatReadingDuration=(seconds)=>{const total=Math.max(0,Math.floor(Number(seconds)||0));if(total<60)return `${total}s`;const hours=Math.floor(total/3600);const minutes=Math.floor(total%3600/60);return hours?`${hours}h ${minutes}m`:`${minutes}m`}
const greeting=computed(()=>{const hour=new Date().getHours();return hour<6?'夜深了':hour<12?'上午好':hour<18?'下午好':'晚上好'})
const areaData=computed(()=>{
  const counts={}
  store.papers.forEach((paper)=>paperAreaNames(paper).forEach((area)=>{counts[area]=(counts[area]||0)+1}))
  const total=Object.values(counts).reduce((sum,count)=>sum+count,0)||1
  return Object.entries(counts).sort((a,b)=>b[1]-a[1]).slice(0,6).map(([name,count],index)=>({name,value:Math.round(count/total*100),count,itemStyle:{color:palette[index%palette.length]}}))
})
const trendData=computed(()=>{
  const count=trendRange.value==='近 6 个月'?6:8
  const months=[]
  const now=new Date()
  for(let offset=count-1;offset>=0;offset--){
    const date=new Date(now.getFullYear(),now.getMonth()-offset,1)
    months.push({key:`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}`,label:`${date.getMonth()+1}月`})
  }
  return {
    labels:months.map((month)=>month.label),
    imported:months.map((month)=>store.papers.filter((paper)=>paper.uploadDate?.startsWith(month.key)).length),
    completed:months.map((month)=>store.papers.filter((paper)=>paper.read&&paper.lastReadTime?.startsWith(month.key)).length)
  }
})

function initCharts() {
  trendInstance = echarts.init(trendChart.value)
  trendInstance.setOption({
    animationDuration: 800,
    grid: { left: 42, right: 25, top: 30, bottom: 35 },
    tooltip: { trigger: 'axis', borderWidth: 0, backgroundColor: '#1d2942', textStyle: { color: '#fff', fontSize: 10 } },
    legend: { right: 18, top: 0, icon: 'circle', itemWidth: 7, textStyle: { color: '#7e899d', fontSize: 10 } },
    xAxis: { type: 'category', boundaryGap: false, data: trendData.value.labels, axisLine: { lineStyle: { color: '#e7ebf2' } }, axisTick: { show: false }, axisLabel: { color: '#97a0b1', fontSize: 9 } },
    yAxis: { type: 'value', splitNumber: 4, axisLabel: { color: '#a0a8b6', fontSize: 9 }, splitLine: { lineStyle: { color: '#eef1f5', type: 'dashed' } } },
    series: [
      { name: '收录文献', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: trendData.value.imported, lineStyle: { width: 2.5, color: '#3156d3' }, itemStyle: { color: '#3156d3' }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'rgba(49,86,211,.20)'},{offset:1,color:'rgba(49,86,211,0)'}]) } },
      { name: '完成阅读', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5, data: trendData.value.completed, lineStyle: { width: 2, color: '#14a688' }, itemStyle: { color: '#14a688' } }
    ]
  })
  areaInstance = echarts.init(areaChart.value)
  areaInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}%', borderWidth: 0 },
    series: [{ type: 'pie', radius: ['59%','78%'], center: ['50%','52%'], avoidLabelOverlap: true, itemStyle: { borderColor: '#fff', borderWidth: 3 }, label: { show: false }, data: areaData.value }]
  })
}

function resizeCharts() {
  trendInstance?.resize()
  areaInstance?.resize()
}

function activityIcon(type) {
  return ({ paper: 'upload', ai: 'sparkles', graph: 'graph', team: 'users', delete: 'trash' })[type] || 'file'
}
function formatActivityTime(value){return value?new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):''}

function openPaper(paper) {
  selectedPaper.value = paper
  detailVisible.value = true
}

watch([trendRange,()=>store.papers],async()=>{await nextTick();trendInstance?.dispose();areaInstance?.dispose();initCharts()},{deep:true})
onMounted(() => { initCharts(); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { window.removeEventListener('resize', resizeCharts); trendInstance?.dispose(); areaInstance?.dispose() })
</script>

<style scoped>
.welcome-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22px; }
.welcome-row h2 { margin: 0 0 6px; color: #18243a; font-size: 23px; letter-spacing: -.6px; }
.welcome-row p { margin: 0; color: #7d899c; font-size: 12px; }.welcome-row p strong{color:#3b57b7}
.dashboard-main { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(300px, .8fr); gap: 16px; margin-top: 17px; }
.trend-chart { height: 286px; }.area-chart{height:185px}
.panel-head select { padding: 6px 27px 6px 9px; border: 1px solid #e1e5ed; border-radius: 5px; outline: 0; color: #6c788c; background: #fff; font-size: 9px; }
.area-legend { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px; padding: 0 22px 19px; }
.area-legend div { display: flex; align-items: center; gap: 7px; font-size: 9px; }
.area-legend i { width: 7px; height: 7px; border-radius: 2px; }.area-legend span{flex:1;color:#758096}.area-legend strong{color:#44516a}
.dashboard-bottom { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(350px, 1fr); gap: 16px; margin-top: 17px; }
.recent-list { padding: 3px 0; }.recent-item{display:flex;align-items:center;gap:13px;padding:13px 20px;border-bottom:1px solid #f0f2f6;cursor:pointer;transition:.15s}.recent-item:last-child{border:0}.recent-item:hover{background:#fafbfe}
.paper-type{display:grid;width:36px;height:42px;place-items:center;border-radius:5px;color:#d35c5c;background:#fff0f0;font-size:8px;font-weight:700}.recent-copy{min-width:0;flex:1}.recent-copy>strong{display:block;color:#334057;font-size:11px}.recent-copy>span{display:block;margin:4px 0 6px;color:#929bad;font-size:9px}.recent-copy>div{display:flex;gap:5px}
.reading-progress{width:76px}.reading-progress>strong{display:block;margin-bottom:6px;color:#627087;text-align:right;font-size:9px}.reading-progress>span{display:block;overflow:hidden;height:3px;border-radius:3px;background:#edf0f5}.reading-progress i{display:block;height:100%;border-radius:3px;background:#4e6bd4}.row-arrow{border:0;color:#a1a9b8;background:transparent}.row-arrow .app-icon{width:15px}
.activity-list{padding:8px 20px}.activity-item{display:flex;align-items:center;gap:11px;padding:11px 0}.activity-icon{display:grid;width:31px;height:31px;place-items:center;border-radius:7px;color:#3156d3;background:#edf1ff}.activity-icon.ai{color:#7956cf;background:#f1edfb}.activity-icon.graph{color:#0e9f78;background:#e9f8f3}.activity-icon.team{color:#dd7a32;background:#fff2e8}.activity-icon.delete{color:#db5656;background:#fff0f0}.activity-icon .app-icon{width:14px}.activity-item>div{min-width:0;flex:1}.activity-item strong{display:block;color:#4a576d;font-size:10px}.activity-item span:not(.activity-icon){display:block;overflow:hidden;margin-top:3px;color:#929bad;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.activity-item time{color:#a5adba;font-size:8px;white-space:nowrap}
.dashboard-empty{padding:25px;color:#99a2b1;text-align:center;font-size:9px}
</style>
