'use client'

import React, { useState } from 'react'
import { motion } from 'framer-motion'
import {
  Settings as SettingsIcon,
  Server,
  Moon,
  Sun,
  Bell,
  Volume2,
  Shield,
  Globe,
  Save,
  Check,
  Loader2,
  Info,
  Trash2,
  AlertCircle,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn, addLog } from '@/lib/utils'

interface Props {
  /** Optional callback when server URL changes (currently informational only) */
  onServerUrlChange?: (url: string) => void
}

interface Settings {
  serverUrl: string
  theme: 'dark' | 'light'
  notifications: boolean
  soundEnabled: boolean
  desktopNotifications: boolean
  autoRefresh: boolean
  refreshInterval: number
  compactMode: boolean
  showLogPanel: boolean
}

const DEFAULT_SETTINGS: Settings = {
  serverUrl: 'https://alsydyabwalzhra.online',
  theme: 'dark',
  notifications: true,
  soundEnabled: true,
  desktopNotifications: false,
  autoRefresh: true,
  refreshInterval: 15,
  compactMode: false,
  showLogPanel: true,
}

const STORAGE_KEY = 'abuzahra_settings'

function loadSettings(): Settings {
  if (typeof window === 'undefined') return DEFAULT_SETTINGS
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      return { ...DEFAULT_SETTINGS, ...JSON.parse(saved) }
    }
  } catch {
    // ignore parse errors
  }
  return DEFAULT_SETTINGS
}

function saveSettings(settings: Settings) {
  if (typeof window === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
  } catch {
    // ignore
  }
}

/* ─── Toggle switch ─── */
function Toggle({
  checked,
  onChange,
  disabled,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => !disabled && onChange(!checked)}
      disabled={disabled}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors duration-200 disabled:opacity-50',
        checked ? 'bg-emerald-500' : 'bg-slate-700'
      )}
    >
      <span
        className={cn(
          'inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform duration-200',
          checked ? '-translate-x-0.5' : '-translate-x-5'
        )}
      />
    </button>
  )
}

/* ─── Setting row ─── */
function SettingRow({
  icon: Icon,
  iconColor,
  iconBg,
  title,
  desc,
  children,
}: {
  icon: typeof Bell
  iconColor: string
  iconBg: string
  title: string
  desc: string
  children: React.ReactNode
}) {
  return (
    <div className="flex items-center gap-4 p-4 rounded-lg bg-slate-900/40 border border-slate-800/40 hover:bg-slate-900/60 transition-colors">
      <div className={cn('p-2 rounded-lg shrink-0', iconBg)}>
        <Icon className={cn('w-4 h-4', iconColor)} />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-white">{title}</p>
        <p className="text-xs text-slate-400 mt-0.5">{desc}</p>
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  )
}

/* ─── Section card ─── */
function SectionCard({
  title,
  icon: Icon,
  children,
}: {
  title: string
  icon: typeof Server
  children: React.ReactNode
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5 space-y-3"
    >
      <div className="flex items-center gap-2 pb-3 border-b border-slate-800/60">
        <Icon className="w-4 h-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-white">{title}</h3>
      </div>
      {children}
    </motion.div>
  )
}

export default function SettingsView({ onServerUrlChange }: Props) {
  // Lazy initialiser — runs once on mount (client-only) without an effect.
  const [settings, setSettings] = useState<Settings>(() =>
    typeof window === 'undefined' ? DEFAULT_SETTINGS : loadSettings()
  )
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [clearingLogs, setClearingLogs] = useState(false)

  const updateSetting = <K extends keyof Settings>(key: K, value: Settings[K]) => {
    setSettings((prev) => ({ ...prev, [key]: value }))
  }

  const handleSave = () => {
    setSaving(true)
    try {
      saveSettings(settings)
      if (onServerUrlChange && settings.serverUrl) {
        onServerUrlChange(settings.serverUrl)
      }
      addLog('success', 'تم حفظ الإعدادات بنجاح')
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'فشل حفظ الإعدادات', msg)
    } finally {
      setSaving(false)
    }
  }

  const handleClearLocalData = () => {
    setClearingLogs(true)
    try {
      localStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem('auth_user')
      setSettings(DEFAULT_SETTINGS)
      addLog('success', 'تم مسح البيانات المحلية بنجاح')
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'خطأ غير معروف'
      addLog('error', 'فشل مسح البيانات المحلية', msg)
    } finally {
      setClearingLogs(false)
    }
  }

  if (typeof window === 'undefined') {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-6 h-6 animate-spin text-emerald-400" />
      </div>
    )
  }

  return (
    <div className="space-y-6 max-w-3xl">
      {/* Page header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">الإعدادات</h1>
          <p className="text-sm text-slate-400 mt-1">
            إدارة تفضيلات النظام والتنبيهات
          </p>
        </div>
        <Button
          size="sm"
          onClick={handleSave}
          disabled={saving}
          className="bg-emerald-600 hover:bg-emerald-500 text-white"
        >
          {saving ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : saved ? (
            <Check className="w-4 h-4" />
          ) : (
            <Save className="w-4 h-4" />
          )}
          <span>{saved ? 'تم الحفظ' : 'حفظ'}</span>
        </Button>
      </div>

      {/* Connection section */}
      <SectionCard title="الاتصال بالخادم" icon={Server}>
        <div className="space-y-3">
          <div className="space-y-2">
            <Label className="text-slate-300 text-sm">رابط الخادم (Server URL)</Label>
            <div className="flex items-center gap-2">
              <Globe className="w-4 h-4 text-slate-500 shrink-0" />
              <Input
                type="text"
                value={settings.serverUrl}
                onChange={(e) => updateSetting('serverUrl', e.target.value)}
                placeholder="https://example.com"
                dir="ltr"
                className="bg-slate-800 border-slate-700 text-white placeholder:text-slate-500 focus-visible:border-emerald-500/50 focus-visible:ring-emerald-500/20 font-mono"
              />
            </div>
            <p className="text-[11px] text-slate-500 leading-relaxed">
              يتم استخدام هذا الرابط للاتصال بالخادم. التغييرات تُحفظ محلياً فقط ولا تؤثر على إعدادات الخادم.
            </p>
          </div>
        </div>
      </SectionCard>

      {/* Appearance section */}
      <SectionCard title="المظهر" icon={settings.theme === 'dark' ? Moon : Sun}>
        <SettingRow
          icon={settings.theme === 'dark' ? Moon : Sun}
          iconColor={settings.theme === 'dark' ? 'text-cyan-400' : 'text-amber-400'}
          iconBg={settings.theme === 'dark' ? 'bg-cyan-500/15' : 'bg-amber-500/15'}
          title="الوضع الليلي"
          desc="تفعيل التصميم الداكن للواجهة"
        >
          <Toggle
            checked={settings.theme === 'dark'}
            onChange={(v) => updateSetting('theme', v ? 'dark' : 'light')}
          />
        </SettingRow>

        <SettingRow
          icon={SettingsIcon}
          iconColor="text-emerald-400"
          iconBg="bg-emerald-500/15"
          title="الوضع المضغوط"
          desc="تقليل المسافات لعرض محتوى أكثر"
        >
          <Toggle
            checked={settings.compactMode}
            onChange={(v) => updateSetting('compactMode', v)}
          />
        </SettingRow>

        <SettingRow
          icon={Bell}
          iconColor="text-amber-400"
          iconBg="bg-amber-500/15"
          title="إظهار لوحة السجلات"
          desc="عرض لوحة السجلات في الزاوية"
        >
          <Toggle
            checked={settings.showLogPanel}
            onChange={(v) => updateSetting('showLogPanel', v)}
          />
        </SettingRow>
      </SectionCard>

      {/* Notifications section */}
      <SectionCard title="التنبيهات" icon={Bell}>
        <SettingRow
          icon={Bell}
          iconColor="text-amber-400"
          iconBg="bg-amber-500/15"
          title="التنبيهات"
          desc="عرض التنبيهات داخل التطبيق"
        >
          <Toggle
            checked={settings.notifications}
            onChange={(v) => updateSetting('notifications', v)}
          />
        </SettingRow>

        <SettingRow
          icon={Volume2}
          iconColor="text-cyan-400"
          iconBg="bg-cyan-500/15"
          title="أصوات التنبيهات"
          desc="تشغيل صوت عند وصول تنبيه جديد"
        >
          <Toggle
            checked={settings.soundEnabled}
            onChange={(v) => updateSetting('soundEnabled', v)}
            disabled={!settings.notifications}
          />
        </SettingRow>

        <SettingRow
          icon={Shield}
          iconColor="text-emerald-400"
          iconBg="bg-emerald-500/15"
          title="تنبيهات سطح المكتب"
          desc="إظهار تنبيهات النظام على المتصفح"
        >
          <Toggle
            checked={settings.desktopNotifications}
            onChange={(v) => {
              if (v && typeof window !== 'undefined' && 'Notification' in window) {
                Notification.requestPermission().then((perm) => {
                  if (perm === 'granted') {
                    updateSetting('desktopNotifications', true)
                    addLog('success', 'تم تفعيل تنبيهات سطح المكتب')
                  } else {
                    addLog('warning', 'تم رفض إذن التنبيهات')
                  }
                })
              } else {
                updateSetting('desktopNotifications', v)
              }
            }}
            disabled={!settings.notifications}
          />
        </SettingRow>
      </SectionCard>

      {/* Data refresh section */}
      <SectionCard title="تحديث البيانات" icon={SettingsIcon}>
        <SettingRow
          icon={SettingsIcon}
          iconColor="text-cyan-400"
          iconBg="bg-cyan-500/15"
          title="التحديث التلقائي"
          desc="تحديث قائمة الأجهزة والأحداث تلقائياً"
        >
          <Toggle
            checked={settings.autoRefresh}
            onChange={(v) => updateSetting('autoRefresh', v)}
          />
        </SettingRow>

        <div className="space-y-2 p-4 rounded-lg bg-slate-900/40 border border-slate-800/40">
          <Label className="text-slate-300 text-sm flex items-center justify-between">
            <span>فترة التحديث (ثانية)</span>
            <Badge className="bg-emerald-500/15 text-emerald-400 border-emerald-500/25 text-xs font-mono">
              {settings.refreshInterval}s
            </Badge>
          </Label>
          <input
            type="range"
            min={5}
            max={60}
            step={5}
            value={settings.refreshInterval}
            onChange={(e) => updateSetting('refreshInterval', Number(e.target.value))}
            disabled={!settings.autoRefresh}
            className="w-full h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-emerald-500 disabled:opacity-50"
          />
          <div className="flex items-center justify-between text-[10px] text-slate-500">
            <span>5 ثانية</span>
            <span>60 ثانية</span>
          </div>
        </div>
      </SectionCard>

      {/* System info section */}
      <SectionCard title="معلومات النظام" icon={Info}>
        <div className="grid grid-cols-2 gap-3">
          <div className="p-3 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <p className="text-[10px] text-slate-500">إصدار النظام</p>
            <p className="text-sm text-white font-mono mt-1" dir="ltr">v4.0</p>
          </div>
          <div className="p-3 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <p className="text-[10px] text-slate-500">نوع الواجهة</p>
            <p className="text-sm text-white mt-1">لوحة تحكم ويب</p>
          </div>
          <div className="p-3 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <p className="text-[10px] text-slate-500">إصدار API</p>
            <p className="text-sm text-white font-mono mt-1" dir="ltr">v1</p>
          </div>
          <div className="p-3 rounded-lg bg-slate-900/40 border border-slate-800/40">
            <p className="text-[10px] text-slate-500">حالة الاتصال</p>
            <p className="text-sm text-emerald-400 mt-1 flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              متصل
            </p>
          </div>
        </div>
      </SectionCard>

      {/* Danger zone */}
      <SectionCard title="منطقة الخطر" icon={AlertCircle}>
        <div className="flex items-center justify-between gap-4 p-4 rounded-lg bg-red-500/5 border border-red-500/20">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2 rounded-lg bg-red-500/15 shrink-0">
              <Trash2 className="w-4 h-4 text-red-400" />
            </div>
            <div className="min-w-0">
              <p className="text-sm font-medium text-red-300">مسح البيانات المحلية</p>
              <p className="text-xs text-red-400/70 mt-0.5">
                يحذف الإعدادات المحفوظة وجلسة تسجيل الدخول. ستحتاج لإعادة تسجيل الدخول.
              </p>
            </div>
          </div>
          <Button
            variant="destructive"
            size="sm"
            onClick={handleClearLocalData}
            disabled={clearingLogs}
            className="shrink-0"
          >
            {clearingLogs ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Trash2 className="w-3.5 h-3.5" />
            )}
            <span className="hidden sm:inline">مسح</span>
          </Button>
        </div>
      </SectionCard>

      {/* Footer info */}
      <div className="text-center text-xs text-slate-500 py-4">
        <p>أبو زهرة — لوحة التحكم الإدارية</p>
        <p className="mt-1" dir="ltr">© 2026 • Built with Next.js 16</p>
      </div>
    </div>
  )
}
