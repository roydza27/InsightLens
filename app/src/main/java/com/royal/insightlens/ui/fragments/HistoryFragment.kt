package com.royal.insightlens.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.royal.insightlens.R
import com.royal.insightlens.data.local.AppDatabase
import com.royal.insightlens.data.repository.BookRepository
import com.royal.insightlens.ui.activities.BookDetailsActivity
import com.royal.insightlens.ui.adapter.HistoryAdapter
import com.royal.insightlens.ui.viewmodel.HistoryUiState
import com.royal.insightlens.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModel.Factory(
            BookRepository(AppDatabase.getInstance(requireContext()).bookDao())
        )
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView

    private val historyAdapter = HistoryAdapter { book ->
        val intent = Intent(requireContext(), BookDetailsActivity::class.java)
        intent.putExtra(BookDetailsActivity.EXTRA_VOLUME_ID, book.volumeId)
        startActivity(intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout    = view.findViewById(R.id.history_tabs)
        recyclerView = view.findViewById(R.id.history_recycler)

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }

        setupTabs()
        observeViewModel()
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(
                    when (tab.position) {
                        1    -> HistoryViewModel.Tab.BOOKS
                        2    -> HistoryViewModel.Tab.ARTICLES
                        3    -> HistoryViewModel.Tab.DOCUMENTS
                        else -> HistoryViewModel.Tab.ALL
                    }
                )
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyState.collect { state ->
                    when (state) {
                        is HistoryUiState.Loading  -> Unit
                        is HistoryUiState.Empty    -> historyAdapter.submitList(emptyList())
                        is HistoryUiState.HasBooks -> historyAdapter.submitList(state.items)
                    }
                }
            }
        }
    }
}