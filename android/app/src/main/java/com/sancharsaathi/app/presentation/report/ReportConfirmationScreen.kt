package com.sancharsaathi.app.presentation.report

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.components.AppTopBar
import com.sancharsaathi.app.presentation.components.ErrorState
import com.sancharsaathi.app.presentation.components.PrimaryButton
import com.sancharsaathi.app.presentation.components.RiskBadge
import com.sancharsaathi.app.presentation.theme.RiskColors

@Composable
fun ReportConfirmationScreen(
    viewModel: ReportViewModel,
    riskResult: RiskResult,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(riskResult) {
        viewModel.submitReport(riskResult)
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(id = R.string.report_submitted))
        }
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            when (val state = uiState) {
                is ReportUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is ReportUiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.submitReport(riskResult) }
                    )
                }
                is ReportUiState.Success -> {
                    val report = state.report
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success checkmark",
                            tint = RiskColors.lowText,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.threat_reported),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.report_id_format, report.reportId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.threat_type_format, report.threatType),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        RiskBadge(riskLevel = report.riskLevel, riskScore = report.riskScore)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = report.integrationNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        PrimaryButton(text = stringResource(id = R.string.done), onClick = onNavigateHome)
                    }
                }
            }
        }
    }
}
