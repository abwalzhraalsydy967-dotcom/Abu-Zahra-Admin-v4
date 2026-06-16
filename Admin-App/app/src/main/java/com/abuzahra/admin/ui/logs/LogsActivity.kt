package com.abuzahra.admin.ui.logs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.databinding.ActivityLogsBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val viewModel: LogsViewModel by viewModels {
        LogsViewModelFactory(Preferences.getInstance(this))
    }
    private val logAdapter: LogAdapter by lazy {
        LogAdapter { event ->
            showEventDetails(event)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupFilters()
        observeViewModel()
        viewModel.loadEvents()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_filter -> {
                toggleFilterChips()
                true
            }
            R.id.action_clear -> {
                showClearLogsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.logs)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(this@LogsActivity)
            adapter = logAdapter
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
        binding.chipAllEvents.setOnClickListener {
            viewModel.setFilter(LogsViewModel.FilterType.ALL)
        }
        binding.chipConnection.setOnClickListener {
            viewModel.setFilter(LogsViewModel.FilterType.CONNECTION)
        }
        binding.chipCommand.setOnClickListener {
            viewModel.setFilter(LogsViewModel.FilterType.COMMAND)
        }
        binding.chipAlert.setOnClickListener {
            viewModel.setFilter(LogsViewModel.FilterType.ALERT)
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadEvents()
        }
    }

    private fun observeViewModel() {
        viewModel.events.observe(this) { result ->
            binding.loadingOverlay.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false

            when (result) {
                is Result.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is Result.Success -> {
                    val events = result.data
                    logAdapter.submitList(events)
                    updateEmptyState(events.isEmpty())
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
                        ).setAction(R.string.retry) { viewModel.loadEvents() }.show()
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvLogs.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showEventDetails(event: Event) {
        val message = buildString {
            append(event.displayEvent)
            if (event.deviceName.isNotEmpty()) {
                append("\n\nالجهاز: ${event.deviceName}")
            }
            if (event.details != null) {
                append("\n\nالتفاصيل: ${event.details}")
            }
            append("\n\nالوقت: ${event.relativeTime}")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("تفاصيل السجل")
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun toggleFilterChips() {
        val isVisible = binding.chipGroup.visibility == View.VISIBLE
        binding.chipGroup.visibility = if (isVisible) View.GONE else View.VISIBLE
    }

    private fun showClearLogsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("مسح السجلات")
            .setMessage("هل تريد تحديث قائمة السجلات؟")
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.loadEvents()
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

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, LogsActivity::class.java)
        }
    }
}

class LogsViewModelFactory(private val preferences: Preferences) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LogsViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}