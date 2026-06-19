'use client'

import React, { useState, useEffect, useRef, ReactNode } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ListChecks,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Clock,
  Loader2,
  ChevronDown,
  ChevronUp,
  Inbox,
  Terminal,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import api, { type Device, type CommandItem } from '@/lib/api'
import { cn, formatTimestamp, timeAgo, addLog } from '@/lib/utils'

interface Props {
  device: Device
}

/* ─── Result parsing helpers ─────────────────────────── */
type ParsedResult =
  | { kind: 'empty' }
  | { kind: 'image'; data: string }
  | { kind: 'text'; data: string }
  | { kind: 'array'; data: unknown[] }
  | { kind: 'object'; data: Record<string, unknown> }
  | { kind: 'primitive'; data: string | number | boolean }

function parseResult(result: unknown): ParsedResult {
  if (result === null || result === undefined || result === '') {
    return { kind: 'empty' }
  }

  let candidate: unknown = result
  if (typeof result === 'string') {
    const trimmed = result.trim()
    if (!trimmed) return { kind: 'empty' }
    // JPEG/PNG base64 marker
    if (trimmed.startsWith('/9j/') || trimmed.startsWith('iVBORw0KGgo')) {
      return { kind: 'image', data: trimmed }
    }
    try {
      candidate = JSON.parse(trimmed)
    } catch {
      return { kind: 'text', data: result }
    }
  }

  if (Array.isArray(candidate)) {
    return { kind: 'array', data: candidate }
  }
  if (candidate && typeof candidate === 'object') {
    return { kind: 'object', data: candidate as Record<string, unknown> }
  }
  if (
    typeof candidate === 'string' ||
    typeof candidate === 'number' ||
    typeof candidate === 'boolean'
  ) {
    return { kind: 'primitive', data: candidate }
  }
  return { kind: 'text', data: String(candidate) }
}

function formatCell(value: unknown): string {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function renderTable(items: Record<string, unknown>[]): ReactNode {
  if (items.length === 0) {
    return <p className="text-slate-500 text-xs italic">قائمة فارغة</p>
  }
  const keys = Array.from(new Set(items.flatMap((obj) => Object.keys(obj))))
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-800/50 bg-slate-900/40">
      <table className="w-full text-xs">
        <thead className="bg-slate-900/80 text-slate-400">
          <tr>
            {keys.map((k) => (
              <th
                key={k}
                className="px-2 py-1.5 text-right font-medium border-b border-slate-800/50 whitespace-nowrap"
              >
                {k}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/40">
          {items.map((row, i) => (
            <tr key={i} className="hover:bg-slate-800/30 transition-colors">
              {keys.map((k) => {
                const v = row[k]
                const text = formatCell(v)
                return (
                  <td
                    key={k}
                    className="px-2 py-1.5 text-slate-300 border-r border-slate-800/40 last:border-r-0 max-w-[220px] truncate"
                    title={text}
                    dir="auto"
                  >
                    {text}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function renderResultContent(result: unknown): ReactNode {
  const parsed = parseResult(result)

  if (parsed.kind === 'empty') {
    return <p className="text-slate-500 text-xs italic">لا توجد نتيجة</p>
  }

  if (parsed.kind === 'image') {
    return (
      <div className="rounded-lg overflow-hidden border border-slate-800/50 bg-black/40 max-w-md">
        <img
          src={`data:image/jpeg;base64,${parsed.data}`}
          alt="نتيجة الأمر"
          className="w-full h-auto"
        />
      </div>
    )
  }

  if (parsed.kind === 'text') {
    return (
      <pre
        className="text-xs text-slate-300 bg-slate-900/60 p-3 rounded-lg overflow-x-auto max-h-60 whitespace-pre-wrap break-words border border-slate-800/50"
        dir="auto"
      >
        {parsed.data}
      </pre>
    )
  }

  if (parsed.kind === 'array') {
    if (parsed.data.length === 0) {
      return <p className="text-slate-500 text-xs italic">قائمة فارغة</p>
    }
    if (
      parsed.data.every(
        (item) => item && typeof item === 'object' && !Array.isArray(item)
      )
    ) {
      return renderTable(parsed.data as Record<string, unknown>[])
    }
    return (
      <ul
        className="text-xs text-slate-300 space-y-0.5 bg-slate-900/60 p-3 rounded-lg max-h-60 overflow-y-auto border border-slate-800/50"
        dir="auto"
      >
        {parsed.data.map((item, i) => (
          <li
            key={i}
            className="py-1 border-b border-slate-800/40 last:border-b-0 break-words"
          >
            {String(item)}
          </li>
        ))}
      </ul>
    )
  }

  if (parsed.kind === 'object') {
    const obj = parsed.data
    // Special: location with lat/lng
    const hasLat = 'lat' in obj || 'latitude' in obj
    const hasLng = 'lng' in obj || 'lon' in obj || 'longitude' in obj
    if (hasLat && hasLng) {
      const lat = (obj.lat ?? obj.latitude) as unknown
      const lng = (obj.lng ?? obj.lon ?? obj.longitude) as unknown
      return (
        <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-xs space-y-1">
          <p className="text-emerald-300 font-medium">📍 الموقع الجغرافي</p>
          <p className="text-slate-200 font-mono" dir="ltr">
            {String(lat)}, {String(lng)}
          </p>
          {'accuracy' in obj && (
            <p className="text-slate-400">الدقة: ~{String(obj.accuracy)} متر</p>
          )}
          {'address' in obj && (
            <p className="text-slate-300">العنوان: {String(obj.address)}</p>
          )}
        </div>
      )
    }
    const entries = Object.entries(obj)
    if (entries.length === 0) {
      return <p className="text-slate-500 text-xs italic">كائن فارغ</p>
    }
    return (
      <div className="rounded-lg border border-slate-800/50 divide-y divide-slate-800/40 max-h-60 overflow-y-auto bg-slate-900/40">
        {entries.map(([k, v]) => (
          <div
            key={k}
            className="flex gap-3 px-3 py-1.5 text-xs hover:bg-slate-800/30 transition-colors"
          >
            <span className="text-slate-400 font-medium min-w-[110px] shrink-0">
              {k}:
            </span>
            <span className="text-slate-200 break-all flex-1" dir="auto">
              {formatCell(v)}
            </span>
          </div>
        ))}
      </div>
    )
  }

  // primitive
  return (
    <pre
      className="text-xs text-slate-300 bg-slate-900/60 p-3 rounded-lg overflow-x-auto max-h-60 whitespace-pre-wrap break-words border border-slate-800/50"
      dir="auto"
    >
      {String(parsed.data)}
    </pre>
  )
}

/* ─── Status badge ───────────────────────────────────── */
function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { cls: string; label: string; icon: ReactNode }> = {
    completed: {
      cls: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25',
      label: 'مكتمل',
      icon: <CheckCircle2 className="w-3 h-3" />,
    },
    failed: {
      cls: 'bg-red-500/15 text-red-400 border-red-500/25',
      label: 'فشل',
      icon: <XCircle className="w-3 h-3" />,
    },
    pending: {
      cls: 'bg-amber-500/15 text-amber-400 border-amber-500/25',
      label: 'قيد الانتظار',
      icon: <Clock className="w-3 h-3" />,
    },
    sent: {
      cls: 'bg-cyan-500/15 text-cyan-400 border-cyan-500/25',
      label: 'تم الإرسال',
      icon: <Loader2 className="w-3 h-3 animate-spin" />,
    },
  }
  const s = map[status] || {
    cls: 'bg-slate-700/50 text-slate-300 border-slate-600/30',
    label: status,
    icon: <Clock className="w-3 h-3" />,
  }
  return (
    <Badge className={cn('text-[10px] gap-1', s.cls)}>
      {s.icon}
      {s.label}
    </Badge>
  )
}

/* ─── Component ──────────────────────────────────────── */
export default function CommandResults({ device }: Props) {
  const [commands, setCommands] = useState<CommandItem[]>([])
  const [loading, setLoading] = useState(true)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    let cancelled = false
    addLog('info', `تحميل نتائج أوامر الجهاز: ${device.name}`)

    async function load() {
      try {
        const res = await api.getCommands(device.id)
        if (cancelled) return
        if (res.ok && res.commands) {
          setCommands(res.commands as CommandItem[])
        } else {
          addLog('error', 'فشل تحميل نتائج الأوامر', res.message)
        }
      } catch (err: unknown) {
        if (cancelled) return
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل النتائج', msg)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    const poll = async () => {
      try {
        const res = await api.getCommands(device.id)
        if (cancelled) return
        if (res.ok && res.commands) {
          setCommands(res.commands as CommandItem[])
        }
      } catch {
        // silent polling
      }
    }

    load()
    pollRef.current = setInterval(poll, 4000)
    return () => {
      cancelled = true
      if (pollRef.current) clearInterval(pollRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [device.id])

  const handleManualRefresh = () => {
    setLoading(true)
    api.getCommands(device.id)
      .then((res) => {
        if (res.ok && res.commands) {
          setCommands(res.commands as CommandItem[])
        } else {
          addLog('error', 'فشل تحميل نتائج الأوامر', res.message)
        }
      })
      .catch((err: unknown) => {
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل النتائج', msg)
      })
      .finally(() => setLoading(false))
  }

  const toggleExpand = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const pendingCount = commands.filter(
    (c) => c.status === 'pending' || c.status === 'sent'
  ).length

  return (
    <div className="space-y-4">
      {/* Top bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <ListChecks className="w-5 h-5 text-emerald-400" />
          <h2 className="text-lg font-semibold text-white">نتائج الأوامر</h2>
          <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs">
            {commands.length}
          </Badge>
          {pendingCount > 0 && (
            <Badge className="bg-amber-500/15 text-amber-400 border-amber-500/25 text-xs">
              {pendingCount} قيد المعالجة
            </Badge>
          )}
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={handleManualRefresh}
          disabled={loading}
          className="text-slate-400 hover:text-white hover:bg-slate-800"
        >
          <RefreshCw className={cn('w-4 h-4', loading && 'animate-spin')} />
          <span className="hidden sm:inline">تحديث</span>
        </Button>
      </div>

      {/* Device hint */}
      <div className="flex items-center gap-3 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
        <div className="p-1.5 rounded-lg bg-emerald-500/15">
          <Terminal className="w-4 h-4 text-emerald-400" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-white text-sm font-medium truncate">
            {device.name}
          </p>
          <p className="text-slate-400 text-xs truncate">
            {device.model} • {device.brand}
          </p>
        </div>
        <p className="text-xs text-slate-500 hidden sm:block">
          آخر تحديث: {timeAgo(new Date().toISOString())}
        </p>
      </div>

      {/* List */}
      {loading && commands.length === 0 ? (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="h-16 w-full rounded-xl bg-slate-800/60 animate-pulse"
            />
          ))}
        </div>
      ) : commands.length === 0 ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-16 text-slate-400"
        >
          <Inbox className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">لا توجد نتائج أوامر بعد</p>
          <p className="text-sm mt-1 text-center px-4">
            أرسل أمراً من تبويب «الأوامر» وستظهر نتيجته هنا تلقائياً
          </p>
        </motion.div>
      ) : (
        <div
          className="max-h-[600px] overflow-y-auto rounded-xl border border-slate-800/50 bg-slate-900/50"
          style={{
            scrollbarWidth: 'thin',
            scrollbarColor: 'rgba(255,255,255,0.15) transparent',
          }}
        >
          <div className="divide-y divide-slate-800/50">
            <AnimatePresence initial={false}>
              {commands.map((cmd) => {
                const isOpen = expanded.has(cmd.id)
                return (
                  <motion.div
                    key={cmd.id}
                    layout
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="hover:bg-slate-800/30 transition-colors"
                  >
                    <button
                      type="button"
                      onClick={() => toggleExpand(cmd.id)}
                      className="w-full flex items-center gap-3 p-3 text-right"
                    >
                      <StatusBadge status={cmd.status} />
                      <div className="min-w-0 flex-1">
                        <p
                          className="text-sm text-slate-200 font-medium truncate font-mono"
                          dir="ltr"
                        >
                          {cmd.command}
                        </p>
                        <p className="text-xs text-slate-500 mt-0.5">
                          {formatTimestamp(cmd.created_at)}
                          {cmd.completed_at
                            ? ` • أكمل: ${timeAgo(cmd.completed_at)}`
                            : ''}
                        </p>
                      </div>
                      {isOpen ? (
                        <ChevronUp className="w-4 h-4 text-slate-400 shrink-0" />
                      ) : (
                        <ChevronDown className="w-4 h-4 text-slate-400 shrink-0" />
                      )}
                    </button>
                    <AnimatePresence initial={false}>
                      {isOpen && (
                        <motion.div
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.2 }}
                          className="overflow-hidden"
                        >
                          <div className="px-4 pb-4 pt-1">
                            {renderResultContent(cmd.result)}
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </motion.div>
                )
              })}
            </AnimatePresence>
          </div>
        </div>
      )}
    </div>
  )
}
