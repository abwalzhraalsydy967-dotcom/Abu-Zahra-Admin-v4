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