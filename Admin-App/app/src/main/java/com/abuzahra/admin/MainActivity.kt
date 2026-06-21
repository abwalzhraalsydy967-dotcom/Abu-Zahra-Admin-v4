package com.abuzahra.admin

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.ui.fragments.CommandsFragment
import com.abuzahra.admin.ui.fragments.DevicesFragment
import com.abuzahra.admin.ui.fragments.EventsFragment
import com.abuzahra.admin.ui.fragments.FilesFragment
import com.abuzahra.admin.ui.fragments.OverviewFragment
import com.abuzahra.admin.ui.fragments.ResultsFragment
import com.abuzahra.admin.ui.fragments.SettingsFragment
import com.abuzahra.admin.ui.fragments.StreamingFragment
import com.abuzahra.admin.ui.fragments.UsersFragment
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar

/**
 * Single-activity host for the admin dashboard.
 *
 * Holds a DrawerLayout with a NavigationView (9 items matching the web sidebar)
 * and a FrameLayout fragment container. Drawer item clicks swap the hosted
 * Fragment. The Toolbar shows the active view's title.
 *
 * The shared [DashboardViewModel] is owned by this Activity so all fragments
 * see the same devices / stats / events / selected device state.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: com.abuzahra.admin.databinding.ActivityMainBinding
    private lateinit var prefs: Preferences

    val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(Preferences.getInstance(this))
    }

    /** Currently selected drawer item id — used for back-press handling. */
    private var currentItemId: Int = R.id.nav_overview

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences.getInstance(this)

        // If not logged in, redirect to login
        if (!prefs.isLoggedIn) {
            goToLogin()
            return
        }

        binding = com.abuzahra.admin.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
        observeViewModel()

        if (savedInstanceState == null) {
            selectDrawerItem(R.id.nav_overview)
        }

        handleBackPressed()
        observeCommandSendResult()
    }

    // ─── Toolbar ──────────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    // ─── Drawer ───────────────────────────────────────────────────
    private fun setupDrawer() {
        binding.navigationView.setNavigationItemSelectedListener(this)
        populateDrawerHeader()
        // Swipe refresh on the host triggers a global data reload
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            // Refresh stops after a short delay (fragments observe and update)
            binding.swipeRefresh.postDelayed({
                binding.swipeRefresh.isRefreshing = false
            }, 1200)
        }
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

        tvRole.visibility = if (role == "admin") View.VISIBLE else View.GONE
        tvRole.text = getString(R.string.role_admin)
    }

    /**
     * Observes shared VM state to update the drawer header's badges
     * (X متصل / Y حدث) and to react to session-expiry errors.
     */
    private fun observeViewModel() {
        viewModel.stats.observe(this) { result ->
            val s = (result as? com.abuzahra.admin.data.api.Result.Success)?.data ?: return@observe
            val headerView = binding.navigationView.getHeaderView(0) ?: return@observe
            headerView.findViewById<TextView>(R.id.tvBadgeOnline)?.text = "${s.onlineCount} متصل"
            headerView.findViewById<TextView>(R.id.tvBadgeEvents)?.text = "${s.totalEvents} حدث"
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        selectDrawerItem(item.itemId)
        return true
    }

    private fun selectDrawerItem(itemId: Int) {
        currentItemId = itemId
        val fragment: Fragment = when (itemId) {
            R.id.nav_overview -> OverviewFragment()
            R.id.nav_devices -> DevicesFragment()
            R.id.nav_commands -> CommandsFragment()
            R.id.nav_results -> ResultsFragment()
            R.id.nav_streaming -> StreamingFragment()
            R.id.nav_files -> FilesFragment()
            R.id.nav_events -> EventsFragment()
            R.id.nav_users -> UsersFragment()
            R.id.nav_settings -> SettingsFragment()
            R.id.nav_logout -> {
                showLogoutDialog()
                return
            }
            else -> OverviewFragment()
        }

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        binding.toolbar.title = drawerTitle(itemId)
        binding.navigationView.setCheckedItem(itemId)
    }

    private fun drawerTitle(itemId: Int): String = when (itemId) {
        R.id.nav_overview -> getString(R.string.nav_overview)
        R.id.nav_devices -> getString(R.string.nav_devices)
        R.id.nav_commands -> getString(R.string.nav_commands)
        R.id.nav_results -> getString(R.string.nav_results)
        R.id.nav_streaming -> getString(R.string.nav_streaming)
        R.id.nav_files -> getString(R.string.nav_files)
        R.id.nav_events -> getString(R.string.nav_events)
        R.id.nav_users -> getString(R.string.nav_users)
        R.id.nav_settings -> getString(R.string.nav_settings)
        else -> getString(R.string.app_name)
    }

    // ─── Public API for fragments ─────────────────────────────────
    /**
     * Switch the drawer to a specific view. Fragments call this when they want
     * to navigate (e.g. overview "view all devices" → devices view).
     */
    fun navigateToView(itemId: Int) {
        selectDrawerItem(itemId)
    }

    /** Shows a snackbar anchored to the coordinator. */
    fun showSnack(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(binding.coordinator, message, Snackbar.LENGTH_LONG)
        if (actionLabel != null && onAction != null) {
            snackbar.setAction(actionLabel) { onAction() }
        }
        snackbar.show()
    }

    fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ─── Command send result observation (for toast feedback) ─────
    private fun observeCommandSendResult() {
        viewModel.commandSendResult.observe(this) { result ->
            if (result == null) return@observe
            when (result) {
                is com.abuzahra.admin.data.api.Result.Success -> {
                    showSnack("تم إرسال الأمر بنجاح — سيظهر في النتائج")
                }
                is com.abuzahra.admin.data.api.Result.Error -> {
                    showSnack("فشل إرسال الأمر: ${result.message}")
                    if (result.code == 401) goToLogin()
                }
                else -> Unit
            }
            viewModel.consumeCommandSendResult()
        }
    }

    // ─── Logout ───────────────────────────────────────────────────
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.logout()
                goToLogin()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Restore overview selection
                selectDrawerItem(R.id.nav_overview)
            }
            .setCancelable(false)
            .show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ─── Back press ───────────────────────────────────────────────
    private fun handleBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    currentItemId != R.id.nav_overview -> {
                        selectDrawerItem(R.id.nav_overview)
                    }
                    else -> showLogoutDialog()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Refresh data on resume (returning from child fragments etc.)
        viewModel.refresh()
    }

    companion object {
        fun newIntent(context: android.content.Context): Intent {
            return Intent(context, MainActivity::class.java)
        }
    }
}
