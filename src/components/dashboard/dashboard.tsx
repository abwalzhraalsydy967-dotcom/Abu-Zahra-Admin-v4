'use client'

import React, { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Shield,
  Smartphone,
  Battery,
  BatteryCharging,
  Wifi,
  Clock,
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
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
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

function StatsSkeleton() {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <Skeleton key={i} className="h-24 w-full" />
      ))}
    </div>
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
  const [loadingDevices, setLoadingDevices] = useState(true)
  const [loadingStats, setLoadingStats] = useState(true)
  const [loadingEvents, setLoadingEvents] = useState(true)
  const [loadingUsers, setLoadingUsers] = useState(false)
  const [activeTab, setActiveTab] = useState('devices')
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null)
  const [activeCategory, setActiveCategory] = useState('data')
  const [commandLoading, setCommandLoading] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [showPermanentCode, setShowPermanentCode] = useState(false)
  const [linkCodeLoading, setLinkCodeLoading] = useState(false)

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
        if (res.ok && res.data) {
          setDevices(res.data as Device[])
          addLog('success', `تم تحميل ${(res.data as Device[]).length} جهاز`)
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
        if (res.ok && res.data) {
          setStats(res.data as Stats)
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
        if (res.ok && res.data) {
          setEvents(res.data as Event[])
          addLog('info', `تم تحميل ${(res.data as Event[]).length} حدث`)
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
        if (res.ok && res.data) {
          setDevices(res.data as Device[])
        }
      } catch {
        // silent refresh
      }
    }, 15000)
    return () => {
      if (refreshTimerRef.current) clearInterval(refreshTimerRef.current)
    }
  }, [])

  /* ── Load Users when Users tab is active ── */
  useEffect(() => {
    if (activeTab !== 'users' || user?.role !== 'admin') return
    let cancelled = false

    ;(async () => {
      setLoadingUsers(true)
      try {
        const res = await api.getUsers()
        if (cancelled) return
        if (res.ok && res.data) {
          setUsers(res.data as UserData[])
          addLog('info', `تم تحميل ${(res.data as UserData[]).length} مستخدم`)
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
  }, [activeTab, user?.role])

  /* ── Handlers ── */
  const handleSelectDevice = (device: Device) => {
    setSelectedDevice(device)
    setActiveTab('commands')
    addLog('info', `تم اختيار الجهاز: ${device.name}`, `الموديل: ${device.model}`)
  }

  const handleGenerateLinkCode = async () => {
    setLinkCodeLoading(true)
    addLog('info', 'جارِ توليد كود ربط جديد...')
    try {
      const res = await api.generateLinkCode()
      if (res.ok && res.data) {
        const code = (res.data as { code: string }).code
        setPermanentCode(code)
        setShowPermanentCode(true)
        addLog('success', 'تم توليد كود ربط جديد', `الكود: ${code}`)
      } else {
        addLog('error', 'فشل توليد كود الربط', res.message)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'خطأ في توليد كود الربط', msg)
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
      } else {
        addLog('error', `فشل إرسال الأمر: ${cmd}`, res.message || 'خطأ من الخادم')
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', `خطأ في إرسال الأمر: ${cmd}`, msg)
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
        // Re-fetch users
        const usersRes = await api.getUsers()
        if (usersRes.ok && usersRes.data) {
          setUsers(usersRes.data as UserData[])
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

  const handleLogout = () => {
    addLog('info', 'تسجيل الخروج', `المستخدم: ${user?.username}`)
    api.logout().catch(() => {})
    logout()
  }

  const formatUptime = (seconds: number): string => {
    const h = Math.floor(seconds / 3600)
    if (h < 24) return `${h} ساعة`
    const d = Math.floor(h / 24)
    const rh = h % 24
    return `${d} يوم ${rh} ساعة`
  }

  /* ─── Render: Header ───────────────────────────────── */
  const renderHeader = () => (
    <header className="sticky top-0 z-40 w-full border-b border-slate-800/60 bg-slate-950/90 backdrop-blur-xl">
      <div className="flex items-center justify-between px-4 py-3 md:px-6">
        {/* Right side: Logo */}
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-600 shadow-lg shadow-emerald-500/20">
            <Shield className="w-5 h-5 text-white" />
          </div>
          <h1 className="text-lg font-bold text-white tracking-tight">أبو زهرة</h1>
          <Badge className="bg-emerald-500/15 text-emerald-400 border-emerald-500/25 text-[10px] px-1.5">
            v4.0
          </Badge>
        </div>

        {/* Left side: User menu */}
        <div className="flex items-center gap-3">
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

          {/* User Dropdown */}
          <DropdownMenu>
            <DropdownMenuTrigger
              render={
                <button
                  type="button"
                  className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-slate-800/60 transition-colors outline-none cursor-pointer"
                />
              }
            >
              <div className="w-8 h-8 rounded-full bg-gradient-to-br from-emerald-500 to-emerald-600 flex items-center justify-center text-white text-sm font-bold">
                {user?.username?.charAt(0)?.toUpperCase() || 'م'}
              </div>
              <span className="hidden sm:block text-sm text-slate-200 font-medium">
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
                توليد كود ربط
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

  /* ─── Render: Stats Row ────────────────────────────── */
  const renderStats = () => {
    if (loadingStats) return <StatsSkeleton />

    const items = [
      {
        label: 'الأجهزة المتصلة',
        value: stats?.online_devices ?? 0,
        icon: Wifi,
        color: 'text-green-400',
        border: 'border-green-500/20',
        iconBg: 'bg-green-500/15',
      },
      {
        label: 'إجمالي الأوامر',
        value: stats?.total_commands ?? 0,
        icon: Terminal,
        color: 'text-emerald-400',
        border: 'border-emerald-500/20',
        iconBg: 'bg-emerald-500/15',
      },
      {
        label: 'الأحداث',
        value: stats?.total_events ?? 0,
        icon: Activity,
        color: 'text-amber-400',
        border: 'border-amber-500/20',
        iconBg: 'bg-amber-500/15',
      },
      {
        label: 'وقت التشغيل',
        value: stats?.uptime ? formatUptime(stats.uptime) : '0 ساعة',
        icon: Clock,
        color: 'text-cyan-400',
        border: 'border-cyan-500/20',
        iconBg: 'bg-cyan-500/15',
      },
    ]

    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {items.map((item) => (
          <motion.div
            key={item.label}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <div
              className={cn(
                'bg-slate-900/80 border rounded-xl p-4',
                item.border
              )}
            >
              <div className="flex items-center justify-between mb-3">
                <div className={cn('p-2 rounded-lg', item.iconBg)}>
                  <item.icon className={cn('w-4 h-4', item.color)} />
                </div>
              </div>
              <p className={cn('text-2xl font-bold', item.color)}>
                {item.value}
              </p>
              <p className="text-slate-400 text-xs mt-1">{item.label}</p>
            </div>
          </motion.div>
        ))}
      </div>
    )
  }

  /* ─── Render: Device Card ──────────────────────────── */
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
            'w-full text-right bg-slate-900/80 border rounded-xl p-4 transition-all duration-200',
            'hover:bg-slate-800/80 hover:border-emerald-500/30 hover:shadow-lg hover:shadow-emerald-500/5',
            isSelected
              ? 'border-emerald-500/50 ring-1 ring-emerald-500/20 bg-emerald-500/5'
              : 'border-slate-800/50'
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

  /* ─── Render: Devices Tab ──────────────────────────── */
  const renderDevicesTab = () => (
    <div className="space-y-4">
      {/* Top bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Smartphone className="w-5 h-5 text-emerald-400" />
          <h2 className="text-lg font-semibold text-white">الأجهزة</h2>
          <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs">
            {devices.length}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              addLog('info', 'تحديث قائمة الأجهزة يدوياً')
              setLoadingDevices(true)
              api.getDevices().then(res => {
                if (res.ok && res.data) setDevices(res.data as Device[])
                setLoadingDevices(false)
              }).catch(() => setLoadingDevices(false))
            }}
            disabled={loadingDevices}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className={cn('w-4 h-4', loadingDevices && 'animate-spin')} />
            <span className="hidden sm:inline">تحديث</span>
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
            <span className="hidden sm:inline">توليد كود ربط</span>
          </Button>
        </div>
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
          className="flex flex-col items-center justify-center py-16 text-slate-400"
        >
          <SmartphoneNfc className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">لا توجد أجهزة متصلة</p>
          <p className="text-sm mt-1">استخدم كود الربط لربط جهاز جديد</p>
        </motion.div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {devices.map(renderDeviceCard)}
        </div>
      )}
    </div>
  )

  /* ─── Render: Commands Tab ─────────────────────────── */
  const renderCommandsTab = () => {
    if (!selectedDevice) {
      return (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex flex-col items-center justify-center py-16 text-slate-400"
        >
          <Eye className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">اختر جهازاً لعرض الأوامر</p>
          <p className="text-sm mt-1">انتقل إلى تبويب الأجهزة واختر جهازاً</p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setActiveTab('devices')}
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
    const commands = currentCategory ? Object.values(currentCategory.commands) : []

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

        {/* Category Chips */}
        <div className="flex gap-2 overflow-x-auto pb-1" style={{ scrollbarWidth: 'none' }}>
          {categoryKeys.map((key) => {
            const cat = CMD_CATEGORIES[key]
            const isActive = activeCategory === key
            return (
              <button
                key={key}
                type="button"
                onClick={() => {
                  setActiveCategory(key)
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
              </button>
            )
          })}
        </div>

        {/* Command Buttons Grid */}
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
                  'bg-slate-900/80 border-slate-800/50',
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
      </div>
    )
  }

  /* ─── Render: Events Tab ───────────────────────────── */
  const renderEventsTab = () => {
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
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Activity className="w-5 h-5 text-amber-400" />
            <h2 className="text-lg font-semibold text-white">الأحداث</h2>
            <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs">
              {events.length}
            </Badge>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setLoadingEvents(true)
              api.getEvents(100).then(res => {
                if (res.ok && res.data) setEvents(res.data as Event[])
                setLoadingEvents(false)
              }).catch(() => setLoadingEvents(false))
            }}
            disabled={loadingEvents}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className={cn('w-4 h-4', loadingEvents && 'animate-spin')} />
          </Button>
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
            className="flex flex-col items-center justify-center py-16 text-slate-400"
          >
            <Activity className="w-12 h-12 mb-3 text-slate-600" />
            <p className="text-base font-medium">لا توجد أحداث</p>
          </motion.div>
        ) : (
          <div className="max-h-[600px] overflow-y-auto rounded-xl border border-slate-800/50 bg-slate-900/50" style={{ scrollbarWidth: 'thin', scrollbarColor: 'rgba(255,255,255,0.15) transparent' }}>
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

  /* ─── Render: Users Tab ────────────────────────────── */
  const renderUsersTab = () => {
    if (user?.role !== 'admin') {
      return (
        <div className="flex flex-col items-center justify-center py-16 text-slate-400">
          <AlertTriangle className="w-12 h-12 mb-3 text-slate-600" />
          <p className="text-base font-medium">ليس لديك صلاحية الوصول</p>
          <p className="text-sm mt-1">هذا القسم متاح للمسؤولين فقط</p>
        </div>
      )
    }

    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Users className="w-5 h-5 text-emerald-400" />
            <h2 className="text-lg font-semibold text-white">المستخدمين</h2>
            <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-xs">
              {users.length}
            </Badge>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setLoadingUsers(true)
              api.getUsers().then(res => {
                if (res.ok && res.data) setUsers(res.data as UserData[])
                setLoadingUsers(false)
              }).catch(() => setLoadingUsers(false))
            }}
            disabled={loadingUsers}
            className="text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <RefreshCw className={cn('w-4 h-4', loadingUsers && 'animate-spin')} />
          </Button>
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
            className="flex flex-col items-center justify-center py-16 text-slate-400"
          >
            <Users className="w-12 h-12 mb-3 text-slate-600" />
            <p className="text-base font-medium">لا يوجد مستخدمين</p>
          </motion.div>
        ) : (
          <div className="max-h-[600px] overflow-y-auto rounded-xl border border-slate-800/50 bg-slate-900/50" style={{ scrollbarWidth: 'thin', scrollbarColor: 'rgba(255,255,255,0.15) transparent' }}>
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

  /* ─── Render: Param Dialog ─────────────────────────── */
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

  /* ─── Render: Security Confirm Dialog ──────────────── */
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

  /* ─── Render: Delete User Dialog ───────────────────── */
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

  /* ─── Main Render ──────────────────────────────────── */
  return (
    <div className="min-h-screen flex flex-col bg-gradient-to-br from-slate-950 via-emerald-950/50 to-slate-950">
      {renderHeader()}

      <main className="flex-1 px-4 py-6 md:px-6 pb-20">
        {/* Stats */}
        <div className="mb-6">{renderStats()}</div>

        {/* Tabs */}
        <Tabs
          value={activeTab}
          onValueChange={(val: string) => {
            setActiveTab(val)
            addLog('info', `التبديل إلى: ${val}`)
          }}
        >
          <TabsList className="w-full sm:w-auto bg-slate-900/80 border border-slate-800/50 p-1 rounded-xl mb-6">
            <TabsTrigger value="devices" className="rounded-lg text-sm gap-1.5 data-active:bg-emerald-500/15 data-active:text-emerald-400">
              <Smartphone className="w-4 h-4" />
              الأجهزة
            </TabsTrigger>
            <TabsTrigger value="commands" className="rounded-lg text-sm gap-1.5 data-active:bg-emerald-500/15 data-active:text-emerald-400">
              <Terminal className="w-4 h-4" />
              الأوامر
            </TabsTrigger>
            <TabsTrigger value="events" className="rounded-lg text-sm gap-1.5 data-active:bg-emerald-500/15 data-active:text-emerald-400">
              <Activity className="w-4 h-4" />
              الأحداث
            </TabsTrigger>
            {user?.role === 'admin' && (
              <TabsTrigger value="users" className="rounded-lg text-sm gap-1.5 data-active:bg-emerald-500/15 data-active:text-emerald-400">
                <Users className="w-4 h-4" />
                المستخدمين
              </TabsTrigger>
            )}
          </TabsList>

          <TabsContent value="devices">
            <AnimatePresence mode="wait">
              <motion.div
                key="devices"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
              >
                {renderDevicesTab()}
              </motion.div>
            </AnimatePresence>
          </TabsContent>

          <TabsContent value="commands">
            <AnimatePresence mode="wait">
              <motion.div
                key={`commands-${selectedDevice?.id || 'none'}`}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
              >
                {renderCommandsTab()}
              </motion.div>
            </AnimatePresence>
          </TabsContent>

          <TabsContent value="events">
            <AnimatePresence mode="wait">
              <motion.div
                key="events"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
              >
                {renderEventsTab()}
              </motion.div>
            </AnimatePresence>
          </TabsContent>

          {user?.role === 'admin' && (
            <TabsContent value="users">
              <AnimatePresence mode="wait">
                <motion.div
                  key="users"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.2 }}
                >
                  {renderUsersTab()}
                </motion.div>
              </AnimatePresence>
            </TabsContent>
          )}
        </Tabs>
      </main>

      {/* Dialogs */}
      {renderParamDialog()}
      {renderConfirmDialog()}
      {renderDeleteDialog()}
    </div>
  )
}