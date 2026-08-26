package com.sancharsaathi.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.BuildConfig
import com.sancharsaathi.app.permissions.SmsPermissionManager
import com.sancharsaathi.app.presentation.components.AppTopBar
import com.sancharsaathi.app.presentation.components.PrimaryButton

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onRequestSmsPermissions: () -> Unit
) {
    val context = LocalContext.current
    val hasPermission = remember { SmsPermissionManager.hasSmsPermissions(context) }

    Scaffold(
        topBar = {
            AppTopBar(title = "Settings & App Info", onBackClick = onNavigateBack)
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "SMS Scanning Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Automatic Background Scanning",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (hasPermission) "Granted" else "Not Granted",
                                color = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "On modern Android versions, background SMS reception without being set as the default SMS application is best-effort. Demo Mode and Share-to-App are always available.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!hasPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Request SMS Permissions",
                                onClick = onRequestSmsPermissions
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = "How Detection Works",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SancharSaathi combines multiple independent security signals:\n\n" +
                                   "• Message Content Analysis: Detects urgency cues, credential requests, financial bait, and intimidation language.\n" +
                                   "• URL & Domain Checks: Evaluates lookalike brand imitations, suspicious TLDs, IP hosts, shorteners, and non-HTTPS links.\n" +
                                   "• Threat Intelligence: Queries domain age & threat registries (RDAP / OpenPhish / Web Risk).\n" +
                                   "• Identity Verification: Cross-checks senders against TRAI DLT registered entity headers.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = "About SancharSaathi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Backend Server: ${BuildConfig.BASE_URL}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
