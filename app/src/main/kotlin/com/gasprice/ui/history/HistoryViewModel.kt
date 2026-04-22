package com.gasprice.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gasprice.data.repository.GasPriceRepository
import com.gasprice.domain.model.GasPriceObservation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: GasPriceRepository
) : ViewModel() {

    val observations: StateFlow<List<GasPriceObservation>> = repository.observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
