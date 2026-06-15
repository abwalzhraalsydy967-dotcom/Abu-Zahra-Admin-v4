'use client'

import React, { useState, useEffect, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Mail, CheckCircle2, ArrowRight, RefreshCw, Loader2, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'
import { addLog } from '@/lib/utils'

const COOLDOWN_SECONDS = 60

export default function VerifyEmailForm() {
  const { pendingEmail, setView } = useAuth()
  const [cooldown, setCooldown] = useState(0)
  const [isResending, setIsResending] = useState(false)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setInterval(() => {
      setCooldown((prev) => {
        if (prev <= 1) {
          clearInterval(timer)
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [cooldown])

  const handleResend = useCallback(async () => {
    if (cooldown > 0 || !pendingEmail) return

    setIsResending(true)
    addLog('info', 'إعادة إرسال رسالة التحقق', `البريد: ${pendingEmail}`)

    try {
      await import('firebase/auth')
      
      // We need to re-authenticate to send verification email
      // Since we signed out after registration, we can't resend from client
      // In a real scenario, this would call a server endpoint
      addLog('warning', 'تم طلب إعادة الإرسال', 'قد تحتاج لتسجيل الدخول أولاً')
      
      setCooldown(COOLDOWN_SECONDS)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : ''
      addLog('error', 'فشل إعادة إرسال رسالة التحقق', message)
    } finally {
      setIsResending(false)
    }
  }, [cooldown, pendingEmail])

  const formatCooldown = (seconds: number): string => {
    const m = Math.floor(seconds / 60)
    const s = seconds % 60
    return `${m}:${s.toString().padStart(2, '0')}`
  }

  const maskedEmail = pendingEmail
    ? pendingEmail.replace(/(.{2})(.*)(@.*)/, (_, a, b, c) => a + '*'.repeat(b.length) + c)
    : ''

  return (
    <div
      dir="rtl"
      className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-b from-emerald-950 via-slate-900 to-emerald-950 relative overflow-hidden"
    >
      {/* Decorative background */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/2 right-1/3 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl" />
        <div className="absolute bottom-1/4 left-1/4 w-80 h-80 bg-emerald-600/8 rounded-full blur-3xl" />
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
            <p className="text-emerald-300/80 text-sm mt-1">التحقق من البريد الإلكتروني</p>
          </motion.div>

          {/* Animated Mail Icon with Check */}
          <motion.div
            initial={{ scale: 0, rotate: -180 }}
            animate={{ scale: 1, rotate: 0 }}
            transition={{ delay: 0.4, duration: 0.6, type: 'spring', stiffness: 150 }}
            className="flex justify-center mb-6"
          >
            <div className="relative">
              <div className="w-20 h-20 rounded-full bg-emerald-500/15 border-2 border-emerald-500/30 flex items-center justify-center">
                <Mail className="w-8 h-8 text-emerald-400" />
              </div>
              <motion.div
                initial={{ scale: 0, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ delay: 0.8, duration: 0.4, type: 'spring' }}
                className="absolute -bottom-1 -left-1 w-7 h-7 rounded-full bg-emerald-500 flex items-center justify-center shadow-lg shadow-emerald-500/30"
              >
                <CheckCircle2 className="w-5 h-5 text-white" />
              </motion.div>
            </div>
          </motion.div>

          {/* Title */}
          <motion.h2
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.6, duration: 0.4 }}
            className="text-xl font-bold text-white text-center mb-2"
          >
            تم إرسال رسالة التحقق!
          </motion.h2>

          {/* Email display */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.7, duration: 0.4 }}
            className="text-center mb-4"
          >
            <p className="text-white/60 text-sm">
              تحقق من بريدك الإلكتروني واضغط على رابط التحقق
            </p>
            {pendingEmail && (
              <div className="mt-3 inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-white/5 border border-white/10">
                <Mail className="w-4 h-4 text-emerald-400" />
                <span className="text-white text-sm font-medium" dir="ltr">
                  {maskedEmail}
                </span>
              </div>
            )}
          </motion.div>

          {/* Resend Button */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.8, duration: 0.4 }}
            className="mt-8"
          >
            <Button
              type="button"
              onClick={handleResend}
              disabled={cooldown > 0 || isResending}
              className={cn(
                'w-full h-11 text-base font-semibold rounded-xl',
                'bg-gradient-to-r from-emerald-600 to-emerald-500',
                'hover:from-emerald-500 hover:to-emerald-400',
                'shadow-lg shadow-emerald-500/25',
                'transition-all duration-200',
                'text-white border-0',
                (cooldown > 0 || isResending) && 'opacity-60 cursor-not-allowed'
              )}
            >
              {isResending ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  جارِ الإرسال...
                </span>
              ) : cooldown > 0 ? (
                <span className="flex items-center gap-2">
                  <RefreshCw className="w-4 h-4" />
                  إعادة إرسال رسالة التحقق ({formatCooldown(cooldown)})
                </span>
              ) : (
                <span className="flex items-center gap-2">
                  <RefreshCw className="w-4 h-4" />
                  إعادة إرسال رسالة التحقق
                </span>
              )}
            </Button>
          </motion.div>

          {/* Back to Login */}
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.9, duration: 0.4 }}
            className="text-center mt-6 text-white/50 text-sm"
          >
            <button
              type="button"
              onClick={() => {
                addLog('info', 'العودة لتسجيل الدخول من شاشة التحقق')
                setView('login')
              }}
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
          transition={{ delay: 1, duration: 0.4 }}
          className="text-center mt-6 text-white/25 text-xs"
        >
          © {new Date().getFullYear()} أبو زهرة — جميع الحقوق محفوظة
        </motion.p>
      </motion.div>
    </div>
  )
}