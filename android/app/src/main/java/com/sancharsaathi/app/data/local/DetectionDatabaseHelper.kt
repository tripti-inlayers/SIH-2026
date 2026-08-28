package com.sancharsaathi.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sancharsaathi.app.domain.model.CaptureSource
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.domain.model.RiskSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectionEntity(
    val analysisId: String,
    val source: String,
    val status: String,
    val sender: String?,
    val message: String,
    val timestamp: Long,
    val riskScore: Int,
    val riskLevel: String,
    val reasons: List<String>,
    val signals: List<RiskSignal>,
    val urls: List<String>,
    val recommendedAction: String,
    val shouldBlock: Boolean,
    val shouldReport: Boolean,
    val detectedUrl: String?,
    val matchedTemplate: String?,
    val createdAt: Long,
    val analyzedAt: Long
)

class DetectionDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val gson = Gson()
    private val _dbUpdateSignal = MutableStateFlow(System.currentTimeMillis())
    val dbUpdateSignal: StateFlow<Long> = _dbUpdateSignal.asStateFlow()

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_NAME (
                analysis_id TEXT PRIMARY KEY,
                source TEXT NOT NULL,
                status TEXT NOT NULL,
                sender TEXT,
                message TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                risk_score INTEGER NOT NULL,
                risk_level TEXT NOT NULL,
                reasons TEXT NOT NULL,
                signals TEXT NOT NULL,
                urls TEXT NOT NULL,
                recommended_action TEXT NOT NULL,
                should_block INTEGER NOT NULL,
                should_report INTEGER NOT NULL,
                detected_url TEXT,
                matched_template TEXT,
                created_at INTEGER NOT NULL,
                analyzed_at INTEGER NOT NULL
            );
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun upsertDetection(entity: DetectionEntity) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("analysis_id", entity.analysisId)
            put("source", entity.source)
            put("status", entity.status)
            put("sender", entity.sender)
            put("message", entity.message)
            put("timestamp", entity.timestamp)
            put("risk_score", entity.riskScore)
            put("risk_level", entity.riskLevel)
            put("reasons", gson.toJson(entity.reasons))
            put("signals", gson.toJson(entity.signals))
            put("urls", gson.toJson(entity.urls))
            put("recommended_action", entity.recommendedAction)
            put("should_block", if (entity.shouldBlock) 1 else 0)
            put("should_report", if (entity.shouldReport) 1 else 0)
            put("detected_url", entity.detectedUrl)
            put("matched_template", entity.matchedTemplate)
            put("created_at", entity.createdAt)
            put("analyzed_at", entity.analyzedAt)
        }
        db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        _dbUpdateSignal.value = System.currentTimeMillis()
    }

    fun getAllDetections(): List<DetectionEntity> {
        val list = mutableListOf<DetectionEntity>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY timestamp DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEntity(it))
            }
        }
        return list
    }

    fun getRealSmsDetections(limit: Int = 50): List<DetectionEntity> {
        val list = mutableListOf<DetectionEntity>()
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_NAME WHERE source NOT IN ('MANUAL_INPUT', 'URL_ANALYSIS') ORDER BY timestamp DESC LIMIT $limit"
        val cursor = db.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEntity(it))
            }
        }
        return list
    }

    fun getDetectionById(analysisId: String): DetectionEntity? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE analysis_id = ? LIMIT 1", arrayOf(analysisId))
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToEntity(it)
            }
        }
        return null
    }

    private fun cursorToEntity(cursor: android.database.Cursor): DetectionEntity {
        val stringListType = object : TypeToken<List<String>>() {}.type
        val signalListType = object : TypeToken<List<RiskSignal>>() {}.type

        val reasonsJson = cursor.getString(cursor.getColumnIndexOrThrow("reasons"))
        val signalsJson = cursor.getString(cursor.getColumnIndexOrThrow("signals"))
        val urlsJson = cursor.getString(cursor.getColumnIndexOrThrow("urls"))

        val reasons: List<String> = gson.fromJson(reasonsJson, stringListType) ?: emptyList()
        val signals: List<RiskSignal> = gson.fromJson(signalsJson, signalListType) ?: emptyList()
        val urls: List<String> = gson.fromJson(urlsJson, stringListType) ?: emptyList()

        return DetectionEntity(
            analysisId = cursor.getString(cursor.getColumnIndexOrThrow("analysis_id")),
            source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            sender = cursor.getString(cursor.getColumnIndexOrThrow("sender")),
            message = cursor.getString(cursor.getColumnIndexOrThrow("message")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
            riskScore = cursor.getInt(cursor.getColumnIndexOrThrow("risk_score")),
            riskLevel = cursor.getString(cursor.getColumnIndexOrThrow("risk_level")),
            reasons = reasons,
            signals = signals,
            urls = urls,
            recommendedAction = cursor.getString(cursor.getColumnIndexOrThrow("recommended_action")),
            shouldBlock = cursor.getInt(cursor.getColumnIndexOrThrow("should_block")) == 1,
            shouldReport = cursor.getInt(cursor.getColumnIndexOrThrow("should_report")) == 1,
            detectedUrl = cursor.getString(cursor.getColumnIndexOrThrow("detected_url")),
            matchedTemplate = cursor.getString(cursor.getColumnIndexOrThrow("matched_template")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            analyzedAt = cursor.getLong(cursor.getColumnIndexOrThrow("analyzed_at"))
        )
    }

    companion object {
        const val DATABASE_NAME = "sancharsaathi.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "detection_history"
    }
}
