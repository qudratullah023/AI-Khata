package com.aikhata.app.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aikhata.app.data.CustomerDetailSnapshot
import com.aikhata.app.data.KhataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CustomerDetailUiState(
    val loading: Boolean = true,
    val detail: CustomerDetailSnapshot? = null,
    val error: String? = null
)

class CustomerDetailViewModel(private val repository: KhataRepository) : ViewModel() {
    private val _state = MutableStateFlow(CustomerDetailUiState())
    val state: StateFlow<CustomerDetailUiState> = _state.asStateFlow()

    fun load(customerId: Long) {
        viewModelScope.launch {
            _state.value = CustomerDetailUiState(loading = true)
            try {
                val detail = repository.loadCustomerDetail(customerId)
                _state.value = CustomerDetailUiState(
                    loading = false,
                    detail = detail,
                    error = if (detail == null) "This customer account could not be found." else null
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Could not load this customer account. Please try again."
                )
            }
        }
    }
}

class CustomerDetailViewModelFactory(
    private val repository: KhataRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomerDetailViewModel::class.java)) {
            return CustomerDetailViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
