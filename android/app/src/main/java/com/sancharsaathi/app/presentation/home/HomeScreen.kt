package com.sancharsaathi.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.domain.model.AnalysisRequest
import com.sancharsaathi.app.domain.model.RiskResult
import com.sancharsaathi.app.presentation.components.EmptyState
import com.sancharsaathi.app.presentation.components.PrimaryButton
import com.sancharsaathi.app.presentation.components.RiskBadge
import com.sancharsaathi.app.presentation.theme.RiskColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAnalyzing: (AnalysisRequest) -> Unit,
    onNavigateToResult: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Protection Active status card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RiskColors.lowSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Protection Active status card" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = RiskColors.lowText,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Protection Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = RiskColors.lowText,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your messages and links are checked for common phishing and scam signals.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Demo Scenario Buttons
            item {
                Text(
                    text = "Try Demo Scenarios",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val req = viewModel.launchDemoScenario(1)
                            onNavigateToAnalyzing(req)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskColors.lowSurface),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Low Risk", color = RiskColors.lowText, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val req = viewModel.launchDemoScenario(2)
                            onNavigateToAnalyzing(req)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskColors.suspiciousSurface),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Suspicious", color = RiskColors.suspiciousText, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val req = viewModel.launchDemoScenario(3)
                            onNavigateToAnalyzing(req)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskColors.highSurface),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("High Risk", color = RiskColors.highText, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Recent activity
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (uiState) {
                is HomeUiState.Loading -> {
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                }
                is HomeUiState.Success -> {
                    val list = (uiState as HomeUiState.Success).recentAnalyses
                    if (list.isEmpty()) {
                        item {
                            EmptyState(message = "No messages checked yet.")
                        }
                    } else {
                        items(list) { item ->
                            RecentActivityCard(
                                result = item,
                                onClick = { onNavigateToResult(item.analysisId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentActivityCard(
    result: RiskResult,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.sender ?: "Unknown Sender",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.detectedUrl ?: (result.reasons.firstOrNull() ?: "Message analysis"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            RiskBadge(riskLevel = result.riskLevel, riskScore = result.riskScore)
        }
    }
}
