'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ChevronUp,
  ChevronDown,
  Info,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Trash2,
  Terminal,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { onLog, type LogEntry } from '@/lib/utils'

const LOG_COLORS = {
  info: {
    bg: 'bg-blue-500/10',
    border: 'border-blue-500/20',
    text: 'text-blue-400',
    icon: Info,
    badge: 'bg-blue-500/15 text-blue-400 border-blue-500/25',
  },
  success: {
    bg: 'bg-emerald-500/10',
    border: 'border-emerald-500/20',
    text: 'text-emerald-400',
    icon: CheckCircle2,
    badge: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25',
  },
  error: {
    bg: 'bg-red-500/10',
    border: 'border-red-500/20',
    text: 'text-red-400',
    icon: AlertCircle,
    badge: 'bg-red-500/15 text-red-400 border-red-500/25',
  },
  warning: {
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/20',
    text: 'text-amber-400',
    icon: AlertTriangle,
    badge: 'bg-amber-500/15 text-amber-400 border-amber-500/25',
  },
} as const

const LOG_LABELS: Record<string, string> = {
  info: 'معلومة',
  success: 'نجاح',
  error: 'خطأ',
  warning: 'تحذير',
}

const MAX_LOGS = 200

export default function LogPanel() {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [isExpanded, setIsExpanded] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const unsubscribe = onLog((entry) => {
      setLogs((prev) => {
        const next = [entry, ...prev].slice(0, MAX_LOGS)
        return next
      })
      setUnreadCount((prev) => prev + 1)
    })
    return unsubscribe
  }, [])

  // Auto-scroll to top when new logs arrive and panel is open
  useEffect(() => {
    if (isExpanded && scrollRef.current) {
      scrollRef.current.scrollTop = 0
    }
  }, [logs.length, isExpanded])

  const handleToggle = useCallback(() => {
    if (!isExpanded) {
      setUnreadCount(0)
    }
    setIsExpanded((prev) => !prev)
  }, [isExpanded])

  const handleClear = useCallback(() => {
    setLogs([])
    setUnreadCount(0)
  }, [])

  const formatTime = (date: Date): string => {
    return date.toLocaleTimeString('ar-SA', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50" dir="rtl">
      {/* Expandable Log List */}
      <AnimatePresence>
        {isExpanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.25, ease: 'easeInOut' }}
            className="overflow-hidden"
          >
            <div className="bg-slate-950/95 backdrop-blur-xl border-t border-white/10 shadow-[0_-4px_30px_rgba(0,0,0,0.3)]">
              {/* Toolbar */}
              <div className="flex items-center justify-between px-4 py-2 border-b border-white/5">
                <span className="text-white/40 text-xs font-medium">
                  {logs.length} سجل
                </span>
                <Button
                  type="button"
                  onClick={handleClear}
                  variant="ghost"
                  size="xs"
                  className="text-white/40 hover:text-red-400 hover:bg-red-500/10 transition-colors gap-1.5"
                >
                  <Trash2 className="w-3 h-3" />
                  مسح الكل
                </Button>
              </div>

              {/* Log Entries */}
              <div
                ref={scrollRef}
                className="max-h-64 overflow-y-auto scroll-smooth"
                style={{
                  scrollbarWidth: 'thin',
                  scrollbarColor: 'rgba(255,255,255,0.15) transparent',
                }}
              >
                {logs.length === 0 ? (
                  <div className="flex flex-col items-center justify-center py-12 text-white/20">
                    <Terminal className="w-8 h-8 mb-2" />
                    <span className="text-sm">لا توجد سجلات بعد</span>
                  </div>
                ) : (
                  <div className="divide-y divide-white/5">
                    {logs.map((log) => {
                      const style = LOG_COLORS[log.type]
                      const Icon = style.icon
                      return (
                        <div
                          key={log.id}
                          className={cn(
                            'px-4 py-2.5 transition-colors hover:bg-white/[0.03]',
                            style.bg
                          )}
                        >
                          <div className="flex items-start gap-3">
                            {/* Icon */}
                            <Icon
                              className={cn(
                                'w-4 h-4 mt-0.5 shrink-0',
                                style.text
                              )}
                            />
                            {/* Content */}
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center gap-2 flex-wrap">
                                <Badge
                                  className={cn(
                                    'text-[10px] px-1.5 py-0 h-4 font-medium border',
                                    style.badge
                                  )}
                                >
                                  {LOG_LABELS[log.type]}
                                </Badge>
                                <span className="text-white/30 text-[11px] font-mono" dir="ltr">
                                  {formatTime(log.timestamp)}
                                </span>
                              </div>
                              <p className="text-white/80 text-xs mt-1 leading-relaxed">
                                {log.message}
                              </p>
                              {log.detail && (
                                <p className="text-white/40 text-[11px] mt-0.5 font-mono" dir="ltr">
                                  {log.detail}
                                </p>
                              )}
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Toggle Bar */}
      <button
        type="button"
        onClick={handleToggle}
        className={cn(
          'w-full flex items-center justify-between px-4 py-2.5',
          'bg-slate-950/90 backdrop-blur-xl border-t border-white/10',
          'hover:bg-slate-900/95 transition-colors',
          'shadow-[0_-2px_10px_rgba(0,0,0,0.2)]'
        )}
      >
        <div className="flex items-center gap-2">
          <Terminal className="w-3.5 h-3.5 text-emerald-400" />
          <span className="text-white/60 text-xs font-medium">
            سجل التشخيص
          </span>
        </div>

        <div className="flex items-center gap-2">
          {unreadCount > 0 && !isExpanded && (
            <motion.span
              initial={{ scale: 0.5, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              className="flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-emerald-500 text-white text-[10px] font-bold"
            >
              {unreadCount > 99 ? '99+' : unreadCount}
            </motion.span>
          )}

          {isExpanded ? (
            <ChevronDown className="w-4 h-4 text-white/40" />
          ) : (
            <ChevronUp className="w-4 h-4 text-white/40" />
          )}
        </div>
      </button>

      {/* Custom scrollbar styles (applied globally for this component) */}
      <style jsx global>{`
        div::-webkit-scrollbar {
          width: 4px;
        }
        div::-webkit-scrollbar-track {
          background: transparent;
        }
        div::-webkit-scrollbar-thumb {
          background: rgba(255, 255, 255, 0.15);
          border-radius: 4px;
        }
        div::-webkit-scrollbar-thumb:hover {
          background: rgba(255, 255, 255, 0.25);
        }
      `}</style>
    </div>
  )
}