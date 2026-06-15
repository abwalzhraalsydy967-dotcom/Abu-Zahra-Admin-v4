"""
Dashboard HTML template - Complete single-page admin dashboard for Abu-Zahra.
All HTML/CSS/JS embedded in a Python string.
"""

DASHBOARD_HTML = r'''
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>أبو زهرا - لوحة التحكم</title>
<style>
:root {
  --bg: #1a1a2e;
  --bg2: #16213e;
  --card: #0f3460;
  --card-hover: #153a6e;
  --accent: #e94560;
  --accent-hover: #ff6b81;
  --text: #eee;
  --text-dim: #8892b0;
  --border: #233554;
  --success: #00b894;
  --warning: #fdcb6e;
  --danger: #d63031;
  --info: #74b9ff;
  --sidebar-w: 260px;
  --topbar-h: 60px;
}
* { margin:0; padding:0; box-sizing:border-box; }
body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; background: var(--bg); color: var(--text); direction: rtl; }
a { color: var(--accent); text-decoration: none; }
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: var(--bg2); }
::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }
input, select, textarea, button { font-family: inherit; }

/* ── LOGIN ── */
#loginPage { display:flex; align-items:center; justify-content:center; min-height:100vh; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%); }
.login-box { background: var(--card); border-radius: 16px; padding: 40px; width: 420px; max-width: 95vw; box-shadow: 0 20px 60px rgba(0,0,0,0.5); }
.login-box h1 { text-align:center; margin-bottom: 8px; font-size: 28px; color: var(--accent); }
.login-box p.sub { text-align:center; color: var(--text-dim); margin-bottom: 30px; font-size: 14px; }
.form-group { margin-bottom: 18px; }
.form-group label { display:block; margin-bottom:6px; font-size:14px; color: var(--text-dim); }
.form-group input { width:100%; padding:12px 14px; border-radius:8px; border:1px solid var(--border); background: var(--bg2); color: var(--text); font-size:15px; outline:none; transition: border-color .2s; }
.form-group input:focus { border-color: var(--accent); }
.btn { padding: 12px 24px; border: none; border-radius: 8px; cursor: pointer; font-size: 15px; font-weight: 600; transition: all .2s; display: inline-flex; align-items: center; gap: 8px; }
.btn-primary { background: var(--accent); color: #fff; }
.btn-primary:hover { background: var(--accent-hover); transform: translateY(-1px); }
.btn-secondary { background: var(--card); color: var(--text); border: 1px solid var(--border); }
.btn-secondary:hover { background: var(--card-hover); }
.btn-success { background: var(--success); color: #fff; }
.btn-danger { background: var(--danger); color: #fff; }
.btn-sm { padding: 6px 14px; font-size: 13px; }
.btn-full { width: 100%; justify-content: center; }
.login-error { color: var(--danger); text-align:center; margin-bottom:12px; font-size:14px; min-height:20px; }

/* ── LAYOUT ── */
#appPage { display:none; }
.sidebar { position:fixed; right:0; top:0; width:var(--sidebar-w); height:100vh; background: var(--bg2); border-left:1px solid var(--border); z-index:100; overflow-y:auto; transition: transform .3s; }
.sidebar-header { padding: 20px; text-align:center; border-bottom: 1px solid var(--border); }
.sidebar-header h2 { color: var(--accent); font-size: 20px; margin-bottom: 4px; }
.sidebar-header .role-badge { font-size: 11px; background: var(--card); color: var(--text-dim); padding: 2px 10px; border-radius: 10px; display: inline-block; }
.sidebar-nav { padding: 12px 0; }
.nav-item { display:flex; align-items:center; gap:12px; padding: 12px 24px; cursor:pointer; transition: all .2s; color: var(--text-dim); font-size: 14px; border-right: 3px solid transparent; }
.nav-item:hover { background: rgba(233,69,96,0.08); color: var(--text); }
.nav-item.active { background: rgba(233,69,96,0.12); color: var(--accent); border-right-color: var(--accent); }
.nav-item .nav-icon { font-size: 18px; width: 24px; text-align: center; }
.sidebar-footer { padding: 16px 24px; border-top: 1px solid var(--border); position: absolute; bottom: 0; width: 100%; }
.topbar { position:fixed; top:0; right:var(--sidebar-w); left:0; height:var(--topbar-h); background: var(--bg2); border-bottom:1px solid var(--border); display:flex; align-items:center; justify-content:space-between; padding:0 24px; z-index:99; }
.topbar-right { display:flex; align-items:center; gap:16px; }
.topbar-left { display:flex; align-items:center; gap:16px; }
.hamburger { display:none; background:none; border:none; color:var(--text); font-size:24px; cursor:pointer; }
.ws-status { font-size:12px; display:flex; align-items:center; gap:6px; }
.ws-dot { width:8px; height:8px; border-radius:50%; background: var(--danger); }
.ws-dot.connected { background: var(--success); }
.main-content { margin-top: var(--topbar-h); margin-right: var(--sidebar-w); padding: 24px; min-height: calc(100vh - var(--topbar-h)); }
.page-section { display:none; }
.page-section.active { display:block; }
.overlay { display:none; position:fixed; inset:0; background:rgba(0,0,0,0.5); z-index:200; }

/* ── CARDS & GRID ── */
.stats-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap:16px; margin-bottom:24px; }
.stat-card { background: var(--card); border-radius:12px; padding:20px; position:relative; overflow:hidden; }
.stat-card .stat-icon { font-size:28px; margin-bottom:8px; }
.stat-card .stat-value { font-size:28px; font-weight:700; }
.stat-card .stat-label { font-size:13px; color: var(--text-dim); margin-top:4px; }
.stat-card::after { content:''; position:absolute; top:0; left:0; width:4px; height:100%; background: var(--accent); }

.cards-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap:16px; }
.card { background: var(--card); border-radius:12px; padding:20px; transition: transform .2s, box-shadow .2s; cursor:pointer; }
.card:hover { transform: translateY(-2px); box-shadow: 0 8px 25px rgba(0,0,0,0.3); }
.card-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:12px; }
.card-title { font-size:16px; font-weight:600; }
.card-subtitle { font-size:12px; color: var(--text-dim); }
.status-dot { width:10px; height:10px; border-radius:50%; }
.status-dot.online { background: var(--success); box-shadow: 0 0 8px var(--success); }
.status-dot.offline { background: var(--danger); }

/* ── TABLE ── */
.table-wrap { background: var(--card); border-radius:12px; overflow:hidden; }
table { width:100%; border-collapse:collapse; }
th, td { padding: 12px 16px; text-align:right; font-size: 13px; }
th { background: var(--bg2); color: var(--text-dim); font-weight: 600; }
td { border-top: 1px solid var(--border); }
tr:hover td { background: rgba(233,69,96,0.04); }

/* ── FILTER BAR ── */
.filter-bar { display:flex; flex-wrap:wrap; gap:12px; align-items:center; margin-bottom:20px; }
.filter-bar input, .filter-bar select { padding:10px 14px; border-radius:8px; border:1px solid var(--border); background:var(--bg2); color:var(--text); font-size:14px; outline:none; }
.filter-bar input:focus, .filter-bar select:focus { border-color: var(--accent); }
.filter-bar input[type="search"] { flex:1; min-width:200px; }

/* ── TABS ── */
.tabs { display:flex; gap:4px; margin-bottom:20px; flex-wrap:wrap; background:var(--bg2); border-radius:10px; padding:4px; }
.tab { padding:8px 18px; border-radius:8px; cursor:pointer; font-size:13px; color:var(--text-dim); transition: all .2s; white-space:nowrap; }
.tab:hover { color: var(--text); }
.tab.active { background: var(--accent); color:#fff; }

/* ── COMMAND GRID ── */
.cmd-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap:10px; }
.cmd-btn { background:var(--bg2); border:1px solid var(--border); border-radius:10px; padding:14px 10px; cursor:pointer; text-align:center; transition: all .2s; color: var(--text); }
.cmd-btn:hover { border-color: var(--accent); background: rgba(233,69,96,0.08); transform: translateY(-1px); }
.cmd-btn .cmd-icon { font-size:24px; display:block; margin-bottom:6px; }
.cmd-btn .cmd-name { font-size:12px; color: var(--text-dim); }

/* ── DEVICE SELECT ── */
.device-select-wrap { margin-bottom:20px; }
.device-select-wrap label { display:block; margin-bottom:6px; font-size:14px; color:var(--text-dim); }
.device-select-wrap select { width:100%; max-width:400px; padding:10px 14px; border-radius:8px; border:1px solid var(--border); background:var(--bg2); color:var(--text); font-size:14px; }

/* ── STREAM VIEWER ── */
.stream-viewer { background:#000; border-radius:12px; min-height:400px; display:flex; align-items:center; justify-content:center; position:relative; overflow:hidden; margin-bottom:16px; }
.stream-viewer img { max-width:100%; max-height:70vh; }
.stream-viewer .no-stream { color:var(--text-dim); font-size:16px; }
.stream-controls { display:flex; gap:12px; flex-wrap:wrap; align-items:center; }

/* ── MODAL ── */
.modal { display:none; position:fixed; inset:0; z-index:300; align-items:center; justify-content:center; }
.modal.show { display:flex; }
.modal-backdrop { position:absolute; inset:0; background:rgba(0,0,0,0.6); }
.modal-content { position:relative; background: var(--card); border-radius:16px; padding:28px; width:90%; max-width:600px; max-height:85vh; overflow-y:auto; z-index:1; }
.modal-close { position:absolute; top:12px; left:12px; background:none; border:none; color:var(--text-dim); font-size:22px; cursor:pointer; }
.modal-close:hover { color: var(--accent); }
.modal-title { font-size:20px; font-weight:700; margin-bottom:16px; }
.modal-body pre { background:var(--bg); border-radius:8px; padding:16px; overflow-x:auto; font-size:13px; max-height:400px; overflow-y:auto; white-space: pre-wrap; word-break: break-all; direction: ltr; text-align: left; }

/* ── TOAST ── */
.toast-container { position:fixed; top:20px; left:20px; z-index:9999; display:flex; flex-direction:column; gap:8px; }
.toast { padding:14px 20px; border-radius:10px; color:#fff; font-size:14px; min-width:280px; animation: toastIn .3s ease; box-shadow:0 8px 24px rgba(0,0,0,0.3); display:flex; align-items:center; gap:10px; }
.toast.success { background: var(--success); }
.toast.error { background: var(--danger); }
.toast.info { background: #3498db; }
.toast.warning { background: var(--warning); color:#333; }
@keyframes toastIn { from { opacity:0; transform:translateX(-40px); } to { opacity:1; transform:translateX(0); } }

/* ── DATA QUICK BTNS ── */
.data-quick-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap:12px; margin-bottom:24px; }
.data-btn { background:var(--card); border:2px solid var(--border); border-radius:12px; padding:20px 14px; cursor:pointer; text-align:center; transition: all .25s; color:var(--text); }
.data-btn:hover { border-color: var(--accent); transform: translateY(-3px); box-shadow: 0 6px 20px rgba(233,69,96,0.2); }
.data-btn .data-icon { font-size:32px; display:block; margin-bottom:8px; }
.data-btn .data-label { font-size:13px; font-weight:600; }

/* ── MONITOR BTNS ── */
.monitor-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap:12px; }
.monitor-card { background:var(--card); border-radius:12px; padding:20px; }
.monitor-card h4 { margin-bottom:12px; font-size:15px; }
.monitor-card .btn-row { display:flex; gap:8px; flex-wrap:wrap; }

/* ── FILE LIST ── */
.file-item { display:flex; align-items:center; justify-content:space-between; padding:12px 16px; background:var(--bg2); border-radius:8px; margin-bottom:8px; }
.file-info { display:flex; align-items:center; gap:12px; }
.file-icon { font-size:24px; }
.file-name { font-size:14px; font-weight:500; }
.file-meta { font-size:12px; color:var(--text-dim); }

/* ── SETTINGS FORM ── */
.settings-form { max-width:600px; }
.settings-form .form-group { margin-bottom:20px; }
.settings-form .form-group input, .settings-form .form-group select { width:100%; padding:10px 14px; border-radius:8px; border:1px solid var(--border); background:var(--bg2); color:var(--text); font-size:14px; }

/* ── USER TABLE ── */
.user-form { background:var(--card); border-radius:12px; padding:20px; margin-bottom:20px; max-width:500px; }
.user-form .form-group { margin-bottom:12px; }
.user-form .form-group input, .user-form .form-group select { width:100%; padding:10px 14px; border-radius:8px; border:1px solid var(--border); background:var(--bg2); color:var(--text); font-size:14px; }

/* ── DETAIL PANEL ── */
.detail-panel { position:fixed; left:0; top:var(--topbar-h); width:380px; height:calc(100vh - var(--topbar-h)); background:var(--bg2); border-right:1px solid var(--border); z-index:90; transform:translateX(-100%); transition:transform .3s; overflow-y:auto; padding:20px; }
.detail-panel.open { transform:translateX(0); }
.detail-panel .close-detail { position:absolute; top:12px; right:12px; background:none; border:none; color:var(--text-dim); font-size:20px; cursor:pointer; }
.detail-panel .close-detail:hover { color:var(--accent); }

/* ── RESPONSIVE ── */
@media (max-width: 768px) {
  .sidebar { transform: translateX(100%); }
  .sidebar.open { transform: translateX(0); }
  .topbar { right:0; }
  .main-content { margin-right:0; }
  .hamburger { display:block; }
  .detail-panel { width:100%; }
  .stats-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
}

/* ── SECTION TITLE ── */
.section-title { font-size:22px; font-weight:700; margin-bottom:20px; display:flex; align-items:center; gap:10px; }
.section-title .title-icon { font-size:26px; }
.empty-state { text-align:center; padding:60px 20px; color:var(--text-dim); }
.empty-state .empty-icon { font-size:48px; margin-bottom:12px; }
.pairing-code-display { text-align:center; padding:20px; }
.pairing-code-display .code { font-size:42px; font-weight:800; letter-spacing:8px; color:var(--accent); margin:16px 0; direction:ltr; }
</style>
</head>
<body>

<!-- ═══════ LOGIN PAGE ═══════ -->
<div id="loginPage">
  <div class="login-box">
    <h1>أبو زهرا</h1>
    <p class="sub">نظام إدارة الأجهزة</p>
    <div class="login-error" id="loginError"></div>
    <div class="form-group">
      <label>عنوان الخادم</label>
      <input type="text" id="loginServer" placeholder="https://example.com" value="">
    </div>
    <div class="form-group">
      <label>اسم المستخدم أو البريد</label>
      <input type="text" id="loginUser" placeholder="admin">
    </div>
    <div class="form-group">
      <label>كلمة المرور</label>
      <input type="password" id="loginPass" placeholder="••••••••">
    </div>
    <button class="btn btn-primary btn-full" id="loginBtn" onclick="doLogin()">تسجيل الدخول</button>
  </div>
</div>

<!-- ═══════ APP PAGE ═══════ -->
<div id="appPage">
  <!-- Sidebar -->
  <aside class="sidebar" id="sidebar">
    <div class="sidebar-header">
      <h2>أبو زهرا</h2>
      <span class="role-badge" id="sidebarRole">مسؤول</span>
    </div>
    <nav class="sidebar-nav">
      <div class="nav-item active" onclick="navigateTo('dashboard')" data-page="dashboard">
        <span class="nav-icon">📊</span><span>لوحة التحكم</span>
      </div>
      <div class="nav-item" onclick="navigateTo('devices')" data-page="devices">
        <span class="nav-icon">📱</span><span>الأجهزة</span>
      </div>
      <div class="nav-item" onclick="navigateTo('commands')" data-page="commands">
        <span class="nav-icon">⚔️</span><span>الأوامر</span>
      </div>
      <div class="nav-item" onclick="navigateTo('files')" data-page="files">
        <span class="nav-icon">📂</span><span>الملفات</span>
      </div>
      <div class="nav-item" onclick="navigateTo('data')" data-page="data">
        <span class="nav-icon">💾</span><span>البيانات</span>
      </div>
      <div class="nav-item" onclick="navigateTo('stream')" data-page="stream">
        <span class="nav-icon">📡</span><span>البث المباشر</span>
      </div>
      <div class="nav-item" onclick="navigateTo('monitor')" data-page="monitor">
        <span class="nav-icon">🔍</span><span>المراقبة</span>
      </div>
      <div class="nav-item" onclick="navigateTo('events')" data-page="events">
        <span class="nav-icon">📋</span><span>الأحداث</span>
      </div>
      <div class="nav-item" onclick="navigateTo('settings')" data-page="settings">
        <span class="nav-icon">⚙️</span><span>الإعدادات</span>
      </div>
      <div class="nav-item" onclick="navigateTo('users')" data-page="users" id="navUsers" style="display:none;">
        <span class="nav-icon">👥</span><span>المستخدمين</span>
      </div>
    </nav>
    <div class="sidebar-footer">
      <button class="btn btn-danger btn-sm btn-full" onclick="doLogout()">تسجيل الخروج</button>
    </div>
  </aside>

  <!-- Topbar -->
  <header class="topbar">
    <div class="topbar-right">
      <button class="hamburger" onclick="toggleSidebar()">☰</button>
      <span id="topbarTitle" style="font-weight:600;">لوحة التحكم</span>
    </div>
    <div class="topbar-left">
      <div class="ws-status">
        <div class="ws-dot" id="wsDot"></div>
        <span id="wsText">غير متصل</span>
      </div>
      <span id="topbarUser" style="font-size:13px; color:var(--text-dim);"></span>
    </div>
  </header>

  <!-- Main -->
  <main class="main-content">

    <!-- ── DASHBOARD ── -->
    <div class="page-section active" id="page-dashboard">
      <div class="section-title"><span class="title-icon">📊</span> لوحة التحكم</div>
      <div class="stats-grid" id="statsGrid"></div>
      <h3 style="margin-bottom:16px;">الأجهزة النشطة</h3>
      <div class="cards-grid" id="dashActiveDevices"></div>
      <h3 style="margin:24px 0 16px;">أحدث الأوامر</h3>
      <div class="table-wrap">
        <table><thead><tr><th>الجهاز</th><th>الأمر</th><th>الحالة</th><th>التاريخ</th></tr></thead>
        <tbody id="dashRecentCmds"></tbody></table>
      </div>
    </div>

    <!-- ── DEVICES ── -->
    <div class="page-section" id="page-devices">
      <div class="section-title"><span class="title-icon">📱</span> الأجهزة</div>
      <div class="filter-bar">
        <input type="search" id="deviceSearch" placeholder="بحث عن جهاز..." oninput="filterDevices()">
        <select id="deviceStatusFilter" onchange="filterDevices()">
          <option value="all">الكل</option>
          <option value="online">متصل</option>
          <option value="offline">غير متصل</option>
        </select>
        <button class="btn btn-primary btn-sm" onclick="generatePairingCode()">🔑 إنشاء كود ربط</button>
      </div>
      <div class="cards-grid" id="devicesGrid"></div>
    </div>

    <!-- ── COMMANDS ── -->
    <div class="page-section" id="page-commands">
      <div class="section-title"><span class="title-icon">⚔️</span> الأوامر</div>
      <div class="device-select-wrap">
        <label>اختر الجهاز:</label>
        <select id="cmdDeviceSelect" onchange="onCmdDeviceChange()">
          <option value="">-- اختر جهاز --</option>
        </select>
      </div>
      <div class="tabs" id="cmdCategoryTabs"></div>
      <div class="cmd-grid" id="cmdGrid"></div>
      <h3 style="margin:24px 0 16px;">سجل الأوامر</h3>
      <div class="table-wrap">
        <table><thead><tr><th>الجهاز</th><th>الأمر</th><th>الحالة</th><th>النتيجة</th><th>التاريخ</th></tr></thead>
        <tbody id="cmdHistoryTable"></tbody></table>
      </div>
    </div>

    <!-- ── FILES ── -->
    <div class="page-section" id="page-files">
      <div class="section-title"><span class="title-icon">📂</span> الملفات</div>
      <div class="device-select-wrap">
        <label>اختر الجهاز:</label>
        <select id="filesDeviceSelect" onchange="loadFiles()">
          <option value="">-- اختر جهاز --</option>
        </select>
      </div>
      <div id="filesList"></div>
    </div>

    <!-- ── DATA ── -->
    <div class="page-section" id="page-data">
      <div class="section-title"><span class="title-icon">💾</span> البيانات السريعة</div>
      <div class="device-select-wrap">
        <label>اختر الجهاز:</label>
        <select id="dataDeviceSelect">
          <option value="">-- اختر جهاز --</option>
        </select>
      </div>
      <div class="data-quick-grid" id="dataQuickGrid">
        <div class="data-btn" onclick="quickData('sms')">
          <span class="data-icon">💬</span><span class="data-label">الرسائل</span>
        </div>
        <div class="data-btn" onclick="quickData('calls')">
          <span class="data-icon">📞</span><span class="data-label">المكالمات</span>
        </div>
        <div class="data-btn" onclick="quickData('contacts')">
          <span class="data-icon">👤</span><span class="data-label">جهات الاتصال</span>
        </div>
        <div class="data-btn" onclick="quickData('location')">
          <span class="data-icon">📍</span><span class="data-label">الموقع</span>
        </div>
        <div class="data-btn" onclick="quickData('notifications')">
          <span class="data-icon">🔔</span><span class="data-label">الإشعارات</span>
        </div>
        <div class="data-btn" onclick="quickData('clipboard')">
          <span class="data-icon">📋</span><span class="data-label">الحافظة</span>
        </div>
        <div class="data-btn" onclick="quickData('battery')">
          <span class="data-icon">🔋</span><span class="data-label">البطارية</span>
        </div>
        <div class="data-btn" onclick="quickData('info')">
          <span class="data-icon">ℹ️</span><span class="data-label">معلومات الجهاز</span>
        </div>
      </div>
    </div>

    <!-- ── LIVE STREAM ── -->
    <div class="page-section" id="page-stream">
      <div class="section-title"><span class="title-icon">📡</span> البث المباشر</div>
      <div class="device-select-wrap">
        <label>اختر الجهاز:</label>
        <select id="streamDeviceSelect">
          <option value="">-- اختر جهاز --</option>
        </select>
      </div>
      <div class="stream-controls" style="margin-bottom:20px;">
        <select id="streamType" style="padding:8px 12px; border-radius:8px; border:1px solid var(--border); background:var(--bg2); color:var(--text);">
          <option value="screen">بث الشاشة</option>
          <option value="camera">بث الكاميرا</option>
          <option value="audio">بث الصوت</option>
        </select>
        <select id="streamQuality" style="padding:8px 12px; border-radius:8px; border:1px solid var(--border); background:var(--bg2); color:var(--text);">
          <option value="2">جودة منخفضة (2 ثانية)</option>
          <option value="1">جودة متوسطة (1 ثانية)</option>
          <option value="0.5">جودة عالية (0.5 ثانية)</option>
        </select>
        <button class="btn btn-success btn-sm" onclick="startStream()">▶ بدء البث</button>
        <button class="btn btn-danger btn-sm" onclick="stopStream()">⏹ إيقاف البث</button>
      </div>
      <div class="stream-viewer" id="streamViewer">
        <div class="no-stream">لم يتم بدء البث بعد</div>
        <img id="streamImg" style="display:none;" alt="stream">
      </div>
    </div>

    <!-- ── MONITOR ── -->
    <div class="page-section" id="page-monitor">
      <div class="section-title"><span class="title-icon">🔍</span> المراقبة</div>
      <div class="device-select-wrap">
        <label>اختر الجهاز:</label>
        <select id="monitorDeviceSelect">
          <option value="">-- اختر جهاز --</option>
        </select>
      </div>
      <div class="monitor-grid" id="monitorGrid">
        <div class="monitor-card">
          <h4>⌨️ تسجيل المفاتيح</h4>
          <div class="btn-row">
            <button class="btn btn-success btn-sm" onclick="sendMonitorCmd('keylogger_start')">بدء</button>
            <button class="btn btn-danger btn-sm" onclick="sendMonitorCmd('keylogger_stop')">إيقاف</button>
            <button class="btn btn-secondary btn-sm" onclick="sendMonitorCmd('get_keylogger')">عرض السجل</button>
          </div>
        </div>
        <div class="monitor-card">
          <h4>🎬 تسجيل الشاشة</h4>
          <div class="btn-row">
            <button class="btn btn-success btn-sm" onclick="sendMonitorCmd('screen_record_start')">بدء</button>
            <button class="btn btn-danger btn-sm" onclick="sendMonitorCmd('screen_record_stop')">إيقاف</button>
          </div>
        </div>
        <div class="monitor-card">
          <h4>📍 تتبع الموقع</h4>
          <div class="btn-row">
            <button class="btn btn-success btn-sm" onclick="sendMonitorCmd('location_live')">بدء التتبع</button>
            <button class="btn btn-danger btn-sm" onclick="sendMonitorCmd('location_stop')">إيقاف التتبع</button>
          </div>
        </div>
        <div class="monitor-card">
          <h4>📋 مراقبة الحافظة</h4>
          <div class="btn-row">
            <button class="btn btn-success btn-sm" onclick="sendMonitorCmd('clipboard_monitor_start')">بدء</button>
            <button class="btn btn-danger btn-sm" onclick="sendMonitorCmd('clipboard_monitor_stop')">إيقاف</button>
          </div>
        </div>
        <div class="monitor-card">
          <h4>💬 مراقبة الرسائل</h4>
          <div class="btn-row">
            <button class="btn btn-success btn-sm" onclick="sendMonitorCmd('sms_monitor')">تفعيل</button>
          </div>
        </div>
        <div class="monitor-card">
          <h4>📞 مراقبة المكالمات</h4>
          <div class="btn-row">
            <button class="btn btn-success btn-sm" onclick="sendMonitorCmd('call_monitor')">تفعيل</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ── EVENTS ── -->
    <div class="page-section" id="page-events">
      <div class="section-title"><span class="title-icon">📋</span> الأحداث</div>
      <div class="filter-bar">
        <select id="eventTypeFilter" onchange="loadEvents()">
          <option value="">كل الأنواع</option>
          <option value="auth">مصادقة</option>
          <option value="command">أوامر</option>
          <option value="device">جهاز</option>
          <option value="system">نظام</option>
        </select>
        <button class="btn btn-secondary btn-sm" onclick="loadEvents()">🔄 تحديث</button>
      </div>
      <div class="table-wrap">
        <table><thead><tr><th>النوع</th><th>الرسالة</th><th>الحالة</th><th>التاريخ</th></tr></thead>
        <tbody id="eventsTableBody"></tbody></table>
      </div>
    </div>

    <!-- ── SETTINGS ── -->
    <div class="page-section" id="page-settings">
      <div class="section-title"><span class="title-icon">⚙️</span> الإعدادات</div>
      <div class="settings-form" id="settingsForm">
        <div class="form-group">
          <label>فترة المزامنة (ثانية)</label>
          <input type="number" id="settSyncInterval" value="15">
        </div>
        <div class="form-group">
          <label>فترة الموقع (ثانية)</label>
          <input type="number" id="settLocationInterval" value="300">
        </div>
        <div class="form-group">
          <label>تتبع الموقع تلقائياً</label>
          <select id="settAutoLocation">
            <option value="true">نعم</option>
            <option value="false">لا</option>
          </select>
        </div>
        <div class="form-group">
          <label>مزامنة تلقائية</label>
          <select id="settAutoSync">
            <option value="true">نعم</option>
            <option value="false">لا</option>
          </select>
        </div>
        <button class="btn btn-primary" onclick="saveSettings()">💾 حفظ الإعدادات</button>
      </div>
    </div>

    <!-- ── USERS (admin only) ── -->
    <div class="page-section" id="page-users">
      <div class="section-title"><span class="title-icon">👥</span> إدارة المستخدمين</div>
      <div class="user-form">
        <h3 style="margin-bottom:16px;">إنشاء مستخدم جديد</h3>
        <div class="form-group">
          <label>البريد الإلكتروني</label>
          <input type="email" id="newUserEmail" placeholder="email@example.com">
        </div>
        <div class="form-group">
          <label>اسم المستخدم</label>
          <input type="text" id="newUserUsername" placeholder="username">
        </div>
        <div class="form-group">
          <label>كلمة المرور</label>
          <input type="password" id="newUserPassword" placeholder="••••••••">
        </div>
        <div class="form-group">
          <label>الدور</label>
          <select id="newUserRole">
            <option value="user">مستخدم</option>
            <option value="admin">مسؤول</option>
          </select>
        </div>
        <button class="btn btn-primary btn-sm" onclick="createUser()">➕ إنشاء مستخدم</button>
      </div>
      <div class="table-wrap">
        <table><thead><tr><th>البريد</th><th>اسم المستخدم</th><th>الدور</th><th>إجراء</th></tr></thead>
        <tbody id="usersTableBody"></tbody></table>
      </div>
    </div>

  </main>
</div>

<!-- ═══════ DEVICE DETAIL PANEL ═══════ -->
<div class="detail-panel" id="detailPanel">
  <button class="close-detail" onclick="closeDetail()">&times;</button>
  <div id="detailContent"></div>
</div>

<!-- ═══════ PAIRING CODE MODAL ═══════ -->
<div class="modal" id="pairingModal">
  <div class="modal-backdrop" onclick="closeModal('pairingModal')"></div>
  <div class="modal-content" style="max-width:420px; text-align:center;">
    <button class="modal-close" onclick="closeModal('pairingModal')">&times;</button>
    <div class="modal-title">🔑 كود الربط</div>
    <p style="color:var(--text-dim); margin-bottom:12px; font-size:14px;">أدخل هذا الكود في تطبيق الجهاز للربط</p>
    <div class="pairing-code-display">
      <div class="code" id="pairingCodeText">------</div>
    </div>
    <p style="color:var(--text-dim); font-size:12px; margin-bottom:20px;" id="pairingExpiry"></p>
    <button class="btn btn-primary btn-sm" onclick="copyPairingCode()">📋 نسخ الكود</button>
  </div>
</div>

<!-- ═══════ GENERIC MODAL ═══════ -->
<div class="modal" id="genericModal">
  <div class="modal-backdrop" onclick="closeModal('genericModal')"></div>
  <div class="modal-content">
    <button class="modal-close" onclick="closeModal('genericModal')">&times;</button>
    <div class="modal-title" id="genericModalTitle"></div>
    <div class="modal-body" id="genericModalBody"></div>
  </div>
</div>

<!-- ═══════ TOAST CONTAINER ═══════ -->
<div class="toast-container" id="toastContainer"></div>

<!-- ═══════ JAVASCRIPT ═══════ -->
<script>
// ── GLOBAL STATE ──
var APP = {
  token: localStorage.getItem('az_token') || '',
  server: localStorage.getItem('az_server') || '',
  username: localStorage.getItem('az_username') || '',
  role: localStorage.getItem('az_role') || '',
  devices: [],
  commands: {},
  categories: {},
  stats: {},
  currentPage: 'dashboard',
  ws: null,
  wsRetries: 0,
  wsMaxRetries: 10,
  pollInterval: null,
  streamPollInterval: null,
  streaming: false,
};

// ── INIT ──
(function init() {
  if (APP.server) {
    document.getElementById('loginServer').value = APP.server;
  }
  if (APP.token && APP.server) {
    showApp();
  } else {
    showLogin();
  }
})();

// ── LOGIN / LOGOUT ──
function doLogin() {
  var server = document.getElementById('loginServer').value.replace(/\/+$/, '');
  var user = document.getElementById('loginUser').value.trim();
  var pass = document.getElementById('loginPass').value;
  if (!server || !user || !pass) {
    document.getElementById('loginError').textContent = 'يرجى ملء جميع الحقول';
    return;
  }
  var btn = document.getElementById('loginBtn');
  btn.disabled = true;
  btn.textContent = 'جاري تسجيل الدخول...';
  fetch(server + '/api/web/login', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({username: user, password: pass})
  })
  .then(function(r) { return r.json(); })
  .then(function(data) {
    btn.disabled = false;
    btn.textContent = 'تسجيل الدخول';
    if (data.ok && data.token) {
      APP.token = data.token;
      APP.server = server;
      APP.username = data.username;
      APP.role = data.role;
      localStorage.setItem('az_token', data.token);
      localStorage.setItem('az_server', server);
      localStorage.setItem('az_username', data.username);
      localStorage.setItem('az_role', data.role);
      showApp();
    } else {
      document.getElementById('loginError').textContent = data.message || 'فشل تسجيل الدخول';
    }
  })
  .catch(function(err) {
    btn.disabled = false;
    btn.textContent = 'تسجيل الدخول';
    document.getElementById('loginError').textContent = 'فشل الاتصال بالخادم';
  });
}

function doLogout() {
  if (APP.token && APP.server) {
    fetch(APP.server + '/api/web/logout', {
      method: 'POST',
      headers: {'Authorization': 'Bearer ' + APP.token}
    }).catch(function(){});
  }
  stopStreamPoll();
  if (APP.ws) { try { APP.ws.close(); } catch(e){} APP.ws = null; }
  if (APP.pollInterval) { clearInterval(APP.pollInterval); APP.pollInterval = null; }
  APP.token = '';
  APP.username = '';
  APP.role = '';
  localStorage.removeItem('az_token');
  localStorage.removeItem('az_username');
  localStorage.removeItem('az_role');
  showLogin();
}

function showLogin() {
  document.getElementById('loginPage').style.display = 'flex';
  document.getElementById('appPage').style.display = 'none';
}

function showApp() {
  document.getElementById('loginPage').style.display = 'none';
  document.getElementById('appPage').style.display = 'block';
  document.getElementById('topbarUser').textContent = APP.username + ' (' + APP.role + ')';
  document.getElementById('sidebarRole').textContent = APP.role === 'admin' ? 'مسؤول' : 'مستخدم';
  if (APP.role === 'admin') {
    document.getElementById('navUsers').style.display = 'flex';
  } else {
    document.getElementById('navUsers').style.display = 'none';
  }
  connectWS();
  startPolling();
  loadStats();
  loadDevices();
}

// ── API HELPER ──
function apiGet(path) {
  return fetch(APP.server + path, {
    headers: {'Authorization': 'Bearer ' + APP.token}
  }).then(function(r) {
    if (r.status === 401) { doLogout(); throw new Error('Unauthorized'); }
    return r.json();
  });
}

function apiPost(path, body) {
  return fetch(APP.server + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + APP.token
    },
    body: JSON.stringify(body)
  }).then(function(r) {
    if (r.status === 401) { doLogout(); throw new Error('Unauthorized'); }
    return r.json();
  });
}

function apiDelete(path) {
  return fetch(APP.server + path, {
    method: 'DELETE',
    headers: {'Authorization': 'Bearer ' + APP.token}
  }).then(function(r) {
    if (r.status === 401) { doLogout(); throw new Error('Unauthorized'); }
    return r.json();
  });
}

// ── WEBSOCKET ──
function connectWS() {
  if (APP.ws) { try { APP.ws.close(); } catch(e){} }
  var protocol = APP.server.startsWith('https') ? 'wss' : 'ws';
  var wsUrl = protocol + '://' + APP.server.replace(/^https?:\/\//, '') + '/ws/dashboard?token=' + APP.token;
  try {
    APP.ws = new WebSocket(wsUrl);
    APP.ws.onopen = function() {
      APP.wsRetries = 0;
      document.getElementById('wsDot').classList.add('connected');
      document.getElementById('wsText').textContent = 'متصل';
    };
    APP.ws.onmessage = function(evt) {
      try {
        var msg = JSON.parse(evt.data);
        handleWSMessage(msg);
      } catch(e) {}
    };
    APP.ws.onclose = function() {
      document.getElementById('wsDot').classList.remove('connected');
      document.getElementById('wsText').textContent = 'غير متصل';
      reconnectWS();
    };
    APP.ws.onerror = function() {
      APP.ws.close();
    };
  } catch(e) {
    reconnectWS();
  }
}

function reconnectWS() {
  if (APP.wsRetries >= APP.wsMaxRetries) return;
  APP.wsRetries++;
  var delay = Math.min(1000 * Math.pow(2, APP.wsRetries), 30000);
  setTimeout(function() { connectWS(); }, delay);
}

function handleWSMessage(msg) {
  if (msg.type === 'init') {
    if (msg.devices) { APP.devices = msg.devices; populateDeviceSelectors(); }
    if (msg.stats) { APP.stats = msg.stats; renderStats(); }
    if (msg.commands) { APP.commands = msg.commands; }
    if (msg.categories) { APP.categories = msg.categories; renderCmdTabs(); }
    renderDashActiveDevices();
  } else if (msg.type === 'stats_update') {
    if (msg.stats) { APP.stats = msg.stats; renderStats(); }
  }
}

// ── POLLING FALLBACK ──
function startPolling() {
  if (APP.pollInterval) clearInterval(APP.pollInterval);
  APP.pollInterval = setInterval(function() {
    loadStats();
    loadDevices();
  }, 8000);
}

// ── NAVIGATION ──
var pageTitles = {
  dashboard: 'لوحة التحكم', devices: 'الأجهزة', commands: 'الأوامر',
  files: 'الملفات', data: 'البيانات', stream: 'البث المباشر',
  monitor: 'المراقبة', events: 'الأحداث', settings: 'الإعدادات', users: 'المستخدمين'
};

function navigateTo(page) {
  APP.currentPage = page;
  document.querySelectorAll('.page-section').forEach(function(s) { s.classList.remove('active'); });
  var el = document.getElementById('page-' + page);
  if (el) el.classList.add('active');
  document.querySelectorAll('.nav-item').forEach(function(n) { n.classList.remove('active'); });
  var navEl = document.querySelector('.nav-item[data-page="' + page + '"]');
  if (navEl) navEl.classList.add('active');
  document.getElementById('topbarTitle').textContent = pageTitles[page] || page;
  // Close sidebar on mobile
  document.getElementById('sidebar').classList.remove('open');
  // Page-specific loads
  if (page === 'dashboard') { loadStats(); renderDashActiveDevices(); loadDashRecentCmds(); }
  if (page === 'commands') { renderCmdTabs(); }
  if (page === 'files') { loadFiles(); }
  if (page === 'events') { loadEvents(); }
  if (page === 'settings') { loadSettings(); }
  if (page === 'users' && APP.role === 'admin') { loadUsers(); }
}

function toggleSidebar() {
  document.getElementById('sidebar').classList.toggle('open');
}

// ── TOAST ──
function toast(msg, type) {
  type = type || 'info';
  var container = document.getElementById('toastContainer');
  var t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  container.appendChild(t);
  setTimeout(function() { t.style.opacity = '0'; t.style.transform = 'translateX(-40px)'; t.style.transition = 'all .3s'; }, 3000);
  setTimeout(function() { if (t.parentNode) t.parentNode.removeChild(t); }, 3500);
}

// ── MODAL ──
function openModal(id) { document.getElementById(id).classList.add('show'); }
function closeModal(id) { document.getElementById(id).classList.remove('show'); }

function showResultModal(title, data) {
  document.getElementById('genericModalTitle').textContent = title;
  var body = document.getElementById('genericModalBody');
  if (typeof data === 'string') {
    try { data = JSON.parse(data); body.innerHTML = '<pre>' + JSON.stringify(data, null, 2) + '</pre>'; }
    catch(e) { body.innerHTML = '<pre>' + data + '</pre>'; }
  } else {
    body.innerHTML = '<pre>' + JSON.stringify(data, null, 2) + '</pre>';
  }
  openModal('genericModal');
}

// ── LOAD STATS ──
function loadStats() {
  apiGet('/api/web/stats').then(function(r) {
    if (r.ok && r.stats) { APP.stats = r.stats; renderStats(); }
  }).catch(function(){});
}

function renderStats() {
  var s = APP.stats;
  var grid = document.getElementById('statsGrid');
  var cards = [
    {icon:'📱', value: s.total_devices || 0, label: 'إجمالي الأجهزة'},
    {icon:'🟢', value: s.online_devices || 0, label: 'متصل', color:'var(--success)'},
    {icon:'🔴', value: (s.total_devices || 0) - (s.online_devices || 0), label: 'غير متصل', color:'var(--danger)'},
    {icon:'⏳', value: s.pending_commands || 0, label: 'أوامر معلقة', color:'var(--warning)'},
    {icon:'📋', value: s.total_events || 0, label: 'الأحداث'},
    {icon:'✅', value: s.completed_commands || 0, label: 'أوامر مكتملة', color:'var(--success)'},
    {icon:'⏱️', value: formatUptime(s.uptime || 0), label: 'مدة التشغيل'},
  ];
  grid.innerHTML = '';
  cards.forEach(function(c) {
    var div = document.createElement('div');
    div.className = 'stat-card';
    if (c.color) div.style.cssText = '--accent:' + c.color;
    div.innerHTML = '<div class="stat-icon">' + c.icon + '</div><div class="stat-value">' + c.value + '</div><div class="stat-label">' + c.label + '</div>';
    grid.appendChild(div);
  });
}

function formatUptime(secs) {
  if (!secs || secs < 0) return '0 د';
  var d = Math.floor(secs / 86400);
  var h = Math.floor((secs % 86400) / 3600);
  var m = Math.floor((secs % 3600) / 60);
  if (d > 0) return d + 'ي ' + h + 'س';
  if (h > 0) return h + 'س ' + m + 'د';
  return m + 'د';
}

// ── LOAD DEVICES ──
function loadDevices() {
  apiGet('/api/web/devices').then(function(r) {
    if (r.ok && r.devices) {
      APP.devices = r.devices;
      populateDeviceSelectors();
      renderDashActiveDevices();
      renderDevicesGrid();
    }
  }).catch(function(){});
}

function populateDeviceSelectors() {
  var selectors = ['cmdDeviceSelect','filesDeviceSelect','dataDeviceSelect','streamDeviceSelect','monitorDeviceSelect'];
  selectors.forEach(function(sid) {
    var sel = document.getElementById(sid);
    if (!sel) return;
    var current = sel.value;
    sel.innerHTML = '<option value="">-- اختر جهاز --</option>';
    APP.devices.forEach(function(d) {
      var opt = document.createElement('option');
      opt.value = d.id || d.device_id || '';
      var name = d.device_name || d.model || d.brand || d.id || 'جهاز';
      var status = d.online ? ' 🟢' : ' 🔴';
      opt.textContent = name + status;
      sel.appendChild(opt);
    });
    sel.value = current;
  });
}

// ── DASHBOARD ACTIVE DEVICES ──
function renderDashActiveDevices() {
  var container = document.getElementById('dashActiveDevices');
  if (!container) return;
  var online = APP.devices.filter(function(d) { return d.online; });
  if (online.length === 0) {
    container.innerHTML = '<div class="empty-state"><div class="empty-icon">📱</div><p>لا توجد أجهزة متصلة</p></div>';
    return;
  }
  container.innerHTML = '';
  online.forEach(function(d) {
    var div = document.createElement('div');
    div.className = 'card';
    div.onclick = function() { openDeviceDetail(d.id || d.device_id); };
    var name = d.device_name || d.model || d.brand || d.id || 'جهاز';
    div.innerHTML = '<div class="card-header"><div><div class="card-title">' + escHtml(name) + '</div><div class="card-subtitle">' + escHtml(d.brand || '') + ' ' + escHtml(d.model || '') + '</div></div><div class="status-dot online"></div></div>' +
      '<div style="font-size:13px; color:var(--text-dim);">ID: ' + escHtml(d.id || d.device_id || '') + '</div>' +
      (d.battery ? '<div style="font-size:13px; color:var(--text-dim); margin-top:4px;">🔋 البطارية: ' + d.battery + '%</div>' : '') +
      (d.last_seen ? '<div style="font-size:11px; color:var(--text-dim); margin-top:4px;">آخر نشاط: ' + escHtml(d.last_seen) + '</div>' : '');
    container.appendChild(div);
  });
}

function loadDashRecentCmds() {
  apiGet('/api/web/commands').then(function(r) {
    if (r.ok && r.commands) {
      renderCommandHistoryTable(r.commands.slice(0, 10), 'dashRecentCmds');
    }
  }).catch(function(){});
}

// ── DEVICES PAGE ──
function renderDevicesGrid() {
  var container = document.getElementById('devicesGrid');
  if (!container) return;
  var search = (document.getElementById('deviceSearch').value || '').toLowerCase();
  var statusFilter = document.getElementById('deviceStatusFilter').value;
  var filtered = APP.devices.filter(function(d) {
    var name = (d.device_name || d.model || d.brand || d.id || '').toLowerCase();
    if (search && name.indexOf(search) === -1) return false;
    if (statusFilter === 'online' && !d.online) return false;
    if (statusFilter === 'offline' && d.online) return false;
    return true;
  });
  if (filtered.length === 0) {
    container.innerHTML = '<div class="empty-state"><div class="empty-icon">📱</div><p>لا توجد أجهزة</p></div>';
    return;
  }
  container.innerHTML = '';
  filtered.forEach(function(d) {
    var div = document.createElement('div');
    div.className = 'card';
    div.onclick = function() { openDeviceDetail(d.id || d.device_id); };
    var name = d.device_name || d.model || d.brand || d.id || 'جهاز';
    div.innerHTML = '<div class="card-header"><div><div class="card-title">' + escHtml(name) + '</div><div class="card-subtitle">' + escHtml(d.brand || '') + ' ' + escHtml(d.model || '') + ' ' + escHtml(d.os_version || '') + '</div></div><div class="status-dot ' + (d.online ? 'online' : 'offline') + '"></div></div>' +
      '<div style="font-size:12px; color:var(--text-dim);">ID: ' + escHtml(d.id || d.device_id || '') + '</div>' +
      (d.battery ? '<div style="font-size:13px; margin-top:4px;">🔋 ' + d.battery + '%</div>' : '') +
      (d.last_seen ? '<div style="font-size:11px; color:var(--text-dim); margin-top:4px;">' + escHtml(d.last_seen) + '</div>' : '');
    container.appendChild(div);
  });
}

function filterDevices() { renderDevicesGrid(); }

function generatePairingCode() {
  apiGet('/api/web/link_code').then(function(r) {
    if (r.ok && r.code) {
      document.getElementById('pairingCodeText').textContent = r.code;
      document.getElementById('pairingExpiry').textContent = 'الكود صالح لمدة 5 دقائق';
      openModal('pairingModal');
    } else {
      toast(r.message || 'فشل إنشاء الكود', 'error');
    }
  }).catch(function() { toast('فشل الاتصال', 'error'); });
}

function copyPairingCode() {
  var code = document.getElementById('pairingCodeText').textContent;
  if (navigator.clipboard) {
    navigator.clipboard.writeText(code).then(function() { toast('تم نسخ الكود', 'success'); });
  } else {
    var ta = document.createElement('textarea');
    ta.value = code;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    toast('تم نسخ الكود', 'success');
  }
}

// ── DEVICE DETAIL PANEL ──
function openDeviceDetail(deviceId) {
  var panel = document.getElementById('detailPanel');
  var content = document.getElementById('detailContent');
  content.innerHTML = '<div style="text-align:center; padding:40px;">جاري التحميل...</div>';
  panel.classList.add('open');
  apiGet('/api/web/devices/' + deviceId).then(function(r) {
    if (!r.ok) { content.innerHTML = '<p>فشل تحميل بيانات الجهاز</p>'; return; }
    var d = r.device || {};
    var cmds = r.commands || [];
    var name = d.device_name || d.model || d.brand || deviceId;
    var html = '<h3 style="margin-bottom:4px;">' + escHtml(name) + '</h3>';
    html += '<p style="color:var(--text-dim); font-size:13px; margin-bottom:16px;">' + escHtml(d.brand || '') + ' ' + escHtml(d.model || '') + ' ' + escHtml(d.os_version || '') + '</p>';
    html += '<div style="display:flex; align-items:center; gap:8px; margin-bottom:12px;"><div class="status-dot ' + (d.online ? 'online' : 'offline') + '"></div><span style="font-size:13px;">' + (d.online ? 'متصل' : 'غير متصل') + '</span></div>';
    if (d.battery) html += '<p style="font-size:13px; margin-bottom:8px;">🔋 البطارية: ' + d.battery + '%</p>';
    if (d.location) html += '<p style="font-size:13px; margin-bottom:8px;">📍 الموقع: ' + escHtml(JSON.stringify(d.location)) + '</p>';
    html += '<p style="font-size:12px; color:var(--text-dim); margin-bottom:16px;">ID: ' + escHtml(deviceId) + '</p>';
    html += '<div style="display:flex; gap:8px; margin-bottom:20px; flex-wrap:wrap;">';
    html += '<button class="btn btn-primary btn-sm" onclick="sendQuickCmd(\'' + escAttr(deviceId) + '\',\'ping\')">🏓 Ping</button>';
    html += '<button class="btn btn-secondary btn-sm" onclick="sendQuickCmd(\'' + escAttr(deviceId) + '\',\'screenshot\')">📸 لقطة شاشة</button>';
    html += '<button class="btn btn-secondary btn-sm" onclick="sendQuickCmd(\'' + escAttr(deviceId) + '\',\'vibrate\')">📳 اهتزاز</button>';
    html += '<button class="btn btn-secondary btn-sm" onclick="sendQuickCmd(\'' + escAttr(deviceId) + '\',\'get_location\')">📍 موقع</button>';
    html += '<button class="btn btn-danger btn-sm" onclick="unlinkDevice(\'' + escAttr(deviceId) + '\')">🗑️ فصل</button>';
    html += '</div>';
    html += '<h4 style="margin-bottom:12px;">آخر الأوامر</h4>';
    if (cmds.length === 0) {
      html += '<p style="color:var(--text-dim); font-size:13px;">لا توجد أوامر</p>';
    } else {
      html += '<div style="max-height:300px; overflow-y:auto;">';
      cmds.forEach(function(c) {
        var statusColor = c.status === 'completed' ? 'var(--success)' : (c.status === 'pending' ? 'var(--warning)' : 'var(--text-dim)');
        html += '<div style="padding:8px 0; border-bottom:1px solid var(--border); font-size:13px;">';
        html += '<div style="font-weight:600;">' + escHtml(c.command || '') + '</div>';
        html += '<div style="color:' + statusColor + ';">' + escHtml(c.status || '') + '</div>';
        html += '<div style="font-size:11px; color:var(--text-dim);">' + escHtml(c.created_at || '') + '</div>';
        html += '</div>';
      });
      html += '</div>';
    }
    content.innerHTML = html;
  }).catch(function() { content.innerHTML = '<p>خطأ في الاتصال</p>'; });
}

function closeDetail() { document.getElementById('detailPanel').classList.remove('open'); }

function unlinkDevice(deviceId) {
  if (!confirm('هل أنت متأكد من فصل هذا الجهاز؟')) return;
  apiDelete('/api/web/devices/' + deviceId).then(function(r) {
    if (r.ok) { toast('تم فصل الجهاز', 'success'); closeDetail(); loadDevices(); }
    else toast(r.message || 'فشل الفصل', 'error');
  }).catch(function() { toast('خطأ في الاتصال', 'error'); });
}

function sendQuickCmd(deviceId, cmdKey) {
  sendCommand(deviceId, cmdKey, {}).then(function(r) {
    if (r.ok) { toast('تم إرسال الأمر: ' + cmdKey, 'success'); openDeviceDetail(deviceId); }
    else toast(r.message || 'فشل إرسال الأمر', 'error');
  });
}

// ── COMMANDS PAGE ──
function renderCmdTabs() {
  var tabsEl = document.getElementById('cmdCategoryTabs');
  tabsEl.innerHTML = '';
  var cats = APP.categories || {};
  var catKeys = ['data','social','control','apps','files','security','monitor','streaming'];
  catKeys.forEach(function(key, idx) {
    var cat = cats[key];
    if (!cat) return;
    var tab = document.createElement('div');
    tab.className = 'tab' + (idx === 0 ? ' active' : '');
    tab.textContent = (cat.icon || '') + ' ' + (cat.name || key);
    tab.setAttribute('data-cat', key);
    tab.onclick = function() {
      document.querySelectorAll('#cmdCategoryTabs .tab').forEach(function(t) { t.classList.remove('active'); });
      tab.classList.add('active');
      renderCmdGrid(key);
    };
    tabsEl.appendChild(tab);
  });
  if (catKeys.length > 0) renderCmdGrid(catKeys[0]);
}

function renderCmdGrid(category) {
  var grid = document.getElementById('cmdGrid');
  grid.innerHTML = '';
  var cmds = APP.commands || {};
  Object.keys(cmds).forEach(function(key) {
    var c = cmds[key];
    if (c.category !== category) return;
    var btn = document.createElement('div');
    btn.className = 'cmd-btn';
    btn.innerHTML = '<span class="cmd-icon">' + (c.icon || '') + '</span><span class="cmd-name">' + escHtml(c.name || key) + '</span>';
    btn.onclick = function() { onCmdClick(key, c); };
    grid.appendChild(btn);
  });
  if (grid.children.length === 0) {
    grid.innerHTML = '<p style="color:var(--text-dim);">لا توجد أوامر في هذا التصنيف</p>';
  }
}

function onCmdClick(cmdKey, cmdDef) {
  var deviceId = document.getElementById('cmdDeviceSelect').value;
  if (!deviceId) { toast('يرجى اختيار جهاز أولاً', 'warning'); return; }
  sendCommand(deviceId, cmdKey, {}).then(function(r) {
    if (r.ok) {
      toast('تم إرسال: ' + (cmdDef.name || cmdKey), 'success');
      loadCmdHistory();
    } else {
      toast(r.message || 'فشل إرسال الأمر', 'error');
    }
  });
}

function onCmdDeviceChange() { loadCmdHistory(); }

function loadCmdHistory() {
  apiGet('/api/web/commands').then(function(r) {
    if (r.ok && r.commands) renderCommandHistoryTable(r.commands, 'cmdHistoryTable');
  }).catch(function(){});
}

function renderCommandHistoryTable(cmds, tbodyId) {
  var tbody = document.getElementById(tbodyId);
  if (!tbody) return;
  if (cmds.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-dim);">لا توجد أوامر</td></tr>';
    return;
  }
  tbody.innerHTML = '';
  cmds.forEach(function(c) {
    var tr = document.createElement('tr');
    var statusLabel = c.status === 'completed' ? '✅ مكتمل' : (c.status === 'pending' ? '⏳ معلق' : (c.status === 'failed' ? '❌ فشل' : escHtml(c.status || '')));
    tr.innerHTML = '<td>' + escHtml(c.device_id || '') + '</td><td>' + escHtml(c.command || '') + '</td><td>' + statusLabel + '</td><td>' + escHtml(truncate(c.result, 60)) + '</td><td style="font-size:12px; color:var(--text-dim);">' + escHtml(c.created_at || '') + '</td>';
    tbody.appendChild(tr);
  });
}

function sendCommand(deviceId, command, params) {
  return apiPost('/api/web/send_command', {device_id: deviceId, command: command, params: params || {}});
}

// ── FILES PAGE ──
function loadFiles() {
  var deviceId = document.getElementById('filesDeviceSelect').value;
  var container = document.getElementById('filesList');
  if (!deviceId) {
    container.innerHTML = '<div class="empty-state"><div class="empty-icon">📂</div><p>اختر جهازاً لعرض الملفات</p></div>';
    return;
  }
  container.innerHTML = '<p style="color:var(--text-dim);">جاري التحميل...</p>';
  apiGet('/api/web/files?device_id=' + encodeURIComponent(deviceId)).then(function(r) {
    if (!r.ok || !r.files || r.files.length === 0) {
      container.innerHTML = '<div class="empty-state"><div class="empty-icon">📂</div><p>لا توجد ملفات</p></div>';
      return;
    }
    container.innerHTML = '';
    r.files.forEach(function(f) {
      var icon = '📄';
      if (f.file_type === 'photo' || f.file_type === 'screenshot' || f.file_type === 'camera') icon = '🖼️';
      else if (f.file_type === 'video') icon = '🎬';
      else if (f.file_type === 'audio') icon = '🎵';
      var div = document.createElement('div');
      div.className = 'file-item';
      div.innerHTML = '<div class="file-info"><span class="file-icon">' + icon + '</span><div><div class="file-name">' + escHtml(f.filename || f.file_name || 'ملف') + '</div><div class="file-meta">' + escHtml(f.file_type || '') + ' • ' + formatSize(f.size || f.file_size || 0) + '</div></div></div>' +
        '<button class="btn btn-primary btn-sm" onclick="downloadFile(\'' + escAttr(f.id) + '\',\'' + escAttr(f.filename || f.file_name || 'file') + '\')">⬇️ تحميل</button>';
      container.appendChild(div);
    });
  }).catch(function() { container.innerHTML = '<p style="color:var(--danger);">خطأ في تحميل الملفات</p>'; });
}

function downloadFile(fileId, filename) {
  var url = APP.server + '/api/web/files/' + fileId + '?token=' + APP.token;
  var a = document.createElement('a');
  a.href = url;
  a.download = filename || 'file';
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  toast('جاري تحميل: ' + filename, 'info');
}

// ── DATA PAGE ──
function quickData(cmdKey) {
  var deviceId = document.getElementById('dataDeviceSelect').value;
  if (!deviceId) { toast('يرجى اختيار جهاز أولاً', 'warning'); return; }
  sendCommand(deviceId, cmdKey, {}).then(function(r) {
    if (r.ok) {
      toast('تم إرسال: ' + cmdKey, 'success');
      // Poll for result
      var attempts = 0;
      var maxAttempts = 30;
      var pollResult = setInterval(function() {
        attempts++;
        if (attempts > maxAttempts) { clearInterval(pollResult); toast('انتهت مهلة الانتظار للنتيجة', 'warning'); return; }
        apiGet('/api/web/devices/' + deviceId).then(function(dr) {
          if (dr.ok && dr.commands) {
            var found = null;
            for (var i = 0; i < dr.commands.length; i++) {
              if (dr.commands[i].id === (r.command && r.command.id) && dr.commands[i].result) {
                found = dr.commands[i];
                break;
              }
            }
            if (found) {
              clearInterval(pollResult);
              showResultModal('نتيجة: ' + cmdKey, found.result);
            }
          }
        });
      }, 2000);
    } else {
      toast(r.message || 'فشل إرسال الأمر', 'error');
    }
  });
}

// ── STREAMING PAGE ──
function startStream() {
  var deviceId = document.getElementById('streamDeviceSelect').value;
  var streamType = document.getElementById('streamType').value;
  var quality = parseFloat(document.getElementById('streamQuality').value);
  if (!deviceId) { toast('يرجى اختيار جهاز أولاً', 'warning'); return; }
  apiPost('/api/stream/jpeg/start', {device_id: deviceId, type: streamType, interval: quality}).then(function(r) {
    if (r.ok) {
      APP.streaming = true;
      toast('تم بدء البث', 'success');
      startStreamPoll(deviceId, streamType);
    } else {
      toast(r.message || 'فشل بدء البث', 'error');
    }
  }).catch(function() { toast('خطأ في الاتصال', 'error'); });
}

function stopStream() {
  var deviceId = document.getElementById('streamDeviceSelect').value;
  if (!deviceId) return;
  apiPost('/api/stream/jpeg/stop', {device_id: deviceId}).then(function(r) {
    APP.streaming = false;
    stopStreamPoll();
    document.getElementById('streamImg').style.display = 'none';
    var viewer = document.getElementById('streamViewer');
    var noStream = viewer.querySelector('.no-stream');
    if (!noStream) {
      var div = document.createElement('div');
      div.className = 'no-stream';
      div.textContent = 'تم إيقاف البث';
      viewer.appendChild(div);
    }
    toast('تم إيقاف البث', 'info');
  }).catch(function(){});
}

function startStreamPoll(deviceId, streamType) {
  stopStreamPoll();
  var typeParam = streamType === 'camera' ? 'camera' : (streamType === 'audio' ? 'audio' : 'video');
  APP.streamPollInterval = setInterval(function() {
    if (!APP.streaming) { stopStreamPoll(); return; }
    fetch(APP.server + '/api/stream/frame/' + deviceId + '?type=' + typeParam, {
      headers: {'Authorization': 'Bearer ' + APP.token}
    }).then(function(r) { return r.json(); }).then(function(r) {
      if (r.ok && r.data) {
        var img = document.getElementById('streamImg');
        var viewer = document.getElementById('streamViewer');
        var noStream = viewer.querySelector('.no-stream');
        if (noStream) viewer.removeChild(noStream);
        img.src = 'data:image/jpeg;base64,' + r.data;
        img.style.display = 'block';
      }
    }).catch(function(){});
  }, 2000);
}

function stopStreamPoll() {
  if (APP.streamPollInterval) { clearInterval(APP.streamPollInterval); APP.streamPollInterval = null; }
  APP.streaming = false;
}

// ── MONITOR PAGE ──
function sendMonitorCmd(cmdKey) {
  var deviceId = document.getElementById('monitorDeviceSelect').value;
  if (!deviceId) { toast('يرجى اختيار جهاز أولاً', 'warning'); return; }
  sendCommand(deviceId, cmdKey, {}).then(function(r) {
    if (r.ok) {
      toast('تم إرسال: ' + cmdKey, 'success');
      if (cmdKey === 'get_keylogger') {
        toast('سيتم عرض النتائج عند الاستلام', 'info');
        pollForResult(deviceId, r.command ? r.command.id : null, 'سجل المفاتيح');
      }
    } else {
      toast(r.message || 'فشل إرسال الأمر', 'error');
    }
  });
}

function pollForResult(deviceId, commandId, title) {
  if (!commandId) return;
  var attempts = 0;
  var interval = setInterval(function() {
    attempts++;
    if (attempts > 30) { clearInterval(interval); toast('انتهت مهلة الانتظار', 'warning'); return; }
    apiGet('/api/web/devices/' + deviceId).then(function(r) {
      if (r.ok && r.commands) {
        for (var i = 0; i < r.commands.length; i++) {
          if (r.commands[i].id === commandId && r.commands[i].result) {
            clearInterval(interval);
            showResultModal(title, r.commands[i].result);
            return;
          }
        }
      }
    });
  }, 2000);
}

// ── EVENTS PAGE ──
function loadEvents() {
  var typeFilter = document.getElementById('eventTypeFilter').value;
  var url = '/api/web/events?limit=100';
  if (typeFilter) url += '&type=' + encodeURIComponent(typeFilter);
  apiGet(url).then(function(r) {
    if (r.ok && r.events) {
      var tbody = document.getElementById('eventsTableBody');
      if (r.events.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:var(--text-dim);">لا توجد أحداث</td></tr>';
        return;
      }
      tbody.innerHTML = '';
      r.events.forEach(function(e) {
        var tr = document.createElement('tr');
        var statusHtml = '';
        if (e.level === 'success' || e.level === 'info') statusHtml = '<span style="color:var(--success);">✅</span>';
        else if (e.level === 'warning') statusHtml = '<span style="color:var(--warning);">⚠️</span>';
        else if (e.level === 'error') statusHtml = '<span style="color:var(--danger);">❌</span>';
        else statusHtml = escHtml(e.level || '');
        tr.innerHTML = '<td>' + escHtml(e.type || e.event_type || '') + '</td><td>' + escHtml(truncate(e.message || '', 80)) + '</td><td>' + statusHtml + '</td><td style="font-size:12px; color:var(--text-dim);">' + escHtml(e.created_at || e.timestamp || '') + '</td>';
        tbody.appendChild(tr);
      });
    }
  }).catch(function(){});
}

// ── SETTINGS PAGE ──
function loadSettings() {
  apiGet('/api/web/settings').then(function(r) {
    if (r.ok && r.settings) {
      var s = r.settings;
      if (s.sync_interval !== undefined) document.getElementById('settSyncInterval').value = s.sync_interval;
      if (s.location_interval !== undefined) document.getElementById('settLocationInterval').value = s.location_interval;
      if (s.auto_location !== undefined) document.getElementById('settAutoLocation').value = s.auto_location ? 'true' : 'false';
      if (s.auto_sync !== undefined) document.getElementById('settAutoSync').value = s.auto_sync ? 'true' : 'false';
    }
  }).catch(function(){});
}

function saveSettings() {
  var body = {
    sync_interval: parseInt(document.getElementById('settSyncInterval').value) || 15,
    location_interval: parseInt(document.getElementById('settLocationInterval').value) || 300,
    auto_location: document.getElementById('settAutoLocation').value === 'true',
    auto_sync: document.getElementById('settAutoSync').value === 'true',
  };
  apiPost('/api/web/settings', body).then(function(r) {
    if (r.ok) toast('تم حفظ الإعدادات', 'success');
    else toast(r.message || 'فشل حفظ الإعدادات', 'error');
  }).catch(function() { toast('خطأ في الاتصال', 'error'); });
}

// ── USERS PAGE ──
function loadUsers() {
  if (APP.role !== 'admin') return;
  apiGet('/api/web/users').then(function(r) {
    if (r.ok && r.users) {
      var tbody = document.getElementById('usersTableBody');
      if (r.users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center; color:var(--text-dim);">لا يوجد مستخدمين</td></tr>';
        return;
      }
      tbody.innerHTML = '';
      r.users.forEach(function(u) {
        var tr = document.createElement('tr');
        tr.innerHTML = '<td>' + escHtml(u.email || '') + '</td><td>' + escHtml(u.username || '') + '</td><td>' + escHtml(u.role || '') + '</td><td><button class="btn btn-danger btn-sm" onclick="deleteUser(\'' + escAttr(u.id) + '\',\'' + escAttr(u.username) + '\')">🗑️ حذف</button></td>';
        tbody.appendChild(tr);
      });
    }
  }).catch(function(){});
}

function createUser() {
  var email = document.getElementById('newUserEmail').value.trim();
  var username = document.getElementById('newUserUsername').value.trim();
  var password = document.getElementById('newUserPassword').value;
  var role = document.getElementById('newUserRole').value;
  if (!email || !username || !password) { toast('يرجى ملء جميع الحقول', 'warning'); return; }
  apiPost('/api/web/users', {email: email, username: username, password: password, role: role}).then(function(r) {
    if (r.ok) {
      toast('تم إنشاء المستخدم: ' + username, 'success');
      document.getElementById('newUserEmail').value = '';
      document.getElementById('newUserUsername').value = '';
      document.getElementById('newUserPassword').value = '';
      loadUsers();
    } else {
      toast(r.message || 'فشل إنشاء المستخدم', 'error');
    }
  }).catch(function() { toast('خطأ في الاتصال', 'error'); });
}

function deleteUser(userId, username) {
  if (!confirm('هل أنت متأكد من حذف المستخدم "' + username + '"؟')) return;
  apiDelete('/api/web/users/' + userId).then(function(r) {
    if (r.ok) { toast('تم حذف المستخدم', 'success'); loadUsers(); }
    else toast(r.message || 'فشل حذف المستخدم', 'error');
  }).catch(function() { toast('خطأ في الاتصال', 'error'); });
}

// ── UTILITIES ──
function escHtml(str) {
  if (!str) return '';
  var div = document.createElement('div');
  div.textContent = String(str);
  return div.innerHTML;
}

function escAttr(str) {
  if (!str) return '';
  return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function truncate(str, len) {
  if (!str) return '';
  return str.length > len ? str.substring(0, len) + '...' : str;
}

function formatSize(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  var sizes = ['B', 'KB', 'MB', 'GB'];
  var i = Math.floor(Math.log(bytes) / Math.log(1024));
  i = Math.min(i, sizes.length - 1);
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
}

// ── KEYBOARD SHORTCUTS ──
document.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') {
    closeDetail();
    closeModal('pairingModal');
    closeModal('genericModal');
  }
});

// ── ENTER KEY ON LOGIN ──
document.getElementById('loginUser').addEventListener('keydown', function(e) { if (e.key === 'Enter') doLogin(); });
document.getElementById('loginPass').addEventListener('keydown', function(e) { if (e.key === 'Enter') doLogin(); });
document.getElementById('loginServer').addEventListener('keydown', function(e) { if (e.key === 'Enter') doLogin(); });
</script>
</body>
</html>
'''