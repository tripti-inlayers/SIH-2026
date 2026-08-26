package com.sancharsaathi.app.domain.model

data class RiskSignal(
    val category: String,
    val code: String,
    val description: String,
    val technicalDetail: String,
    val weight: Double,
    val triggered: Boolean
)
