'use client'

import React from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Shield,
  LayoutDashboard,
  Smartphone,
  Terminal,
  ListChecks,
  Radio,
  FolderOpen,
  Activity,
  Users,
  Settings,
  LogOut,
  X,
  Link2,
  MessageCircle,
  Loader2,
  ChevronLeft,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn, addLog } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'

export type DashboardView =
  | 'overview'
  | 'devices'
  | 'commands'
  | 'results'
  | 'streaming'
  | 'files'
  | 'events'
  | 'users'
  | 'settings'

interface NavItem {
  id: DashboardView
  label: string
  icon: typeof LayoutDashboard
  adminOnly?: boolean
  /** Optional badge count shown to the left of the label */
  badge?: number
  /** Subtitle shown under the label (desktop only) */
  hint?: string
}

interface Props {
  activeView: DashboardView
  onSelect: (view: DashboardView) => void
  /** Mobile drawer open state */
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Stats / counters for badges */
  onlineDevices?: number
  totalDevices?: number
  eventsCount?: number
  usersCount?: number
  /** Callbacks for actions available from the sidebar footer */
  onGenerateLinkCode: () => void
  onGenerateTgLink: () => void
  linkCodeLoading?: boolean
  tgLinkLoading?: boolean
}

export default function Sidebar({
  activeView,
  onSelect,
  open,
  onOpenChange,
  onlineDevices = 0,
  totalDevices = 0,
  eventsCount = 0,
  usersCount = 0,
  onGenerateLinkCode,
  onGenerateTgLink,
  linkCodeLoading = false,
  tgLinkLoading = false,
}: Props) {
  const { user, logout } = useAuth()

  const navItems: NavItem[] = [
    { id: 'overview', label: 'لوحة المعلومات', icon: LayoutDashboard, hint: 'نظرة عامة' },
    { id: 'devices', label: 'الأجهزة', icon: Smartphone, badge: totalDevices, hint: `${onlineDevices} متصل` },
    { id: 'commands', label: 'الأوامر', icon: Terminal, hint: 'مكتبة الأوامر' },
    { id: 'results', label: 'النتائج', icon: ListChecks, hint: 'نتائج الأوامر' },
    { id: 'streaming', label: 'البث', icon: Radio, hint: 'بث مباشر' },
    { id: 'files', label: 'الملفات', icon: FolderOpen, hint: 'الملفات المرفوعة' },
    { id: 'events', label: 'الأحداث', icon: Activity, badge: eventsCount, hint: 'سجل الأحداث' },
    { id: 'users', label: 'المستخدمين', icon: Users, badge: usersCount, adminOnly: true, hint: 'إدارة الحسابات' },
    { id: 'settings', label: 'الإعدادات', icon: Settings, hint: 'إعدادات النظام' },
  ]

  const visibleItems = navItems.filter((item) => !item.adminOnly || user?.role === 'admin')

  const handleClick = (view: DashboardView) => {
    onSelect(view)
    addLog('info', `التبديل إلى: ${navItems.find((i) => i.id === view)?.label || view}`)
    onOpenChange(false)
  }

  const handleLogout = () => {
    addLog('info', 'تسجيل الخروج', `المستخدم: ${user?.username}`)
    logout()
  }

  /* ─── Sidebar inner content ─── */
  const SidebarContent = (
    <div className="flex h-full flex-col bg-slate-950/95 backdrop-blur-xl border-l border-slate-800/60">
      {/* Brand */}
      <div className="flex items-center justify-between gap-2 px-4 h-16 border-b border-slate-800/60 shrink-0">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="flex items-center justify-center w-9 h-9 rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-600 shadow-lg shadow-emerald-500/20 shrink-0">
            <Shield className="w-5 h-5 text-white" />
          </div>
          <div className="min-w-0 hidden md:block">
            <p className="text-sm font-bold text-white truncate">أبو زهرة</p>
            <p className="text-[10px] text-slate-500 truncate">لوحة التحكم الإدارية</p>
          </div>
        </div>
        {/* Mobile close button */}
        <button
          type="button"
          onClick={() => onOpenChange(false)}
          className="md:hidden p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
          aria-label="إغلاق القائمة"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* Nav items */}
      <nav className="flex-1 overflow-y-auto py-3 px-2 space-y-1" style={{ scrollbarWidth: 'thin' }}>
        {visibleItems.map((item) => {
          const isActive = activeView === item.id
          const Icon = item.icon
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => handleClick(item.id)}
              className={cn(
                'group relative w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-right transition-all duration-200',
                isActive
                  ? 'bg-emerald-500/15 text-emerald-300'
                  : 'text-slate-400 hover:bg-slate-800/50 hover:text-slate-200'
              )}
              title={item.label}
            >
              {/* Active accent bar (right side, RTL) */}
              {isActive && (
                <motion.span
                  layoutId="sidebar-active-bar"
                  className="absolute right-0 top-1.5 bottom-1.5 w-1 rounded-full bg-emerald-400"
                  transition={{ type: 'spring', stiffness: 380, damping: 30 }}
                />
              )}
              <span
                className={cn(
                  'flex items-center justify-center w-8 h-8 rounded-lg shrink-0 transition-colors',
                  isActive
                    ? 'bg-emerald-500/20 text-emerald-300'
                    : 'bg-slate-800/50 text-slate-400 group-hover:text-slate-200'
                )}
              >
                <Icon className="w-4 h-4" />
              </span>

              {/* Label + hint (desktop only) */}
              <span className="flex-1 min-w-0 hidden md:block">
                <span className="block text-sm font-medium leading-tight truncate">
                  {item.label}
                </span>
                {item.hint && (
                  <span className="block text-[10px] text-slate-500 truncate mt-0.5">
                    {item.hint}
                  </span>
                )}
              </span>

              {/* Label (mobile only, no hint) */}
              <span className="md:hidden text-xs font-medium">
                {item.label}
              </span>

              {/* Badge */}
              {item.badge !== undefined && item.badge > 0 && (
                <Badge
                  className={cn(
                    'shrink-0 text-[10px] h-5 min-w-5 px-1.5',
                    isActive
                      ? 'bg-emerald-500/25 text-emerald-200 border-emerald-500/30'
                      : 'bg-slate-700/60 text-slate-300 border-slate-600/40'
                  )}
                >
                  {item.badge}
                </Badge>
              )}
            </button>
          )
        })}
      </nav>

      {/* Quick actions */}
      <div className="px-2 pb-2 hidden md:block">
        <div className="border-t border-slate-800/60 pt-2 space-y-1">
          <button
            type="button"
            onClick={onGenerateLinkCode}
            disabled={linkCodeLoading}
            className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-slate-400 hover:bg-slate-800/50 hover:text-emerald-300 transition-colors disabled:opacity-50"
          >
            {linkCodeLoading ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <Link2 className="w-3.5 h-3.5" />
            )}
            <span className="truncate">كود الربط الخاص بي</span>
          </button>
          <button
            type="button"
            onClick={onGenerateTgLink}
            disabled={tgLinkLoading}
            className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-slate-400 hover:bg-slate-800/50 hover:text-emerald-300 transition-colors disabled:opacity-50"
          >
            {tgLinkLoading ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <MessageCircle className="w-3.5 h-3.5" />
            )}
            <span className="truncate">ربط بوت Telegram</span>
          </button>
        </div>
      </div>

      {/* Footer: user info + logout */}
      <div className="border-t border-slate-800/60 p-2 shrink-0">
        <div className="flex items-center gap-3 p-2 rounded-lg bg-slate-900/60">
          <div className="relative shrink-0">
            <div className="w-9 h-9 rounded-full bg-gradient-to-br from-emerald-500 to-emerald-600 flex items-center justify-center text-white text-sm font-bold">
              {user?.username?.charAt(0)?.toUpperCase() || 'م'}
            </div>
            <span
              className={cn(
                'absolute -bottom-0.5 -left-0.5 w-3 h-3 rounded-full border-2 border-slate-950',
                user ? 'bg-emerald-400' : 'bg-slate-500'
              )}
            />
          </div>
          <div className="min-w-0 flex-1 hidden md:block">
            <p className="text-sm text-white font-medium truncate">
              {user?.username || 'مستخدم'}
            </p>
            <p className="text-[10px] text-slate-500 truncate" dir="ltr">
              {user?.email || ''}
            </p>
          </div>
          {user?.role === 'admin' && (
            <Badge className="hidden md:inline-flex bg-emerald-500/15 text-emerald-400 border-emerald-500/25 text-[10px] shrink-0">
              مسؤول
            </Badge>
          )}
          <button
            type="button"
            onClick={handleLogout}
            className="shrink-0 p-2 rounded-md text-slate-400 hover:bg-red-500/15 hover:text-red-400 transition-colors"
            title="تسجيل الخروج"
            aria-label="تسجيل الخروج"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  )

  return (
    <>
      {/* Desktop sidebar (fixed, RTL right) */}
      <aside className="hidden md:flex md:w-64 md:shrink-0 md:sticky md:top-0 md:h-screen">
        {SidebarContent}
      </aside>

      {/* Mobile drawer */}
      <AnimatePresence>
        {open && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="md:hidden fixed inset-0 z-50 bg-black/60 backdrop-blur-sm"
              onClick={() => onOpenChange(false)}
            />
            <motion.aside
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 380, damping: 38 }}
              className="md:hidden fixed top-0 right-0 bottom-0 z-50 w-72 max-w-[85vw]"
            >
              {SidebarContent}
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </>
  )
}

/** Mobile top-bar hamburger trigger (rendered by the dashboard header) */
export function MobileSidebarTrigger({
  onClick,
}: {
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="md:hidden inline-flex items-center justify-center w-9 h-9 rounded-lg bg-slate-900/80 border border-slate-800/60 text-slate-300 hover:text-white hover:bg-slate-800 transition-colors"
      aria-label="فتح القائمة"
    >
      <ChevronLeft className="w-5 h-5" />
    </button>
  )
}
