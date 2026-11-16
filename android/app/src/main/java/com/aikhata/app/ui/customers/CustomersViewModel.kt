package com.aikhata.app.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.aikhata.app.CustomerRow
import com.aikhata.app.data.KhataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CustomersUiState(
    val customers: List<CustomerRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val dueOnly: Boolean = false,
    val sortByBalance: Boolean = false
)

class CustomersViewModel(private val repository: KhataRepository) : ViewModel() {
    private val _state = MutableStateFlow(CustomersUiState())
    val state: StateFlow<CustomersUiState> = _state.asStateFlow()

    init { refresh() }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
        refresh()
    }

    fun toggleDueOnly() {
        _state.value = _state.value.copy(dueOnly = !_state.value.dueOnly)
        refresh()
    }

    fun toggleSort() {
        _state.value = _state.value.copy(sortByBalance = !_state.value.sortByBalance)
        refresh()
    }

    fun refresh() {
        val query = _state.value.query
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val customers = repository.loadCustomers(query)
                val current = _state.value
                val filtered = if (current.dueOnly) customers.filter { it.balance > 0.009 } else customers
                val sorted = if (current.sortByBalance) filtered.sortedByDescending { it.balance } else filtered
                _state.value = current.copy(customers = sorted, loading = false, error = null)
            } catch (error: Exception) {
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not load customers.")
            }
        }
    }
}

class CustomersViewModelFactory(private val repository: KhataRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomersViewModel::class.java)) return CustomersViewModel(repository) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
