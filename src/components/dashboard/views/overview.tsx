'use client'

import React from 'react'
import { motion } from 'framer-motion'
import {
  Smartphone,
  Wifi,
  Terminal,
  Activity,
  Clock,
  Battery,
  BatteryCharging,
  RefreshCw,
  TrendingUp,
  Server,
  Zap,
  Radio,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { type Device, type Event, type Stats } from '@/lib/api'
import { cn, timeAgo } from '@/lib/utils'

interface Props {
  devices: Device[]
  events: Event[]
  stats: Stats | null
  loadingDevices: boolean
  loadingStats: boolean
  loadingEvents: boolean
  onRefresh: () => void
  onGoToDevices: () => void
}

function formatUptime(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  if (h < 24) return `${h} ساعة`
  const d = Math.floor(h / 24)
  const rh = h % 24
  return `${d} يوم ${rh} ساعة`
}

/* ─── Mini sparkline bar chart ─── */
function MiniBars({ values, max, color }: { values: number[]; max: number; color: string }) {
  const safeMax = max || 1
  return (
    <div className="flex items-end gap-1 h-12">
      {values.map((v, i) => (
        <motion.div
          key={i}
          initial={{ height: 0 }}
          animate={{ height: `${Math.max((v / safeMax) * 100, 6)}%` }}
          transition={{ duration: 0.4, delay: i * 0.05 }}
          className={cn('flex-1 rounded-t', color)}
        />
      ))}
    </div>
  )
}

/* ─── Stat Card ─── */
function StatCard({
  label,
  value,
  icon: Icon,
  iconBg,
  iconColor,
  border,
  trend,
  delay,
}: {
  label: string
  value: React.ReactNode
  icon: typeof Smartphone
  iconBg: string
  iconColor: string
  border: string
  trend?: string
  delay: number
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, delay }}
    >
      <div
        className={cn(
          'bg-white/5 backdrop-blur-md border rounded-xl p-4 transition-all hover:bg-white/[0.07]',
          border
        )}
      >
        <div className="flex items-start justify-between mb-3">
          <div className={cn('p-2 rounded-lg', iconBg)}>
            <Icon className={cn('w-4 h-4', iconColor)} />
          </div>
          {trend && (
            <span className="flex items-center gap-1 text-[10px] text-emerald-400">
              <TrendingUp className="w-3 h-3" />
              {trend}
            </span>
          )}
        </div>
        <p className={cn('text-2xl font-bold', iconColor)}>{value}</p>
        <p className="text-slate-400 text-xs mt-1">{label}</p>
      </div>
    </motion.div>
  )
}

export default function OverviewView({
  devices,
  events,
  stats,
  loadingDevices,
  loadingStats,
  loadingEvents,
  onRefresh,
  onGoToDevices,
}: Props) {
  /* Online / offline counts */
  const onlineCount = devices.filter((d) => d.is_online).length
  const offlineCount = devices.length - onlineCount

  /* Battery distribution (last 8 devices) */
  const batteryBars = devices.slice(0, 8).map((d) => d.battery_level || 0)
  const maxBattery = 100

  /* Events distribution by level (last 12) */
  const eventLevels = ['info', 'success', 'warning', 'error', 'critical'] as const
  const eventCounts = eventLevels.map((lvl) => events.filter((e) => e.level === lvl).length)
  const maxEvents = Math.max(...eventCounts, 1)

  /* Recent events (latest 5) */
  const recentEvents = events.slice(0, 6)

  /* Recent devices (latest 4) */
  const recentDevices = [...devices]
    .sort((a, b) => new Date(b.last_seen).getTime() - new Date(a.last_seen).getTime())
    .slice(0, 4)

  const levelBadge: Record<string, string> = {
    info: 'bg-cyan-500/15 text-cyan-400 border-cyan-500/25',
    success: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/25',
    warning: 'bg-amber-500/15 text-amber-400 border-amber-500/25',
    error: 'bg-red-500/15 text-red-400 border-red-500/25',
    critical: 'bg-red-600/15 text-red-300 border-red-600/30',
  }

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-white">لوحة المعلومات</h1>
          <p className="text-sm text-slate-400 mt-1">
            نظرة شاملة على نشاط النظام والأجهزة
          </p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={onRefresh}
          disabled={loadingDevices || loadingStats}
          className="text-slate-400 hover:text-white hover:bg-slate-800"
        >
          <RefreshCw className={cn('w-4 h-4', (loadingDevices || loadingStats) && 'animate-spin')} />
          <span>تحديث</span>
        </Button>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {loadingStats ? (
          Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="h-28 rounded-xl bg-white/5 backdrop-blur-md border border-slate-800/60 animate-pulse"
            />
          ))
        ) : (
          <>
            <StatCard
              label="الأجهزة المتصلة"
              value={`${onlineCount} / ${devices.length}`}
              icon={Wifi}
              iconBg="bg-green-500/15"
              iconColor="text-green-400"
              border="border-green-500/20"
              trend={onlineCount > 0 ? 'مباشر' : undefined}
              delay={0}
            />
            <StatCard
              label="إجمالي الأوامر"
              value={stats?.total_commands ?? 0}
              icon={Terminal}
              iconBg="bg-emerald-500/15"
              iconColor="text-emerald-400"
              border="border-emerald-500/20"
              delay={0.05}
            />
            <StatCard
              label="الأحداث"
              value={stats?.total_events ?? 0}
              icon={Activity}
              iconBg="bg-amber-500/15"
              iconColor="text-amber-400"
              border="border-amber-500/20"
              delay={0.1}
            />
            <StatCard
              label="وقت التشغيل"
              value={stats?.uptime ? formatUptime(stats.uptime) : '0 ساعة'}
              icon={Clock}
              iconBg="bg-cyan-500/15"
              iconColor="text-cyan-400"
              border="border-cyan-500/20"
              delay={0.15}
            />
          </>
        )}
      </div>

      {/* System status row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-slate-400">حالة الخادم</span>
            <Server className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="text-sm text-emerald-300 font-medium">يعمل بشكل طبيعي</span>
          </div>
          <p className="text-[10px] text-slate-500 mt-2" dir="ltr">
            {stats?.version ? `v${stats.version}` : 'v4.0'} • Firebase: {stats?.firebase ? 'متصل' : 'غير متصل'}
          </p>
        </div>

        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-slate-400">إجمالي المستخدمين</span>
            <Terminal className="w-4 h-4 text-cyan-400" />
          </div>
          <p className="text-xl font-bold text-cyan-300">
            {stats?.total_users ?? 0}
          </p>
          <p className="text-[10px] text-slate-500 mt-2">حسابات مسجّلة في النظام</p>
        </div>

        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-4">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-slate-400">البث النشط</span>
            <Radio className="w-4 h-4 text-pink-400" />
          </div>
          <p className="text-xl font-bold text-pink-300">
            {devices.filter((d) => d.is_online).length}
          </p>
          <p className="text-[10px] text-slate-500 mt-2">جهاز قابل للبث المباشر</p>
        </div>
      </div>

      {/* Two-column layout: charts + activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Battery distribution chart */}
        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Zap className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-semibold text-white">مستوى بطارية الأجهزة</h3>
            </div>
            <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-[10px]">
              آخر {batteryBars.length} جهاز
            </Badge>
          </div>
          {batteryBars.length > 0 ? (
            <MiniBars values={batteryBars} max={maxBattery} color="bg-gradient-to-t from-emerald-600 to-emerald-400" />
          ) : (
            <div className="h-12 flex items-center justify-center text-xs text-slate-500">
              لا توجد أجهزة لعرضها
            </div>
          )}
          <div className="flex items-center justify-between mt-3 text-[10px] text-slate-500">
            <span>0%</span>
            <span>50%</span>
            <span>100%</span>
          </div>
        </div>

        {/* Events distribution chart */}
        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-semibold text-white">توزيع الأحداث حسب النوع</h3>
            </div>
            <Badge className="bg-slate-700/50 text-slate-300 border-slate-600/30 text-[10px]">
              {events.length} حدث
            </Badge>
          </div>
          {events.length > 0 ? (
            <MiniBars values={eventCounts} max={maxEvents} color="bg-gradient-to-t from-amber-600 to-amber-400" />
          ) : (
            <div className="h-12 flex items-center justify-center text-xs text-slate-500">
              لا توجد أحداث لعرضها
            </div>
          )}
          <div className="flex items-center justify-between mt-3 text-[10px] text-slate-500 flex-wrap gap-1">
            {eventLevels.map((lvl, i) => (
              <span key={lvl}>
                {lvl === 'info' ? 'معلومة' :
                  lvl === 'success' ? 'نجاح' :
                    lvl === 'warning' ? 'تحذير' :
                      lvl === 'error' ? 'خطأ' : 'حرج'}: {eventCounts[i]}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* Recent activity + Recent devices */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Recent events */}
        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-semibold text-white">آخر الأحداث</h3>
            </div>
          </div>
          {loadingEvents ? (
            <div className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-12 rounded-lg bg-slate-800/40 animate-pulse" />
              ))}
            </div>
          ) : recentEvents.length === 0 ? (
            <div className="text-center py-8 text-slate-500 text-sm">
              <Activity className="w-8 h-8 mx-auto mb-2 text-slate-700" />
              لا توجد أحداث بعد
            </div>
          ) : (
            <div className="space-y-2 max-h-72 overflow-y-auto" style={{ scrollbarWidth: 'thin' }}>
              {recentEvents.map((event) => (
                <div
                  key={event.id}
                  className="flex items-start gap-2 p-2 rounded-lg hover:bg-slate-800/40 transition-colors"
                >
                  <Badge className={cn('text-[9px] shrink-0 mt-0.5', levelBadge[event.level] || levelBadge.info)}>
                    {event.level === 'info' ? 'معلومة' :
                      event.level === 'success' ? 'نجاح' :
                        event.level === 'warning' ? 'تحذير' :
                          event.level === 'error' ? 'خطأ' : 'حرج'}
                  </Badge>
                  <div className="min-w-0 flex-1">
                    <p className="text-xs text-slate-200 truncate">{event.message}</p>
                    <p className="text-[10px] text-slate-500 mt-0.5">
                      {timeAgo(event.timestamp)} • {event.type}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Recent devices */}
        <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-semibold text-white">أحدث الأجهزة</h3>
            </div>
            <Button
              variant="ghost"
              size="xs"
              onClick={onGoToDevices}
              className="text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10"
            >
              عرض الكل
            </Button>
          </div>
          {loadingDevices ? (
            <div className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-12 rounded-lg bg-slate-800/40 animate-pulse" />
              ))}
            </div>
          ) : recentDevices.length === 0 ? (
            <div className="text-center py-8 text-slate-500 text-sm">
              <Smartphone className="w-8 h-8 mx-auto mb-2 text-slate-700" />
              لا توجد أجهزة مسجّلة
            </div>
          ) : (
            <div className="space-y-2">
              {recentDevices.map((device) => (
                <div
                  key={device.id}
                  className="flex items-center gap-2 p-2 rounded-lg hover:bg-slate-800/40 transition-colors"
                >
                  <div className={cn(
                    'p-1.5 rounded-lg shrink-0',
                    device.is_online ? 'bg-green-500/15' : 'bg-slate-700/40'
                  )}>
                    <Smartphone className={cn(
                      'w-3.5 h-3.5',
                      device.is_online ? 'text-green-400' : 'text-slate-500'
                    )} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-xs text-white font-medium truncate">{device.name}</p>
                    <p className="text-[10px] text-slate-500 truncate">
                      {device.brand} • {device.model}
                    </p>
                  </div>
                  <div className="flex items-center gap-1 text-[10px] text-slate-400 shrink-0">
                    {device.is_charging ? (
                      <BatteryCharging className="w-3 h-3 text-emerald-400" />
                    ) : (
                      <Battery className="w-3 h-3" />
                    )}
                    <span>{device.battery_level}%</span>
                  </div>
                  <span className={cn(
                    'text-[10px] shrink-0',
                    device.is_online ? 'text-green-400' : 'text-slate-500'
                  )}>
                    {timeAgo(device.last_seen)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Online vs offline summary */}
      <div className="bg-white/5 backdrop-blur-md border border-slate-800/60 rounded-xl p-5">
        <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
          <Wifi className="w-4 h-4 text-emerald-400" />
          توزيع حالة الاتصال
        </h3>
        <div className="grid grid-cols-2 gap-4">
          <div className="rounded-lg bg-green-500/10 border border-green-500/20 p-4 text-center">
            <p className="text-2xl font-bold text-green-400">{onlineCount}</p>
            <p className="text-xs text-green-300/80 mt-1">جهاز متصل</p>
          </div>
          <div className="rounded-lg bg-red-500/10 border border-red-500/20 p-4 text-center">
            <p className="text-2xl font-bold text-red-400">{offlineCount}</p>
            <p className="text-xs text-red-300/80 mt-1">جهاز غير متصل</p>
          </div>
        </div>
      </div>
    </div>
  )
}
