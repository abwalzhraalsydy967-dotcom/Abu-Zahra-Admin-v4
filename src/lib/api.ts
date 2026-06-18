const SERVER_URL = process.env.NEXT_PUBLIC_SERVER_URL || 'https://alsydyabwalzhra.online'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface ApiResponse<T = any> {
  ok: boolean
  message?: string
  data?: T
  token?: string
  user_id?: string
  username?: string
  role?: string
  email?: string
  permanent_code?: string
  expires_at?: string
  // Server response field names
  devices?: T
  stats?: T
  events?: T
  users?: T
  commands?: T
  code?: string
  session_id?: string
}

export interface Device {
  id: string
  name: string
  model: string
  brand: string
  android_version: string
  battery_level: number
  is_charging: boolean
  is_online: boolean
  last_seen: string
  ip_address: string
  linked_at: string
  link_code: string
  owner_id: string
}

export interface Event {
  id: string
  type: string
  message: string
  level: string
  timestamp: string
  device_id?: string
  user_id?: string
}

export interface CommandItem {
  id: string
  device_id: string
  command: string
  status: string
  result?: unknown
  created_at: string
  completed_at?: string
}

export interface Stats {
  total_devices: number
  online_devices: number
  total_commands: number
  total_events: number
  total_users: number
  firebase: boolean
  uptime: number
  version: string
}

class ApiClient {
  private baseUrl: string
  private token: string | null = null

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl
  }

  setToken(token: string | null) {
    this.token = token
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private async request<T = any>(path: string, options: RequestInit = {}): Promise<ApiResponse<T>> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...((options.headers as Record<string, string>) || {}),
    }

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`
    }

    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        ...options,
        headers,
      })

      const data = await response.json()
      return data as ApiResponse<T>
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : 'خطأ غير معروف'
      return {
        ok: false,
        message: `خطأ في الاتصال بالخادم: ${msg}`,
      }
    }
  }

  // Auth
  async login(username: string, password: string) {
    return this.request('/api/web/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    })
  }

  async register(email: string, username: string, password: string) {
    return this.request('/api/web/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, password }),
    })
  }

  async firebaseAuth(idToken: string, email: string, displayName: string) {
    return this.request('/api/web/firebase_auth', {
      method: 'POST',
      body: JSON.stringify({
        id_token: idToken,
        email,
        display_name: displayName,
      }),
    })
  }

  // Dashboard
  async getDevices() {
    return this.request<Device[]>('/api/web/devices')
  }

  async getDeviceDetail(deviceId: string) {
    return this.request(`/api/web/device/${deviceId}`)
  }

  async getStats() {
    return this.request<Stats>('/api/web/stats')
  }

  async getEvents(limit = 50) {
    return this.request<Event[]>(`/api/web/events?limit=${limit}`)
  }

  async getCommands(deviceId?: string) {
    const query = deviceId ? `?device_id=${deviceId}` : ''
    return this.request<CommandItem[]>(`/api/web/commands${query}`)
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async sendCommand(deviceId: string, command: string, params?: Record<string, any>) {
    return this.request('/api/web/send_command', {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command, params }),
    })
  }

  async generateLinkCode() {
    return this.request<{ code: string }>('/api/web/link_code')
  }

  async getUsers() {
    return this.request('/api/web/users')
  }

  async deleteUser(userId: string) {
    return this.request(`/api/web/users/${userId}`, { method: 'DELETE' })
  }

  async getFiles() {
    return this.request('/api/web/files')
  }

  async unlinkDevice(deviceId: string) {
    return this.request(`/api/web/unlink/${deviceId}`, { method: 'DELETE' })
  }

  async logout() {
    return this.request('/api/web/logout', { method: 'POST' })
  }

  async healthCheck() {
    return this.request('/api/health')
  }
}

export const api = new ApiClient(SERVER_URL)
export default api