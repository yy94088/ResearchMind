<template>
  <div>
    <header class="page-header">
      <div><h2>我的文献</h2><p>集中管理、检索和整理你的科研资料</p></div>
      <div class="header-actions">
        <button class="secondary-button" @click="exportReferences"><AppIcon name="download" /> 导出引用</button>
        <button class="primary-button" @click="uploadVisible = true"><AppIcon name="upload" /> 导入文献</button>
      </div>
    </header>

    <section class="library-toolbar panel">
      <div class="search-box">
        <AppIcon name="search" />
        <input v-model="query" placeholder="搜索标题、作者、关键词或 DOI…" />
        <button v-if="query" @click="query = ''">×</button>
      </div>
      <select v-model="areaFilter"><option value="">全部研究领域</option><option v-for="area in store.areas" :key="area" :value="area">{{ area }}</option></select>
      <select v-model="yearFilter"><option value="">全部年份</option><option v-for="year in years" :key="year" :value="year">{{ year }} 年</option></select>
      <select v-model="stateFilter"><option value="">全部状态</option><option value="favorite">已收藏</option><option value="read">已读完</option><option value="reading">阅读中</option><option value="unread">未开始</option></select>
      <button class="filter-reset" :disabled="!hasFilters" @click="resetFilters"><AppIcon name="refresh" /> 重置</button>
    </section>

    <div class="library-summary">
      <span>共找到 <strong>{{ filteredPapers.length }}</strong> 篇文献</span>
      <div class="view-actions">
        <span>排序：</span>
        <select v-model="sortBy"><option value="recent">最近导入</option><option value="year">发表年份</option><option value="title">标题 A-Z</option><option value="progress">阅读进度</option></select>
        <button :class="{ active: viewMode === 'list' }" title="列表视图" @click="viewMode = 'list'">☷</button>
        <button :class="{ active: viewMode === 'grid' }" title="卡片视图" @click="viewMode = 'grid'">▦</button>
      </div>
    </div>

    <section v-if="pagedPapers.length && viewMode === 'list'" class="paper-table panel">
      <div class="paper-table-head">
        <span class="check-cell"><input v-model="allSelected" type="checkbox" /></span>
        <span>文献信息</span><span>研究领域</span><span>发表年份</span><span>阅读状态</span><span>操作</span>
      </div>
      <article v-for="paper in pagedPapers" :key="paper.id" class="paper-row" @dblclick="openDetail(paper)">
        <span class="check-cell"><input v-model="selectedIds" type="checkbox" :value="paper.id" /></span>
        <div class="paper-info">
          <span class="paper-file">PDF</span>
          <div>
            <strong :title="paper.title">{{ paper.title || paper.titleZh }}</strong>
            <p v-if="paper.titleZh && paper.titleZh !== paper.title" :title="paper.titleZh">中文译名：{{ paper.titleZh }}</p>
            <small>{{ paper.authors.slice(0, 3).join(' · ') }}{{ paper.authors.length > 3 ? ' 等' : '' }} · {{ paper.journal }}</small>
            <div class="paper-tags"><span v-for="tag in paper.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span></div>
          </div>
        </div>
        <span><i class="area-dot" :style="{ background: areaColor(paper.area) }"></i>{{ paper.area }}<small v-if="relatedAreaCount(paper)"> +{{ relatedAreaCount(paper) }}</small></span>
        <span>{{ paper.year }}</span>
        <div class="progress-cell">
          <div><span>{{ progressLabel(paper) }}</span><strong>{{ paper.progress }}%</strong></div>
          <i><b :style="{ width: `${paper.progress}%`, background: progressColor(paper.progress) }"></b></i>
        </div>
        <div class="row-actions">
          <button :class="{ favorite: paper.favorite }" title="收藏" @click="toggleFavorite(paper)"><AppIcon name="star" /></button>
          <button title="查看详情" @click="openDetail(paper)"><AppIcon name="file" /></button>
          <el-dropdown trigger="click" @command="(command) => handleCommand(command, paper)">
            <button title="更多操作"><AppIcon name="more" /></button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑文献信息</el-dropdown-item>
                <el-dropdown-item command="ai">AI 深度解读</el-dropdown-item>
                <el-dropdown-item command="delete" divided>移出文献库</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </article>
    </section>

    <section v-else-if="pagedPapers.length" class="paper-grid">
      <article v-for="paper in pagedPapers" :key="paper.id" class="paper-card panel" @click="openDetail(paper)">
        <div class="paper-card-top"><span class="paper-file">PDF</span><button :class="{ favorite: paper.favorite }" @click.stop="toggleFavorite(paper)"><AppIcon name="star" /></button></div>
        <span class="area-chip">{{ paper.area }}<small v-if="relatedAreaCount(paper)"> +{{ relatedAreaCount(paper) }}</small></span>
        <h3>{{ paper.title || paper.titleZh }}</h3><p v-if="paper.titleZh && paper.titleZh !== paper.title">中文译名：{{ paper.titleZh }}</p>
        <div class="card-authors">{{ paper.authors.slice(0, 2).join(' · ') }} · {{ paper.year }}</div>
        <div class="paper-tags"><span v-for="tag in paper.tags.slice(0, 3)" :key="tag" class="tag">{{ tag }}</span></div>
        <div class="card-progress"><span>{{ progressLabel(paper) }}</span><i><b :style="{ width: `${paper.progress}%` }"></b></i><strong>{{ paper.progress }}%</strong></div>
      </article>
    </section>

    <section v-else class="empty-library panel">
      <span><AppIcon :name="hasFilters ? 'search' : 'book'" /></span>
      <h3>{{ hasFilters ? '没有找到匹配的文献' : '你的文献库还是空的' }}</h3>
      <p>{{ hasFilters ? '尝试调整关键词或筛选条件' : '导入第一篇文献，数据将保存到 MySQL' }}</p>
      <button v-if="hasFilters" class="secondary-button" @click="resetFilters">清除筛选</button>
      <button v-else class="primary-button" @click="uploadVisible = true">导入第一篇文献</button>
    </section>

    <footer v-if="filteredPapers.length" class="pagination-row">
      <span>第 {{ startIndex + 1 }}–{{ Math.min(startIndex + pageSize, filteredPapers.length) }} 条，共 {{ filteredPapers.length }} 条</span>
      <div><button :disabled="page === 1" @click="page--">‹</button><button v-for="item in totalPages" :key="item" :class="{ active: item === page }" @click="page = item">{{ item }}</button><button :disabled="page === totalPages" @click="page++">›</button></div>
    </footer>

    <div v-if="selectedIds.length" class="batch-bar">
      <span>已选择 <strong>{{ selectedIds.length }}</strong> 篇</span>
      <button @click="batchFavorite"><AppIcon name="star" /> 批量收藏</button>
      <button class="danger" @click="batchDelete"><AppIcon name="trash" /> 批量删除</button>
      <button @click="selectedIds = []">取消选择</button>
    </div>

    <PaperUploadDialog v-model="uploadVisible" @imported="page = 1" />
    <PaperDetailDialog v-model="detailVisible" :paper="selectedPaper" />
    <el-dialog v-model="editVisible" width="600px" title="编辑文献信息">
      <el-form v-if="editForm" :model="editForm" label-position="top">
        <el-form-item label="原始标题"><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="中文译名（可选）"><el-input v-model="editForm.titleZh" /></el-form-item>
        <div class="edit-grid"><el-form-item label="主研究领域"><el-select v-model="editForm.area" style="width:100%"><el-option v-for="area in areaOptions" :key="area" :label="area" :value="area" /></el-select></el-form-item><el-form-item label="年份"><el-input-number v-model="editForm.year" :min="1900" :max="2100" style="width:100%" /></el-form-item></div>
        <el-form-item label="关联研究领域（可多选）"><el-select v-model="editForm.relatedAreas" multiple filterable collapse-tags style="width:100%"><el-option v-for="area in relatedEditAreaOptions" :key="area" :label="area" :value="area" /></el-select></el-form-item>
        <el-form-item label="关键词（保持论文原文语言，逗号分隔）"><el-input v-model="editForm.tagsText" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="editForm.abstract" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><button class="secondary-button" @click="editVisible = false">取消</button><button class="primary-button" @click="saveEdit">保存修改</button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import PaperUploadDialog from '../components/PaperUploadDialog.vue'
import PaperDetailDialog from '../components/PaperDetailDialog.vue'
import { useAppStore } from '../stores/app'
import { apiErrorMessage } from '../api/http'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const areaFilter = ref('')
const yearFilter = ref('')
const stateFilter = ref('')
const sortBy = ref('recent')
const viewMode = ref(store.preferences.defaultGridView ? 'grid' : 'list')
const selectedIds = ref([])
const page = ref(1)
const pageSize = 6
const uploadVisible = ref(false)
const detailVisible = ref(false)
const editVisible = ref(false)
const selectedPaper = ref(null)
const editForm = ref(null)
const supportedAreas = ['自然语言处理', '图神经网络', '计算机视觉', '大语言模型', '时间序列', '可信人工智能', '隐私计算']

const years = computed(() => [...new Set(store.papers.map((paper) => paper.year))].sort((a, b) => b - a))
const areaOptions = computed(() => [...new Set([...supportedAreas, ...store.areas, '未分类'])])
const relatedEditAreaOptions = computed(() => areaOptions.value.filter(
  (area) => area !== '未分类' && area !== editForm.value?.area
))
const hasFilters = computed(() => Boolean(query.value || areaFilter.value || yearFilter.value || stateFilter.value))
const filteredPapers = computed(() => {
  const normalized = query.value.trim().toLowerCase()
  const result = store.papers.filter((paper) => {
    const haystack = [paper.title, paper.titleZh, paper.doi, paper.journal, ...paper.authors, ...paper.tags].join(' ').toLowerCase()
    const stateMatch = !stateFilter.value
      || (stateFilter.value === 'favorite' && paper.favorite)
      || (stateFilter.value === 'read' && paper.read)
      || (stateFilter.value === 'reading' && paper.progress > 0 && paper.progress < 100)
      || (stateFilter.value === 'unread' && paper.progress === 0)
    return (!normalized || haystack.includes(normalized))
      && (!areaFilter.value || paper.areas?.some((area) => area.name === areaFilter.value) || paper.area === areaFilter.value)
      && (!yearFilter.value || paper.year === Number(yearFilter.value))
      && stateMatch
  })
  return result.sort((a, b) => {
    if (sortBy.value === 'year') return b.year - a.year
    if (sortBy.value === 'title') return a.title.localeCompare(b.title)
    if (sortBy.value === 'progress') return b.progress - a.progress
    return b.uploadDate.localeCompare(a.uploadDate)
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredPapers.value.length / pageSize)))
const startIndex = computed(() => (page.value - 1) * pageSize)
const pagedPapers = computed(() => filteredPapers.value.slice(startIndex.value, startIndex.value + pageSize))
const allSelected = computed({
  get: () => pagedPapers.value.length > 0 && pagedPapers.value.every((paper) => selectedIds.value.includes(paper.id)),
  set: (checked) => {
    const ids = pagedPapers.value.map((paper) => paper.id)
    selectedIds.value = checked ? [...new Set([...selectedIds.value, ...ids])] : selectedIds.value.filter((id) => !ids.includes(id))
  }
})

watch([query, areaFilter, yearFilter, stateFilter, sortBy], () => { page.value = 1 })
watch(totalPages, (value) => { if (page.value > value) page.value = value })
watch(() => route.query.q, (value) => { if (typeof value === 'string') query.value = value })

function areaColor(area) {
  const map = { '自然语言处理': '#3156d3', '图神经网络': '#7956cf', '计算机视觉': '#0e9f78', '大语言模型': '#e27a32', '时间序列': '#19a2b8', '可信人工智能': '#d45f7a', '隐私计算': '#64748b' }
  return map[area] || '#9aa4b5'
}
function relatedAreaCount(paper) {
  return Math.max(0, (paper.areas?.length || 1) - 1)
}
function progressLabel(paper) {
  if (paper.progress >= 100) return '已读完'
  if (paper.progress > 0) return '阅读中'
  return '未开始'
}
function progressColor(progress) {
  return progress >= 100 ? '#11a47d' : progress > 0 ? '#3156d3' : '#b4bdcb'
}
function resetFilters() {
  query.value = ''; areaFilter.value = ''; yearFilter.value = ''; stateFilter.value = ''; router.replace({ path: '/library' })
}
function openDetail(paper) {
  selectedPaper.value = paper
  detailVisible.value = true
}
function handleCommand(command, paper) {
  if (command === 'edit') {
    const areaDetails = paper.areas?.length
      ? paper.areas
      : [{ name: paper.area || '未分类', confidence: 1, primary: true }]
    editForm.value = reactive({
      ...paper,
      area: areaDetails.find((area) => area.primary)?.name || paper.area || areaDetails[0].name,
      relatedAreas: areaDetails.filter((area) => !area.primary && area.name !== '未分类').map((area) => area.name),
      areaDetails,
      tagsText: paper.tags.join(', ')
    })
    editVisible.value = true
  }
  if (command === 'ai') router.push({ path: '/ai', query: { paper: paper.id } })
  if (command === 'delete') confirmDelete([paper.id])
}
async function saveEdit() {
  try {
    const selectedAreas = [
      editForm.value.area,
      ...editForm.value.relatedAreas.filter((area) => area !== editForm.value.area)
    ].map((name, index) => ({
      name,
      confidence: editForm.value.areaDetails.find((area) => area.name === name)?.confidence ?? (index === 0 ? 1 : 0.7),
      primary: index === 0
    }))
    await store.updatePaper(editForm.value.id, {
      titleZh: editForm.value.titleZh,
      title: editForm.value.title,
      area: editForm.value.area,
      areas: selectedAreas,
      year: editForm.value.year,
      abstract: editForm.value.abstract,
      tags: editForm.value.tagsText.split(/[,，]/).map((item) => item.trim()).filter(Boolean)
    })
    editVisible.value = false
    ElMessage.success('文献信息已更新到 MySQL')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '文献信息更新失败'))
  }
}
async function confirmDelete(ids) {
  if (store.preferences.confirmPaperDeletion) {
    try {
      await ElMessageBox.confirm(`确定从知识库移除选中的 ${ids.length} 篇文献吗？`, '删除确认', { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' })
    } catch {
      return
    }
  }
  try {
    await Promise.all(ids.map((id) => store.deletePaper(id)))
    selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
    ElMessage.success('文献已从 MySQL 移除')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '删除文献失败'))
    await store.loadPapers()
  }
}
function batchDelete() { confirmDelete([...selectedIds.value]) }
async function batchFavorite() {
  const papers = selectedIds.value
    .map((id) => store.findPaper(id))
    .filter((paper) => paper && !paper.favorite)
  try {
    await Promise.all(papers.map((paper) => store.toggleFavorite(paper.id)))
    ElMessage.success(`已收藏 ${papers.length} 篇文献`)
    selectedIds.value = []
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '批量收藏失败'))
    await store.loadPapers()
  }
}
async function toggleFavorite(paper) {
  try {
    await store.toggleFavorite(paper.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '收藏状态更新失败'))
  }
}
function exportReferences() {
  const records = filteredPapers.value.map((paper) => `${paper.authors.join(', ')}. ${paper.title}. ${paper.journal}, ${paper.year}. ${paper.doi}`).join('\n\n')
  const blob = new Blob([records], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'ResearchMind-文献引用.txt'
  anchor.click()
  URL.revokeObjectURL(url)
  ElMessage.success('引用列表已导出')
}
</script>

<style scoped>
.library-toolbar{display:flex;align-items:center;gap:10px;padding:14px 16px;box-shadow:none}.search-box{display:flex;height:36px;min-width:300px;flex:1;align-items:center;gap:8px;padding:0 10px;border:1px solid #dfe4ed;border-radius:6px}.search-box .app-icon{width:16px;color:#9aa4b5}.search-box input{min-width:0;flex:1;border:0;outline:0;color:#3f4b61;font-size:11px}.search-box button{border:0;color:#a0a9b8;background:transparent;font-size:16px}.library-toolbar select,.view-actions select{height:36px;padding:0 27px 0 10px;border:1px solid #dfe4ed;border-radius:6px;outline:0;color:#657188;background:#fff;font-size:10px}.filter-reset{display:flex;height:36px;align-items:center;gap:6px;padding:0 10px;border:0;color:#718097;background:transparent;font-size:10px}.filter-reset:disabled{opacity:.4}.filter-reset .app-icon{width:14px}
.library-summary{display:flex;align-items:center;justify-content:space-between;margin:18px 2px 10px;color:#8590a2;font-size:10px}.library-summary strong{color:#46546c}.view-actions{display:flex;align-items:center;gap:6px}.view-actions select{height:30px;border:0;background:transparent}.view-actions button{display:grid;width:29px;height:29px;place-items:center;border:1px solid #e1e5ed;color:#9aa3b3;background:#fff}.view-actions button:first-of-type{border-radius:5px 0 0 5px}.view-actions button:last-child{margin-left:-6px;border-radius:0 5px 5px 0}.view-actions button.active{z-index:1;color:#3156d3;border-color:#9cace5;background:#f3f5ff}
.paper-table{overflow:hidden;box-shadow:none}.paper-table-head,.paper-row{display:grid;grid-template-columns:40px minmax(430px,2fr) minmax(130px,.7fr) 90px minmax(120px,.65fr) 115px;align-items:center}.paper-table-head{height:42px;padding:0 13px;border-bottom:1px solid #e9ecf2;color:#919bad;background:#f8f9fb;font-size:9px;font-weight:600}.paper-row{min-height:112px;padding:0 13px;border-bottom:1px solid #edf0f4;color:#67748a;font-size:10px;transition:.15s}.paper-row:last-child{border:0}.paper-row:hover{background:#fbfcff}.check-cell{display:flex;justify-content:center}.check-cell input{accent-color:#3156d3}.paper-info{display:flex;min-width:0;align-items:flex-start;gap:12px;padding:14px 18px 14px 0}.paper-file{display:grid;width:35px;height:43px;flex:0 0 auto;place-items:center;border-radius:5px;color:#d05a5a;background:#fff0f0;font-size:8px;font-weight:700}.paper-info>div{min-width:0}.paper-info strong{display:block;overflow:hidden;color:#334158;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.paper-info p{overflow:hidden;margin:4px 0;color:#798599;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.paper-info small{display:block;overflow:hidden;color:#a0a8b6;font-size:8px;text-overflow:ellipsis;white-space:nowrap}.paper-tags{display:flex;gap:5px;margin-top:7px}.area-dot{display:inline-block;width:6px;height:6px;margin-right:6px;border-radius:50%}.progress-cell{padding-right:18px}.progress-cell>div{display:flex;justify-content:space-between;color:#7d899c;font-size:9px}.progress-cell strong{color:#69768a}.progress-cell>i{display:block;overflow:hidden;height:3px;margin-top:7px;border-radius:3px;background:#edf0f4}.progress-cell b{display:block;height:100%;border-radius:inherit}.row-actions{display:flex;align-items:center;gap:3px}.row-actions button,.paper-card-top button{display:grid;width:29px;height:29px;place-items:center;border:0;border-radius:5px;color:#9aa4b4;background:transparent}.row-actions button:hover{color:#3156d3;background:#eef2ff}.row-actions button.favorite,.paper-card-top button.favorite{color:#e29a2d}.row-actions .app-icon,.paper-card-top .app-icon{width:15px}
.paper-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:15px}.paper-card{padding:19px;cursor:pointer;transition:.18s}.paper-card:hover{transform:translateY(-2px);box-shadow:0 14px 30px rgba(23,41,81,.1)}.paper-card-top{display:flex;align-items:flex-start;justify-content:space-between}.area-chip{display:inline-block;margin-top:14px;color:#3156d3;font-size:9px;font-weight:600}.paper-card h3{height:42px;margin:7px 0 5px;overflow:hidden;color:#334158;font-size:13px;line-height:1.6}.paper-card>p{height:28px;margin:0;overflow:hidden;color:#8c96a8;font-size:9px;line-height:1.5}.card-authors{margin-top:12px;color:#929bad;font-size:9px}.card-progress{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:8px;margin-top:17px;padding-top:12px;border-top:1px solid #edf0f4;color:#8490a3;font-size:8px}.card-progress i{overflow:hidden;height:3px;border-radius:3px;background:#edf0f4}.card-progress b{display:block;height:100%;background:#3156d3}.card-progress strong{color:#58667d}
.empty-library{display:flex;min-height:390px;flex-direction:column;align-items:center;justify-content:center}.empty-library>span{display:grid;width:54px;height:54px;place-items:center;border-radius:50%;color:#8090be;background:#f0f3fb}.empty-library h3{margin:14px 0 5px;color:#546176;font-size:13px}.empty-library p{margin:0 0 17px;color:#9aa3b2;font-size:10px}.pagination-row{display:flex;align-items:center;justify-content:space-between;margin-top:15px;color:#8b95a7;font-size:9px}.pagination-row div{display:flex}.pagination-row button{width:30px;height:29px;margin-left:-1px;border:1px solid #e0e4ec;color:#778398;background:#fff}.pagination-row button:first-child{border-radius:5px 0 0 5px}.pagination-row button:last-child{border-radius:0 5px 5px 0}.pagination-row button.active{z-index:1;color:#fff;border-color:#3156d3;background:#3156d3}.pagination-row button:disabled{color:#c6ccd5}
.batch-bar{position:fixed;z-index:15;right:35px;bottom:25px;display:flex;align-items:center;gap:8px;padding:9px 11px 9px 17px;border-radius:9px;color:#fff;background:#17233f;box-shadow:0 12px 30px rgba(18,30,60,.25);font-size:10px}.batch-bar>span{margin-right:8px}.batch-bar button{display:flex;height:30px;align-items:center;gap:5px;padding:0 9px;border:1px solid rgba(255,255,255,.14);border-radius:5px;color:#dce4f6;background:rgba(255,255,255,.06);font-size:9px}.batch-bar button.danger{color:#ffb8b8}.batch-bar .app-icon{width:13px}.edit-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}
</style>
