import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshPromise = null

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    const refreshToken = localStorage.getItem('refresh_token')

    if (error.response?.status === 401 && !original?._retry && refreshToken) {
      original._retry = true
      try {
        if (!refreshPromise) {
          refreshPromise = axios
            .post('/api/auth/refresh', { refreshToken })
            .then((response) => {
              const data = response.data.data
              localStorage.setItem('access_token', data.accessToken)
              localStorage.setItem('refresh_token', data.refreshToken)
              return data.accessToken
            })
            .finally(() => {
              refreshPromise = null
            })
        }
        const accessToken = await refreshPromise
        original.headers.Authorization = `Bearer ${accessToken}`
        return http(original)
      } catch (refreshError) {
        clearAuth()
        window.location.reload()
      }
    }

    return Promise.reject(error)
  }
)

export function saveAuth(data) {
  localStorage.setItem('access_token', data.accessToken)
  localStorage.setItem('refresh_token', data.refreshToken)
  localStorage.setItem('user_info', JSON.stringify(data.user))
}

export function clearAuth() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user_info')
  localStorage.removeItem('chat_memory_id')
}

export function currentUser() {
  const raw = localStorage.getItem('user_info')
  return raw ? JSON.parse(raw) : null
}

export default http
