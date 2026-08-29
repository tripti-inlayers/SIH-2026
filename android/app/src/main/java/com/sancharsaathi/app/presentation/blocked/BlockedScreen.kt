package com.sancharsaathi.app.presentation.blocked

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.components.*
import com.sancharsaathi.app.presentation.result.TechnicalDetailsSection
import com.sancharsaathi.app.presentation.theme.RiskColors

@Composable
fun BlockedScreen(
    result: RiskResult,
    onNavigateBack: () -> Unit,
    onNavigateToReport: () -> Unit
) {
    var showTechnicalDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(id = R.string.security_alert), onBackClick = onNavigateBack)
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Link Blocked Icon",
                        tint = RiskColors.highText,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.link_blocked),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = RiskColors.highText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.link_blocked_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    RiskBadge(riskLevel = result.riskLevel, riskScore = result.riskScore)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (result.detectedUrl != null) {
                item {
                    Text(
                        text = stringResource(id = R.string.blocked_target_link),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.detectedUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.risk_indicators_triggered),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                result.reasons.take(4).forEach { reason ->
                    ReasonRow(reasonText = reason, iconColor = RiskColors.highText)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (showTechnicalDetails) {
                item {
                    TechnicalDetailsSection(
                        signals = result.signals,
                        modelVersion = result.modelVersion,
                        confidence = result.confidence,
                        degraded = result.degraded,
                        degradedReason = result.degradedReason
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                PrimaryButton(
                    text = stringResource(id = R.string.report_threat),
                    onClick = onNavigateToReport
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryButton(
                    text = if (showTechnicalDetails) stringResource(id = R.string.hide_details) else stringResource(id = R.string.view_details),
                    onClick = { showTechnicalDetails = !showTechnicalDetails }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryButton(
                    text = stringResource(id = R.string.back),
                    onClick = onNavigateBack
                )
            }
        }
    }
}
