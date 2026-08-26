package com.sancharsaathi.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AnalyzeRequestDto(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("text") val text: String,
    @SerializedName("urls") val urls: List<String>,
    @SerializedName("sender_id") val senderId: String?,
    @SerializedName("claimed_organization") val claimedOrganization: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("timestamp_epoch_millis") val timestampEpochMillis: Long,
    @SerializedName("source") val source: String
)
