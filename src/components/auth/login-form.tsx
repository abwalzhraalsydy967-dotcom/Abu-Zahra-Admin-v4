'use client'

import React, { useState } from 'react'
import { motion } from 'framer-motion'
import { Mail, Lock, Eye, EyeOff, Loader2, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'
import { addLog } from '@/lib/utils'

interface LoginFormProps {
  onLoginSuccess?: () => void
}

export default function LoginForm({ onLoginSuccess }: LoginFormProps) {
  const { login, loginWithGoogle, setView } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [isGoogleLoading, setIsGoogleLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!email.trim() || !password.trim()) {
      setError('يرجى ملء جميع الحقول')
      addLog('warning', 'محاولة تسجيل دخول بحقول فارغة')
      return
    }

    setIsLoading(true)
    addLog('info', 'بدء تسجيل الدخول', `البريد: ${email}`)

    try {
      const success = await login(email, password)
      if (success) {
        addLog('success', 'تم تسجيل الدخول بنجاح')
        onLoginSuccess?.()
      } else {
        setError('البريد الإلكتروني أو كلمة المرور غير صحيحة')
      }
    } catch {
      setError('حدث خطأ غير متوقع. يرجى المحاولة مرة أخرى')
      addLog('error', 'خطأ غير متوقع أثناء تسجيل الدخول')
    } finally {
      setIsLoading(false)
    }
  }

  const handleGoogleLogin = async () => {
    setError('')
    setIsGoogleLoading(true)
    addLog('info', 'بدء تسجيل الدخول عبر Google')

    try {
      const success = await loginWithGoogle()
      if (success) {
        addLog('success', 'تم تسجيل الدخول عبر Google بنجاح')
        onLoginSuccess?.()
      } else {
        setError('فشل تسجيل الدخول عبر Google')
      }
    } catch {
      setError('حدث خطأ أثناء تسجيل الدخول عبر Google')
      addLog('error', 'خطأ في تسجيل الدخول عبر Google')
    } finally {
      setIsGoogleLoading(false)
    }
  }

  return (
    <div
      dir="rtl"
      className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-b from-emerald-950 via-slate-900 to-emerald-950 relative overflow-hidden"
    >
      {/* Decorative background elements */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 right-1/4 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 left-1/4 w-80 h-80 bg-emerald-600/8 rounded-full blur-3xl" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-emerald-500/5 rounded-full blur-3xl" />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className="w-full max-w-md relative z-10"
      >
        <div className="bg-white/10 backdrop-blur-xl border border-white/20 rounded-2xl p-8 shadow-2xl shadow-black/20">
          {/* Logo & Title */}
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2, duration: 0.5 }}
            className="text-center mb-8"
          >
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-gradient-to-br from-emerald-500 to-emerald-600 mb-4 shadow-lg shadow-emerald-500/25">
              <ShieldCheck className="w-8 h-8 text-white" />
            </div>
            <h1 className="text-3xl font-bold text-white tracking-tight">
              أبو زهرة
            </h1>
            <p className="text-emerald-300/80 text-sm mt-1">لوحة التحكم</p>
          </motion.div>

          {/* Error Message */}
          {error && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              className="mb-4 p-3 rounded-lg bg-red-500/15 border border-red-500/25 text-red-300 text-sm text-center"
            >
              {error}
            </motion.div>
          )}

          {/* Login Form */}
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Email Field */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.3, duration: 0.4 }}
              className="space-y-2"
            >
              <Label htmlFor="email" className="text-white/80 text-sm font-medium">
                البريد الإلكتروني أو اسم المستخدم
              </Label>
              <div className="relative">
                <Mail className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                <Input
                  id="email"
                  type="text"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="example@email.com"
                  className={cn(
                    'h-11 pr-10 pl-4 bg-white/5 border-white/10 text-white',
                    'placeholder:text-white/40 rounded-xl',
                    'focus-visible:border-emerald-500/50 focus-visible:ring-emerald-500/20',
                    'transition-all duration-200'
                  )}
                  autoComplete="email"
                  disabled={isLoading}
                />
              </div>
            </motion.div>

            {/* Password Field */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.4, duration: 0.4 }}
              className="space-y-2"
            >
              <Label htmlFor="password" className="text-white/80 text-sm font-medium">
                كلمة المرور
              </Label>
              <div className="relative">
                <Lock className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className={cn(
                    'h-11 pr-10 pl-10 bg-white/5 border-white/10 text-white',
                    'placeholder:text-white/40 rounded-xl',
                    'focus-visible:border-emerald-500/50 focus-visible:ring-emerald-500/20',
                    'transition-all duration-200'
                  )}
                  autoComplete="current-password"
                  disabled={isLoading}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40 hover:text-white/70 transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? (
                    <EyeOff className="w-4 h-4" />
                  ) : (
                    <Eye className="w-4 h-4" />
                  )}
                </button>
              </div>
            </motion.div>

            {/* Login Button */}
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5, duration: 0.4 }}
            >
              <Button
                type="submit"
                disabled={isLoading}
                className={cn(
                  'w-full h-11 text-base font-semibold rounded-xl',
                  'bg-gradient-to-r from-emerald-600 to-emerald-500',
                  'hover:from-emerald-500 hover:to-emerald-400',
                  'shadow-lg shadow-emerald-500/25',
                  'transition-all duration-200',
                  'text-white border-0'
                )}
              >
                {isLoading ? (
                  <span className="flex items-center gap-2">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    جارِ تسجيل الدخول...
                  </span>
                ) : (
                  'تسجيل الدخول'
                )}
              </Button>
            </motion.div>
          </form>

          {/* Divider */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.6, duration: 0.4 }}
            className="flex items-center gap-4 my-6"
          >
            <div className="flex-1 h-px bg-white/15" />
            <span className="text-white/40 text-sm font-medium">أو</span>
            <div className="flex-1 h-px bg-white/15" />
          </motion.div>

          {/* Google Login Button */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.7, duration: 0.4 }}
          >
            <Button
              type="button"
              onClick={handleGoogleLogin}
              disabled={isGoogleLoading}
              className={cn(
                'w-full h-11 text-base font-medium rounded-xl',
                'bg-white/10 border border-white/20',
                'hover:bg-white/20 text-white',
                'transition-all duration-200'
              )}
            >
              {isGoogleLoading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  جارِ الاتصال...
                </span>
              ) : (
                <span className="flex items-center gap-2">
                  <svg className="w-5 h-5" viewBox="0 0 24 24">
                    <path
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"
                      fill="#4285F4"
                    />
                    <path
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                      fill="#34A853"
                    />
                    <path
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                      fill="#FBBC05"
                    />
                    <path
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                      fill="#EA4335"
                    />
                  </svg>
                  تسجيل الدخول عبر Google
                </span>
              )}
            </Button>
          </motion.div>

          {/* Register Link */}
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.8, duration: 0.4 }}
            className="text-center mt-6 text-white/50 text-sm"
          >
            ليس لديك حساب؟{' '}
            <button
              type="button"
              onClick={() => setView('register')}
              className="text-emerald-400 hover:text-emerald-300 font-medium transition-colors underline-offset-4 hover:underline"
            >
              إنشاء حساب جديد
            </button>
          </motion.p>
        </div>

        {/* Footer */}
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 1, duration: 0.4 }}
          className="text-center mt-6 text-white/25 text-xs"
        >
          © {new Date().getFullYear()} أبو زهرة — جميع الحقوق محفوظة
        </motion.p>
      </motion.div>
    </div>
  )
}