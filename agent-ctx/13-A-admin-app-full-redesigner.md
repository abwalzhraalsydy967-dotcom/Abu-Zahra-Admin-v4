# Task 13-A — Admin App Full Redesigner

## Task
إعادة تصميم تطبيق الإدارة بـ Navigation Drawer مطابق للوحة التحكم الويب (sidebar بـ 9 عناصر).

## Status: COMPLETED ✅

## What was done

### Files created (new)
- `Admin-App/app/src/main/res/menu/drawer_menu.xml` — 9 nav items + logout group
- `Admin-App/app/src/main/res/layout/nav_drawer_header.xml` — drawer header (user info + badges)
- `Admin-App/app/src/main/res/layout/view_overview.xml` — Overview page (stats + recent activity)
- `Admin-App/app/src/main/res/layout/item_recent_device.xml` — recent-device row
- `Admin-App/app/src/main/res/layout/item_recent_event.xml` — recent-event row
- `Admin-App/app/src/main/res/color/drawer_item_tint.xml` — emerald/gray selector
- `Admin-App/app/src/main/res/drawable/ic_terminal.xml`, `ic_list_checks.xml`, `ic_radio.xml`, `ic_dashboard.xml`, `ic_activity.xml`, `ic_people.xml`, `ic_menu.xml`, `ic_check.xml`, `ic_block.xml`, `ic_refresh.xml`
- `Admin-App/app/src/main/res/drawable/bg_drawer_badge.xml`, `bg_role_chip.xml`, `bg_drawer_item.xml`, `bg_status_summary_online.xml`, `bg_status_summary_offline.xml`

### Files modified (rewritten)
- `Admin-App/app/src/main/res/layout/activity_dashboard.xml` — replaced BottomNavigationView with `DrawerLayout` + `NavigationView` (right-side RTL) + `FrameLayout` hosting `view_overview` (default) and the legacy devices list
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/dashboard/DashboardActivity.kt` — implements `NavigationView.OnNavigationItemSelectedListener`; routes drawer selections (overview/devices in-place, others launch activities); inflates recent-device & recent-event rows on the overview; manages drawer header (user info + badges); smart back-press handling
- `Admin-App/app/src/main/java/com/abuzahra/admin/ui/dashboard/DashboardViewModel.kt` — added `_events` and `_users` LiveData + `loadEvents()` / `loadUsers()` + helper snapshot functions
- `Admin-App/app/src/main/res/values/strings.xml` — added 23 new strings (nav_* labels, overview texts, role labels, refresh, copied_code, etc.)

### Drawer items (9 + logout) — matches web sidebar.tsx navItems exactly
| # | ID | Arabic label | Icon |
|---|----|--------------|------|
| 1 | nav_overview | لوحة المعلومات | ic_dashboard |
| 2 | nav_devices | الأجهزة | ic_phone |
| 3 | nav_commands | الأوامر | ic_terminal |
| 4 | nav_results | النتائج | ic_list_checks |
| 5 | nav_streaming | البث | ic_radio |
| 6 | nav_files | الملفات | ic_folder |
| 7 | nav_events | الأحداث | ic_activity |
| 8 | nav_users | المستخدمين | ic_people |
| 9 | nav_settings | الإعدادات | ic_settings |
| + | nav_logout | تسجيل الخروج | ic_logout |

### Routing
- **nav_overview** → swap to `overviewRoot` view in-place
- **nav_devices** → swap to `devicesRoot` view in-place (search + chips + RecyclerView)
- **nav_commands / nav_results / nav_streaming** → `pickDeviceAndOpen { ... }` device-picker dialog → launches DeviceDetailActivity / StreamingActivity
- **nav_files / nav_events / nav_users / nav_settings** → `startActivity(...)` directly
- **nav_logout** → logout confirmation dialog

### Overview view (matches web `OverviewView`)
- Header: title + refresh button (ic_refresh)
- 2×2 stat cards (MaterialCardView with colored strokes):
  - الأجهزة المتصلة (online / total) — green
  - إجمالي الأوامر — emerald
  - الأحداث — amber
  - إجمالي المستخدمين — cyan
- Quick actions: "كود الربط" (copies `prefs.permanentCode`) + "ربط بوت Telegram" (hint + open users)
- "أحدث الأجهزة" card — last 5 devices, tap → DeviceDetailActivity
- "آخر الأحداث" card — last 5 events
- "توزيع حالة الاتصال" card — online / offline summary
- SwipeRefreshLayout + NestedScrollView

### Drawer header (matches web sidebar footer)
- Shield logo + "أبو زهرة" / "لوحة التحكم الإدارية"
- Avatar (first letter of username) + green status dot
- Username + email + role chip ("مسؤول" for admin only)
- Badge row: "X متصل" + "Y حدث" (auto-updated from stats LiveData)

## Verification
- All 22 XML files parse via ElementTree ✓
- DashboardActivity.kt braces balanced (103 open / 103 close) ✓
- DashboardViewModel.kt braces balanced (36 open / 36 close) ✓
- All `binding.overviewRoot.<field>` (17 fields) exist in view_overview.xml IDs ✓
- All `binding.<field>` (17 fields) exist in activity_dashboard.xml IDs ✓
- No orphaned view ID references
- `androidx.gridlayout:gridlayout:1.0.0` already in build.gradle ✓
- `material:1.11.0` provides NavigationView + MaterialToolbar ✓
- No changes to API layer (ApiClient, ApiService, Preferences) — used existing interfaces only
- Existing activities (DeviceDetail, Streaming, Files, Users, Logs, Settings) untouched — drawer just launches them
- AndroidManifest.xml unchanged (DashboardActivity stays `exported=false`; LoginActivity stays launcher)
- bun run lint on web project: no new errors introduced

## Notes for next agent
- The drawer uses `android:layoutDirection="rtl"` on the DrawerLayout root, which forces RTL for the entire dashboard regardless of device locale. This is intentional for an Arabic app — the drawer slides from the physical right and toolbar items appear RTL.
- `GravityCompat.START` is used (not END) because in RTL, START = physical right.
- For nav_commands/results/streaming: a device picker dialog is shown first. If no devices exist, the user is bounced to the Devices view with a Snackbar hint.
- The dashboard refreshes data on `onResume()` (when returning from a child activity) — this is intentional so newly sent commands / received events show up immediately.
- The `menu_dashboard.xml` (action_settings, action_logout) is now unused — left in place for backward compat, but no longer inflated.
