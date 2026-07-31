const LOCAL_TOKEN_KEY = 'researchmind-access-token'
const SESSION_TOKEN_KEY = 'researchmind-session-token'

export function getAccessToken() {
  return localStorage.getItem(LOCAL_TOKEN_KEY) || sessionStorage.getItem(SESSION_TOKEN_KEY)
}

export function setAccessToken(token, rememberMe) {
  clearAccessToken()
  const storage = rememberMe ? localStorage : sessionStorage
  storage.setItem(rememberMe ? LOCAL_TOKEN_KEY : SESSION_TOKEN_KEY, token)
}

export function clearAccessToken() {
  localStorage.removeItem(LOCAL_TOKEN_KEY)
  sessionStorage.removeItem(SESSION_TOKEN_KEY)
}

export function hasAccessToken() {
  return Boolean(getAccessToken())
}
