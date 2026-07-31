import axios from 'axios'
import { clearAccessToken, getAccessToken } from './authStorage'

export const http = axios.create({
  baseURL: '/api',
  timeout: 12000
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const hadToken = Boolean(getAccessToken())
    const requestUrl = error.config?.url || ''
    const isAuthSubmission = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register')

    if (error.response?.status === 401 && hadToken && !isAuthSubmission) {
      clearAccessToken()
      localStorage.removeItem('researchmind-session')
      window.dispatchEvent(new CustomEvent('researchmind:unauthorized'))
    }

    return Promise.reject(error)
  }
)

export function apiErrorMessage(error, fallback = '请求失败，请稍后重试') {
  if (!error.response) return '无法连接后端，请确认 Spring Boot 服务已在 8080 端口运行'
  const fieldMessage = Object.values(error.response.data?.fieldErrors || {})[0]
  return fieldMessage || error.response.data?.message || fallback
}
