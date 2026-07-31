<template>
  <el-dialog
    :model-value="modelValue"
    class="pdf-reader-dialog"
    width="calc(100vw - 48px)"
    top="2vh"
    append-to-body
    :close-on-click-modal="false"
    destroy-on-close
    @close="closeReader"
  >
    <template #header>
      <div class="reader-title">
        <strong>{{ paper?.title || paper?.titleZh || 'PDF 原文阅读' }}</strong>
        <span v-if="paper?.fileName">{{ paper.fileName }}</span>
      </div>
    </template>

    <div class="reader-toolbar">
      <div class="page-controls">
        <button
          :disabled="!pdfDocument || persistedPage < 1"
          @click="scrollToPage(persistedPage)"
        >回到上次位置</button>
        <span>当前第 <strong>{{ activePage || '—' }}</strong> / {{ totalPages || '—' }} 页</span>
      </div>

      <div class="reader-progress">
        <span>已读到第 {{ savedPage }} 页</span>
        <i><b :style="{ width: `${readingProgress}%` }"></b></i>
        <strong>{{ readingProgress }}%</strong>
        <small v-if="savingCount">同步中…</small>
      </div>

      <div class="zoom-controls">
        <button :disabled="!pdfDocument || zoom <= 0.7" title="缩小" @click="changeZoom(-0.1)">−</button>
        <span>{{ Math.round(zoom * 100) }}%</span>
        <button :disabled="!pdfDocument || zoom >= 1.8" title="放大" @click="changeZoom(0.1)">＋</button>
      </div>
    </div>

    <div ref="viewerBody" class="pdf-viewer-body" @scroll.passive="handleViewerScroll">
      <div v-if="loading" class="reader-state">
        <i class="reader-spinner"></i>
        <strong>正在安全加载整篇 PDF…</strong>
        <span>加载完成后可连续上下滚动阅读</span>
      </div>
      <div v-else-if="errorMessage" class="reader-state error">
        <strong>PDF 原文无法显示</strong>
        <span>{{ errorMessage }}</span>
        <button class="secondary-button" @click="loadPdf">重新加载</button>
      </div>
      <div v-else class="page-stack">
        <article
          v-for="page in pageInfos"
          :key="page.number"
          :ref="(element) => setPageElement(page.number, element)"
          class="pdf-page-shell"
          :data-page="page.number"
          :style="pageStyle(page)"
        >
          <canvas
            :ref="(element) => setPageCanvas(page.number, element)"
            class="pdf-page-canvas"
          ></canvas>
          <div v-if="!renderedPages.has(page.number) && !pageErrors[page.number]" class="page-placeholder">
            <i class="reader-spinner small"></i>
            <span>第 {{ page.number }} 页</span>
          </div>
          <div v-if="pageErrors[page.number]" class="page-placeholder error">
            <span>{{ pageErrors[page.number] }}</span>
            <button @click="renderPage(page.number, true)">重试本页</button>
          </div>
          <small class="page-number">{{ page.number }} / {{ totalPages }}</small>
        </article>
      </div>
    </div>

    <template #footer>
      <div class="reader-footer">
        <span>
          <template v-if="store.preferences.autoSaveReadingProgress">
            本次有效阅读 {{ formatDuration(sessionReadSeconds) }} ·
            累计 {{ formatDuration(displayTotalReadSeconds) }}
            （仅标签页可见且窗口处于焦点时计时）
          </template>
          <template v-else>自动记录已关闭，本次阅读不会更新页码和累计时长</template>
        </span>
        <button class="secondary-button" @click="closeReader">退出阅读</button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch
} from 'vue'
import { ElMessage } from 'element-plus'
import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs'
import pdfWorkerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'
import { apiErrorMessage, http } from '../api/http'
import { useAppStore } from '../stores/app'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

const props = defineProps({
  modelValue: Boolean,
  paper: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])
const store = useAppStore()
const viewerBody = ref(null)
const loading = ref(false)
const errorMessage = ref('')
// PDF.js 对象包含 JavaScript 私有字段，不能被 Vue 深度响应式 Proxy 包装。
const pdfDocument = shallowRef(null)
const pageInfos = ref([])
const renderedPages = ref(new Set())
const pageErrors = ref({})
const activePage = ref(0)
const totalPages = ref(0)
const savedPage = ref(0)
const persistedPage = ref(0)
const zoom = ref(1)
const viewerWidth = ref(900)
const savingCount = ref(0)
const sessionReadSeconds = ref(0)
const pendingReadMilliseconds = ref(0)
const inFlightReadSeconds = ref(0)
const pageElements = new Map()
const pageCanvases = new Map()
const renderTasks = new Map()
const renderingPages = new Set()
let loadingTask = null
let renderObserver = null
let loadSequence = 0
let renderGeneration = 0
let scrollFrame = null
let resizeTimer = null
let progressSaveTimer = null
let readingTimer = null
let lastReadingTick = 0
let sessionReadMilliseconds = 0
let flushInFlight = false

const readingProgress = computed(() => (
  totalPages.value
    ? Math.min(100, Math.ceil(savedPage.value * 100 / totalPages.value))
    : 0
))
const displayTotalReadSeconds = computed(() => (
  Number(props.paper?.totalReadSeconds || 0)
  + Math.floor(pendingReadMilliseconds.value / 1000)
  + inFlightReadSeconds.value
))

watch(
  () => [props.modelValue, props.paper?.id],
  ([visible, paperId], previous = []) => {
    if (visible && paperId && (!previous[0] || previous[1] !== paperId)) {
      loadPdf()
    }
    if (!visible) {
      captureReadingTime()
      flushReadingState()
      disposePdf()
    }
  },
  { immediate: true }
)

async function loadPdf() {
  if (!props.paper?.id || !props.paper.fileAvailable) {
    errorMessage.value = '该文献没有可用的 PDF 原文'
    return
  }
  const sequence = ++loadSequence
  await disposePdf(false)
  resetReaderState()
  loading.value = true
  try {
    const { data } = await http.get(`/papers/${props.paper.id}/file`, {
      responseType: 'arraybuffer',
      timeout: 60000
    })
    if (sequence !== loadSequence) return
    loadingTask = pdfjsLib.getDocument({ data: new Uint8Array(data) })
    const document = await loadingTask.promise
    if (sequence !== loadSequence) {
      await document.destroy()
      return
    }
    pdfDocument.value = document
    totalPages.value = document.numPages
    const infos = await Promise.all(
      Array.from({ length: document.numPages }, async (_, index) => {
        const page = await document.getPage(index + 1)
        const viewport = page.getViewport({ scale: 1 })
        return {
          number: index + 1,
          width: viewport.width,
          height: viewport.height
        }
      })
    )
    if (sequence !== loadSequence) return
    pageInfos.value = infos
    const recordedPage = Math.min(
      document.numPages,
      Math.max(0, Number(props.paper.currentPage || 0))
    )
    const legacyPage = props.paper.progress
      ? Math.ceil(Number(props.paper.progress) * document.numPages / 100)
      : 0
    persistedPage.value = recordedPage
    savedPage.value = Math.min(document.numPages, Math.max(recordedPage, legacyPage))
    loading.value = false
    await nextTick()
    updateViewerWidth()
    setupRenderObserver()
    scrollToPage(
      store.preferences.resumeReading ? (savedPage.value || 1) : 1,
      'auto'
    )
    updateActivePage()
    renderNearbyPages()
    lastReadingTick = performance.now()
  } catch (error) {
    if (sequence !== loadSequence) return
    loading.value = false
    errorMessage.value = error?.response
      ? apiErrorMessage(error, 'PDF 原文读取失败')
      : String(error?.message || '请确认 PDF 文件完整且格式受支持')
  }
}

function resetReaderState() {
  loading.value = false
  errorMessage.value = ''
  pageInfos.value = []
  renderedPages.value = new Set()
  pageErrors.value = {}
  activePage.value = 0
  totalPages.value = 0
  savedPage.value = Math.max(0, Number(props.paper?.currentPage || 0))
  persistedPage.value = savedPage.value
  zoom.value = 1
  sessionReadSeconds.value = 0
  pendingReadMilliseconds.value = 0
  inFlightReadSeconds.value = 0
  sessionReadMilliseconds = 0
  lastReadingTick = 0
  pageElements.clear()
  pageCanvases.clear()
}

function setupRenderObserver() {
  renderObserver?.disconnect()
  renderObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          renderPage(Number(entry.target.dataset.page))
        }
      })
    },
    {
      root: viewerBody.value,
      rootMargin: '900px 0px',
      threshold: 0
    }
  )
  pageElements.forEach((element) => renderObserver.observe(element))
}

function setPageElement(pageNumber, element) {
  if (element) pageElements.set(pageNumber, element)
  else pageElements.delete(pageNumber)
}

function setPageCanvas(pageNumber, element) {
  if (element) pageCanvases.set(pageNumber, element)
  else pageCanvases.delete(pageNumber)
}

function pageScale(page) {
  const availableWidth = Math.max(320, viewerWidth.value - 72)
  return Math.min(1.6, availableWidth / page.width) * zoom.value
}

function pageStyle(page) {
  const scale = pageScale(page)
  return {
    width: `${Math.floor(page.width * scale)}px`,
    height: `${Math.floor(page.height * scale)}px`
  }
}

async function renderPage(pageNumber, force = false) {
  if (
    !pdfDocument.value
    || renderingPages.has(pageNumber)
    || (!force && renderedPages.value.has(pageNumber))
  ) return
  const canvas = pageCanvases.get(pageNumber)
  const info = pageInfos.value[pageNumber - 1]
  if (!canvas || !info) return
  const generation = renderGeneration
  renderingPages.add(pageNumber)
  if (force) {
    const nextErrors = { ...pageErrors.value }
    delete nextErrors[pageNumber]
    pageErrors.value = nextErrors
  }
  try {
    const page = await pdfDocument.value.getPage(pageNumber)
    if (generation !== renderGeneration) return
    const viewport = page.getViewport({ scale: pageScale(info) })
    const outputScale = Math.min(window.devicePixelRatio || 1, 2)
    canvas.width = Math.floor(viewport.width * outputScale)
    canvas.height = Math.floor(viewport.height * outputScale)
    const task = page.render({
      canvas,
      viewport,
      transform: outputScale === 1
        ? null
        : [outputScale, 0, 0, outputScale, 0, 0]
    })
    renderTasks.set(pageNumber, task)
    await task.promise
    if (generation !== renderGeneration) return
    renderedPages.value = new Set([...renderedPages.value, pageNumber])
    if (pageNumber === activePage.value) updateActivePage()
  } catch (error) {
    if (error?.name !== 'RenderingCancelledException') {
      console.error(`PDF page ${pageNumber} render failed`, error)
      pageErrors.value = {
        ...pageErrors.value,
        [pageNumber]: String(error?.message || `第 ${pageNumber} 页渲染失败`)
      }
    }
  } finally {
    renderingPages.delete(pageNumber)
    renderTasks.delete(pageNumber)
  }
}

function handleViewerScroll() {
  if (scrollFrame) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = null
    updateActivePage()
    renderNearbyPages()
  })
}

function updateActivePage() {
  if (!viewerBody.value || !pageElements.size) return
  const viewerRect = viewerBody.value.getBoundingClientRect()
  const center = viewerRect.top + viewerRect.height / 2
  let closestPage = activePage.value || 1
  let closestDistance = Number.POSITIVE_INFINITY
  pageElements.forEach((element, pageNumber) => {
    const rect = element.getBoundingClientRect()
    const distance = Math.abs(rect.top + rect.height / 2 - center)
    if (distance < closestDistance) {
      closestDistance = distance
      closestPage = pageNumber
    }
  })
  activePage.value = closestPage
  if (renderedPages.value.has(closestPage)) {
    if (closestPage > savedPage.value) savedPage.value = closestPage
    if (
      store.preferences.autoSaveReadingProgress
      && closestPage > persistedPage.value
    ) scheduleProgressSave()
  }
}

function renderNearbyPages() {
  if (!viewerBody.value) return
  const viewerRect = viewerBody.value.getBoundingClientRect()
  pageElements.forEach((element, pageNumber) => {
    const rect = element.getBoundingClientRect()
    if (rect.bottom >= viewerRect.top - 900 && rect.top <= viewerRect.bottom + 900) {
      renderPage(pageNumber)
    }
  })
}

function scheduleProgressSave() {
  if (!store.preferences.autoSaveReadingProgress) return
  window.clearTimeout(progressSaveTimer)
  progressSaveTimer = window.setTimeout(() => flushReadingState(), 500)
}

async function flushReadingState() {
  if (!store.preferences.autoSaveReadingProgress) {
    pendingReadMilliseconds.value = 0
    lastReadingTick = 0
    return
  }
  if (flushInFlight || !props.paper?.id || !pdfDocument.value) return
  captureReadingTime()
  const targetPage = Math.max(1, savedPage.value || activePage.value || 1)
  const seconds = Math.min(60, Math.floor(pendingReadMilliseconds.value / 1000))
  if (seconds === 0 && targetPage <= persistedPage.value) return
  pendingReadMilliseconds.value -= seconds * 1000
  inFlightReadSeconds.value += seconds
  flushInFlight = true
  savingCount.value++
  try {
    const updated = await store.updateReadingPage(
      props.paper.id,
      targetPage,
      seconds
    )
    persistedPage.value = Math.max(
      persistedPage.value,
      Number(updated?.currentPage || targetPage)
    )
    savedPage.value = Math.max(savedPage.value, persistedPage.value)
  } catch (error) {
    pendingReadMilliseconds.value += seconds * 1000
    ElMessage.error(apiErrorMessage(error, '阅读进度或时长自动保存失败'))
  } finally {
    inFlightReadSeconds.value -= seconds
    savingCount.value--
    flushInFlight = false
    if (
      pendingReadMilliseconds.value >= 1000
      || savedPage.value > persistedPage.value
    ) {
      scheduleProgressSave()
    }
  }
}

function isActivelyReading() {
  return Boolean(
    store.preferences.autoSaveReadingProgress
    &&
    props.modelValue
    && pdfDocument.value
    && !loading.value
    && renderedPages.value.has(activePage.value)
    && document.visibilityState === 'visible'
    && document.hasFocus()
  )
}

function captureReadingTime() {
  if (!store.preferences.autoSaveReadingProgress) {
    lastReadingTick = 0
    pendingReadMilliseconds.value = 0
    return
  }
  const now = performance.now()
  if (isActivelyReading() && lastReadingTick) {
    const elapsed = Math.min(2000, Math.max(0, now - lastReadingTick))
    pendingReadMilliseconds.value += elapsed
    sessionReadMilliseconds += elapsed
    sessionReadSeconds.value = Math.floor(sessionReadMilliseconds / 1000)
  }
  lastReadingTick = isActivelyReading() ? now : 0
}

function scrollToPage(pageNumber, behavior = 'smooth') {
  const target = pageElements.get(
    Math.max(1, Math.min(totalPages.value || 1, Number(pageNumber) || 1))
  )
  target?.scrollIntoView({ behavior, block: 'start' })
}

function changeZoom(delta) {
  zoom.value = Math.max(
    0.7,
    Math.min(1.8, Math.round((zoom.value + delta) * 10) / 10)
  )
  invalidateRenderedPages()
}

function invalidateRenderedPages() {
  renderGeneration++
  renderTasks.forEach((task) => task.cancel())
  renderTasks.clear()
  renderingPages.clear()
  renderedPages.value = new Set()
  pageErrors.value = {}
  pageCanvases.forEach((canvas) => {
    canvas.width = 0
    canvas.height = 0
  })
  nextTick(() => {
    setupRenderObserver()
    renderNearbyPages()
  })
}

function updateViewerWidth() {
  const nextWidth = Number(viewerBody.value?.clientWidth || 900)
  if (Math.abs(nextWidth - viewerWidth.value) < 5) return
  viewerWidth.value = nextWidth
}

function scheduleResize() {
  window.clearTimeout(resizeTimer)
  resizeTimer = window.setTimeout(() => {
    const previousWidth = viewerWidth.value
    updateViewerWidth()
    if (pdfDocument.value && previousWidth !== viewerWidth.value) {
      invalidateRenderedPages()
    }
  }, 200)
}

function handleVisibilityChange() {
  captureReadingTime()
  if (!isActivelyReading()) flushReadingState()
}

async function disposePdf(invalidate = true) {
  if (invalidate) loadSequence++
  renderGeneration++
  renderObserver?.disconnect()
  renderObserver = null
  renderTasks.forEach((task) => task.cancel())
  renderTasks.clear()
  renderingPages.clear()
  const task = loadingTask
  const document = pdfDocument.value
  loadingTask = null
  pdfDocument.value = null
  try {
    if (task) await task.destroy()
    else if (document) await document.destroy()
  } catch {
    // 关闭阅读器时清理已终止的 worker，无需向用户报错。
  }
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  if (total < 60) return `${total} 秒`
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor(total % 3600 / 60)
  return hours ? `${hours} 小时 ${minutes} 分钟` : `${minutes} 分钟`
}

function closeReader() {
  captureReadingTime()
  flushReadingState()
  emit('update:modelValue', false)
}

onMounted(() => {
  readingTimer = window.setInterval(() => {
    captureReadingTime()
    if (pendingReadMilliseconds.value >= 15000) flushReadingState()
  }, 1000)
  window.addEventListener('resize', scheduleResize)
  window.addEventListener('focus', handleVisibilityChange)
  window.addEventListener('blur', handleVisibilityChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  captureReadingTime()
  flushReadingState()
  window.clearInterval(readingTimer)
  window.clearTimeout(resizeTimer)
  window.clearTimeout(progressSaveTimer)
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
  window.removeEventListener('resize', scheduleResize)
  window.removeEventListener('focus', handleVisibilityChange)
  window.removeEventListener('blur', handleVisibilityChange)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  disposePdf()
})
</script>

<style scoped>
.reader-title{display:flex;min-width:0;flex-direction:column;padding-right:40px}.reader-title strong{overflow:hidden;color:#2f3c54;font-size:14px;text-overflow:ellipsis;white-space:nowrap}.reader-title span{margin-top:4px;color:#98a1b0;font-size:8px}.reader-toolbar{display:grid;grid-template-columns:auto minmax(280px,1fr) auto;position:sticky;z-index:2;top:0;align-items:center;gap:24px;padding:10px 16px;border-bottom:1px solid #dfe3ea;background:#fff}.page-controls,.zoom-controls{display:flex;align-items:center;gap:8px;color:#7c8799;font-size:9px}.page-controls button,.zoom-controls button{height:30px;padding:0 10px;border:1px solid #dce1e9;border-radius:5px;color:#5f6d83;background:#fff;font-size:9px}.page-controls button:disabled,.zoom-controls button:disabled{color:#c3c9d2;background:#f7f8fa}.page-controls strong{color:#3156d3}.reader-progress{display:grid;grid-template-columns:auto minmax(90px,240px) auto auto;align-items:center;justify-content:center;gap:8px;color:#7d899b;font-size:8px}.reader-progress>i{overflow:hidden;height:4px;border-radius:4px;background:#e7eaf0}.reader-progress b{display:block;height:100%;border-radius:inherit;background:#3156d3}.reader-progress strong{color:#405170;font-size:9px}.reader-progress small{color:#3156d3}.zoom-controls span{width:37px;text-align:center}.zoom-controls button{width:30px;padding:0;font-size:15px}.pdf-viewer-body{position:relative;overflow:auto;height:calc(96vh - 180px);min-height:520px;background:#dfe3e9}.page-stack{display:flex;min-width:max-content;min-height:100%;align-items:center;gap:22px;padding:28px 36px 50px;flex-direction:column}.pdf-page-shell{position:relative;flex:0 0 auto;background:#fff;box-shadow:0 8px 28px rgba(31,42,63,.2);transition:width .15s,height .15s}.pdf-page-canvas{display:block;width:100%;height:100%;background:#fff}.page-placeholder{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:9px;color:#929cad;background:#f8f9fb;font-size:9px}.page-placeholder.error{padding:20px;flex-direction:column;color:#bb5858;text-align:center}.page-placeholder button{padding:5px 9px;border:1px solid #dce1e9;border-radius:4px;color:#627087;background:#fff;font-size:8px}.page-number{position:absolute;right:9px;bottom:7px;padding:3px 6px;border-radius:9px;color:#fff;background:rgba(28,40,62,.66);font-size:7px}.reader-state{display:flex;height:100%;min-height:520px;flex-direction:column;align-items:center;justify-content:center;color:#7f8999}.reader-state strong{margin-top:12px;color:#536076;font-size:12px}.reader-state span{margin-top:5px;font-size:9px}.reader-state button{margin-top:14px}.reader-state.error strong{color:#bd5555}.reader-spinner{width:31px;height:31px;border:3px solid #d6dbe5;border-top-color:#3156d3;border-radius:50%;animation:reader-spin .8s linear infinite}.reader-spinner.small{width:18px;height:18px;border-width:2px}.reader-footer{display:flex;align-items:center;justify-content:space-between}.reader-footer span{color:#7d899b;font-size:8px}@keyframes reader-spin{to{transform:rotate(360deg)}}:global(.pdf-reader-dialog){margin-bottom:2vh}:global(.pdf-reader-dialog .el-dialog__body){padding:0!important}:global(.pdf-reader-dialog .el-dialog__footer){padding:10px 16px!important}
@media(max-width:800px){.reader-toolbar{grid-template-columns:1fr auto;gap:8px}.reader-progress{grid-column:1/-1;grid-row:2}.pdf-viewer-body{height:calc(96vh - 225px)}.reader-footer span{max-width:72%}}
</style>
