import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatTimestamp(ts: string | number): string {
  if (!ts) return ''
  const date = new Date(typeof ts === 'number' ? ts * 1000 : ts)
  return date.toLocaleDateString('ar-SA', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function timeAgo(ts: string | number): string {
  if (!ts) return 'غير متاح'
  const now = Date.now()
  const then = new Date(typeof ts === 'number' ? ts * 1000 : ts).getTime()
  const diff = Math.floor((now - then) / 1000)

  if (diff < 60) return 'الآن'
  if (diff < 3600) return `منذ ${Math.floor(diff / 60)} دقيقة`
  if (diff < 86400) return `منذ ${Math.floor(diff / 3600)} ساعة`
  return `منذ ${Math.floor(diff / 86400)} يوم`
}

export interface TimeUntilResult {
  text: string
  urgent: boolean
  expired: boolean
}

export function timeUntil(ts: string | number): TimeUntilResult {
  if (!ts) return { text: 'غير متاح', urgent: false, expired: false }
  const now = Date.now()
  const then = new Date(typeof ts === 'number' ? ts * 1000 : ts).getTime()
  const diff = Math.floor((then - now) / 1000)
  if (diff <= 0) return { text: 'منتهي', urgent: true, expired: true }
  if (diff < 60) return { text: `${diff} ث`, urgent: true, expired: false }
  if (diff < 3600) {
    const m = Math.floor(diff / 60)
    return { text: `${m} د`, urgent: m < 5, expired: false }
  }
  const h = Math.floor(diff / 3600)
  return { text: `${h} س`, urgent: false, expired: false }
}

export interface LogEntry {
  id: string
  timestamp: Date
  type: 'info' | 'success' | 'error' | 'warning'
  message: string
  detail?: string
}

let logListeners: ((entry: LogEntry) => void)[] = []

export function addLog(type: LogEntry['type'], message: string, detail?: string) {
  const entry: LogEntry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    timestamp: new Date(),
    type,
    message,
    detail,
  }
  logListeners.forEach(fn => fn(entry))
  return entry
}

export function onLog(fn: (entry: LogEntry) => void) {
  logListeners.push(fn)
  return () => {
    logListeners = logListeners.filter(l => l !== fn)
  }
}