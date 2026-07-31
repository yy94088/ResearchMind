<template>
  <div>
    <header class="page-header">
      <div><h2>知识图谱</h2><p>探索文献、作者、关键词与研究方向之间的关联</p></div>
      <div class="header-actions">
        <button class="secondary-button" @click="fitGraph"><AppIcon name="refresh" /> 重置视图</button>
        <button class="secondary-button" :disabled="loading" @click="rebuildGraph"><AppIcon name="refresh" /> 重新构建</button>
        <button class="primary-button" @click="exportGraph"><AppIcon name="download" /> 导出图谱</button>
      </div>
    </header>

    <section v-loading="loading" class="graph-workspace panel">
      <div class="graph-toolbar">
        <label><AppIcon name="search" /><input v-model="query" placeholder="查找图谱节点…" @keyup.enter="focusSearch" /></label>
        <div class="graph-filters">
          <button v-for="type in nodeTypes" :key="type.value" :class="{ muted: !visibleTypes.includes(type.value) }" @click="toggleType(type.value)">
            <i :style="{ background: type.color }"></i>{{ type.label }}
          </button>
        </div>
        <span class="graph-count">{{ visibleNodeCount }} 个节点 · {{ visibleLinkCount }} 条关系</span>
      </div>
      <div class="graph-main">
        <div class="graph-canvas-wrap">
          <div ref="graphEl" class="graph-canvas"></div>
          <div v-if="!loading && !graphData.nodes.length" class="graph-empty">
            <span><AppIcon name="graph" /></span>
            <h3>还没有可展示的知识图谱</h3>
            <p>先向文献库导入论文，系统会根据作者、关键词和研究领域自动构建关联。</p>
          </div>
          <div class="zoom-tools">
            <button title="放大" @click="zoomGraph(1.2)">＋</button>
            <button title="缩小" @click="zoomGraph(.8)">−</button>
            <button title="复位" @click="fitGraph">⌖</button>
          </div>
          <div class="graph-help">拖拽节点调整布局 · 滚轮缩放 · 点击查看详情</div>
        </div>
        <aside class="node-detail">
          <template v-if="selectedNode">
            <div class="node-detail-head">
              <span :style="{ background: categoryColor(selectedNode.category) }"><AppIcon :name="categoryIcon(selectedNode.category)" /></span>
              <div><small>{{ categoryName(selectedNode.category) }}</small><h3>{{ selectedNode.name }}</h3></div>
              <button @click="selectedNode = null">×</button>
            </div>
            <div v-if="selectedNode.raw?.title" class="node-paper">
              <p>{{ selectedNode.raw.title }}</p>
              <dl><div><dt>作者</dt><dd>{{ selectedNode.raw.authors?.join('、') || '—' }}</dd></div><div><dt>年份</dt><dd>{{ selectedNode.raw.year || '—' }}</dd></div><div><dt>期刊</dt><dd>{{ selectedNode.raw.journal || '—' }}</dd></div></dl>
              <button class="primary-button" @click="openPaper(selectedNode.raw)">查看文献详情</button>
            </div>
            <div v-else class="node-paper">
              <dl><div><dt>关联数</dt><dd>{{ selectedNode.degree }}</dd></div><div><dt>数据类型</dt><dd>{{ categoryName(selectedNode.category) }}</dd></div></dl>
            </div>
            <div class="relations">
              <h4>关联节点 <span>{{ relatedNodes.length }}</span></h4>
              <button v-for="node in relatedNodes" :key="node.id" @click="selectNode(node)">
                <i :style="{ background: categoryColor(node.category) }"></i><span>{{ node.name }}</span><small>{{ node.relationLabel }}</small><AppIcon name="chevron" />
              </button>
            </div>
          </template>
          <div v-else class="node-empty">
            <span><AppIcon name="graph" /></span><h3>选择一个节点</h3><p>点击图谱中的节点<br />查看其属性和关联关系</p>
          </div>
        </aside>
      </div>
    </section>
    <PaperDetailDialog v-model="detailVisible" :paper="selectedPaper" />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { http } from '../api/http'
import AppIcon from '../components/AppIcon.vue'
import PaperDetailDialog from '../components/PaperDetailDialog.vue'
import { useAppStore } from '../stores/app'

const store = useAppStore()
const graphEl = ref()
const query = ref('')
const visibleTypes = ref([0, 1, 2, 3])
const selectedNode = ref(null)
const selectedPaper = ref(null)
const detailVisible = ref(false)
const loading = ref(false)
const graphData = ref({ nodes: [], links: [] })
let chart

const nodeTypes = [
  { value: 0, label: '文献', color: '#3156d3' },
  { value: 1, label: '关键词', color: '#7956cf' },
  { value: 2, label: '作者', color: '#0e9f78' },
  { value: 3, label: '研究领域', color: '#e8873d' }
]

const typeCategories = { PAPER: 0, KEYWORD: 1, AUTHOR: 2, AREA: 3 }
const relationLabels = {
  AUTHORED_BY: '作者',
  HAS_KEYWORD: '包含关键词',
  BELONGS_TO: '属于',
  CITES: '引用',
  COOPERATES_WITH: '合作',
  RELATED_TO: '相关'
}

const visibleNodes = computed(() => graphData.value.nodes.filter((node) => visibleTypes.value.includes(node.category)))
const visibleNodeIds = computed(() => new Set(visibleNodes.value.map((node) => node.id)))
const visibleLinks = computed(() => graphData.value.links.filter((link) => visibleNodeIds.value.has(link.source) && visibleNodeIds.value.has(link.target)))
const visibleNodeCount = computed(() => visibleNodes.value.length)
const visibleLinkCount = computed(() => visibleLinks.value.length)
const relatedNodes = computed(() => {
  if (!selectedNode.value) return []
  const related = new Map()
  graphData.value.links.forEach((link) => {
    const relatedId = link.source === selectedNode.value.id
      ? link.target
      : link.target === selectedNode.value.id ? link.source : null
    if (!relatedId || related.has(relatedId)) return
    const node = graphData.value.nodes.find((item) => item.id === relatedId)
    if (node) related.set(relatedId, { ...node, relationLabel: link.name })
  })
  return [...related.values()].slice(0, 20)
})

function renderGraph() {
  if (!chart) return
  chart.setOption({
    animationDurationUpdate: 500,
    tooltip: { formatter: (params) => params.dataType === 'node' ? `${categoryName(params.data.category)} · ${params.name}` : params.data.name, borderWidth: 0, backgroundColor: '#1e2942', textStyle: { color: '#fff', fontSize: 10 } },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      focusNodeAdjacency: true,
      categories: nodeTypes.map((type) => ({ name: type.label, itemStyle: { color: type.color } })),
      data: visibleNodes.value,
      links: visibleLinks.value,
      force: { repulsion: 260, edgeLength: [75, 145], gravity: .08, friction: .55 },
      lineStyle: { color: 'source', opacity: .22, curveness: .05, width: 1 },
      emphasis: { focus: 'adjacency', lineStyle: { opacity: .75, width: 2 } },
      label: { show: true, position: 'right', color: '#5b6880', fontSize: 9, formatter: ({ name }) => name.length > 11 ? `${name.slice(0, 11)}…` : name },
      itemStyle: { borderColor: '#fff', borderWidth: 2, shadowBlur: 8, shadowColor: 'rgba(25,39,76,.14)' }
    }]
  }, true)
}

function categoryName(category) { return nodeTypes.find((type) => type.value === category)?.label || '节点' }
function categoryColor(category) { return nodeTypes.find((type) => type.value === category)?.color || '#8b95a8' }
function categoryIcon(category) { return ['file', 'sparkles', 'users', 'graph'][category] || 'graph' }
function normalizeGraph(data) {
  const papersById = new Map(store.papers.map((paper) => [paper.id, paper]))
  return {
    nodes: (data.nodes || []).map((node) => {
      const category = typeCategories[node.type]
      const raw = node.type === 'PAPER'
        ? papersById.get(node.referenceId) || { id: node.referenceId, ...node.properties, authors: [] }
        : null
      return {
        ...node,
        category,
        raw,
        symbolSize: 25 + Math.min(Number(node.degree || 0) * 3, node.type === 'PAPER' ? 22 : 30)
      }
    }).filter((node) => Number.isInteger(node.category)),
    links: (data.relations || []).map((relation) => {
      const isAreaRelation = relation.type === 'BELONGS_TO'
      const isPrimaryArea = isAreaRelation && Boolean(Number(relation.properties?.primary))
      return {
        ...relation,
        name: isAreaRelation
          ? (isPrimaryArea ? '主领域' : '关联领域')
          : relationLabels[relation.type] || relation.type,
        lineStyle: {
          width: isPrimaryArea ? 2.4 : 1 + Math.min(Number(relation.weight || 1) * .35, 2.5),
          type: isAreaRelation && !isPrimaryArea ? 'dashed' : 'solid',
          opacity: isAreaRelation && !isPrimaryArea ? .16 : .28
        }
      }
    })
  }
}
async function loadGraph(rebuild = false) {
  loading.value = true
  try {
    const request = rebuild ? http.post('/graph/rebuild') : http.get('/graph')
    const { data } = await request
    graphData.value = normalizeGraph(data)
    selectedNode.value = null
    renderGraph()
    return data
  } catch (error) {
    graphData.value = { nodes: [], links: [] }
    renderGraph()
    ElMessage.error(error.response?.data?.message || '知识图谱加载失败')
    return null
  } finally {
    loading.value = false
  }
}
async function rebuildGraph() {
  const result = await loadGraph(true)
  if (result) ElMessage.success(`图谱已构建：${result.summary.nodeCount} 个节点，${result.summary.relationCount} 条关系`)
}
function selectNode(node) {
  selectedNode.value = node
  chart.dispatchAction({ type: 'focusNodeAdjacency', seriesIndex: 0, dataIndex: visibleNodes.value.findIndex((item) => item.id === node.id) })
}
function toggleType(type) {
  visibleTypes.value = visibleTypes.value.includes(type) ? visibleTypes.value.filter((item) => item !== type) : [...visibleTypes.value, type]
}
function focusSearch() {
  const normalized = query.value.trim().toLowerCase()
  const found = graphData.value.nodes.find((node) => node.name.toLowerCase().includes(normalized))
  if (!normalized) return
  if (!found) return ElMessage.warning('没有找到匹配节点')
  if (!visibleTypes.value.includes(found.category)) visibleTypes.value.push(found.category)
  nextTick(() => selectNode(found))
}
function fitGraph() { chart?.dispatchAction({ type: 'restore' }); renderGraph(); selectedNode.value = null }
function zoomGraph(factor) {
  const option = chart.getOption()
  const zoom = (option.series?.[0]?.zoom || 1) * factor
  chart.setOption({ series: [{ zoom }] })
}
function openPaper(paper) { selectedPaper.value = paper; detailVisible.value = true }
function exportGraph() {
  if (!graphData.value.nodes.length) return ElMessage.warning('当前没有可导出的图谱')
  const url = chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' })
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'ResearchMind-知识图谱.png'; anchor.click()
  ElMessage.success('知识图谱已导出')
}
function resize() { chart?.resize() }

watch(visibleTypes, () => { renderGraph(); if (selectedNode.value && !visibleTypes.value.includes(selectedNode.value.category)) selectedNode.value = null }, { deep: true })
onMounted(async () => {
  chart = echarts.init(graphEl.value)
  renderGraph()
  chart.on('click', (params) => { if (params.dataType === 'node') selectedNode.value = params.data })
  window.addEventListener('resize', resize)
  await loadGraph()
})
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>

<style scoped>
.graph-workspace{overflow:hidden;height:calc(100vh - 151px);min-height:610px;box-shadow:none}.graph-toolbar{display:flex;height:55px;align-items:center;gap:20px;padding:0 17px;border-bottom:1px solid #e9ecf2}.graph-toolbar>label{display:flex;width:240px;height:34px;align-items:center;gap:8px;padding:0 10px;border:1px solid #e0e4ec;border-radius:6px}.graph-toolbar label .app-icon{width:15px;color:#9ca5b4}.graph-toolbar input{min-width:0;flex:1;border:0;outline:0;font-size:10px}.graph-filters{display:flex;gap:6px}.graph-filters button{display:flex;height:29px;align-items:center;gap:6px;padding:0 9px;border:1px solid #e2e6ed;border-radius:5px;color:#627087;background:#fff;font-size:9px}.graph-filters button.muted{opacity:.38}.graph-filters i{width:7px;height:7px;border-radius:50%}.graph-count{margin-left:auto;color:#9aa3b2;font-size:9px}.graph-main{display:grid;height:calc(100% - 55px);grid-template-columns:minmax(0,1fr) 285px}.graph-canvas-wrap{position:relative;overflow:hidden;background:radial-gradient(circle,#e1e5ee 1px,transparent 1px);background-size:22px 22px}.graph-canvas{width:100%;height:100%;background:linear-gradient(135deg,rgba(248,250,253,.5),rgba(245,247,252,.2))}.graph-empty{position:absolute;top:50%;left:50%;display:flex;width:310px;transform:translate(-50%,-50%);flex-direction:column;align-items:center;color:#9099aa;text-align:center}.graph-empty span{display:grid;width:58px;height:58px;place-items:center;border-radius:50%;color:#7185c6;background:#eef2fc}.graph-empty .app-icon{width:25px}.graph-empty h3{margin:14px 0 6px;color:#536178;font-size:13px}.graph-empty p{margin:0;font-size:9px;line-height:1.7}.zoom-tools{position:absolute;right:15px;bottom:15px;display:flex;overflow:hidden;border:1px solid #dfe4eb;border-radius:6px;box-shadow:0 5px 14px rgba(25,40,75,.09)}.zoom-tools button{width:32px;height:31px;border:0;border-right:1px solid #e5e8ef;color:#637087;background:#fff}.zoom-tools button:last-child{border:0}.graph-help{position:absolute;bottom:17px;left:17px;color:#a0a8b6;font-size:8px}.node-detail{overflow:auto;border-left:1px solid #e9ecf2;background:#fff}.node-detail-head{position:relative;display:flex;align-items:flex-start;gap:11px;padding:19px 17px;border-bottom:1px solid #edf0f4}.node-detail-head>span{display:grid;width:38px;height:38px;flex:0 0 auto;place-items:center;border-radius:9px;color:#fff}.node-detail-head .app-icon{width:17px}.node-detail-head small{color:#9aa4b4;font-size:8px}.node-detail-head h3{margin:4px 18px 0 0;color:#354259;font-size:12px;line-height:1.5}.node-detail-head>button{position:absolute;top:10px;right:10px;border:0;color:#9ba4b3;background:transparent;font-size:18px}.node-paper{padding:17px;border-bottom:1px solid #edf0f4}.node-paper>p{margin:0 0 13px;color:#6e7a8e;font-size:9px;line-height:1.6}.node-paper dl{margin:0}.node-paper dl div{display:flex;margin:8px 0;font-size:9px}.node-paper dt{width:48px;color:#a0a8b7}.node-paper dd{overflow:hidden;margin:0;flex:1;color:#627087;text-overflow:ellipsis;white-space:nowrap}.node-paper .primary-button{width:100%;height:33px;margin-top:9px}.relations{padding:16px}.relations h4{margin:0 0 10px;color:#46536a;font-size:10px}.relations h4 span{color:#9ba4b3;font-weight:400}.relations button{display:grid;width:100%;grid-template-columns:8px 1fr auto 13px;align-items:center;gap:7px;padding:9px 3px;border:0;border-bottom:1px solid #f0f2f5;color:#59667d;background:#fff;text-align:left;font-size:9px}.relations button i{width:7px;height:7px;border-radius:50%}.relations button small{color:#a0a8b5;font-size:8px}.relations .app-icon{width:12px;color:#a5adba}.node-empty{display:flex;height:100%;flex-direction:column;align-items:center;justify-content:center;text-align:center}.node-empty>span{display:grid;width:52px;height:52px;place-items:center;border-radius:50%;color:#7185c6;background:#f0f3fb}.node-empty h3{margin:13px 0 5px;color:#57647a;font-size:11px}.node-empty p{margin:0;color:#9aa3b2;font-size:9px;line-height:1.7}
</style>
