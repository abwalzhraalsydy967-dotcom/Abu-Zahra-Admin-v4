'use client'

import React, { useState, useMemo } from 'react'
import { motion } from 'framer-motion'
import { Mail, Lock, Eye, EyeOff, Loader2, User, ShieldCheck, ArrowRight, Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'
import { addLog } from '@/lib/utils'

function getPasswordStrength(password: string): {
  score: number
  label: string
  color: string
  width: string
} {
  if (!password) return { score: 0, label: '', color: '', width: '0%' }
  let score = 0
  if (password.length >= 6) score++
  if (password.length >= 10) score++
  if (/[A-Z]/.test(password)) score++
  if (/[0-9]/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++

  if (score <= 1) return { score, label: 'ضعيفة', color: 'bg-red-500', width: '33%' }
  if (score <= 3) return { score, label: 'متوسطة', color: 'bg-amber-500', width: '66%' }
  return { score, label: 'قوية', color: 'bg-emerald-500', width: '100%' }
}

function validateEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

export default function RegisterForm() {
  const { register, setView } = useAuth()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  const passwordStrength = useMemo(() => getPasswordStrength(password), [password])

  const validate = (): boolean => {
    if (!username.trim()) {
      setError('يرجى إدخال الاسم')
      return false
    }
    if (username.trim().length < 3) {
      setError('يجب أن يكون الاسم 3 أحرف على الأقل')
      return false
    }
    if (!email.trim()) {
      setError('يرجى إدخال البريد الإلكتروني')
      return false
    }
    if (!validateEmail(email)) {
      setError('بريد إلكتروني غير صالح')
      return false
    }
    if (!password) {
      setError('يرجى إدخال كلمة المرور')
      return false
    }
    if (password.length < 6) {
      setError('كلمة المرور يجب أن تكون 6 أحرف على الأقل')
      return false
    }
    if (password !== confirmPassword) {
      setError('كلمتا المرور غير متطابقتين')
      return false
    }
    return true
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!validate()) {
      addLog('warning', 'فشل التحقق من بيانات التسجيل', error)
      return
    }

    setIsLoading(true)
    addLog('info', 'بدء إنشاء حساب جديد', `البريد: ${email}, الاسم: ${username}`)

    try {
      const success = await register(email, username, password)
      if (!success) {
        setError('فشل إنشاء الحساب. يرجى المحاولة مرة أخرى')
      }
    } catch {
      setError('حدث خطأ غير متوقع. يرجى المحاولة لاحقاً')
      addLog('error', 'خطأ غير متوقع أثناء إنشاء الحساب')
    } finally {
      setIsLoading(false)
    }
  }

  const inputClass = cn(
    'h-11 pr-10 pl-10 bg-white/5 border-white/10 text-white',
    'placeholder:text-white/40 rounded-xl',
    'focus-visible:border-emerald-500/50 focus-visible:ring-emerald-500/20',
    'transition-all duration-200'
  )

  return (
    <div
      dir="rtl"
      className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-b from-emerald-950 via-slate-900 to-emerald-950 relative overflow-hidden"
    >
      {/* Decorative background */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/3 left-1/3 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl" />
        <div className="absolute bottom-1/3 right-1/4 w-80 h-80 bg-emerald-600/8 rounded-full blur-3xl" />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className="w-full max-w-md relative z-10"
      >
        <div className="bg-white/10 backdrop-blur-xl border border-white/20 rounded-2xl p-8 shadow-2xl shadow-black/20">
          {/* Header */}
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
            <p className="text-emerald-300/80 text-sm mt-1">إنشاء حساب جديد</p>
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

          {/* Registration Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Username Field */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.3, duration: 0.4 }}
              className="space-y-2"
            >
              <Label htmlFor="reg-username" className="text-white/80 text-sm font-medium">
                الاسم
              </Label>
              <div className="relative">
                <User className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                <Input
                  id="reg-username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="اسم المستخدم"
                  className={cn(inputClass, 'pl-4')}
                  autoComplete="username"
                  disabled={isLoading}
                />
              </div>
            </motion.div>

            {/* Email Field */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.35, duration: 0.4 }}
              className="space-y-2"
            >
              <Label htmlFor="reg-email" className="text-white/80 text-sm font-medium">
                البريد الإلكتروني
              </Label>
              <div className="relative">
                <Mail className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                <Input
                  id="reg-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="example@email.com"
                  className={cn(inputClass, 'pl-4')}
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
              <Label htmlFor="reg-password" className="text-white/80 text-sm font-medium">
                كلمة المرور
              </Label>
              <div className="relative">
                <Lock className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                <Input
                  id="reg-password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className={inputClass}
                  autoComplete="new-password"
                  disabled={isLoading}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40 hover:text-white/70 transition-colors"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {/* Password Strength Indicator */}
              {password && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  className="space-y-1.5"
                >
                  <div className="h-1.5 w-full bg-white/10 rounded-full overflow-hidden">
                    <motion.div
                      initial={{ width: '0%' }}
                      animate={{ width: passwordStrength.width }}
                      transition={{ duration: 0.3 }}
                      className={cn('h-full rounded-full', passwordStrength.color)}
                    />
                  </div>
                  <p className={cn(
                    'text-xs font-medium',
                    passwordStrength.score <= 1 ? 'text-red-400' :
                    passwordStrength.score <= 3 ? 'text-amber-400' : 'text-emerald-400'
                  )}>
                    قوة كلمة المرور: {passwordStrength.label}
                  </p>
                </motion.div>
              )}
            </motion.div>

            {/* Confirm Password Field */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.45, duration: 0.4 }}
              className="space-y-2"
            >
              <Label htmlFor="reg-confirm-password" className="text-white/80 text-sm font-medium">
                تأكيد كلمة المرور
              </Label>
              <div className="relative">
                <Lock className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
                <Input
                  id="reg-confirm-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••"
                  className={cn(
                    inputClass,
                    confirmPassword && confirmPassword !== password && 'border-red-500/50 focus-visible:border-red-500/50 focus-visible:ring-red-500/20'
                  )}
                  autoComplete="new-password"
                  disabled={isLoading}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-white/40 hover:text-white/70 transition-colors"
                  tabIndex={-1}
                >
                  {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {confirmPassword && confirmPassword !== password && (
                <p className="text-xs text-red-400 font-medium">كلمتا المرور غير متطابقتين</p>
              )}
            </motion.div>

            {/* Info text */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.5, duration: 0.4 }}
              className="flex items-start gap-2 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20"
            >
              <Info className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
              <p className="text-xs text-emerald-300/80 leading-relaxed">
                سيتم إرسال رسالة تحقق إلى بريدك الإلكتروني
              </p>
            </motion.div>

            {/* Register Button */}
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.55, duration: 0.4 }}
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
                    جارِ إنشاء الحساب...
                  </span>
                ) : (
                  'إنشاء حساب'
                )}
              </Button>
            </motion.div>
          </form>

          {/* Back to Login */}
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.65, duration: 0.4 }}
            className="text-center mt-6 text-white/50 text-sm"
          >
            <button
              type="button"
              onClick={() => setView('login')}
              className="inline-flex items-center gap-1.5 text-emerald-400 hover:text-emerald-300 font-medium transition-colors"
            >
              <ArrowRight className="w-4 h-4" />
              العودة لتسجيل الدخول
            </button>
          </motion.p>
        </div>

        {/* Footer */}
        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.8, duration: 0.4 }}
          className="text-center mt-6 text-white/25 text-xs"
        >
          © {new Date().getFullYear()} أبو زهرة — جميع الحقوق محفوظة
        </motion.p>
      </motion.div>
    </div>
  )
}