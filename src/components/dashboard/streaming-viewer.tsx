'use client'

import React, { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Radio,
  Play,
  Square,
  Monitor,
  Camera,
  Video,
  Loader2,
  AlertCircle,
  RefreshCw,
  Image as ImageIcon,
  Clock,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import api, { type Device } from '@/lib/api'
import { cn, formatTimestamp, addLog } from '@/lib/utils'

interface Props {
  device: Device
}

type StreamType = 'screen' | 'front_camera' | 'back_camera'
type StreamStatus = 'idle' | 'starting' | 'active' | 'stopping' | 'error'

const STREAM_TYPES: {
  value: StreamType
  label: string
  icon: typeof Monitor
  startCmd: string
  stopCmd: string
}[] = [
  {
    value: 'screen',
    label: 'بث الشاشة',
    icon: Monitor,
    startCmd: 'start_screen_stream',
    stopCmd: 'stop_screen_stream',
  },
  {
    value: 'front_camera',
    label: 'الكاميرا الأمامية',
    icon: Video,
    startCmd: 'start_camera_stream',
    stopCmd: 'stop_camera_stream',
  },
  {
    value: 'back_camera',
    label: 'الكاميرا الخلفية',
    icon: Camera,
    startCmd: 'start_camera_stream',
    stopCmd: 'stop_camera_stream',
  },
]

export default function StreamingViewer({ device }: Props) {
  const [streamType, setStreamType] = useState<StreamType>('screen')
  const [status, setStatus] = useState<StreamStatus>('idle')
  const [frameUrl, setFrameUrl] = useState<string | null>(null)
  const [frameTs, setFrameTs] = useState<number | null>(null)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)
  const [lastFetchOk, setLastFetchOk] = useState<boolean | null>(null)

  const framePollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const statusRef = useRef<StreamStatus>(status)
  const streamTypeRef = useRef<StreamType>(streamType)
  const deviceIdRef = useRef<string>(device.id)

  useEffect(() => {
    statusRef.current = status
  }, [status])
  useEffect(() => {
    streamTypeRef.current = streamType
  }, [streamType])
  useEffect(() => {
    deviceIdRef.current = device.id
  }, [device.id])

  const pollFrame = async () => {
    try {
      const frame = await api.streamFrame(deviceIdRef.current, 'video')
      if (frame.ok && frame.data) {
        setFrameUrl(`data:image/jpeg;base64,${frame.data}`)
        if (frame.timestamp) setFrameTs(frame.timestamp)
        setLastFetchOk(true)
        if (statusRef.current === 'starting') {
          setStatus('active')
          addLog('success', 'تم استقبال أول إطار من البث')
        }
      } else {
        setLastFetchOk(false)
        // Don't log spam — server returns 404 with "No frame available" until first frame arrives
      }
    } catch {
      setLastFetchOk(false)
    }
  }

  // If user switches streamType while active, restart with the new type
  const handleSwitchType = async (newType: StreamType) => {
    if (newType === streamType) return
    const wasActive = status === 'active' || status === 'starting'
    setStreamType(newType)
    streamTypeRef.current = newType
    addLog(
      'info',
      `تبديل نوع البث: ${STREAM_TYPES.find((t) => t.value === newType)?.label}`
    )

    if (wasActive) {
      // Stop current stream then start the new one
      await handleStop()
      setTimeout(() => {
        void handleStartWith(newType)
      }, 500)
    }
  }

  const handleStartWith = async (type: StreamType) => {
    if (statusRef.current === 'active' || statusRef.current === 'starting') return
    setStatus('starting')
    setErrorMsg(null)
    setFrameUrl(null)
    setFrameTs(null)
    setLastFetchOk(null)

    const label =
      type === 'screen'
        ? 'الشاشة'
        : type === 'front_camera'
          ? 'الكاميرا الأمامية'
          : 'الكاميرا الخلفية'
    addLog('info', `بدء بث ${label}`, `الجهاز: ${device.name}`)

    try {
      const startCmd = STREAM_TYPES.find((t) => t.value === type)!.startCmd
      const cmdRes = await api.sendCommand(device.id, startCmd, {
        camera: type === 'back_camera' ? 'back' : 'front',
      })
      if (!cmdRes.ok) {
        addLog('warning', 'تعذّر إرسال أمر بدء البث للجهاز', cmdRes.message)
      } else {
        addLog('success', `تم إرسال أمر بدء البث: ${startCmd}`)
      }

      const jpegRes = await api.jpegStreamStart(device.id, type, 3)
      if (!jpegRes.ok) {
        addLog('warning', 'تحذير من بدء JPEG stream', jpegRes.message)
      }

      pollFrame()
      framePollRef.current = setInterval(pollFrame, 2000)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في بدء البث', msg)
      setErrorMsg(msg)
      setStatus('error')
      if (framePollRef.current) {
        clearInterval(framePollRef.current)
        framePollRef.current = null
      }
    }
  }

  const handleStart = async () => {
    await handleStartWith(streamType)
  }

  const handleStop = async () => {
    if (status === 'idle' || status === 'stopping') return
    setStatus('stopping')
    addLog('info', `إيقاف البث`, `الجهاز: ${device.name}`)

    try {
      // Stop server-side polling
      await api.jpegStreamStop(device.id)
      // Send stop command to device
      const stopCmd = STREAM_TYPES.find((t) => t.value === streamType)!.stopCmd
      await api.sendCommand(device.id, stopCmd)
      addLog('success', 'تم إيقاف البث بنجاح')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('warning', 'تحذير أثناء إيقاف البث', msg)
    }

    if (framePollRef.current) {
      clearInterval(framePollRef.current)
      framePollRef.current = null
    }
    setStatus('idle')
    setFrameUrl(null)
    setFrameTs(null)
    setLastFetchOk(null)
  }

  // Stop stream when device changes or component unmounts
  useEffect(() => {
    return () => {
      if (framePollRef.current) {
        clearInterval(framePollRef.current)
        framePollRef.current = null
      }
      // Best-effort stop
      api.jpegStreamStop(deviceIdRef.current).catch(() => {})
    }
  }, [])

  const statusInfo: Record<
    StreamStatus,
    { label: string; cls: string; dot: string }
  > = {
    idle: {
      label: 'متوقف',
      cls: 'bg-slate-700/50 text-slate-300 border-slate-600/30',
      dot: 'bg-slate-400',
    },
    starting: {
      label: 'جارٍ التشغيل...',
      cls: 'bg-amber-500/15 text-amber-400 border-amber-500/25',
      dot: 'bg-amber-400 animate-pulse',
    },
    active: {
      label: 'نشط',
      cls: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25',
      dot: 'bg-emerald-400 animate-pulse',
    },
    stopping: {
      label: 'جارٍ الإيقاع...',
      cls: 'bg-amber-500/15 text-amber-400 border-amber-500/25',
      dot: 'bg-amber-400 animate-pulse',
    },
    error: {
      label: 'خطأ',
      cls: 'bg-red-500/15 text-red-400 border-red-500/25',
      dot: 'bg-red-400',
    },
  }

  const si = statusInfo[status]
  const isBusy = status === 'starting' || status === 'stopping'
  const isActive = status === 'active'

  return (
    <div className="space-y-4">
      {/* Top bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Radio className="w-5 h-5 text-emerald-400" />
          <h2 className="text-lg font-semibold text-white">البث المباشر</h2>
          <Badge className={cn('text-[10px] gap-1.5', si.cls)}>
            <span className={cn('w-1.5 h-1.5 rounded-full', si.dot)} />
            {si.label}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={pollFrame}
            disabled={!isActive}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className="w-4 h-4" />
            <span className="hidden sm:inline">إطار جديد</span>
          </Button>
        </div>
      </div>

      {/* Device hint */}
      <div className="flex items-center gap-3 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
        <div className="p-1.5 rounded-lg bg-emerald-500/15">
          <Monitor className="w-4 h-4 text-emerald-400" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-white text-sm font-medium truncate">
            {device.name}
          </p>
          <p className="text-slate-400 text-xs truncate">
            {device.model} • {device.brand}
          </p>
        </div>
        {!device.is_online && (
          <Badge className="bg-red-500/15 text-red-400 border-red-500/25 text-[10px]">
            الجهاز غير متصل
          </Badge>
        )}
      </div>

      {/* Stream type selector */}
      <div className="flex gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
        {STREAM_TYPES.map((t) => {
          const isSelected = streamType === t.value
          return (
            <button
              key={t.value}
              type="button"
              onClick={() => handleSwitchType(t.value)}
              disabled={isBusy}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all duration-200 border disabled:opacity-50',
                isSelected
                  ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                  : 'bg-slate-800/50 text-slate-400 border-slate-700/50 hover:bg-slate-700/50 hover:text-slate-300'
              )}
            >
              <t.icon className="w-3.5 h-3.5" />
              {t.label}
            </button>
          )
        })}
      </div>

      {/* Stream viewer */}
      <div className="rounded-xl border border-slate-800/60 bg-slate-950/60 overflow-hidden">
        <div className="relative aspect-video bg-black flex items-center justify-center">
          <AnimatePresence mode="wait">
            {frameUrl ? (
              <motion.img
                key={frameUrl.slice(0, 50)}
                src={frameUrl}
                alt="البث المباشر"
                initial={{ opacity: 0.3 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
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
                {isBusy ? (
                  <>
                    <Loader2 className="w-10 h-10 mb-3 text-emerald-400/60 animate-spin" />
                    <p className="text-sm">
                      {status === 'starting'
                        ? 'جارٍ تشغيل البث وانتظار أول إطار...'
                        : 'جارٍ الإيقاف...'}
                    </p>
                    <p className="text-xs mt-1 text-slate-600">
                      قد يستغرق ذلك بضع ثوانٍ
                    </p>
                  </>
                ) : status === 'error' ? (
                  <>
                    <AlertCircle className="w-10 h-10 mb-3 text-red-400/70" />
                    <p className="text-sm text-red-300">تعذّر تشغيل البث</p>
                    {errorMsg && (
                      <p className="text-xs mt-1 text-red-400/60 break-all text-center max-w-xs">
                        {errorMsg}
                      </p>
                    )}
                  </>
                ) : (
                  <>
                    <ImageIcon className="w-10 h-10 mb-3 text-slate-700" />
                    <p className="text-sm">البث متوقف</p>
                    <p className="text-xs mt-1 text-slate-600">
                      اضغط «بدء البث» لبدء استقبال الإطارات
                    </p>
                  </>
                )}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Live badge overlay */}
          {isActive && (
            <div className="absolute top-2 left-2 flex items-center gap-1.5 px-2 py-1 rounded-md bg-black/60 backdrop-blur-sm">
              <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
              <span className="text-[10px] text-white font-medium">مباشر</span>
            </div>
          )}

          {/* Frame timestamp overlay */}
          {frameTs && (
            <div className="absolute bottom-2 right-2 flex items-center gap-1 px-2 py-1 rounded-md bg-black/60 backdrop-blur-sm">
              <Clock className="w-2.5 h-2.5 text-slate-300" />
              <span className="text-[10px] text-slate-300 font-mono">
                {formatTimestamp(frameTs)}
              </span>
            </div>
          )}
        </div>

        {/* Controls bar */}
        <div className="flex items-center justify-between gap-3 p-3 bg-slate-900/80 border-t border-slate-800/60">
          <div className="flex items-center gap-2 text-xs text-slate-400">
            {lastFetchOk === false && isActive && (
              <span className="flex items-center gap-1 text-amber-400">
                <AlertCircle className="w-3 h-3" />
                بانتظار إطار من الجهاز...
              </span>
            )}
            {lastFetchOk === true && (
              <span className="flex items-center gap-1 text-emerald-400">
                <RefreshCw className="w-3 h-3 animate-spin" />
                يتم التحديث كل 2 ثانية
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="destructive"
              size="sm"
              onClick={handleStop}
              disabled={!isActive && !isBusy && status !== 'error'}
              className="bg-red-500/15 text-red-400 hover:bg-red-500/25 border border-red-500/25"
            >
              {status === 'stopping' ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Square className="w-4 h-4" />
              )}
              <span>إيقاف</span>
            </Button>
            <Button
              size="sm"
              onClick={handleStart}
              disabled={isActive || isBusy}
              className="bg-emerald-600 hover:bg-emerald-500 text-white"
            >
              {status === 'starting' ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <Play className="w-4 h-4" />
              )}
              <span>بدء البث</span>
            </Button>
          </div>
        </div>
      </div>

      {/* Help text */}
      <div className="text-xs text-slate-500 leading-relaxed p-3 rounded-lg bg-slate-900/40 border border-slate-800/40">
        <p className="font-medium text-slate-400 mb-1">ملاحظات:</p>
        <ul className="list-disc list-inside space-y-0.5 text-slate-500">
          <li>يتم إرسال أمر البث إلى الجهاز وبدء التقاط الإطارات من الخادم.</li>
          <li>يُحدّث الإطار تلقائياً كل ثانيتين أثناء النشاط.</li>
          <li>إذا لم تظهر الإطارات، تأكد من اتصال الجهاز وصلاحيات الكاميرا/الشاشة.</li>
          <li>اضغط «إيقاف» قبل مغادرة الصفحة لإيقاف البث على الجهاز.</li>
        </ul>
      </div>
    </div>
  )
}
