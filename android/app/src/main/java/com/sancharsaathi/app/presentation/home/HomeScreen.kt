package com.sancharsaathi.app.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var manualInputText by remember { mutableStateOf("") }

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
                    IconButton(onClick = { viewModel.refreshInbox() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh SMS feed")
                    }
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
            // 1. Protection Active Status Card
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
                                text = "Supported incoming messages are checked automatically.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 2. Manual Analysis Input Card
            item {
                Text(
                    text = "Analyze Message or Link",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Paste a suspicious message or link below",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualInputText,
                            onValueChange = { manualInputText = it },
                            placeholder = { Text("Paste SMS, WhatsApp message, email, or URL here...") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PrimaryButton(
                            text = "Analyze",
                            onClick = {
                                if (manualInputText.isNotBlank()) {
                                    val req = viewModel.createManualAnalysisRequest(manualInputText)
                                    manualInputText = ""
                                    onNavigateToAnalyzing(req)
                                }
                            },
                            enabled = manualInputText.isNotBlank()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 3. Recent Message Detections Header
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Recent Message Detections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToHistory) {
                        Text("See All")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 4. Recent Detections List
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
                            EmptyState(message = "No messages detected yet. Incoming SMS will appear here automatically.")
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val contactName = remember(result.sender) {
        com.sancharsaathi.app.data.local.ContactHelper.getContactName(context, result.sender)
    }
    val displayName = contactName ?: result.sender ?: "Unknown Sender"

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
                val formattedTime = remember(result.timestamp) {
                    if (result.timestamp > 0L) {
                        try {
                            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(result.timestamp))
                        } catch (e: Exception) {
                            ""
                        }
                    } else ""
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (contactName != null && result.sender != null && result.sender != contactName) {
                            Text(
                                text = result.sender,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (formattedTime.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.smsBody ?: (result.detectedUrl ?: (result.reasons.firstOrNull() ?: "Message analysis")),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            RiskBadge(riskLevel = result.riskLevel, riskScore = result.riskScore, degraded = result.degraded)
        }
    }
}
