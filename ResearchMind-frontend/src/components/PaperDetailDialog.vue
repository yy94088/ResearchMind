<template>
  <el-dialog :model-value="modelValue" width="760px" title="文献详情" @close="$emit('update:modelValue', false)">
    <div v-if="paper" class="paper-detail">
      <div class="detail-top">
        <span class="detail-pdf">PDF</span>
        <div>
          <div class="area-labels">
            <span v-for="area in paperAreas" :key="area.name" class="area-label" :class="{ primary: area.primary }">
              {{ area.name }} · {{ area.primary ? '主领域' : '关联领域' }}
            </span>
          </div>
          <h2>{{ paper.title || paper.titleZh }}</h2>
          <p v-if="paper.titleZh && paper.titleZh !== paper.title">中文译名：{{ paper.titleZh }}</p>
        </div>
      </div>
      <dl class="meta-grid">
        <div><dt>作者</dt><dd>{{ paper.authors.join('，') }}</dd></div>
        <div><dt>发表信息</dt><dd>{{ paper.journal }} · {{ paper.year }}</dd></div>
        <div><dt>机构</dt><dd>{{ paper.institutions?.join('，') || '暂未识别' }}</dd></div>
        <div><dt>DOI</dt><dd>{{ paper.doi || '暂未录入' }}</dd></div>
        <div><dt>导入日期</dt><dd>{{ paper.uploadDate }}</dd></div>
      </dl>
      <div class="detail-block"><h3>关键词</h3><div class="tag-row"><span v-for="tag in paper.tags" :key="tag" class="tag">{{ tag }}</span></div></div>
      <div class="detail-block"><h3>原文摘要</h3><p>{{ paper.abstract }}</p></div>
      <div class="reading-editor">
        <div class="reading-heading">
          <div>
            <h3>阅读进度</h3>
            <p>
              {{ progressStatus }}
              <span v-if="paper.lastReadTime"> · 最近更新 {{ formatReadTime(paper.lastReadTime) }}</span>
            </p>
          </div>
          <strong>{{ paper.progress }}%</strong>
        </div>
        <i class="reading-meter"><b :style="{ width: `${paper.progress}%` }"></b></i>
        <div class="reading-actions">
          <small v-if="paper.currentPage">已读到第 {{ paper.currentPage }} 页{{ paper.pages ? ` / 共 ${paper.pages} 页` : '' }} · 累计有效阅读 {{ formatDuration(paper.totalReadSeconds) }}。</small>
          <small v-else-if="paper.fileAvailable">尚未开始阅读，打开原文后将自动记录页码。</small>
          <small v-else>该条目没有 PDF 原文，暂时无法自动记录阅读进度。</small>
          <button
            v-if="paper.fileAvailable"
            class="primary-button"
            @click="readerVisible = true"
          >{{ paper.currentPage ? '继续阅读 PDF' : '开始阅读 PDF' }}</button>
        </div>
      </div>
      <div class="ai-summary">
        <div class="summary-heading"><span><AppIcon name="sparkles" /></span><strong>AI 核心解读</strong></div>
        <p v-if="analysisLoading">正在读取已保存的 DeepSeek 解读…</p>
        <template v-else-if="analysis">
          <p>{{ analysis.summary }}</p>
          <ul><li v-for="item in analysis.contributions" :key="item">{{ item }}</li></ul>
        </template>
        <p v-else>尚未生成 AI 解读，可点击“深度解读”后使用 DeepSeek 分析。</p>
      </div>
    </div>
    <template #footer>
      <button class="secondary-button" @click="$emit('update:modelValue', false)">关闭</button>
      <button v-if="paper?.fileAvailable" class="secondary-button" :disabled="downloading" @click="downloadFile">
        <AppIcon name="download" /> {{ downloading ? '正在读取…' : '下载 PDF 原文' }}
      </button>
      <button v-if="paper" class="secondary-button" @click="toggleFavorite">
        <AppIcon name="star" /> {{ paper.favorite ? '取消收藏' : '加入收藏' }}
      </button>
      <button class="primary-button" @click="goToAi"><AppIcon name="sparkles" /> 深度解读</button>
    </template>
    <PdfReaderDialog v-model="readerVisible" :paper="paper" />
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from './AppIcon.vue'
import PdfReaderDialog from './PdfReaderDialog.vue'
import { useAppStore } from '../stores/app'
import { apiErrorMessage, http } from '../api/http'

const props = defineProps({ modelValue: Boolean, paper: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue'])
const store = useAppStore()
const router = useRouter()
const downloading = ref(false)
const analysisLoading = ref(false)
const analysis = ref(null)
const readerVisible = ref(false)
const paperAreas = computed(() => props.paper?.areas?.length
  ? props.paper.areas
  : [{ name: props.paper?.area || '未分类', primary: true }]
)
const progressStatus = computed(() => {
  if (Number(props.paper?.progress || 0) >= 100) return '已读完'
  if (Number(props.paper?.progress || 0) > 0) return '阅读中'
  return '未开始'
})

watch(
  () => [props.modelValue, props.paper?.id],
  async ([visible,paperId]) => {
    analysis.value = null
    if (!visible || !paperId) return
    analysisLoading.value = true
    try {
      const { data } = await http.get(`/ai/papers/${paperId}/analysis`)
      analysis.value = data || null
    } catch {
      analysis.value = null
    } finally {
      analysisLoading.value = false
    }
  },
  { immediate: true }
)

function goToAi() {
  emit('update:modelValue', false)
  router.push({ path: '/ai', query: { paper: props.paper?.id } })
}

async function toggleFavorite() {
  try {
    await store.toggleFavorite(props.paper.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '收藏状态更新失败'))
  }
}

function formatReadTime(value) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  if (total < 60) return `${total} 秒`
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor(total % 3600 / 60)
  return hours ? `${hours} 小时 ${minutes} 分钟` : `${minutes} 分钟`
}

async function downloadFile() {
  downloading.value = true
  try {
    const { data } = await http.get(`/papers/${props.paper.id}/file`, {
      responseType: 'blob',
      timeout: 60000
    })
    const url = URL.createObjectURL(data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = props.paper.fileName || 'research-paper.pdf'
    anchor.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, 'PDF 原文下载失败'))
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.detail-top{display:flex;gap:16px}.detail-pdf{display:grid;width:48px;height:57px;flex:0 0 auto;place-items:center;border-radius:7px;color:#d25757;background:#fff0f0;font-size:10px;font-weight:700}.area-labels{display:flex;flex-wrap:wrap;gap:5px}.area-label{padding:3px 7px;border-radius:10px;color:#72809a;background:#f0f2f7;font-size:8px;font-weight:600}.area-label.primary{color:#3156d3;background:#edf1ff}.detail-top h2{margin:5px 0;color:#253149;font-size:17px;line-height:1.5}.detail-top p{margin:0;color:#8a95a8;font-size:10px}
.meta-grid{display:grid;grid-template-columns:1.25fr .75fr;gap:13px 25px;margin:21px 0;padding:15px 17px;border-radius:8px;background:#f7f9fc}.meta-grid div{min-width:0}.meta-grid dt{margin-bottom:4px;color:#98a1b1;font-size:8px}.meta-grid dd{overflow:hidden;margin:0;color:#556278;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.detail-block{margin-top:17px}.detail-block h3{margin:0 0 8px;color:#46536a;font-size:11px}.detail-block>p{margin:0;color:#69768b;font-size:10px;line-height:1.8}.tag-row{display:flex;gap:6px}
.reading-editor{margin-top:19px;padding:15px 16px;border:1px solid #e4e8ef;border-radius:9px;background:#f8f9fc}.reading-heading{display:flex;align-items:center;justify-content:space-between}.reading-heading h3{margin:0;color:#46536a;font-size:11px}.reading-heading p{margin:4px 0 0;color:#929bad;font-size:8px}.reading-heading strong{color:#3156d3;font-size:18px}.reading-meter{display:block;overflow:hidden;height:5px;margin:13px 0 11px;border-radius:5px;background:#e5e9f0}.reading-meter b{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#3156d3,#0e9f78)}.reading-actions{display:flex;align-items:center;justify-content:flex-end;gap:7px}.reading-actions small{margin-right:auto;color:#929bad;font-size:8px}.reading-actions button{height:31px;padding:0 10px;font-size:8px}
.ai-summary{margin-top:19px;padding:16px;border:1px solid #dfe5fb;border-radius:9px;background:linear-gradient(135deg,#f8f9ff,#f3f0fc)}.summary-heading{display:flex;align-items:center;gap:8px}.summary-heading>span{display:grid;width:26px;height:26px;place-items:center;border-radius:6px;color:#7255c9;background:#ebe5fb}.summary-heading .app-icon{width:13px}.summary-heading strong{color:#4f4772;font-size:11px}.ai-summary>p{margin:10px 0;color:#635f76;font-size:10px;line-height:1.7}.ai-summary ul{margin:8px 0 0;padding-left:18px;color:#69657b;font-size:9px;line-height:1.8}
</style>
