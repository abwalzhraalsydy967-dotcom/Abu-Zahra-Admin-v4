package com.abuzahra.admin.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.databinding.ActivityDashboardBinding
import com.abuzahra.admin.ui.device.DeviceDetailActivity
import com.abuzahra.admin.ui.files.FilesActivity
import com.abuzahra.admin.ui.logs.LogsActivity
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.ui.settings.SettingsActivity
import com.abuzahra.admin.ui.streaming.StreamingActivity
import com.abuzahra.admin.ui.users.UsersActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar

/**
 * Dashboard with a Navigation Drawer (right-side / RTL) mirroring the web's sidebar.
 *
 * The drawer has 9 items: لوحة المعلومات (Overview), الأجهزة (Devices), الأوامر (Commands),
 * النتائج (Results), البث (Streaming), الملفات (Files), الأحداث (Events),
 * المستخدمين (Users), الإعدادات (Settings) — plus a logout entry at the bottom.
 *
 * The main content area hosts two views swapped by visibility:
 *   - `overviewRoot` — stats cards + recent devices + recent events + quick actions
 *   - `devicesRoot` — the legacy search + filter + RecyclerView device list
 *
 * Drawer items that don't have a dedicated in-place view (commands, results, streaming,
 * files, events, users, settings) launch their corresponding activities.
 */
class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(Preferences.getInstance(this))
    }

    private val deviceAdapter: DeviceAdapter by lazy {
        DeviceAdapter { device -> navigateToDeviceDetail(device) }
    }

    private lateinit var prefs: Preferences

    /** Currently visible in-place view (overview or devices). */
    private var visibleView: Int = R.id.nav_overview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Preferences.getInstance(this)

        setupToolbar()
        setupDrawer()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupOverviewActions()
        observeViewModel()
        handleBackPressed()

        // Show overview by default (matches web's default activeView='overview')
        showView(R.id.nav_overview)
        binding.navigationView.setCheckedItem(R.id.nav_overview)
    }

    // ─── Toolbar ──────────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        // Hamburger icon opens the drawer (RTL: drawer slides from physical right)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.toolbar.title = getString(R.string.overview_title)

        // Both swipe layouts trigger a refresh
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.overviewRoot.overviewSwipe.setOnRefreshListener { viewModel.refresh() }
    }

    // ─── Drawer ───────────────────────────────────────────────────
    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener(this)
        populateDrawerHeader()
    }

    private fun populateDrawerHeader() {
        val headerView = binding.navigationView.getHeaderView(0) ?: return

        val tvUserName = headerView.findViewById<TextView>(R.id.tvDrawerUserName)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvDrawerUserEmail)
        val tvRole = headerView.findViewById<TextView>(R.id.tvDrawerRole)
        val tvAvatar = headerView.findViewById<TextView>(R.id.tvAvatarInitial)

        val name = prefs.userName ?: "مستخدم"
        val email = prefs.userEmail ?: ""
        val role = prefs.userRole ?: "viewer"

        tvUserName.text = name
        tvUserEmail.text = email
        tvAvatar.text = name.firstOrNull()?.toString() ?: "م"

        if (role == "admin") {
            tvRole.visibility = View.VISIBLE
            tvRole.text = getString(R.string.role_admin)
        } else {
            tvRole.visibility = View.GONE
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        handleDrawerItem(item.itemId)
        return true
    }

    /**
     * Routes the drawer selection. For overview/devices we swap the in-place view;
     * for everything else we launch the corresponding activity.
     */
    private fun handleDrawerItem(itemId: Int) {
        when (itemId) {
            R.id.nav_overview -> {
                binding.toolbar.title = getString(R.string.nav_overview)
                showView(R.id.nav_overview)
            }
            R.id.nav_devices -> {
                binding.toolbar.title = getString(R.string.nav_devices)
                showView(R.id.nav_devices)
            }
            R.id.nav_commands -> pickDeviceAndOpen { device ->
                startActivity(DeviceDetailActivity.newIntent(this, device))
            }
            R.id.nav_results -> pickDeviceAndOpen { device ->
                startActivity(DeviceDetailActivity.newIntent(this, device))
            }
            R.id.nav_streaming -> pickDeviceAndOpen { device ->
                startActivity(StreamingActivity.newIntent(this, device))
            }
            R.id.nav_files -> startActivity(Intent(this, FilesActivity::class.java))
            R.id.nav_events -> startActivity(Intent(this, LogsActivity::class.java))
            R.id.nav_users -> startActivity(Intent(this, UsersActivity::class.java))
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_logout -> showLogoutDialog()
        }
    }

    /** Toggles visibility between the overview and devices views. */
    private fun showView(viewId: Int) {
        visibleView = viewId
        binding.overviewRoot.root.visibility =
            if (viewId == R.id.nav_overview) View.VISIBLE else View.GONE
        binding.devicesRoot.visibility =
            if (viewId == R.id.nav_devices) View.VISIBLE else View.GONE
    }

    // ─── Device picker dialog ─────────────────────────────────────
    /**
     * Shows a dialog listing devices. Once the user picks one, [onSelected] is
     * invoked with that device. If no devices are available, the user is sent
     * to the Devices view so they can see the empty state.
     */
    private fun pickDeviceAndOpen(onSelected: (Device) -> Unit) {
        val current = (viewModel.devices.value as? Result.Success)?.data ?: emptyList()
        if (current.isEmpty()) {
            Snackbar.make(
                binding.coordinator,
                getString(R.string.select_device_first),
                Snackbar.LENGTH_LONG
            ).show()
            // Send the user to the Devices view so they can see / add devices
            binding.toolbar.title = getString(R.string.nav_devices)
            showView(R.id.nav_devices)
            binding.navigationView.setCheckedItem(R.id.nav_devices)
            return
        }

        val labels = current.map { d ->
            buildString {
                append(d.name.ifEmpty { d.model.ifEmpty { "جهاز" } })
                if (d.isOnline) append("  •  متصل") else append("  •  غير متصل")
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.devices)
            .setItems(labels) { _, which ->
                if (which in current.indices) onSelected(current[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ─── RecyclerView (Devices view) ──────────────────────────────
    private fun setupRecyclerView() {
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = deviceAdapter
            setHasFixedSize(false)
        }
    }

    // ─── Search & filters (Devices view) ──────────────────────────
    private fun setupSearch() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.setSearchQuery(binding.etSearch.text.toString().trim())
                true
            } else {
                false
            }
        }
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text.toString().trim())
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            viewModel.setFilter(DashboardViewModel.FilterType.ALL)
        }
        binding.chipOnline.setOnClickListener {
            viewModel.setFilter(DashboardViewModel.FilterType.ONLINE)
        }
        binding.chipOffline.setOnClickListener {
            viewModel.setFilter(DashboardViewModel.FilterType.OFFLINE)
        }
    }

    // ─── Overview actions ─────────────────────────────────────────
    private fun setupOverviewActions() {
        // Refresh button (top-right of overview)
        binding.overviewRoot.btnOverviewRefresh.setOnClickListener { viewModel.refresh() }

        // "عرض الكل" buttons
        binding.overviewRoot.btnViewAllDevices.setOnClickListener {
            binding.toolbar.title = getString(R.string.nav_devices)
            showView(R.id.nav_devices)
            binding.navigationView.setCheckedItem(R.id.nav_devices)
        }
        binding.overviewRoot.btnViewAllEvents.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }

        // Quick action: copy link code
        binding.overviewRoot.btnLinkCodeAction.setOnClickListener {
            val code = prefs.permanentCode
            if (code.isNullOrEmpty()) {
                Snackbar.make(
                    binding.coordinator,
                    "لا يوجد كود ربط — استخدم شاشة المستخدمين لإعادة التوليد",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("link_code", code))
                Snackbar.make(binding.coordinator, getString(R.string.copied_code), Snackbar.LENGTH_SHORT).show()
            }
        }

        // Quick action: Telegram bot link hint
        binding.overviewRoot.btnTgLinkAction.setOnClickListener {
            Snackbar.make(
                binding.coordinator,
                "افتح لوحة الويب لربط بوت Telegram — أو استخدم إعادة توليد الكود",
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.users)) {
                startActivity(Intent(this, UsersActivity::class.java))
            }.show()
        }
    }

    // ─── ViewModel observation ────────────────────────────────────
    private fun observeViewModel() {
        // Devices
        viewModel.devices.observe(this) { result ->
            binding.loadingOverlay.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            binding.overviewRoot.overviewSwipe.isRefreshing = false

            when (result) {
                is Result.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is Result.Success -> {
                    val devices = result.data
                    deviceAdapter.submitList(devices)
                    updateEmptyState(devices.isEmpty())
                    renderOverviewDevices(devices)
                    renderOnlineSummary(devices)
                }
                is Result.Error -> {
                    updateEmptyState(true)
                    renderOverviewDevices(emptyList())
                    renderOnlineSummary(emptyList())
                    if (result.code == 401) {
                        showSessionExpired()
                    } else {
                        Snackbar.make(
                            binding.coordinator,
                            result.message,
                            Snackbar.LENGTH_LONG
                        ).setAction(R.string.retry) { viewModel.refresh() }.show()
                    }
                }
            }
        }

        // Stats — feeds overview stat cards
        viewModel.stats.observe(this) { result ->
            if (result is Result.Success) {
                val s = result.data
                binding.overviewRoot.tvStatCommands.text = s.totalCommands.toString()
                binding.overviewRoot.tvStatEvents.text = s.totalEvents.toString()
                binding.tvTotalDevices.text = s.devicesCount.toString()
                binding.tvOnlineDevices.text = s.onlineCount.toString()
                binding.tvOfflineDevices.text = s.offlineCount.toString()
                binding.overviewRoot.tvStatOnline.text =
                    "${s.onlineCount} / ${s.devicesCount}"
                updateDrawerBadges(s.onlineCount, s.totalEvents)
            }
        }

        // Events — feeds overview recent events list
        viewModel.events.observe(this) { result ->
            val list = (result as? Result.Success)?.data ?: emptyList()
            renderOverviewEvents(list)
        }

        // Users — feeds the "إجمالي المستخدمين" stat
        viewModel.users.observe(this) { result ->
            val count = (result as? Result.Success)?.data?.size ?: 0
            binding.overviewRoot.tvStatUsers.text = count.toString()
        }
    }

    // ─── Overview rendering helpers ───────────────────────────────

    /** Inflates up to 5 recent-device rows inside the overview card. */
    private fun renderOverviewDevices(devices: List<Device>) {
        val container: LinearLayout = binding.overviewRoot.recentDevicesList
        val empty: View = binding.overviewRoot.recentDevicesEmpty
        container.removeAllViews()

        val recent = devices.take(5)
        if (recent.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (device in recent) {
            val row = inflater.inflate(R.layout.item_recent_device, container, false)
            row.findViewById<TextView>(R.id.tvDeviceName).text =
                device.name.ifEmpty { device.model.ifEmpty { "جهاز" } }
            row.findViewById<TextView>(R.id.tvDeviceMeta).text = buildString {
                if (device.brand.isNotEmpty()) append(device.brand)
                if (device.model.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(device.model)
                }
                if (device.batteryLevel >= 0) append(" • ${device.batteryLevel}%")
            }
            row.findViewById<TextView>(R.id.tvDeviceStatus).text =
                if (device.isOnline) getString(R.string.online) else device.displayLastSeen
            row.setOnClickListener { navigateToDeviceDetail(device) }
            container.addView(row)
        }
    }

    /** Inflates up to 5 recent-event rows inside the overview card. */
    private fun renderOverviewEvents(events: List<Event>) {
        val container: LinearLayout = binding.overviewRoot.recentEventsList
        val empty: View = binding.overviewRoot.recentEventsEmpty
        container.removeAllViews()

        val recent = events.take(5)
        if (recent.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (event in recent) {
            val row = inflater.inflate(R.layout.item_recent_event, container, false)
            row.findViewById<TextView>(R.id.tvEventLevel).text = event.eventTypeCategory
            row.findViewById<TextView>(R.id.tvEventMessage).text = event.displayEvent
            row.findViewById<TextView>(R.id.tvEventMeta).text = buildString {
                append(event.relativeTime)
                val dn = event.deviceName.ifEmpty { event.deviceId }
                if (dn.isNotEmpty()) {
                    append(" • ")
                    append(dn)
                }
            }
            container.addView(row)
        }
    }

    /** Updates the online/offline summary card on the overview. */
    private fun renderOnlineSummary(devices: List<Device>) {
        val online = devices.count { it.isOnline }
        val offline = devices.size - online
        binding.overviewRoot.tvOnlineCount.text = online.toString()
        binding.overviewRoot.tvOfflineCount.text = offline.toString()
        binding.overviewRoot.tvStatOnline.text = "$online / ${devices.size}"
    }

    /** Updates the small badge row at the bottom of the drawer header. */
    private fun updateDrawerBadges(onlineCount: Int, eventsCount: Int) {
        val headerView = binding.navigationView.getHeaderView(0) ?: return
        headerView.findViewById<TextView>(R.id.tvBadgeOnline).text = "$onlineCount متصل"
        headerView.findViewById<TextView>(R.id.tvBadgeEvents).text = "$eventsCount حدث"
    }

    // ─── Empty state (devices view) ───────────────────────────────
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // ─── Navigation ───────────────────────────────────────────────
    private fun navigateToDeviceDetail(device: Device) {
        startActivity(DeviceDetailActivity.newIntent(this, device))
    }

    // ─── Logout ───────────────────────────────────────────────────
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.logout()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSessionExpired() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_expired)
            .setMessage("يرجى تسجيل الدخول مرة أخرى")
            .setPositiveButton(R.string.ok) { _, _ ->
                Preferences.getInstance(this).clear()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setCancelable(false)
            .show()
    }

    // ─── Back-press ───────────────────────────────────────────────
    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    visibleView != R.id.nav_overview -> {
                        binding.toolbar.title = getString(R.string.nav_overview)
                        showView(R.id.nav_overview)
                        binding.navigationView.setCheckedItem(R.id.nav_overview)
                    }
                    else -> showLogoutDialog()
                }
            }
        })
    }

    // ─── Lifecycle ────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        // Re-sync the drawer selection to the visible view (in case we returned
        // from a launched activity whose menu item we briefly checked).
        binding.navigationView.setCheckedItem(visibleView)
        // Refresh data on resume (e.g. when returning from a child activity).
        viewModel.refresh()
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, DashboardActivity::class.java)
        }
    }
}

class DashboardViewModelFactory(private val preferences: Preferences) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
