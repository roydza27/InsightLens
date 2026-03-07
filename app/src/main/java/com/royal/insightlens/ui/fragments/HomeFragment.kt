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
import com.royal.insightlens.R
import com.royal.insightlens.data.local.AppDatabase
import com.royal.insightlens.data.repository.BookRepository
import com.royal.insightlens.ui.activities.BookDetailsActivity
import com.royal.insightlens.ui.adapter.RecentScanAdapter
import com.royal.insightlens.ui.viewmodel.HomeUiState
import com.royal.insightlens.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(
            BookRepository(AppDatabase.getInstance(requireContext()).bookDao())
        )
    }

    private lateinit var recentRecycler: RecyclerView

    private val recentAdapter = RecentScanAdapter { book ->
        val intent = Intent(requireContext(), BookDetailsActivity::class.java)
        intent.putExtra(BookDetailsActivity.EXTRA_VOLUME_ID, book.volumeId)
        startActivity(intent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recentRecycler = view.findViewById(R.id.home_recent_recycler)
        recentRecycler.apply {
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
            adapter = recentAdapter
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentScans.collect { state ->
                    when (state) {
                        is HomeUiState.Loading  -> Unit
                        is HomeUiState.Empty    -> recentAdapter.submitList(emptyList())
                        is HomeUiState.HasBooks -> recentAdapter.submitList(state.books)
                    }
                }
            }
        }
    }
}