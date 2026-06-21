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
import com.abuzahra.admin.databinding.FragmentEventsBinding
import com.abuzahra.admin.ui.adapters.EventAdapter
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences

/**
 * Events fragment — functional copy of the web's EventsView:
 *  - Filter by level (info/success/warning/error) — chip group
 *  - Search events in real time
 *  - RecyclerView with icon, level, event message, meta (time + type + device)
 *  - Pull-to-refresh + manual refresh button
 */
class EventsFragment : BaseFragment() {

    private var _binding: FragmentEventsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private val eventAdapter: EventAdapter by lazy { EventAdapter() }

    private var levelFilter: String? = null
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.eventsSwipe.setOnRefreshListener { viewModel.refresh() }

        binding.levelFilter.setOnCheckedStateChangeListener { group, _ ->
            levelFilter = when (group.checkedChipId) {
                R.id.chipInfo -> "info"
                R.id.chipSuccess -> "success"
                R.id.chipWarning -> "warning"
                R.id.chipError -> "error"
                else -> null
            }
            applyFilter()
        }

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString()?.trim()?.lowercase() ?: ""
            applyFilter()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.events.observe(viewLifecycleOwner) { result ->
            binding.eventsSwipe.isRefreshing = false
            when (result) {
                is Result.Loading -> Unit
                is Result.Success -> applyFilter()
                is Result.Error -> {
                    (activity as? MainActivity)?.showSnack(result.message)
                    applyFilter()
                }
            }
        }
    }

    private fun applyFilter() {
        val all = (viewModel.events.value as? Result.Success)?.data ?: emptyList()
        val filtered = all.filter { e ->
            val matchesLevel = levelFilter == null || e.level.lowercase() == levelFilter
            val matchesSearch = searchQuery.isBlank() ||
                    e.event.lowercase().contains(searchQuery) ||
                    e.displayEvent.lowercase().contains(searchQuery) ||
                    e.deviceName.lowercase().contains(searchQuery)
            matchesLevel && matchesSearch
        }
        eventAdapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvEvents.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
