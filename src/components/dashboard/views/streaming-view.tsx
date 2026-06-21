'use client'

import React, { useState, useEffect, useRef, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Radio,
  Monitor,
  Camera,
  Video,
  Mic,
  RefreshCw,
  SwitchCamera,
  Settings2,
  Camera as CameraIcon,
  AlertCircle,
  Loader2,
  Wifi,
  Smartphone,
  Gauge,
  Check,
  ChevronLeft,
  StopCircle,
  Signal,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import api, { type Device } from '@/lib/api'
import { cn, formatTimestamp, addLog } from '@/lib/utils'

interface Props {
  device: Device
  /** All available devices (so user can switch device from within streaming view) */
  devices: Device[]
  onSelectDevice: (device: Device) => void
}

type StreamType = 'screen' | 'front_camera' | 'back_camera' | 'audio'
type StreamStatus = 'idle' | 'connecting' | 'active' | 'stopping' | 'error'
type Quality = '480p' | '720p' | '1080p'

/* ─── Stream type cards config ─── */
const STREAM_TYPE_CARDS: {
  value: StreamType
  label: string
  desc: string
  icon: typeof Monitor
  startCmd: string
  stopCmd: string
  /** Whether the server-side JPEG polling loop is useful (audio uses AAC chunks over ws). */
  skipJpegLoop?: boolean
  accent: string
  glow: string
}[] = [
  {
    value: 'screen',
    label: 'بث الشاشة',
    desc: 'عرض شاشة الجهاز مباشرةً',
    icon: Monitor,
    startCmd: 'start_screen_stream',
    stopCmd: 'stop_screen_stream',
    accent: 'from-emerald-500/20 to-emerald-700/10 border-emerald-500/30',
    glow: 'group-hover:shadow-emerald-500/20',
  },
  {
    value: 'front_camera',
    label: 'الكاميرا الأمامية',
    desc: 'بث مباشر من الكاميرا الأمامية',
    icon: Video,
    startCmd: 'start_camera_stream',
    stopCmd: 'stop_camera_stream',
    accent: 'from-cyan-500/20 to-cyan-700/10 border-cyan-500/30',
    glow: 'group-hover:shadow-cyan-500/20',
  },
  {
    value: 'back_camera',
    label: 'الكاميرا الخلفية',
    desc: 'بث مباشر من الكاميرا الخلفية',
    icon: Camera,
    startCmd: 'start_camera_stream',
    stopCmd: 'stop_camera_stream',
    accent: 'from-purple-500/20 to-purple-700/10 border-purple-500/30',
    glow: 'group-hover:shadow-purple-500/20',
  },
  {
    value: 'audio',
    label: 'بث الصوت',
    desc: 'استقبال البث الصوتي من الميكروفون',
    icon: Mic,
    startCmd: 'start_audio_stream',
    stopCmd: 'stop_audio_stream',
    skipJpegLoop: true,
    accent: 'from-rose-500/20 to-rose-700/10 border-rose-500/30',
    glow: 'group-hover:shadow-rose-500/20',
  },
]

const QUALITY_OPTIONS: { value: Quality; label: string; hint: string }[] = [
  { value: '480p', label: '480p', hint: 'منخفض — أسرع' },
  { value: '720p', label: '720p', hint: 'متوسط — متوازن' },
  { value: '1080p', label: '1080p', hint: 'عالي — أوضح' },
]

const isAudioType = (t: StreamType) => t === 'audio'

/* ─── Connecting overlay ─── */
function ConnectingOverlay({ streamType, quality, elapsed }: {
  streamType: StreamType
  quality: Quality
  elapsed: number
}) {
  const card = STREAM_TYPE_CARDS.find((t) => t.value === streamType)!
  const label = streamType === 'screen'
    ? 'الشاشة'
    : streamType === 'front_camera'
      ? 'الكاميرا الأمامية'
      : streamType === 'back_camera'
        ? 'الكاميرا الخلفية'
        : 'الصوت'

  // Progress bar fills over 12 seconds (max time we wait before giving up)
  const progress = Math.min((elapsed / 12) * 100, 100)

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="absolute inset-0 z-20 flex items-center justify-center bg-slate-950/95 backdrop-blur-md"
    >
      <div className="flex flex-col items-center text-center px-6">
        {/* Pulsing icon */}
        <div className="relative mb-6">
          <span className="absolute inset-0 rounded-full bg-emerald-500/20 animate-ping" />
          <span className="absolute inset-0 rounded-full bg-emerald-500/10 animate-ping" style={{ animationDelay: '0.5s' }} />
          <div className="relative p-6 rounded-full bg-gradient-to-br from-emerald-500/20 to-emerald-700/10 border border-emerald-500/30">
            <card.icon className="w-10 h-10 text-emerald-300" />
          </div>
        </div>

        <h3 className="text-lg font-bold text-white mb-1">
          جارٍ الاقتران بـ{label}
        </h3>
        <p className="text-sm text-slate-400 mb-1">
          جودة: <span className="text-emerald-300 font-mono">{quality}</span> • الجهاز: <span className="text-slate-300">جارٍ التهيئة</span>
        </p>
        <p className="text-xs text-slate-500 mb-6">
          يتم إرسال أمر البث وانتظار أول إطار من الجهاز...
        </p>

        {/* Progress bar */}
        <div className="w-64 max-w-full">
          <div className="h-1.5 rounded-full bg-slate-800 overflow-hidden">
            <motion.div
              className="h-full bg-gradient-to-r from-emerald-500 to-emerald-400"
              initial={{ width: 0 }}
              animate={{ width: `${progress}%` }}
              transition={{ duration: 0.3, ease: 'linear' }}
            />
          </div>
          <div className="flex items-center justify-between mt-2 text-[10px] text-slate-500">
            <span className="flex items-center gap-1">
              <Loader2 className="w-2.5 h-2.5 animate-spin" />
              جارٍ الاتصال
            </span>
            <span className="font-mono">{elapsed.toFixed(1)}s</span>
          </div>
        </div>
      </div>
    </motion.div>
  )
}

/* ─── Main streaming view ─── */
export default function StreamingView({ device, devices, onSelectDevice }: Props) {
  const [streamType, setStreamType] = useState<StreamType | null>(null)
  const [quality, setQuality] = useState<Quality>('720p')
  const [status, setStatus] = useState<StreamStatus>('idle')
  const [frameUrl, setFrameUrl] = useState<string | null>(null)
  const [frameTs, setFrameTs] = useState<number | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [connectingElapsed, setConnectingElapsed] = useState(0)
  const [showQualityMenu, setShowQualityMenu] = useState(false)
  const [showDeviceMenu, setShowDeviceMenu] = useState(false)
  const [screenshotFlash, setScreenshotFlash] = useState(false)
  const [screenshotSaved, setScreenshotSaved] = useState(false)

  // FPS + latency tracking
  const [fps, setFps] = useState(0)
  const [latencyMs, setLatencyMs] = useState<number | null>(null)
  const [resolution, setResolution] = useState<string>('')

  const framePollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const connectingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  // lastReset starts at 0; lazily initialised to Date.now() on first frame poll
  const fpsCounterRef = useRef<{ count: number; lastReset: number }>({ count: 0, lastReset: 0 })

  const statusRef = useRef<StreamStatus>(status)
  const streamTypeRef = useRef<StreamType | null>(streamType)
  const deviceIdRef = useRef<string>(device.id)
  const frameUrlRef = useRef<string | null>(null)
  const qualityRef = useRef<Quality>(quality)

  useEffect(() => { statusRef.current = status }, [status])
  useEffect(() => { streamTypeRef.current = streamType }, [streamType])
  useEffect(() => { qualityRef.current = quality }, [quality])
  useEffect(() => { frameUrlRef.current = frameUrl }, [frameUrl])
  useEffect(() => { deviceIdRef.current = device.id }, [device.id])

  /* ─── Poll a single frame ─── */
  const pollFrame = useCallback(async () => {
    const currentType = streamTypeRef.current
    if (!currentType) return
    const fetchType = isAudioType(currentType) ? 'audio' : 'video'
    const t0 = Date.now()
    try {
      const frame = await api.streamFrame(deviceIdRef.current, fetchType)
      const elapsedMs = Date.now() - t0
      if (frame.ok && frame.data) {
        if (!isAudioType(currentType)) {
          setFrameUrl(`data:image/jpeg;base64,${frame.data}`)
          // Try to decode resolution from the JPEG (first few bytes contain SOF0 marker)
          try {
            const res = decodeJpegResolution(frame.data)
            if (res) setResolution(`${res.w}×${res.h}`)
          } catch {
            // ignore
          }
        }
        if (frame.timestamp) {
          setFrameTs(frame.timestamp)
          setLatencyMs(Math.max(0, Date.now() - (frame.timestamp * 1000)))
        } else {
          setLatencyMs(elapsedMs)
        }
        // FPS counter
        const now = Date.now()
        if (fpsCounterRef.current.lastReset === 0) {
          fpsCounterRef.current.lastReset = now
        }
        fpsCounterRef.current.count += 1
        const dt = (now - fpsCounterRef.current.lastReset) / 1000
        if (dt >= 1) {
          setFps(Math.round(fpsCounterRef.current.count / dt))
          fpsCounterRef.current.count = 0
          fpsCounterRef.current.lastReset = now
        }

        if (statusRef.current === 'connecting') {
          setStatus('active')
          addLog('success', isAudioType(currentType)
            ? 'تم استقبال أول إطارات صوتية من البث'
            : 'تم استقبال أول إطار من البث')
          if (connectingTimerRef.current) {
            clearInterval(connectingTimerRef.current)
            connectingTimerRef.current = null
          }
        }
      }
    } catch {
      // silent — server returns 404 until first frame arrives
    }
  }, [])

  /* ─── Start a stream with the selected type ─── */
  const handleStartWith = useCallback(async (type: StreamType, q: Quality) => {
    if (statusRef.current === 'active' || statusRef.current === 'connecting') return
    setStreamType(type)
    streamTypeRef.current = type
    setStatus('connecting')
    setErrorMsg(null)
    setFrameUrl(null)
    setFrameTs(null)
    setFps(0)
    setLatencyMs(null)
    setResolution('')
    setConnectingElapsed(0)
    fpsCounterRef.current = { count: 0, lastReset: 0 }

    const label = type === 'screen' ? 'الشاشة'
      : type === 'front_camera' ? 'الكاميرا الأمامية'
        : type === 'back_camera' ? 'الكاميرا الخلفية'
          : 'الصوت'
    addLog('info', `جارٍ الاقتران بـ${label}`, `الجهاز: ${device.name} • الجودة: ${q}`)

    // Start elapsed timer for connecting overlay
    connectingTimerRef.current = setInterval(() => {
      setConnectingElapsed((prev) => {
        const next = prev + 0.1
        // Timeout after 12 seconds — show error
        if (next >= 12 && statusRef.current === 'connecting') {
          setErrorMsg('انتهى وقت الانتظار لأول إطار. تأكد من اتصال الجهاز وصلاحيات البث.')
          setStatus('error')
          if (connectingTimerRef.current) {
            clearInterval(connectingTimerRef.current)
            connectingTimerRef.current = null
          }
        }
        return next
      })
    }, 100)

    try {
      const typeConfig = STREAM_TYPE_CARDS.find((t) => t.value === type)!
      const startCmd = typeConfig.startCmd
      const cmdRes = await api.sendCommand(device.id, startCmd, {
        camera: type === 'back_camera' ? 'back' : 'front',
        quality: q,
      })
      if (!cmdRes.ok) {
        addLog('warning', 'تعذّر إرسال أمر بدء البث للجهاز', cmdRes.message)
      } else {
        addLog('success', `تم إرسال أمر بدء البث: ${startCmd}`)
      }

      // JPEG-poll loop is only useful for visual streams
      if (!typeConfig.skipJpegLoop) {
        const jpegRes = await api.jpegStreamStart(device.id, type, 2)
        if (!jpegRes.ok) {
          addLog('warning', 'تحذير من بدء JPEG stream', jpegRes.message)
        }
      }

      pollFrame()
      // For video streams poll every 300ms; for audio every 3s (just to confirm alive)
      framePollRef.current = setInterval(pollFrame, isAudioType(type) ? 3000 : 300)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في بدء البث', msg)
      setErrorMsg(msg)
      setStatus('error')
      if (connectingTimerRef.current) {
        clearInterval(connectingTimerRef.current)
        connectingTimerRef.current = null
      }
      if (framePollRef.current) {
        clearInterval(framePollRef.current)
        framePollRef.current = null
      }
    }
  }, [device.id, device.name, pollFrame])

  /* ─── Stop the current stream ─── */
  const handleStop = useCallback(async () => {
    if (status === 'idle') return
    // Use the ref so we always stop the device we were streaming from,
    // even if the `device` prop has already changed (e.g. user switched device).
    const targetDeviceId = deviceIdRef.current
    setStatus('stopping')
    addLog('info', 'إيقاف البث', `الجهاز: ${device.name}`)

    try {
      await api.jpegStreamStop(targetDeviceId)
      const currentType = streamTypeRef.current
      if (currentType) {
        const stopCmd = STREAM_TYPE_CARDS.find((t) => t.value === currentType)!.stopCmd
        await api.sendCommand(targetDeviceId, stopCmd)
      }
      addLog('success', 'تم إيقاف البث بنجاح')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('warning', 'تحذير أثناء إيقاف البث', msg)
    }

    if (framePollRef.current) {
      clearInterval(framePollRef.current)
      framePollRef.current = null
    }
    if (connectingTimerRef.current) {
      clearInterval(connectingTimerRef.current)
      connectingTimerRef.current = null
    }
    setStatus('idle')
    setStreamType(null)
    setFrameUrl(null)
    setFrameTs(null)
    setFps(0)
    setLatencyMs(null)
    setResolution('')
    setConnectingElapsed(0)
  }, [status, device.name])

  /* ─── Switch camera (front ↔ back) while streaming ─── */
  const handleSwitchCamera = useCallback(async () => {
    if (status !== 'active' || !streamType) return
    const newType: StreamType = streamType === 'front_camera' ? 'back_camera' : 'front_camera'
    addLog('info', `تبديل الكاميرا: ${streamType === 'front_camera' ? 'الأمامية' : 'الخلفية'} → ${newType === 'front_camera' ? 'الأمامية' : 'الخلفية'}`)

    // Send switch_camera command (device-side)
    const switchRes = await api.sendCommand(device.id, 'switch_camera')
    if (!switchRes.ok) {
      addLog('warning', 'تعذّر إرسال أمر تبديل الكاميرا', switchRes.message)
    }

    // Stop current JPEG loop then start new one with the new type
    try {
      await api.jpegStreamStop(device.id)
      await api.jpegStreamStart(device.id, newType, 2)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('warning', 'خطأ أثناء تبديل الكاميرا', msg)
    }

    setStreamType(newType)
    streamTypeRef.current = newType
    setFrameUrl(null)
    // Reset FPS counter
    fpsCounterRef.current = { count: 0, lastReset: 0 }
  }, [status, streamType, device.id])

  /* ─── Change quality live ─── */
  const handleChangeQuality = useCallback(async (newQ: Quality) => {
    if (status !== 'active' && status !== 'connecting') {
      setQuality(newQ)
      qualityRef.current = newQ
      setShowQualityMenu(false)
      return
    }
    addLog('info', `تغيير جودة البث: ${quality} → ${newQ}`)
    setQuality(newQ)
    qualityRef.current = newQ
    setShowQualityMenu(false)
    try {
      const res = await api.sendCommand(device.id, 'set_stream_quality', { quality: newQ })
      if (res.ok) {
        addLog('success', `تم تغيير الجودة إلى ${newQ}`)
      } else {
        addLog('warning', 'تعذّر تغيير الجودة على الجهاز', res.message)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في تغيير الجودة', msg)
    }
  }, [status, quality, device.id])

  /* ─── Screenshot capture ─── */
  const handleScreenshot = useCallback(() => {
    const url = frameUrlRef.current
    if (!url) return
    setScreenshotFlash(true)
    setTimeout(() => setScreenshotFlash(false), 250)

    try {
      const a = document.createElement('a')
      a.href = url
      const ts = new Date().toISOString().replace(/[:.]/g, '-')
      a.download = `screenshot-${device.id}-${ts}.jpg`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      addLog('success', 'تم التقاط لقطة شاشة للإطار الحالي')
      setScreenshotSaved(true)
      setTimeout(() => setScreenshotSaved(false), 2000)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في التقاط لقطة الشاشة', msg)
    }
  }, [device.id])

  /* ─── Cleanup on unmount / device change ─── */
  useEffect(() => {
    return () => {
      if (framePollRef.current) {
        clearInterval(framePollRef.current)
        framePollRef.current = null
      }
      if (connectingTimerRef.current) {
        clearInterval(connectingTimerRef.current)
        connectingTimerRef.current = null
      }
      api.jpegStreamStop(deviceIdRef.current).catch(() => {})
    }
  }, [])

  /* ─── If user changes device while a stream is active, stop the old one ─── */
  useEffect(() => {
    if (deviceIdRef.current !== device.id) {
      handleStop()
    }
    deviceIdRef.current = device.id
  }, [device.id, handleStop])

  const isActive = status === 'active'
  const isConnecting = status === 'connecting'
  const isBusy = isConnecting || status === 'stopping'
  const showViewer = streamType !== null && (isConnecting || isActive || status === 'stopping' || status === 'error')

  /* ─── Selection screen ─── */
  const renderSelectionScreen = () => (
    <div className="space-y-6">
      {/* Device selector card */}
      <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-4">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0 flex-1">
            <div className="p-2 rounded-lg bg-emerald-500/15 shrink-0">
              <Smartphone className="w-5 h-5 text-emerald-400" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm text-white font-medium truncate">
                {device.name}
              </p>
              <p className="text-xs text-slate-400 truncate">
                {device.model} • {device.brand}
              </p>
            </div>
            <Badge className={cn(
              'text-[10px] shrink-0',
              device.is_online
                ? 'bg-green-500/15 text-green-400 border-green-500/25'
                : 'bg-red-500/15 text-red-400 border-red-500/25'
            )}>
              {device.is_online ? 'متصل' : 'غير متصل'}
            </Badge>
          </div>
          {devices.length > 1 && (
            <div className="relative shrink-0">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowDeviceMenu((v) => !v)}
                className="border-slate-700 text-slate-300 hover:bg-slate-800 bg-transparent"
              >
                <SwitchCamera className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">تبديل الجهاز</span>
              </Button>
              {showDeviceMenu && (
                <div className="absolute top-full mt-1 right-0 z-30 w-64 max-h-72 overflow-y-auto rounded-lg border border-slate-700 bg-slate-900 shadow-xl" style={{ scrollbarWidth: 'thin' }}>
                  {devices.map((d) => (
                    <button
                      key={d.id}
                      type="button"
                      onClick={() => {
                        onSelectDevice(d)
                        setShowDeviceMenu(false)
                      }}
                      className={cn(
                        'w-full text-right px-3 py-2 hover:bg-slate-800 transition-colors flex items-center gap-2 border-b border-slate-800/50 last:border-b-0',
                        d.id === device.id && 'bg-emerald-500/10'
                      )}
                    >
                      <Smartphone className={cn('w-3.5 h-3.5 shrink-0', d.is_online ? 'text-green-400' : 'text-slate-500')} />
                      <span className="text-xs text-slate-200 truncate flex-1">{d.name}</span>
                      {d.id === device.id && <Check className="w-3 h-3 text-emerald-400" />}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
        {!device.is_online && (
          <p className="mt-3 text-xs text-amber-400 flex items-center gap-1.5">
            <AlertCircle className="w-3 h-3" />
            الجهاز غير متصل حالياً — قد يفشل البث.
          </p>
        )}
      </div>

      {/* Quality selector */}
      <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5">
        <div className="flex items-center gap-2 mb-3">
          <Gauge className="w-4 h-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-white">جودة البث</h3>
        </div>
        <div className="grid grid-cols-3 gap-2">
          {QUALITY_OPTIONS.map((opt) => {
            const isSelected = quality === opt.value
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => setQuality(opt.value)}
                className={cn(
                  'flex flex-col items-center gap-1 p-3 rounded-lg border transition-all',
                  isSelected
                    ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-300'
                    : 'bg-slate-900/40 border-slate-800/60 text-slate-400 hover:bg-slate-800/40 hover:text-slate-200'
                )}
              >
                <span className="text-sm font-bold font-mono">{opt.label}</span>
                <span className="text-[10px] text-slate-500">{opt.hint}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Stream type cards */}
      <div>
        <div className="flex items-center gap-2 mb-3">
          <Radio className="w-4 h-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-white">اختر نوع البث</h3>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {STREAM_TYPE_CARDS.map((card, idx) => (
            <motion.button
              key={card.value}
              type="button"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, delay: idx * 0.05 }}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              onClick={() => handleStartWith(card.value, quality)}
              disabled={isBusy}
              className={cn(
                'group relative overflow-hidden rounded-xl border bg-gradient-to-br p-5 text-right transition-all shadow-lg disabled:opacity-50 disabled:cursor-not-allowed',
                card.accent,
                card.glow
              )}
            >
              <div className="flex items-start gap-4">
                <div className="p-3 rounded-xl bg-white/5 border border-white/10 shrink-0">
                  <card.icon className="w-6 h-6 text-white" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-base font-bold text-white mb-1">{card.label}</p>
                  <p className="text-xs text-slate-300/80">{card.desc}</p>
                </div>
                <ChevronLeft className="w-5 h-5 text-white/40 group-hover:text-white/80 transition-colors shrink-0" />
              </div>
            </motion.button>
          ))}
        </div>
      </div>

      {/* Help text */}
      <div className="text-xs text-slate-500 leading-relaxed p-4 rounded-lg bg-slate-900/40 border border-slate-800/40">
        <p className="font-medium text-slate-400 mb-2">ملاحظات قبل البدء:</p>
        <ul className="list-disc list-inside space-y-1 text-slate-500">
          <li>اختر نوع البث المناسب من الأعلى — سيتم إرسال أمر البث للجهاز فوراً.</li>
          <li>يظهر «جارٍ الاقتران...» أثناء انتظار أول إطار من الجهاز.</li>
          <li>تأكد من صلاحيات الكاميرا/الشاشة على الجهاز قبل البدء.</li>
          <li>يمكنك تغيير الجودة وتبديل الكاميرا وأخذ لقطات شاشة أثناء البث.</li>
          <li>البث الصوتي يعرض مؤشر تسجيل — ملف التسجيل الكامل متاح في تبويب «الملفات».</li>
        </ul>
      </div>
    </div>
  )

  /* ─── Full-screen viewer ─── */
  const renderViewer = () => {
    if (!streamType) return null
    const card = STREAM_TYPE_CARDS.find((t) => t.value === streamType)!

    return (
      <div className="space-y-4">
        {/* Top bar: stream info + back button */}
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="sm"
              onClick={handleStop}
              disabled={isBusy}
              className="text-slate-400 hover:text-white hover:bg-slate-800"
            >
              <ChevronLeft className="w-4 h-4" />
              <span>رجوع</span>
            </Button>
            <div className="flex items-center gap-2">
              <div className={cn('p-1.5 rounded-lg', 'bg-emerald-500/15')}>
                <card.icon className="w-4 h-4 text-emerald-400" />
              </div>
              <div>
                <p className="text-sm font-semibold text-white">{card.label}</p>
                <p className="text-xs text-slate-400">{device.name}</p>
              </div>
            </div>
          </div>

          {/* Quality indicator */}
          <div className="relative">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowQualityMenu((v) => !v)}
              disabled={isBusy}
              className="border-slate-700 text-slate-300 hover:bg-slate-800 bg-transparent gap-1.5"
            >
              <Settings2 className="w-3.5 h-3.5" />
              <span className="font-mono">{quality}</span>
            </Button>
            {showQualityMenu && (
              <div className="absolute top-full mt-1 left-0 z-30 w-44 rounded-lg border border-slate-700 bg-slate-900 shadow-xl">
                {QUALITY_OPTIONS.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => handleChangeQuality(opt.value)}
                    className={cn(
                      'w-full text-right px-3 py-2 hover:bg-slate-800 transition-colors flex items-center justify-between border-b border-slate-800/50 last:border-b-0',
                      quality === opt.value && 'bg-emerald-500/10'
                    )}
                  >
                    <div>
                      <p className="text-xs font-mono text-slate-200">{opt.label}</p>
                      <p className="text-[10px] text-slate-500">{opt.hint}</p>
                    </div>
                    {quality === opt.value && <Check className="w-3 h-3 text-emerald-400" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Stream viewport */}
        <div className="relative rounded-xl border border-slate-800/60 bg-black overflow-hidden">
          <div className="relative aspect-video bg-black flex items-center justify-center">
            <AnimatePresence mode="wait">
              {isAudioType(streamType) ? (
                /* Audio recording indicator */
                <motion.div
                  key="audio-indicator"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex flex-col items-center justify-center text-center p-8"
                >
                  <div className="relative mb-4">
                    <span className="absolute inset-0 rounded-full bg-rose-500/20 animate-ping" />
                    <div className="relative p-5 rounded-full bg-rose-500/15 border border-rose-500/30">
                      <Mic className="w-10 h-10 text-rose-400" />
                    </div>
                  </div>
                  <p className="text-base font-bold text-rose-300">
                    البث الصوتي جارٍ...
                  </p>
                  <p className="text-xs mt-2 text-slate-400 max-w-xs">
                    يتم استقبال إطارات الصوت من الجهاز. سيكون ملف التسجيل
                    الكامل متاحاً في تبويب «الملفات» بعد انتهاء التسجيل.
                  </p>
                </motion.div>
              ) : frameUrl ? (
                <motion.img
                  key={frameUrl.slice(0, 50)}
                  src={frameUrl}
                  alt="البث المباشر"
                  initial={{ opacity: 0.3 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.1 }}
                  className="w-full h-full object-contain"
                />
              ) : (
                <motion.div
                  key="empty"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex flex-col items-center justify-center text-slate-500 p-8"
                >
                  {status === 'error' ? (
                    <>
                      <AlertCircle className="w-12 h-12 mb-3 text-red-400/70" />
                      <p className="text-sm text-red-300">تعذّر تشغيل البث</p>
                      {errorMsg && (
                        <p className="text-xs mt-1 text-red-400/60 break-all text-center max-w-xs">
                          {errorMsg}
                        </p>
                      )}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={handleStop}
                        className="mt-4 border-slate-700 text-slate-300 hover:bg-slate-800 bg-transparent"
                      >
                        العودة لاختيار البث
                      </Button>
                    </>
                  ) : (
                    <>
                      <Loader2 className="w-10 h-10 mb-3 text-emerald-400/60 animate-spin" />
                      <p className="text-sm">جارٍ تشغيل البث...</p>
                    </>
                  )}
                </motion.div>
              )}
            </AnimatePresence>

            {/* Connecting overlay */}
            <AnimatePresence>
              {isConnecting && (
                <ConnectingOverlay
                  streamType={streamType}
                  quality={quality}
                  elapsed={connectingElapsed}
                />
              )}
            </AnimatePresence>

            {/* Screenshot flash effect */}
            <AnimatePresence>
              {screenshotFlash && (
                <motion.div
                  initial={{ opacity: 0.9 }}
                  animate={{ opacity: 0 }}
                  transition={{ duration: 0.25 }}
                  className="absolute inset-0 bg-white pointer-events-none z-30"
                />
              )}
            </AnimatePresence>

            {/* LIVE badge (top right for RTL) */}
            {isActive && (
              <div className="absolute top-3 right-3 flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-red-500/90 backdrop-blur-sm shadow-lg">
                <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse" />
                <span className="text-[10px] text-white font-bold tracking-wide">LIVE</span>
              </div>
            )}

            {/* Stats overlay (top left) */}
            {isActive && (
              <div className="absolute top-3 left-3 flex flex-col gap-1.5">
                <div className="flex items-center gap-3 px-2.5 py-1.5 rounded-md bg-black/70 backdrop-blur-sm">
                  <span className="flex items-center gap-1 text-[10px] text-emerald-300 font-mono">
                    <Gauge className="w-2.5 h-2.5" />
                    {fps} FPS
                  </span>
                  <span className="text-slate-600 text-[10px]">|</span>
                  <span className="flex items-center gap-1 text-[10px] text-cyan-300 font-mono">
                    <Signal className="w-2.5 h-2.5" />
                    {latencyMs !== null ? `${latencyMs}ms` : '--'}
                  </span>
                  {resolution && (
                    <>
                      <span className="text-slate-600 text-[10px]">|</span>
                      <span className="text-[10px] text-slate-300 font-mono">
                        {resolution}
                      </span>
                    </>
                  )}
                </div>
                <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-black/70 backdrop-blur-sm">
                  <Wifi className="w-2.5 h-2.5 text-emerald-400" />
                  <span className="text-[10px] text-slate-300 font-mono">{quality}</span>
                </div>
              </div>
            )}

            {/* Frame timestamp overlay (bottom right) */}
            {frameTs && (
              <div className="absolute bottom-3 right-3 flex items-center gap-1 px-2 py-1 rounded-md bg-black/70 backdrop-blur-sm">
                <RefreshCw className="w-2.5 h-2.5 text-emerald-400 animate-spin" />
                <span className="text-[10px] text-slate-300 font-mono">
                  {formatTimestamp(frameTs)}
                </span>
              </div>
            )}
          </div>

          {/* Controls bar */}
          <div className="flex items-center justify-between gap-3 p-3 bg-slate-950/90 border-t border-slate-800/60 flex-wrap">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              {isActive ? (
                <span className="flex items-center gap-1 text-emerald-400">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                  البث نشط — يتم التحديث تلقائياً
                </span>
              ) : isConnecting ? (
                <span className="flex items-center gap-1 text-amber-400">
                  <Loader2 className="w-3 h-3 animate-spin" />
                  جارٍ الاقتران بالجهاز...
                </span>
              ) : status === 'error' ? (
                <span className="flex items-center gap-1 text-red-400">
                  <AlertCircle className="w-3 h-3" />
                  حدث خطأ في البث
                </span>
              ) : (
                <span>البث متوقف</span>
              )}
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              {/* Screenshot capture */}
              <Button
                variant="outline"
                size="sm"
                onClick={handleScreenshot}
                disabled={!isActive || isAudioType(streamType)}
                className="border-slate-700 text-slate-300 hover:bg-slate-800 bg-transparent"
                title="التقاط لقطة شاشة للإطار الحالي"
              >
                {screenshotSaved ? (
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <CameraIcon className="w-3.5 h-3.5" />
                )}
                <span className="hidden sm:inline">لقطة</span>
              </Button>

              {/* Switch camera (only for camera streams) */}
              {(streamType === 'front_camera' || streamType === 'back_camera') && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleSwitchCamera}
                  disabled={!isActive}
                  className="border-slate-700 text-slate-300 hover:bg-slate-800 bg-transparent"
                  title="تبديل الكاميرا"
                >
                  <SwitchCamera className="w-3.5 h-3.5" />
                  <span className="hidden sm:inline">تبديل الكاميرا</span>
                </Button>
              )}

              {/* Stop button */}
              <Button
                variant="destructive"
                size="sm"
                onClick={handleStop}
                disabled={status === 'idle'}
                className="bg-red-500/90 hover:bg-red-500 text-white border-red-500/50 shadow-lg shadow-red-500/20"
              >
                {status === 'stopping' ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <StopCircle className="w-3.5 h-3.5" />
                )}
                <span className="font-bold">إيقاف البث</span>
              </Button>
            </div>
          </div>
        </div>

        {/* Help text below viewer */}
        <div className="text-xs text-slate-500 leading-relaxed p-4 rounded-lg bg-slate-900/40 border border-slate-800/40">
          <p className="font-medium text-slate-400 mb-2">ملاحظات:</p>
          <ul className="list-disc list-inside space-y-1 text-slate-500">
            <li>يتم تحديث الإطار تلقائياً كل 300 مللي ثانية أثناء النشاط.</li>
            <li>اضغط «إيقاف البث» قبل مغادرة الصفحة لإيقاف البث على الجهاز.</li>
            <li>يمكنك تغيير الجودة وتبديل الكاميرا أثناء البث مباشرةً.</li>
            {isAudioType(streamType) && (
              <li>البث الصوتي يرسل إطارات AAC — ملف التسجيل الكامل متاح في تبويب «الملفات».</li>
            )}
          </ul>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">البث المباشر</h1>
          <p className="text-sm text-slate-400 mt-1">
            بث الشاشة والكاميرا والصوت من الجهاز في الوقت الفعلي
          </p>
        </div>
        {showViewer && (
          <Badge className={cn(
            'text-xs gap-1.5',
            isActive
              ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25'
              : isConnecting
                ? 'bg-amber-500/15 text-amber-400 border-amber-500/25'
                : status === 'error'
                  ? 'bg-red-500/15 text-red-400 border-red-500/25'
                  : 'bg-slate-700/50 text-slate-300 border-slate-600/30'
          )}>
            <span className={cn(
              'w-1.5 h-1.5 rounded-full',
              isActive ? 'bg-emerald-400 animate-pulse' :
                isConnecting ? 'bg-amber-400 animate-pulse' :
                  status === 'error' ? 'bg-red-400' : 'bg-slate-400'
            )} />
            {isActive ? 'نشط' : isConnecting ? 'جارٍ الاقتران' : status === 'error' ? 'خطأ' : 'متوقف'}
          </Badge>
        )}
      </div>

      <AnimatePresence mode="wait">
        <motion.div
          key={showViewer ? 'viewer' : 'selection'}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -10 }}
          transition={{ duration: 0.2 }}
        >
          {showViewer ? renderViewer() : renderSelectionScreen()}
        </motion.div>
      </AnimatePresence>
    </div>
  )
}

/* ─── Helper: decode JPEG resolution from base64 ─── */
function decodeJpegResolution(b64: string): { w: number; h: number } | null {
  try {
    const bytes = atob(b64)
    // Look for SOF0 (0xFF 0xC0) marker
    let i = 0
    while (i < bytes.length - 1) {
      if (bytes.charCodeAt(i) === 0xff) {
        const marker = bytes.charCodeAt(i + 1)
        // SOF markers: C0-C3, C5-C7, C9-CB, CD-CF
        if (
          (marker >= 0xc0 && marker <= 0xc3) ||
          (marker >= 0xc5 && marker <= 0xc7) ||
          (marker >= 0xc9 && marker <= 0xcb) ||
          (marker >= 0xcd && marker <= 0xcf)
        ) {
          // SOF structure: FF C0 <length:2> <precision:1> <height:2> <width:2>
          const h = (bytes.charCodeAt(i + 5) << 8) | bytes.charCodeAt(i + 6)
          const w = (bytes.charCodeAt(i + 7) << 8) | bytes.charCodeAt(i + 8)
          if (w > 0 && h > 0 && w < 10000 && h < 10000) {
            return { w, h }
          }
          return null
        }
        // Skip variable-length markers
        if (marker >= 0xd0 && marker <= 0xd9) {
          i += 2
          continue
        }
        if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd9)) {
          i += 2
          continue
        }
        const len = (bytes.charCodeAt(i + 2) << 8) | bytes.charCodeAt(i + 3)
        i += 2 + len
        continue
      }
      i += 1
    }
    return null
  } catch {
    return null
  }
}
