package com.aikhata.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aikhata.app.data.KhataRepository
import com.aikhata.app.data.ReportsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsUiState(
    val loading: Boolean = true,
    val snapshot: ReportsSnapshot? = null,
    val error: String? = null
)

class ReportsViewModel(private val repository: KhataRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    fun load(month: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val snapshot = repository.loadReports(month)
                _state.value = ReportsUiState(loading = false, snapshot = snapshot)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Could not calculate this report. Please try again."
                )
            }
        }
    }
}

class ReportsViewModelFactory(private val repository: KhataRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportsViewModel::class.java)) {
            return ReportsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
