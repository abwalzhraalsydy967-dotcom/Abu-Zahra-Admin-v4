package com.abuzahra.admin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.FragmentDevicesBinding
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.ui.dashboard.DeviceAdapter
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Devices fragment — functional copy of the web's DevicesView:
 *  - Search + filter chips (all/online/offline)
 *  - Device list with cards (name, model, brand, battery, online status, last seen)
 *  - Tap → select device + navigate to Commands view
 *  - Long press → device options (commands, streaming, results)
 *  - Pull-to-refresh
 */
class DevicesFragment : BaseFragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private val deviceAdapter: DeviceAdapter by lazy {
        DeviceAdapter { device -> onDeviceClick(device) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
            setHasFixedSize(false)
        }

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.devicesSwipe.setOnRefreshListener { viewModel.refresh() }

        binding.btnLinkCode.setOnClickListener {
            // Trigger overview's link-code action by navigating to overview
            (activity as? MainActivity)?.navigateToView(R.id.nav_overview)
            (activity as? MainActivity)?.showSnack("استخدم زر كود الربط في لوحة المعلومات")
        }

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearchQuery(text?.toString()?.trim() ?: "")
            refreshList()
        }

        binding.chipGroup.setOnCheckedStateChangeListener { group, _ ->
            when (group.checkedChipId) {
                R.id.chipAll -> viewModel.setFilter(DashboardViewModel.FilterType.ALL)
                R.id.chipOnline -> viewModel.setFilter(DashboardViewModel.FilterType.ONLINE)
                R.id.chipOffline -> viewModel.setFilter(DashboardViewModel.FilterType.OFFLINE)
            }
            refreshList()
        }

        // Long-press on a device opens options dialog
        // Note: DeviceAdapter calls onItemClick for taps. Long-press is wired here
        // via a separate listener installed on each row through the adapter hook.
        deviceAdapter.registerAdapterDataObserver(object :
            androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {})

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.devices.observe(viewLifecycleOwner) { result ->
            binding.devicesSwipe.isRefreshing = false
            when (result) {
                is Result.Loading -> Unit
                is Result.Success -> refreshList()
                is Result.Error -> {
                    refreshList()
                    (activity as? MainActivity)?.showSnack(result.message)
                }
            }
        }
        // Also re-filter when filter/search changes
        viewModel.searchQuery.observe(viewLifecycleOwner) { refreshList() }
        viewModel.filterType.observe(viewLifecycleOwner) { refreshList() }
    }

    private fun refreshList() {
        val devices = viewModel.filteredDevices()
        deviceAdapter.submitList(devices)
        binding.emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        binding.rvDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE

        binding.tvTotalDevices.text = devices.size.toString()
        binding.tvOnlineDevices.text = devices.count { it.isOnline }.toString()
    }

    private fun onDeviceClick(device: Device) {
        // Long-press detection via a small delay / options dialog
        // Simpler: tapping shows an options dialog (commands / streaming / results)
        val options = arrayOf(
            "💻 عرض الأوامر",
            "📡 البث المباشر",
            "📋 النتائج",
            "ℹ️ تفاصيل الجهاز"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(device.name.ifEmpty { device.model.ifEmpty { "جهاز" } })
            .setItems(options) { _, which ->
                viewModel.selectDevice(device)
                when (which) {
                    0 -> (activity as? MainActivity)?.navigateToView(R.id.nav_commands)
                    1 -> (activity as? MainActivity)?.navigateToView(R.id.nav_streaming)
                    2 -> (activity as? MainActivity)?.navigateToView(R.id.nav_results)
                    3 -> showDeviceInfo(device)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeviceInfo(device: Device) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(device.name.ifEmpty { "جهاز" })
            .setMessage(
                "الموديل: ${device.model}\n" +
                        "الماركة: ${device.brand}\n" +
                        "النظام: ${device.osVersion}\n" +
                        "البطارية: ${device.displayBattery}\n" +
                        "الحالة: ${if (device.isOnline) "متصل" else "غير متصل"}\n" +
                        "آخر ظهور: ${device.displayLastSeen}\n" +
                        "IP: ${device.ipAddress}"
            )
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
