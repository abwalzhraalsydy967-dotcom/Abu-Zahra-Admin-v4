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
      // Create account in Firebase Auth first (client-side for email verification)
      const { createUserWithEmailAndPassword, sendEmailVerification } = await import('firebase/auth')
      const cred = await createUserWithEmailAndPassword(auth, email, password)
      
      // Send verification email
      addLog('info', 'إرسال رسالة التحقق إلى البريد الإلكتروني...')
      await sendEmailVerification(cred.user)
      
      // Sign out from Firebase client (we use server-side auth)
      await auth.signOut()
      
      addLog('success', 'تم إنشاء الحساب بنجاح! تم إرسال رسالة التحقق', `تحقق من بريدك: ${email}`)
      
      // Also register on the server
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
        // Server registration failed but Firebase account created
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
    try {
      const { GoogleAuthProvider, signInWithPopup } = await import('firebase/auth')
      
      const provider = new GoogleAuthProvider()
      provider.addScope('email')
      provider.addScope('profile')
      
      const result = await signInWithPopup(auth, provider)
      const idToken = await result.user.getIdToken()
      const email = result.user.email || ''
      const displayName = result.user.displayName || ''
      
      addLog('info', 'تم المصادقة عبر Google', `البريد: ${email}`)
      
      // Check if email is verified
      if (!result.user.emailVerified) {
        addLog('warning', 'البريد الإلكتروني غير مُتحقق منه في Google', 'يُفضل التحقق من البريد في إعدادات Google')
      }
      
      // Send to server
      const res: ApiResponse = await api.firebaseAuth(idToken, email, displayName)
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
        return true
      } else {
        addLog('error', 'فشل تسجيل الدخول عبر Google', res.message || 'خطأ من الخادم')
        await auth.signOut()
        return false
      }
    } catch (err: unknown) {
      const firebaseErr = err as { code?: string; message?: string }
      if (firebaseErr.code === 'auth/popup-closed-by-user') {
        addLog('warning', 'تم إلغاء تسجيل الدخول عبر Google', 'أغلق المستخدم النافذة')
      } else {
        addLog('error', 'خطأ في تسجيل الدخول عبر Google', firebaseErr.message || firebaseErr.code || 'خطأ غير معروف')
      }
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