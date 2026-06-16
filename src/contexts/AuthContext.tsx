'use client'

import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react'
import { auth } from '@/lib/firebase'
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
      const { createUserWithEmailAndPassword, sendEmailVerification } = await import('firebase/auth')
      const cred = await createUserWithEmailAndPassword(auth, email, password)

      addLog('info', 'إرسال رسالة التحقق إلى البريد الإلكتروني...')
      await sendEmailVerification(cred.user)

      await auth.signOut()

      addLog('success', 'تم إنشاء الحساب بنجاح! تم إرسال رسالة التحقق', `تحقق من بريدك: ${email}`)

      const res: ApiResponse = await api.register(email, username, password)
      if (res.ok && res.token) {
        const userData: User = {
          id: res.user_id || '',
          username: res.username || username,
          email: res.email || email,
          role: res.role || 'user',
          token: res.token,
          permanent_code: res.permanent_code,
          expires_at: res.expires_at,
        }
        saveUser(userData)
        setView('dashboard')
        return true
      } else {
        addLog('warning', 'تم إنشاء حساب Firebase لكن فشل التسجيل في الخادم', res.message || '')
        setPendingEmail(email)
        setView('verify-email')
        return false
      }
    } catch (err: unknown) {
      const firebaseErr = err as { code?: string; message?: string }
      const msg = firebaseErr.code === 'auth/email-already-in-use'
        ? 'البريد الإلكتروني مستخدم بالفعل'
        : firebaseErr.code === 'auth/weak-password'
        ? 'كلمة المرور ضعيفة (6 أحرف على الأقل)'
        : firebaseErr.code === 'auth/invalid-email'
        ? 'بريد إلكتروني غير صالح'
        : firebaseErr.message || 'خطأ غير معروف'
      addLog('error', 'فشل إنشاء الحساب', msg)
      return false
    }
  }, [saveUser])

  const loginWithGoogle = useCallback(async (): Promise<boolean> => {
    addLog('info', 'محاولة تسجيل الدخول عبر Google...')

    const clientId = process.env.NEXT_PUBLIC_FIREBASE_WEB_CLIENT_ID
    if (!clientId) {
      addLog('error', 'معرف عميل Google غير موجود', 'تحقق من إعدادات Firebase')
      return false
    }

    try {
      await loadGISScript()
      addLog('info', 'تم تحميل Google Identity Services', `العميل: ${clientId.substring(0, 20)}...`)

      const { GoogleAuthProvider, signInWithCredential } = await import('firebase/auth')

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
              const credential = GoogleAuthProvider.credential(response.id_token)
              const result = await signInWithCredential(auth, credential)
              const email = result.user.email || ''
              const displayName = result.user.displayName || ''

              addLog('info', 'تم المصادقة عبر Google بنجاح', `البريد: ${email}`)

              const res: ApiResponse = await api.firebaseAuth(response.id_token, email, displayName)
              if (res.ok && res.token) {
                const userData: User = {
                  id: res.user_id || '',
                  username: res.username || displayName,
                  email: res.email || email,
                  role: res.role || 'user',
                  token: res.token,
                  permanent_code: res.permanent_code,
                  expires_at: res.expires_at,
                }
                saveUser(userData)
                setView('dashboard')
                addLog('success', 'تم تسجيل الدخول عبر Google بنجاح', res.message || '')
                resolve(true)
              } else {
                addLog('error', 'فشل تسجيل الدخول عبر Google', res.message || 'خطأ من الخادم')
                await auth.signOut()
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
    auth.signOut().catch(() => {})
    setView('login')
  }, [user])

  return (
    <AuthContext.Provider value={{ user, loading: false, view, pendingEmail, login, register, loginWithGoogle, setView, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}