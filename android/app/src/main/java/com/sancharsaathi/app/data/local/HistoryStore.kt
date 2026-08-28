package com.sancharsaathi.app.data.local

import android.content.Context
import com.sancharsaathi.app.domain.model.CaptureSource
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryStore(context: Context? = null) {

    private var dbHelper: DetectionDatabaseHelper? = context?.let { DetectionDatabaseHelper(it) }

    fun setContext(context: Context) {
        if (dbHelper == null) {
            dbHelper = DetectionDatabaseHelper(context.applicationContext)
        }
    }

    val history: Flow<List<RiskResult>>
        get() {
            val helper = dbHelper ?: return kotlinx.coroutines.flow.flowOf(emptyList())
            return helper.dbUpdateSignal.map {
                helper.getAllDetections().map { entityToRiskResult(it) }
            }
        }

    val realSmsHistory: Flow<List<RiskResult>>
        get() {
            val helper = dbHelper ?: return kotlinx.coroutines.flow.flowOf(emptyList())
            return helper.dbUpdateSignal.map {
                helper.getRealSmsDetections(50).map { entityToRiskResult(it) }
            }
        }

    fun add(result: RiskResult, source: CaptureSource = CaptureSource.SMS, status: String = "ANALYZED") {
        val helper = dbHelper ?: return
        val entity = riskResultToEntity(result, source, status)
        helper.upsertDetection(entity)
    }

    fun get(analysisId: String): RiskResult? {
        val helper = dbHelper ?: return null
        val entity = helper.getDetectionById(analysisId) ?: return null
        return entityToRiskResult(entity)
    }

    private fun riskResultToEntity(result: RiskResult, source: CaptureSource, status: String): DetectionEntity {
        val effectiveSource = when {
            source == CaptureSource.SHARED || result.sender == "MANUAL_INPUT" -> "MANUAL_INPUT"
            result.sender == "URL_ANALYSIS" -> "URL_ANALYSIS"
            source == CaptureSource.DEMO -> "DEMO"
            source == CaptureSource.SMS -> "REAL_SMS"
            else -> "REAL_SMS"
        }

        return DetectionEntity(
            analysisId = result.analysisId,
            source = effectiveSource,
            status = status,
            sender = result.sender,
            message = result.smsBody ?: (result.reasons.firstOrNull() ?: "Message Analysis"),
            timestamp = if (result.timestamp != 0L) result.timestamp else System.currentTimeMillis(),
            riskScore = result.riskScore,
            riskLevel = result.riskLevel.name,
            reasons = result.reasons,
            signals = result.signals,
            urls = listOfNotNull(result.detectedUrl),
            recommendedAction = result.recommendedAction,
            shouldBlock = result.shouldBlock,
            shouldReport = result.shouldReport,
            detectedUrl = result.detectedUrl,
            matchedTemplate = result.signals.firstOrNull { it.category == "local_template" }?.code,
            createdAt = System.currentTimeMillis(),
            analyzedAt = System.currentTimeMillis()
        )
    }

    private fun entityToRiskResult(entity: DetectionEntity): RiskResult {
        val level = try {
            RiskLevel.valueOf(entity.riskLevel.uppercase())
        } catch (e: Exception) {
            RiskLevel.LOW
        }

        return RiskResult(
            analysisId = entity.analysisId,
            riskScore = entity.riskScore,
            riskLevel = level,
            confidence = 0.95,
            reasons = entity.reasons,
            signals = entity.signals,
            recommendedAction = entity.recommendedAction,
            shouldBlock = entity.shouldBlock,
            shouldReport = entity.shouldReport,
            detectedUrl = entity.detectedUrl,
            sender = entity.sender,
            modelVersion = "1.0.0",
            degraded = entity.status == "ERROR" || entity.status == "PENDING",
            degradedReason = if (entity.status == "ERROR") "backend_offline" else null,
            smsBody = entity.message,
            timestamp = entity.timestamp
        )
    }
}
