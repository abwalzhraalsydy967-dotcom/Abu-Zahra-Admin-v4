'use client'

import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { api, type ApiResponse } from '@/lib/api'
import { addLog } from '@/lib/utils'

export interface User {
  id: string
  username: string
  email: string
  role: string
  token: string
  permanent_code?: string
  expires_at?: string
}

interface AuthContextType {
  user: User | null
  loading: boolean
  view: 'login' | 'register' | 'verify-email' | 'dashboard'
  pendingEmail: string | null
  verificationLink: string | null
  login: (username: string, password: string) => Promise<boolean>
  register: (email: string, username: string, password: string) => Promise<boolean>
  loginWithGoogle: () => Promise<boolean>
  setView: (view: 'login' | 'register' | 'verify-email' | 'dashboard') => void
  logout: () => void
}

const AuthContext = createContext<AuthContextType | null>(null)

function getInitialUser(): { user: User | null; view: 'login' | 'register' | 'verify-email' | 'dashboard' } {
  if (typeof window === 'undefined') return { user: null, view: 'login' }
  const saved = localStorage.getItem('auth_user')
  if (saved) {
    try {
      const parsed = JSON.parse(saved) as User
      api.setToken(parsed.token)
      return { user: parsed, view: 'dashboard' }
    } catch {
      localStorage.removeItem('auth_user')
    }
  }
  return { user: null, view: 'login' }
}

/** Load Google Identity Services script dynamically */
function loadGISScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    const w = window as unknown as { google?: { accounts?: Record<string, unknown> } }
    if (w.google?.accounts) {
      resolve()
      return
    }
    const script = document.createElement('script')
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('فشل تحميل Google Identity Services'))
    document.head.appendChild(script)
  })
}

interface GISTokenResponse {
  access_token?: string
  id_token?: string
  error?: string
  error_description?: string
  error_uri?: string
  scope?: string
  token_type?: string
  expires_in?: number
}

interface GISOAuth2 {
  initTokenClient: (config: {
    client_id: string
    scope: string
    callback: (response: GISTokenResponse) => void
    error_callback?: (error: unknown) => void
  }) => {
    requestAccessToken: (overrideConfig?: Record<string, unknown>) => void
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => getInitialUser().user)
  const [view, setView] = useState<'login' | 'register' | 'verify-email' | 'dashboard'>(() => getInitialUser().view)
  const [pendingEmail, setPendingEmail] = useState<string | null>(null)
  const [verificationLink, setVerificationLink] = useState<string | null>(null)

  useEffect(() => {
    const saved = localStorage.getItem('auth_user')
    if (saved) {
      addLog('info', 'تم استعادة الجلسة المحفوظة')
    }
  }, [])

  const saveUser = useCallback((userData: User) => {
    setUser(userData)
    api.setToken(userData.token)
    localStorage.setItem('auth_user', JSON.stringify(userData))
  }, [])

  const login = useCallback(async (username: string, password: string): Promise<boolean> => {
    addLog('info', 'محاولة تسجيل الدخول...', `اسم المستخدم: ${username}`)
    try {
      const res: ApiResponse = await api.login(username, password)
      if (res.ok && res.token) {
        const userData: User = {
          id: res.user_id || '',
          username: res.username || username,
          email: res.email || '',
          role: res.role || 'user',
          token: res.token,
          permanent_code: res.permanent_code,
          expires_at: res.expires_at,
        }
        saveUser(userData)
        setView('dashboard')
        addLog('success', 'تم تسجيل الدخول بنجاح', res.message || '')
        return true
      } else {
        addLog('error', 'فشل تسجيل الدخول', res.message || 'خطأ غير معروف')
        return false
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في تسجيل الدخول', msg)
      return false
    }
  }, [saveUser])

  const register = useCallback(async (email: string, username: string, password: string): Promise<boolean> => {
    addLog('info', 'محاولة إنشاء حساب جديد...', `البريد: ${email}`)
    try {
      // Use our API route which uses Firebase Admin SDK
      const res = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, displayName: username }),
      })

      const data = await res.json() as Record<string, unknown>

      if (data.ok) {
        addLog('success', 'تم إنشاء الحساب! تم إرسال رسالة التحقق', `تحقق من بريدك: ${email}`)

        // Store verification link for display
        if (data.verification_link) {
          setVerificationLink(data.verification_link as string)
        }

        // If server also registered successfully
        if (data.server_ok && data.token) {
          const userData: User = {
            id: (data.user_id as string) || '',
            username: (data.username as string) || username,
            email: email,
            role: (data.role as string) || 'user',
            token: data.token as string,
          }
          saveUser(userData)
          setView('dashboard')
          return true
        }

        // Account created, waiting for email verification
        setPendingEmail(email)
        setView('verify-email')
        return true
      } else {
        addLog('error', 'فشل إنشاء الحساب', (data.message as string) || 'خطأ غير معروف')
        return false
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في إنشاء الحساب', msg)
      return false
    }
  }, [saveUser])

  const loginWithGoogle = useCallback(async (): Promise<boolean> => {
    addLog('info', 'محاولة تسجيل الدخول عبر Google...')

    const clientId = process.env.NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID || '159319780620-sq56idflgn6up0n7f9rvogml8rlonp95.apps.googleusercontent.com'
    if (!clientId) {
      addLog('error', 'معرف عميل Google غير موجود', 'تحقق من إعدادات Firebase')
      return false
    }

    try {
      await loadGISScript()
      addLog('info', 'تم تحميل Google Identity Services')

      return new Promise<boolean>((resolve) => {
        const w = window as unknown as { google: { accounts: { oauth2: GISOAuth2 } } }

        const tokenClient = w.google.accounts.oauth2.initTokenClient({
          client_id: clientId,
          scope: 'openid email profile',
          callback: async (response: GISTokenResponse) => {
            if (response.error) {
              addLog('error', 'خطأ Google OAuth', response.error_description || response.error)
              resolve(false)
              return
            }

            if (!response.id_token) {
              addLog('error', 'لم يتم استلام رمز المعرّف من Google', 'حاول مرة أخرى')
              resolve(false)
              return
            }

            try {
              // Send to our API route which verifies with Admin SDK
              addLog('info', 'تم الحصول على رمز Google، جارٍ التحقق...')
              const apiRes = await fetch('/api/auth/google', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ idToken: response.id_token }),
              })

              const apiData = await apiRes.json() as Record<string, unknown>

              if (apiData.ok && apiData.token) {
                const email = (apiData.email as string) || ''
                const displayName = (apiData.username as string) || ''

                addLog('success', 'تم تسجيل الدخول عبر Google بنجاح', `البريد: ${email}`)

                const userData: User = {
                  id: (apiData.user_id as string) || '',
                  username: displayName,
                  email,
                  role: (apiData.role as string) || 'user',
                  token: apiData.token as string,
                  permanent_code: apiData.permanent_code as string | undefined,
                  expires_at: apiData.expires_at as string | undefined,
                }
                saveUser(userData)
                setView('dashboard')
                resolve(true)
              } else {
                addLog('error', 'فشل تسجيل الدخول عبر Google', (apiData.message as string) || 'خطأ من الخادم')
                resolve(false)
              }
            } catch (credErr) {
              const msg = credErr instanceof Error ? credErr.message : 'خطأ في المصادقة'
              addLog('error', 'خطأ في معالجة بيانات Google', msg)
              resolve(false)
            }
          },
        })

        tokenClient.requestAccessToken()
      })
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في تسجيل الدخول عبر Google', msg)
      return false
    }
  }, [saveUser])

  const logout = useCallback(() => {
    addLog('info', 'تسجيل الخروج', `المستخدم: ${user?.username || 'غير معروف'}`)
    setUser(null)
    api.setToken(null)
    localStorage.removeItem('auth_user')
    setView('login')
  }, [user])

  return (
    <AuthContext.Provider value={{ user, loading: false, view, pendingEmail, verificationLink, login, register, loginWithGoogle, setView, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}