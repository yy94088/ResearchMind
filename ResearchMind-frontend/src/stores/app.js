import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { http } from '../api/http'
import { clearAccessToken, getAccessToken, setAccessToken } from '../api/authStorage'

const STORAGE_KEY = 'researchmind-data-v1'
const SESSION_KEY = 'researchmind-session'
const DEFAULT_PREFERENCES = Object.freeze({
  resumeReading: true,
  autoSaveReadingProgress: true,
  confirmPaperDeletion: true,
  defaultGridView: false
})

function loadData() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {}
  } catch {
    return {}
  }
}

function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY)) || null
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

function roleLabel(role) {
  return {
    USER: '科研用户',
    MANAGER: '团队管理员',
    ADMIN: '系统管理员'
  }[role] || role || '科研用户'
}

function profileFromUser(user) {
  return {
    id: user.id,
    username: user.username,
    name: user.realName,
    email: user.email,
    avatarUrl: user.avatarUrl,
    institution: user.institution || '',
    direction: user.researchDirection || '',
    bio: user.bio || '',
    role: roleLabel(user.role),
    roleCode: user.role,
    createTime: user.createTime
  }
}

export const useAppStore = defineStore('app', () => {
  const saved = loadData()
  const papers = ref([])
  const papersLoading = ref(false)
  const activities = ref([])
  const avatarSrc = ref('')
  const preferences = ref({ ...DEFAULT_PREFERENCES })
  let avatarObjectUrl = ''
  const profile = ref(saved.profile || {
    name: '林知远',
    email: 'lin.zhiyuan@example.com',
    institution: '华东智能计算研究院',
    direction: '图神经网络与知识发现',
    bio: '关注图学习、知识图谱与大语言模型的交叉研究。',
    role: '团队管理员'
  })
  const session = ref(loadSession())

  const favoritePapers = computed(() => papers.value.filter((paper) => paper.favorite))
  const readPapers = computed(() => papers.value.filter((paper) => paper.read))
  const totalPages = computed(() => papers.value.reduce((total, paper) => total + Number(paper.pages || 0), 0))
  const allTags = computed(() => [...new Set(papers.value.flatMap((paper) => paper.tags))])
  const areas = computed(() => [...new Set(
    papers.value.flatMap((paper) => (
      paper.areas?.length
        ? paper.areas.map((area) => area.name)
        : [paper.area]
    )).filter((area) => area && area !== '未分类')
  )])

  watch(profile, () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      profile: profile.value
    }))
  }, { deep: true })

  function saveSession(user, expiresAt) {
    profile.value = profileFromUser(user)
    session.value = {
      user,
      expiresAt,
      loginAt: new Date().toISOString()
    }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session.value))
  }

  function clearSession() {
    clearAccessToken()
    session.value = null
    papers.value = []
    preferences.value = { ...DEFAULT_PREFERENCES }
    clearAvatar()
    localStorage.removeItem(SESSION_KEY)
  }

  async function login(account, password, rememberMe) {
    const { data } = await http.post('/auth/login', { account, password, rememberMe })
    setAccessToken(data.accessToken, rememberMe)
    saveSession(data.user, data.expiresAt)
    await Promise.all([
      loadPapersSafely(),
      loadActivitiesSafely(),
      loadPreferencesSafely(),
      loadAvatar()
    ])
    return data.user
  }

  async function register(values) {
    const { data } = await http.post('/auth/register', values)
    setAccessToken(data.accessToken, true)
    saveSession(data.user, data.expiresAt)
    await Promise.all([
      loadPapersSafely(),
      loadActivitiesSafely(),
      loadPreferencesSafely(),
      loadAvatar()
    ])
    return data.user
  }

  async function restoreSession() {
    if (!getAccessToken()) {
      if (session.value) clearSession()
      return false
    }

    try {
      const { data } = await http.get('/auth/me')
      saveSession(data, session.value?.expiresAt)
    } catch {
      clearSession()
      return false
    }
    await Promise.all([
      loadPapersSafely(),
      loadActivitiesSafely(),
      loadPreferencesSafely(),
      loadAvatar()
    ])
    return true
  }

  async function logout() {
    try {
      if (getAccessToken()) await http.post('/auth/logout')
    } catch {
      // 后端暂时不可达时也必须允许用户清除本地登录状态。
    } finally {
      clearSession()
    }
  }

  async function loadPapers() {
    papersLoading.value = true
    try {
      const { data } = await http.get('/papers')
      papers.value = data
      return data
    } finally {
      papersLoading.value = false
    }
  }

  async function loadPapersSafely() {
    try {
      await loadPapers()
    } catch {
      papers.value = []
    }
  }

  async function loadActivities() {
    const { data } = await http.get('/activities')
    activities.value = data
    return data
  }

  async function loadActivitiesSafely() {
    try {
      await loadActivities()
    } catch {
      activities.value = []
    }
  }

  async function loadPreferences() {
    const { data } = await http.get('/preferences')
    preferences.value = {
      ...DEFAULT_PREFERENCES,
      ...data
    }
    return preferences.value
  }

  async function loadPreferencesSafely() {
    try {
      await loadPreferences()
    } catch {
      preferences.value = { ...DEFAULT_PREFERENCES }
    }
  }

  async function savePreferences(values) {
    const { data } = await http.put('/preferences', {
      resumeReading: Boolean(values.resumeReading),
      autoSaveReadingProgress: Boolean(values.autoSaveReadingProgress),
      confirmPaperDeletion: Boolean(values.confirmPaperDeletion),
      defaultGridView: Boolean(values.defaultGridView)
    })
    preferences.value = {
      ...DEFAULT_PREFERENCES,
      ...data
    }
    return preferences.value
  }

  function clearAvatar() {
    if (avatarObjectUrl) URL.revokeObjectURL(avatarObjectUrl)
    avatarObjectUrl = ''
    avatarSrc.value = ''
  }

  async function loadAvatar() {
    clearAvatar()
    if (!profile.value.avatarUrl) return ''
    try {
      const { data } = await http.get('/auth/avatar', { responseType: 'blob' })
      avatarObjectUrl = URL.createObjectURL(data)
      avatarSrc.value = avatarObjectUrl
    } catch {
      avatarSrc.value = ''
    }
    return avatarSrc.value
  }

  async function uploadAvatar(file) {
    const body = new FormData()
    body.append('file', file)
    const { data: user } = await http.post('/auth/avatar', body)
    profile.value = profileFromUser(user)
    if (session.value) {
      session.value.user = user
      localStorage.setItem(SESSION_KEY, JSON.stringify(session.value))
    }
    await loadAvatar()
    return avatarSrc.value
  }

  async function removeAvatar() {
    const { data: user } = await http.delete('/auth/avatar')
    profile.value = profileFromUser(user)
    if (session.value) {
      session.value.user = user
      localStorage.setItem(SESSION_KEY, JSON.stringify(session.value))
    }
    clearAvatar()
  }

  function paperPayload(paper) {
    const primaryArea = paper.area
      || paper.areas?.find((area) => area.primary)?.name
      || paper.areas?.[0]?.name
      || '未分类'
    const sourceAreas = paper.areas?.length
      ? paper.areas
      : [{ name: primaryArea, confidence: 1, primary: true }]
    const areaNames = new Set()
    const normalizedAreas = [
      { name: primaryArea, confidence: sourceAreas.find((area) => area.name === primaryArea)?.confidence ?? 1, primary: true },
      ...sourceAreas
        .filter((area) => area.name !== primaryArea)
        .map((area) => ({ name: area.name, confidence: area.confidence ?? 0.7, primary: false }))
    ].filter((area) => {
      const key = area.name?.trim().toLowerCase()
      if (!key || areaNames.has(key)) return false
      areaNames.add(key)
      return true
    })
    return {
      title: paper.title,
      titleZh: paper.titleZh || '',
      abstract: paper.abstract || '',
      doi: paper.doi || '',
      journal: paper.journal || '',
      year: paper.year || null,
      language: paper.language || 'en',
      fileName: paper.fileName || null,
      pages: paper.pages || 0,
      authors: paper.authors || [],
      tags: paper.tags || [],
      area: primaryArea,
      areas: normalizedAreas,
      uploadId: paper.uploadId || null
    }
  }

  async function addPaper(paper) {
    const { data: created } = await http.post('/papers', paperPayload(paper))
    papers.value.unshift(created)
    await loadActivitiesSafely()
    return created
  }

  async function updatePaper(id, patch) {
    const index = papers.value.findIndex((paper) => paper.id === id)
    if (index < 0) return null
    const merged = { ...papers.value[index], ...patch }
    const { data } = await http.put(`/papers/${id}`, paperPayload(merged))
    Object.assign(papers.value[index], data)
    return data
  }

  async function deletePaper(id) {
    const paper = papers.value.find((item) => item.id === id)
    await http.delete(`/papers/${id}`)
    papers.value = papers.value.filter((item) => item.id !== id)
    await loadActivitiesSafely()
  }

  async function toggleFavorite(id) {
    const index = papers.value.findIndex((item) => item.id === id)
    if (index < 0) return null
    const paper = papers.value[index]
    const { data } = await http.put(`/papers/${id}/favorite`, {
      favorite: !paper.favorite
    })
    Object.assign(papers.value[index], data)
    return data
  }

  async function updateReadingPage(id, currentPage, readSeconds = 0) {
    const index = papers.value.findIndex((item) => item.id === id)
    if (index < 0) return null
    const { data } = await http.put(`/papers/${id}/progress`, {
      currentPage,
      readSeconds
    })
    Object.assign(papers.value[index], data)
    return data
  }

  function replacePaper(updated) {
    const index = papers.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) Object.assign(papers.value[index], updated)
  }

  function findPaper(id) {
    const paper = papers.value.find((item) => item.id === id)
    return paper || null
  }

  async function updateProfile(values) {
    const { data: user } = await http.put('/auth/me', {
      realName: values.name,
      email: values.email,
      institution: values.institution || '',
      researchDirection: values.direction || '',
      bio: values.bio || ''
    })
    profile.value = profileFromUser(user)
    if (session.value) {
      session.value.user = user
      localStorage.setItem(SESSION_KEY, JSON.stringify(session.value))
    }
    return profile.value
  }

  return {
    papers,
    papersLoading,
    activities,
    avatarSrc,
    preferences,
    profile,
    session,
    favoritePapers,
    readPapers,
    totalPages,
    allTags,
    areas,
    login,
    register,
    restoreSession,
    clearSession,
    logout,
    loadPapers,
    loadActivities,
    loadActivitiesSafely,
    loadPreferences,
    savePreferences,
    loadAvatar,
    uploadAvatar,
    removeAvatar,
    addPaper,
    updatePaper,
    deletePaper,
    toggleFavorite,
    updateReadingPage,
    replacePaper,
    findPaper,
    updateProfile
  }
})
