package com.abuzahra.admin.ui.notifications

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.api.NotificationEntry
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.ActivityNotificationsBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NotificationsActivity — real-time notifications stream from devices.
 *
 * The Android client app posts every notification it sees (via its
 * NotificationListenerService) to /api/data/{device_id} with type=
 * "notifications", which the server stores in Firebase at
 * "notifications/{device_id}". This activity polls
 *   GET /api/web/notifications/{device_id}
 * every 5 seconds and renders the list with [NotificationAdapter].
 *
 * The activity also offers a "clear" button that calls
 *   DELETE /api/web/notifications/{device_id}
 * to wipe the stored notifications in Firebase.
 *
 * The user can switch between multiple devices via a chip row at the
 * top (one chip per device owned by the current user).
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private lateinit var api: ApiService
    private val preferences: Preferences by lazy { Preferences.getInstance(this) }

    private val adapter = NotificationAdapter()

    private var devices: List<Device> = emptyList()
    private var selectedDeviceId: String = ""

    // Polling: refresh every 5 seconds.
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 5000L
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (selectedDeviceId.isNotEmpty()) {
                fetchNotifications(silent = true)
            }
            pollHandler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        api = ApiClient.createWithToken(preferences.serverUrl, preferences.token ?: "")

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        loadDevices()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "الإشعارات"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            if (selectedDeviceId.isNotEmpty()) fetchNotifications(silent = false)
        }
    }

    private fun setupRecyclerView() {
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            adapter = this@NotificationsActivity.adapter
        }
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener {
            if (selectedDeviceId.isNotEmpty()) fetchNotifications(silent = false)
            else Snackbar.make(binding.root, "اختر جهازاً أولاً", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnClear.setOnClickListener {
            if (selectedDeviceId.isEmpty()) {
                Snackbar.make(binding.root, "اختر جهازاً أولاً", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("مسح الإشعارات")
                .setMessage("سيتم مسح جميع الإشعارات المخزّنة لهذا الجهاز من Firebase. متابعة؟")
                .setPositiveButton(R.string.confirm) { _, _ -> clearNotifications() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /**
     * Loads the user's devices and renders one chip per device. If the
     * activity was launched with an EXTRA_DEVICE, that device is pre-selected.
     */
    private fun loadDevices() {
        val preselected = intent.getSerializableExtra(EXTRA_DEVICE) as? Device
        lifecycleScope.launch {
            try {
                devices = withContext(Dispatchers.IO) { api.getDevices() }
                if (devices.isEmpty()) {
                    binding.tvStatus.text = "لا توجد أجهزة مرتبطة بحسابك"
                    binding.emptyState.visibility = View.VISIBLE
                    return@launch
                }

                binding.chipGroupDevices.removeAllViews()
                devices.forEach { d ->
                    val chip = Chip(this@NotificationsActivity).apply {
                        text = d.name.ifEmpty { d.model }
                        isCheckable = true
                        setOnClickListener {
                            binding.chipGroupDevices.clearCheck()
                            isChecked = true
                            selectDevice(d.id)
                        }
                    }
                    binding.chipGroupDevices.addView(chip)
                }

                // Pre-select: explicit EXTRA_DEVICE, else first online, else first device.
                val target = preselected?.takeIf { p -> devices.any { it.id == p.id } }
                    ?: devices.firstOrNull { it.isOnline }
                    ?: devices.first()
                selectDevice(target.id)
                // Mark the matching chip as checked.
                val idx = devices.indexOfFirst { it.id == target.id }
                if (idx >= 0) {
                    (binding.chipGroupDevices.getChildAt(idx) as? Chip)?.isChecked = true
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "خطأ تحميل الأجهزة: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun selectDevice(deviceId: String) {
        selectedDeviceId = deviceId
        val d = devices.find { it.id == deviceId }
        binding.tvStatus.text = "الجهاز: ${d?.name?.ifEmpty { d?.model ?: "—" }} • جاري التحميل..."
        fetchNotifications(silent = false)
    }

    /**
     * Fetches notifications from GET /api/web/notifications/{device_id}.
     * When [silent] is true (auto-poll), we don't show the SwipeRefresh
     * spinner — the list simply updates in place.
     */
    private fun fetchNotifications(silent: Boolean) {
        if (selectedDeviceId.isEmpty()) return
        if (!silent) binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getDeviceNotifications(selectedDeviceId)
                }
                if (!silent) binding.swipeRefresh.isRefreshing = false

                if (response.ok) {
                    renderNotifications(response.notifications)
                    binding.tvStatus.text = buildString {
                        val d = devices.find { it.id == selectedDeviceId }
                        append("الجهاز: ${d?.name?.ifEmpty { d?.model ?: "—" }}")
                        append("  •  ${response.count} إشعار")
                        append("  •  آخر تحديث: الآن")
                    }
                } else {
                    binding.tvStatus.text = response.message.ifEmpty { "فشل تحميل الإشعارات" }
                }
            } catch (e: retrofit2.HttpException) {
                if (!silent) binding.swipeRefresh.isRefreshing = false
                binding.tvStatus.text = "خطأ HTTP ${e.code()}"
            } catch (e: Exception) {
                if (!silent) binding.swipeRefresh.isRefreshing = false
                binding.tvStatus.text = "خطأ: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun renderNotifications(items: List<NotificationEntry>) {
        if (items.isEmpty()) {
            adapter.submitList(emptyList())
            binding.emptyState.visibility = View.VISIBLE
            binding.rvNotifications.visibility = View.GONE
        } else {
            adapter.submitList(items)
            binding.emptyState.visibility = View.GONE
            binding.rvNotifications.visibility = View.VISIBLE
        }
    }

    private fun clearNotifications() {
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    api.clearDeviceNotifications(selectedDeviceId)
                }
                binding.swipeRefresh.isRefreshing = false
                if (ok) {
                    adapter.submitList(emptyList())
                    binding.emptyState.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                    Snackbar.make(binding.root, "تم مسح الإشعارات", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "فشل المسح", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Snackbar.make(
                    binding.root,
                    "خطأ: ${e.message ?: e.javaClass.simpleName}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start polling when the activity is in the foreground.
        pollHandler.postDelayed(pollRunnable, pollIntervalMs)
    }

    override fun onPause() {
        super.onPause()
        // Stop polling when the user leaves — avoid unnecessary requests.
        pollHandler.removeCallbacks(pollRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollHandler.removeCallbacks(pollRunnable)
    }

    companion object {
        const val EXTRA_DEVICE = "extra_device"
    }
}
