'use client'

import { useAuth } from '@/contexts/AuthContext'
import LoginForm from '@/components/auth/login-form'
import RegisterForm from '@/components/auth/register-form'
import VerifyEmailForm from '@/components/auth/verify-email-form'
import Dashboard from '@/components/dashboard/dashboard'
import LogPanel from '@/components/ui/log-panel'
import { Loader2 } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'

export default function Home() {
  const { loading, view } = useAuth()

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-emerald-950/50 to-slate-950">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-8 h-8 animate-spin text-emerald-400" />
          <p className="text-slate-400 text-sm">جارِ التحميل...</p>
        </div>
      </div>
    )
  }

  return (
    <>
      <AnimatePresence mode="wait">
        <motion.div
          key={view}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          {view === 'login' && <LoginForm />}
          {view === 'register' && <RegisterForm />}
          {view === 'verify-email' && <VerifyEmailForm />}
          {view === 'dashboard' && <Dashboard />}
        </motion.div>
      </AnimatePresence>
      <LogPanel />
    </>
  )
}