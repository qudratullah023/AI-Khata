package com.aikhata.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aikhata.app.data.DashboardSnapshot
import com.aikhata.app.data.KhataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val dashboard: DashboardSnapshot? = null,
    val error: String? = null
)

class HomeViewModel(private val repository: KhataRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { repository.loadDashboard() }
                .onSuccess { _state.value = HomeUiState(loading = false, dashboard = it) }
                .onFailure {
                    _state.value = HomeUiState(
                        loading = false,
                        dashboard = _state.value.dashboard,
                        error = "Could not load your shop summary. Please try again."
                    )
                }
        }
    }
}

class HomeViewModelFactory(private val repository: KhataRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java))
        return HomeViewModel(repository) as T
    }
}
