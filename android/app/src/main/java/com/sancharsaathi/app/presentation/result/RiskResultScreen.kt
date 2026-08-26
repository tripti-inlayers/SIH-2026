package com.sancharsaathi.app.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val uiState by viewModel.uiState.collectAsState()
    var showVerifySheet by remember { mutableStateOf(false) }

    LaunchedEffect(analysisId) {
        viewModel.loadResult(analysisId, initialResult)
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "Analysis Result", onBackClick = onNavigateBack)
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
                    text = "Independent Verification Guide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "1. Do not use phone numbers or links included in the message.\n" +
                           "2. Visit the official website directly by typing its web address.\n" +
                           "3. Call official customer support numbers listed on verified portals (e.g. Sanchar Saathi / Chakshu portal).",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(text = "Understood", onClick = { showVerifySheet = false })
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
                    RiskLevel.LOW -> "Looks okay."
                    RiskLevel.SUSPICIOUS -> "Looks suspicious. Verify before you act."
                    RiskLevel.HIGH -> "Don't open this link."
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
                        text = "Verification unavailable — proceed with caution.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = "Key Reasons Identified",
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
                Text(
                    text = "Detected Target URL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = result.detectedUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
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
                    SecondaryButton(text = "Go Back", onClick = onNavigateBack)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This does not guarantee the message is completely safe",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RiskLevel.SUSPICIOUS -> {
                    PrimaryButton(text = "Continue with caution", onClick = onNavigateBack)
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(text = "Verify", onClick = onVerifyClick)
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(text = "Go Back", onClick = onNavigateBack)
                }
                RiskLevel.HIGH -> {
                    PrimaryButton(text = "Report", onClick = onNavigateToReport)
                    Spacer(modifier = Modifier.height(12.dp))
                    SecondaryButton(text = "Go Back", onClick = onNavigateBack)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
