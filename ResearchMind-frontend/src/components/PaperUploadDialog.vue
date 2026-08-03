<template>
  <el-dialog :model-value="modelValue" width="620px" title="导入科研文献" @close="close">
    <div v-if="step === 1">
      <el-upload
        drag
        action="#"
        accept=".pdf"
        :auto-upload="false"
        multiple
        :limit="10"
        :on-change="handleFile"
        :on-remove="handleRemove"
        :on-exceed="handleExceed"
      >
        <AppIcon name="upload" class="upload-icon" />
        <div class="upload-title">拖拽 PDF 到这里，或 <em>点击选择文件</em></div>
        <p>一次最多选择 10 篇，单个文件不超过 50 MB</p>
      </el-upload>
      <p v-if="selectedFiles.length > 1" class="batch-hint">已加入 {{ selectedFiles.length }} 篇，将逐篇解析并供你核对后导入。</p>
      <div class="import-tips">
        <span><AppIcon name="sparkles" /></span>
        <div><strong>PDF 解析 + AI 补全</strong><p>系统先提取 PDF 元数据，再由 AI 补全缺失的作者、关键词、研究领域等信息；你可以在导入前检查并修改。</p></div>
      </div>
      <el-checkbox v-model="aiEnrich" class="ai-consent">使用 AI 补全缺失信息（会将 PDF 正文节选发送至已配置的 DeepSeek 服务）</el-checkbox>
    </div>
    <el-form v-else :model="form" label-position="top">
      <div v-if="selectedFiles.length > 1" class="batch-progress">
        批量导入 {{ currentIndex + 1 }} / {{ selectedFiles.length }} · {{ fileName }}
      </div>
      <div class="parse-result" :class="{ warning: parseMeta.aiWarning }">
        <span><AppIcon :name="parseMeta.aiWarning ? 'info' : 'check'" /></span>
        <div>
          <strong>{{ parseMeta.aiEnriched ? 'PDF 解析与 AI 补全完成' : parseMeta.model ? 'PDF 解析与 AI 核对完成' : 'PDF 本地解析完成' }}</strong>
          <p v-if="parseMeta.aiEnriched">AI 已补全：{{ parseMeta.fields.join('、') }}。请在导入前核对自动生成的信息。</p>
          <p v-else-if="parseMeta.aiWarning">{{ parseMeta.aiWarning }}</p>
          <p v-else-if="parseMeta.model">AI 未发现能够可靠补全的缺失字段。</p>
          <p v-else>已从 {{ fileName }} 提取 {{ form.pages }} 页 PDF 的元数据</p>
        </div>
      </div>
      <el-form-item label="原始标题（自动识别）"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="中文译名（可选）"><el-input v-model="form.titleZh" /></el-form-item>
      <div class="form-grid">
        <el-form-item label="作者（用逗号分隔）"><el-input v-model="form.authors" /></el-form-item>
        <el-form-item label="主研究领域"><el-select v-model="form.primaryArea" filterable style="width:100%"><el-option v-for="area in areas" :key="area" :label="area" :value="area" /></el-select></el-form-item>
      </div>
      <el-form-item label="作者机构 / 单位（每行一个）"><el-input v-model="form.institutions" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="关联研究领域（可多选）">
        <el-select v-model="form.relatedAreas" multiple filterable collapse-tags style="width:100%" placeholder="选择论文涉及的其他领域">
          <el-option v-for="area in relatedAreaOptions" :key="area" :label="area" :value="area" />
        </el-select>
      </el-form-item>
      <div v-if="parseMeta.model && aiAreaDetails.length" class="area-confidence">
        <strong>AI 领域建议</strong>
        <span v-for="area in aiAreaDetails" :key="area.name">
          {{ area.name }} · {{ area.primary ? '主领域' : '关联领域' }} · {{ Math.round((area.confidence ?? 0) * 100) }}%
        </span>
        <p>关联领域不是必填，请移除与论文核心研究无关的建议。</p>
      </div>
      <div class="form-grid">
        <el-form-item label="发表年份"><el-input-number v-model="form.year" :min="1900" :max="new Date().getFullYear()" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="期刊 / 会议"><el-input v-model="form.journal" /></el-form-item>
      </div>
      <el-form-item label="DOI"><el-input v-model="form.doi" placeholder="例如 10.1000/example" /></el-form-item>
      <el-form-item label="关键词（保持论文原文语言，用逗号分隔）"><el-input v-model="form.tags" /></el-form-item>
      <el-form-item label="摘要"><el-input v-model="form.abstract" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <button class="secondary-button" @click="close">取消</button>
      <button v-if="step === 1" class="primary-button" :disabled="!selectedFiles.length || parsing" @click="parseFile">
        <AppIcon name="sparkles" /> {{ parsing ? '正在智能解析…' : selectedFiles.length > 1 ? `开始批量解析（${selectedFiles.length} 篇）` : '开始解析' }}
      </button>
      <button v-else class="primary-button" :disabled="!form.title || saving" @click="savePaper">
        <AppIcon name="check" /> {{ saving ? '正在写入 MySQL…' : currentIndex + 1 < selectedFiles.length ? '导入并解析下一篇' : '确认导入' }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from './AppIcon.vue'
import { useAppStore } from '../stores/app'
import { apiErrorMessage, http } from '../api/http'

defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'imported'])
const store = useAppStore()
const step = ref(1)
const parsing = ref(false)
const saving = ref(false)
const aiEnrich = ref(true)
const fileName = ref('')
const selectedFiles = ref([])
const currentIndex = ref(0)
const areas = ['自然语言处理', '图神经网络', '计算机视觉', '大语言模型', '时间序列', '可信人工智能', '隐私计算', '未分类']
const form = reactive({
  titleZh: '',
  title: '',
  authors: '',
  institutions: '',
  primaryArea: '未分类',
  relatedAreas: [],
  areaDetails: [],
  year: null,
  journal: '',
  tags: '',
  abstract: '',
  doi: '',
  pages: 0,
  uploadId: ''
})
const parseMeta = reactive({
  aiEnriched: false,
  fields: [],
  model: '',
  aiWarning: ''
})
const relatedAreaOptions = computed(() => areas.filter(
  (area) => area !== '未分类' && area !== form.primaryArea
))
const aiAreaDetails = computed(() => form.areaDetails.filter(
  (area) => area.name !== '未分类'
))

function selectedAreas() {
  const names = [
    form.primaryArea || '未分类',
    ...form.relatedAreas.filter((area) => area !== form.primaryArea)
  ]
  return names.map((name, index) => {
    const parsed = form.areaDetails.find((area) => area.name === name)
    return {
      name,
      confidence: parsed?.confidence ?? (index === 0 ? 1 : 0.7),
      primary: index === 0
    }
  })
}

function handleFile(file, fileList) {
  const invalid = fileList.find((item) => item.size > 50 * 1024 * 1024)
  if (invalid) ElMessage.error(`${invalid.name} 超过 50 MB，已从队列忽略`)
  selectedFiles.value = fileList
    .filter((item) => item.size <= 50 * 1024 * 1024 && item.raw)
    .map((item) => ({ name: item.name, raw: item.raw }))
  currentIndex.value = 0
  fileName.value = selectedFiles.value[0]?.name || ''
}
function handleRemove(file, fileList) {
  selectedFiles.value = fileList
    .filter((item) => item.size <= 50 * 1024 * 1024 && item.raw)
    .map((item) => ({ name: item.name, raw: item.raw }))
  currentIndex.value = 0
  fileName.value = selectedFiles.value[0]?.name || ''
}
function handleExceed() {
  ElMessage.warning('一次最多批量导入 10 篇 PDF')
}
async function parseFile() {
  const selectedFile = selectedFiles.value[currentIndex.value]
  if (!selectedFile) return
  fileName.value = selectedFile.name
  parsing.value = true
  try {
    const body = new FormData()
    body.append('file', selectedFile.raw, selectedFile.name)
    body.append('aiEnrich', String(aiEnrich.value))
    const { data } = await http.post('/uploads/papers', body, { timeout: 200000 })
    form.uploadId = data.uploadId
    form.title = data.title || ''
    form.titleZh = data.titleZh || ''
    form.authors = (data.authors || []).join(', ')
    form.institutions = (data.institutions || []).join('\n')
    form.areaDetails = data.areas?.length
      ? data.areas
      : [{ name: data.area || '未分类', confidence: 1, primary: true }]
    form.primaryArea = form.areaDetails.find((area) => area.primary)?.name
      || data.area
      || form.areaDetails[0]?.name
      || '未分类'
    form.relatedAreas = form.areaDetails
      .filter((area) => area.name !== form.primaryArea && area.name !== '未分类')
      .map((area) => area.name)
    form.year = data.year || null
    form.journal = data.journal || ''
    form.tags = (data.tags || []).join(', ')
    form.abstract = data.abstract || ''
    form.doi = data.doi || ''
    form.pages = data.pages || 0
    parseMeta.aiEnriched = Boolean(data.aiEnriched)
    parseMeta.fields = data.aiEnrichedFields || []
    parseMeta.model = data.aiModel || ''
    parseMeta.aiWarning = data.aiWarning || ''
    fileName.value = data.fileName || fileName.value
    step.value = 2
    ElMessage.success(data.aiEnriched ? 'PDF 解析与 AI 元数据补全完成' : 'PDF 本地解析完成')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, 'PDF 上传或解析失败'))
  } finally {
    parsing.value = false
  }
}
async function savePaper() {
  saving.value = true
  try {
    const created = await store.addPaper({
      title: form.title,
      titleZh: form.titleZh,
      authors: form.authors.split(/[,，]/).map((item) => item.trim()).filter(Boolean),
      institutions: form.institutions.split(/[\n;；]+/).map((item) => item.trim()).filter(Boolean),
      area: form.primaryArea,
      areas: selectedAreas(),
      year: form.year,
      journal: form.journal,
      doi: form.doi,
      tags: form.tags.split(/[,，]/).map((item) => item.trim()).filter(Boolean),
      abstract: form.abstract,
      fileName: fileName.value,
      pages: form.pages,
      uploadId: form.uploadId
    })
    ElMessage.success('文献元数据已写入 MySQL')
    emit('imported', created)
    form.uploadId = ''
    if (currentIndex.value + 1 < selectedFiles.value.length) {
      currentIndex.value++
      resetMetadata()
      fileName.value = selectedFiles.value[currentIndex.value].name
      step.value = 1
      await parseFile()
    } else {
      close()
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '文献导入失败'))
  } finally {
    saving.value = false
  }
}
function close() {
  if (form.uploadId && !saving.value) {
    const abandonedUploadId = form.uploadId
    form.uploadId = ''
    http.delete(`/uploads/${abandonedUploadId}`).catch(() => {})
  }
  emit('update:modelValue', false)
  window.setTimeout(() => {
    step.value = 1
    parsing.value = false
    saving.value = false
    fileName.value = ''
    selectedFiles.value = []
    currentIndex.value = 0
    aiEnrich.value = true
    resetMetadata()
  }, 250)
}
function resetMetadata() {
  Object.assign(form, {
      titleZh: '',
      title: '',
      authors: '',
      institutions: '',
      primaryArea: '未分类',
      relatedAreas: [],
      areaDetails: [],
      year: null,
      journal: '',
      tags: '',
      abstract: '',
      doi: '',
      pages: 0,
      uploadId: ''
    })
  Object.assign(parseMeta, {
      aiEnriched: false,
      fields: [],
      model: '',
      aiWarning: ''
  })
}
</script>

<style scoped>
:deep(.el-upload-dragger){width:100%;padding:35px 20px;border:1px dashed #bcc7e4;border-radius:9px;background:#fafbff}:deep(.el-upload){width:100%}
.upload-icon{width:31px;height:31px;margin-bottom:12px;color:#5973d6}.upload-title{color:#4b5870;font-size:12px}.upload-title em{color:#3156d3;font-style:normal;font-weight:600}:deep(.el-upload-dragger p){margin:8px 0 0;color:#9aa4b6;font-size:10px}
.ai-consent{margin-top:13px;color:#69768a}:deep(.ai-consent .el-checkbox__label){font-size:9px}
.batch-hint,.batch-progress{margin:10px 0 0;color:#5267b1;font-size:9px}.batch-progress{margin:0 0 12px;padding:8px 10px;border-radius:6px;background:#eef2ff}
.import-tips,.parse-result{display:flex;align-items:flex-start;gap:12px;margin-top:17px;padding:14px;border-radius:8px;background:#f3f6ff}.import-tips>span,.parse-result>span{display:grid;width:29px;height:29px;place-items:center;border-radius:7px;color:#3156d3;background:#e3eaff}.import-tips .app-icon,.parse-result .app-icon{width:14px}.import-tips strong,.parse-result strong{color:#45536c;font-size:11px}.import-tips p,.parse-result p{margin:4px 0 0;color:#8490a5;font-size:9px;line-height:1.6}.parse-result{margin:0 0 18px;background:#ecf8f3}.parse-result>span{color:#0e9f78;background:#d8f2e8}.parse-result.warning{background:#fff7e8}.parse-result.warning>span{color:#d18a24;background:#ffedc7}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}:deep(.el-form-item){margin-bottom:14px}
.area-confidence{display:flex;align-items:center;gap:6px;margin:-4px 0 14px;flex-wrap:wrap}.area-confidence strong{margin-right:2px;color:#68758a;font-size:9px}.area-confidence span{padding:4px 7px;border-radius:10px;color:#5267b1;background:#eef2ff;font-size:8px}.area-confidence p{width:100%;margin:1px 0 0;color:#9a7b48;font-size:8px}
</style>
