package com.abuzahra.admin.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import com.abuzahra.admin.R
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.databinding.FragmentOverviewBinding
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Overview fragment — functional copy of the web's OverviewView:
 *  - 4 stat cards (online, commands, events, users)
 *  - Quick actions (link code + Telegram bot link)
 *  - Recent 5 devices (tap → select device + navigate to commands)
 *  - Recent 5 events
 *  - Online/offline summary
 *  - Pull-to-refresh
 */
class OverviewFragment : BaseFragment() {

    private var _binding: FragmentOverviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private lateinit var prefs: Preferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Preferences.getInstance(requireContext())

        binding.overviewSwipe.setOnRefreshListener { viewModel.refresh() }
        binding.btnOverviewRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnViewAllDevices.setOnClickListener {
            (activity as? MainActivity)?.navigateToView(R.id.nav_devices)
        }
        binding.btnViewAllEvents.setOnClickListener {
            (activity as? MainActivity)?.navigateToView(R.id.nav_events)
        }
        binding.btnLinkCodeAction.setOnClickListener { showLinkCode() }
        binding.btnTgLinkAction.setOnClickListener { showTgLinkHint() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.devices.observe(viewLifecycleOwner) { result ->
            binding.overviewSwipe.isRefreshing = false
            val devices = (result as? Result.Success)?.data ?: emptyList()
            renderRecentDevices(devices.take(5))
            renderOnlineSummary(devices)
        }
        viewModel.stats.observe(viewLifecycleOwner) { result ->
            val s = (result as? Result.Success)?.data ?: return@observe
            binding.tvStatCommands.text = s.totalCommands.toString()
            binding.tvStatEvents.text = s.totalEvents.toString()
            binding.tvStatOnline.text = "${s.onlineCount} / ${s.devicesCount}"
        }
        viewModel.events.observe(viewLifecycleOwner) { result ->
            val events = (result as? Result.Success)?.data ?: emptyList()
            renderRecentEvents(events.take(5))
        }
        viewModel.users.observe(viewLifecycleOwner) { result ->
            val count = (result as? Result.Success)?.data?.size ?: 0
            binding.tvStatUsers.text = count.toString()
        }
    }

    private fun renderRecentDevices(devices: List<Device>) {
        val container: LinearLayout = binding.recentDevicesList
        val empty: View = binding.recentDevicesEmpty
        container.removeAllViews()
        if (devices.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (device in devices) {
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
            row.setOnClickListener {
                viewModel.selectDevice(device)
                (activity as? MainActivity)?.navigateToView(R.id.nav_commands)
            }
            container.addView(row)
        }
    }

    private fun renderRecentEvents(events: List<Event>) {
        val container: LinearLayout = binding.recentEventsList
        val empty: View = binding.recentEventsEmpty
        container.removeAllViews()
        if (events.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (event in events) {
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

    private fun renderOnlineSummary(devices: List<Device>) {
        val online = devices.count { it.isOnline }
        val offline = devices.size - online
        binding.tvOnlineCount.text = online.toString()
        binding.tvOfflineCount.text = offline.toString()
    }

    // ── Quick actions ─────────────────────────────────────────────
    private fun showLinkCode() {
        val code = prefs.permanentCode
        if (code.isNullOrEmpty()) {
            (activity as? MainActivity)?.showSnack("جارٍ الحصول على كود الربط...")
            viewModel.generateLinkCode()
            viewModel.linkCode.observe(viewLifecycleOwner) { result ->
                if (result == null) return@observe
                when (result) {
                    is Result.Success -> showCodeDialog(result.data)
                    is Result.Error -> {
                        (activity as? MainActivity)?.showSnack("فشل: ${result.message}")
                    }
                    else -> Unit
                }
            }
        } else {
            showCodeDialog(code)
        }
    }

    private fun showCodeDialog(code: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔗 كود الربط الخاص بك")
            .setMessage("الكود: $code\n\nهذا الكود دائم — استخدمه لربط الأجهزة الجديدة بحسابك.")
            .setPositiveButton(R.string.copy_code) { _, _ ->
                val clipboard = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("link_code", code))
                (activity as? MainActivity)?.showSnack(getString(R.string.copied_code))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTgLinkHint() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("💬 ربط بوت Telegram")
            .setMessage(
                "لربط حسابك مع بوت Telegram:\n\n" +
                        "1. افتح لوحة التحكم على المتصفح\n" +
                        "2. اضغط زر «ربط بوت Telegram» في الشريط الجانبي\n" +
                        "3. افتح الرابط على هاتفك الذي يحمل تطبيق Telegram\n\n" +
                        "أو استخدم زر «إعادة توليد الكود» في قسم المستخدمين."
            )
            .setPositiveButton(R.string.users) { _, _ ->
                (activity as? MainActivity)?.navigateToView(R.id.nav_users)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
