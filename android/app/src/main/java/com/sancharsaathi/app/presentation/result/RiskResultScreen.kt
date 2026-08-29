package com.sancharsaathi.app.presentation.result

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskResultScreen(
    viewModel: RiskResultViewModel,
    analysisId: String,
    initialResult: RiskResult? = null,
    onNavigateBack: () -> Unit,
    onNavigateToReport: (RiskResult) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showVerifySheet by remember { mutableStateOf(false) }

    LaunchedEffect(analysisId) {
        viewModel.loadResult(analysisId, initialResult)
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(id = R.string.analysis_result), onBackClick = onNavigateBack)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is RiskResultUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is RiskResultUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.loadResult(analysisId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is RiskResultUiState.Success -> {
                    val result = state.result
                    ResultContent(
                        result = result,
                        onNavigateBack = onNavigateBack,
                        onNavigateToReport = { onNavigateToReport(result) },
                        onVerifyClick = { showVerifySheet = true }
                    )
                }
            }
        }
    }

    if (showVerifySheet) {
        ModalBottomSheet(onDismissRequest = { showVerifySheet = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(id = R.string.verification_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.verification_guide_text),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(text = stringResource(id = R.string.understood), onClick = { showVerifySheet = false })
            }
        }
    }
}

@Composable
fun ResultContent(
    result: RiskResult,
    onNavigateBack: () -> Unit,
    onNavigateToReport: () -> Unit,
    onVerifyClick: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                RiskBadge(riskLevel = result.riskLevel, riskScore = result.riskScore)
                Spacer(modifier = Modifier.height(16.dp))
                
                val verdictText = when (result.riskLevel) {
                    RiskLevel.LOW -> stringResource(id = R.string.verdict_low)
                    RiskLevel.SUSPICIOUS -> stringResource(id = R.string.verdict_suspicious)
                    RiskLevel.HIGH -> stringResource(id = R.string.verdict_high)
                }
                Text(
                    text = verdictText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.recommendedAction,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                if (result.degraded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.verification_unavailable_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = stringResource(id = R.string.key_reasons_identified),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            result.reasons.take(4).forEach { reason ->
                ReasonRow(reasonText = reason)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (result.detectedUrl != null) {
            item {
                val context = LocalContext.current
                Text(
                    text = stringResource(id = R.string.detected_target_url),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    onClick = {
                        try {
                            val gateIntent = Intent(context, com.sancharsaathi.app.presentation.linkgate.LinkGateActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                data = android.net.Uri.parse(result.detectedUrl)
                            }
                            context.startActivity(gateIntent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open link gate: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = result.detectedUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Inspect Link",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Dedicated PhishDestroy Threat Intelligence Card
                val ti = result.threatIntel
                if (ti != null && ti.checked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (ti.threat) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(id = R.string.threat_intel_header),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (ti.threat) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusText = when {
                                ti.verdict == "CHECKED_THREAT" || ti.threat -> "● Flagged Threat (Severity: ${ti.severity ?: "unknown"})"
                                ti.reachable -> "● Verified Clean (No matching threat on reputation lists)"
                                else -> "● Lookup Unavailable (${ti.error ?: "Offline / Degraded"})"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ti.threat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            if (ti.riskScore > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Reputation Score: ${ti.riskScore}/100 | Flags: ${ti.flags.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ti.threat) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        item {
            TechnicalDetailsSection(
                signals = result.signals,
                modelVersion = result.modelVersion,
                confidence = result.confidence,
                degraded = result.degraded,
                degradedReason = result.degradedReason
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            when (result.riskLevel) {
                RiskLevel.LOW -> {
                    SecondaryButton(text = stringResource(id = R.string.back), onClick = onNavigateBack)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.not_completely_safe_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RiskLevel.SUSPICIOUS -> {
                    PrimaryButton(text = stringResource(id = R.string.continue_with_caution), onClick = onNavigateBack)
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(text = stringResource(id = R.string.verify), onClick = onVerifyClick)
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(text = stringResource(id = R.string.back), onClick = onNavigateBack)
                }
                RiskLevel.HIGH -> {
                    PrimaryButton(text = stringResource(id = R.string.report), onClick = onNavigateToReport)
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(text = stringResource(id = R.string.back), onClick = onNavigateBack)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
