package com.abuzahra.admin.ui.dashboard

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Device
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

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(Preferences.getInstance(this))
    }
    private val deviceAdapter: DeviceAdapter by lazy {
        DeviceAdapter { device -> navigateToDeviceDetail(device) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupLinkCode()
        setupDrawerHeader()
        observeViewModel()
        handleBackPressed()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.dashboard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    private fun setupDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.dashboard,
            R.string.dashboard
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)
    }

    private fun setupDrawerHeader() {
        val headerView = binding.navView.getHeaderView(0)
        val prefs = Preferences.getInstance(this)
        headerView.findViewById<android.widget.TextView>(R.id.tvDrawerUsername)
            ?.text = prefs.userName ?: "المستخدم"
        headerView.findViewById<android.widget.TextView>(R.id.tvDrawerEmail)
            ?.text = prefs.userEmail ?: ""
    }

    private fun setupRecyclerView() {
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = deviceAdapter
            setHasFixedSize(false)
        }
    }

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

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_overview -> {
                binding.drawerLayout.closeDrawer(binding.navView)
            }
            R.id.nav_devices -> {
                binding.drawerLayout.closeDrawer(binding.navView)
            }
            R.id.nav_commands -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                val devices = (viewModel.devices.value as? Result.Success)?.data
                if (devices.isNullOrEmpty()) {
                    Toast.makeText(this, "لا توجد أجهزة — اربط جهازاً أولاً", Toast.LENGTH_SHORT).show()
                } else if (devices.size == 1) {
                    startActivity(DeviceDetailActivity.newIntent(this, devices[0]))
                } else {
                    Toast.makeText(this, "اختر جهازاً من القائمة بالأسفل", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.nav_results -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                Toast.makeText(this, "اختر جهازاً من القائمة لعرض النتائج", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_streaming -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                val devices = (viewModel.devices.value as? Result.Success)?.data
                if (devices.isNullOrEmpty()) {
                    Toast.makeText(this, "لا توجد أجهزة", Toast.LENGTH_SHORT).show()
                } else if (devices.size == 1) {
                    startActivity(StreamingActivity.newIntent(this, devices[0]))
                } else {
                    Toast.makeText(this, "اختر جهازاً من القائمة للبث", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.nav_files -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                startActivity(Intent(this, FilesActivity::class.java))
            }
            R.id.nav_events -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                startActivity(Intent(this, LogsActivity::class.java))
            }
            R.id.nav_users -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                startActivity(Intent(this, UsersActivity::class.java))
            }
            R.id.nav_settings -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_logout -> {
                binding.drawerLayout.closeDrawer(binding.navView)
                showLogoutDialog()
            }
        }
        return true
    }

    private fun observeViewModel() {
        viewModel.devices.observe(this) { result ->
            binding.loadingOverlay.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            when (result) {
                is Result.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is Result.Success -> {
                    val devices = result.data
                    deviceAdapter.submitList(devices)
                    updateEmptyState(devices.isEmpty())
                }
                is Result.Error -> {
                    updateEmptyState(true)
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

        viewModel.stats.observe(this) { result ->
            if (result is Result.Success) {
                binding.tvTotalDevices.text = result.data.devicesCount.toString()
                binding.tvOnlineDevices.text = result.data.onlineCount.toString()
                binding.tvOfflineDevices.text = result.data.offlineCount.toString()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun navigateToDeviceDetail(device: Device) {
        startActivity(DeviceDetailActivity.newIntent(this, device))
    }

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

    private fun setupLinkCode() {
        val prefs = Preferences.getInstance(this)
        val code = prefs.permanentCode
        if (!code.isNullOrEmpty()) {
            binding.cardLinkCode.visibility = View.VISIBLE
            binding.tvLinkCode.text = code
            binding.btnCopyCode.setOnClickListener {
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                val clip = ClipData.newPlainText("link_code", code)
                clipboard.setPrimaryClip(clip)
                Snackbar.make(binding.root, "تم نسخ الكود", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(binding.navView)) {
                    binding.drawerLayout.closeDrawer(binding.navView)
                } else {
                    showLogoutDialog()
                }
            }
        })
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
