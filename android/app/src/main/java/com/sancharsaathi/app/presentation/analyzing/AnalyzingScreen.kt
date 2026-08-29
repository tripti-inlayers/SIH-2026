package com.sancharsaathi.app.presentation.analyzing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.components.LoadingIndicatorStep
import com.sancharsaathi.app.presentation.components.PrimaryButton

@Composable
fun AnalyzingScreen(
    viewModel: AnalyzingViewModel,
    request: AnalysisRequest,
    onNavigateToResult: (RiskResult) -> Unit,
    onNavigateToBlocked: (RiskResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(request) {
        viewModel.analyze(request)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        when (val state = uiState) {
            is AnalyzingUiState.Loading -> {
                LoadingIndicatorStep(
                    onAllStepsCompleted = null
                )
            }
            is AnalyzingUiState.Success -> {
                LaunchedEffect(state.result) {
                    if (state.result.shouldBlock) {
                        onNavigateToBlocked(state.result)
                    } else {
                        onNavigateToResult(state.result)
                    }
                }
            }
            is AnalyzingUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.full_analysis_unavailable),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    PrimaryButton(
                        text = stringResource(id = R.string.retry),
                        onClick = { viewModel.analyze(request) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            val fallback = viewModel.getUnverifiedFallbackResult(request)
                            onNavigateToResult(fallback)
                        }
                    ) {
                        Text(stringResource(id = R.string.continue_without_analysis))
                    }
                }
            }
        }
    }
}
