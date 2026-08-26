package com.sancharsaathi.app.data.local

import com.sancharsaathi.app.domain.model.RiskResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryStore {
    private val _history = MutableStateFlow<List<RiskResult>>(emptyList())
    val history: Flow<List<RiskResult>> = _history.asStateFlow()

    fun add(result: RiskResult) {
        val current = _history.value.toMutableList()
        current.removeAll { it.analysisId == result.analysisId }
        current.add(0, result)
        _history.value = current
    }

    fun get(analysisId: String): RiskResult? {
        return _history.value.find { it.analysisId == analysisId }
    }
}
