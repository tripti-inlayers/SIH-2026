package com.sancharsaathi.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.BuildConfig
import com.sancharsaathi.app.data.local.ConnectionMode
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.permissions.SmsPermissionManager
import com.sancharsaathi.app.presentation.components.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onRequestSmsPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configStore = remember { AppModule.networkConfigStore }
    val hasPermission = remember { SmsPermissionManager.hasSmsPermissions(context) }

    var selectedMode by remember { mutableStateOf(configStore.connectionMode) }
    var wifiHostText by remember { mutableStateOf(configStore.wifiHost) }
    var wifiPortText by remember { mutableStateOf(configStore.wifiPort.toString()) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestStatus by remember { mutableStateOf<String?>(null) }
    var connectionTestDetails by remember { mutableStateOf<List<String>>(emptyList()) }

    fun saveConfig() {
        configStore.connectionMode = selectedMode
        configStore.wifiHost = wifiHostText
        val portInt = wifiPortText.toIntOrNull() ?: 8000
        configStore.wifiPort = portInt
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "Settings & Configuration", onBackClick = onNavigateBack)
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Section 1: Backend Connection Settings
            item {
                Text(
                    text = "Backend Connection Settings",
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
                            text = "Current Base URL: ${configStore.getBaseUrl()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Connection Mode Selectors
                        Text(text = "Connection Mode:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            selectedMode = ConnectionMode.USB
                            configStore.connectionMode = ConnectionMode.USB
                        }) {
                            RadioButton(
                                selected = selectedMode == ConnectionMode.USB,
                                onClick = {
                                    selectedMode = ConnectionMode.USB
                                    configStore.connectionMode = ConnectionMode.USB
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(text = "USB / ADB Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Uses http://127.0.0.1:8000/ (Requires `adb reverse tcp:8000 tcp:8000`)", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                            selectedMode = ConnectionMode.WIFI
                            configStore.connectionMode = ConnectionMode.WIFI
                        }) {
                            RadioButton(
                                selected = selectedMode == ConnectionMode.WIFI,
                                onClick = {
                                    selectedMode = ConnectionMode.WIFI
                                    configStore.connectionMode = ConnectionMode.WIFI
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(text = "Wi-Fi Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Connects directly to laptop LAN IP across local Wi-Fi", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (selectedMode == ConnectionMode.WIFI) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = wifiHostText,
                                onValueChange = {
                                    wifiHostText = it
                                    saveConfig()
                                },
                                label = { Text("Laptop Host / IP") },
                                placeholder = { Text("192.168.29.24") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = wifiPortText,
                                onValueChange = {
                                    wifiPortText = it
                                    saveConfig()
                                },
                                label = { Text("FastAPI Port") },
                                placeholder = { Text("8000") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Test Connection Button
                        Button(
                            onClick = {
                                saveConfig()
                                val target = configStore.getBaseUrl()
                                android.util.Log.d("NetworkConfig", "CONNECTION_TEST_START\nmode=${configStore.connectionMode.name}\ntarget=$target")
                                isTestingConnection = true
                                connectionTestStatus = "Testing connection to $target..."
                                connectionTestDetails = emptyList()

                                scope.launch {
                                    try {
                                        val response = AppModule.apiService.health()
                                        if (response.isSuccessful && response.body() != null) {
                                            val body = response.body()!!
                                            val mlInfo = body.mlService
                                            val mlStatusText = if (mlInfo?.status == "ok") {
                                                "● ML Service: Connected (${if (mlInfo.mockMode == true) "Mock Mode" else "RoBERTa Active"})"
                                            } else {
                                                "● ML Service: Unavailable (${mlInfo?.details ?: "Unreachable"})"
                                            }

                                            connectionTestStatus = "● Backend: Connected (v${body.version})"
                                            connectionTestDetails = listOf(
                                                "Database: ${body.database}",
                                                "Threat Intel: ${body.threatIntelProvider}",
                                                "Identity Engine: ${body.identityProvider}",
                                                mlStatusText
                                            )
                                        } else {
                                            connectionTestStatus = "● Backend Unhealthy (HTTP ${response.code()})"
                                            connectionTestDetails = listOf("Server returned status code ${response.code()}")
                                        }
                                    } catch (e: SocketTimeoutException) {
                                        if (configStore.connectionMode == ConnectionMode.USB) {
                                            connectionTestStatus = "● USB backend unavailable. FastAPI is not responding on port 8000."
                                        } else {
                                            connectionTestStatus = "● Wi-Fi backend unavailable at $target"
                                        }
                                        connectionTestDetails = listOf(e.message ?: "Timeout")
                                    } catch (e: IOException) {
                                        if (configStore.connectionMode == ConnectionMode.USB) {
                                            connectionTestStatus = "● USB backend unavailable. Run:\nadb reverse tcp:8000 tcp:8000"
                                        } else {
                                            connectionTestStatus = "● Wi-Fi backend unavailable at $target"
                                        }
                                        connectionTestDetails = listOf(e.message ?: "Connection Refused")
                                    } catch (e: Exception) {
                                        connectionTestStatus = "● Connection Error"
                                        connectionTestDetails = listOf(e.message ?: "Unknown error")
                                    } finally {
                                        isTestingConnection = false
                                    }
                                }
                            },
                            enabled = !isTestingConnection,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isTestingConnection) "Testing..." else "Test Connection")
                        }

                        if (connectionTestStatus != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = connectionTestStatus!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (connectionTestStatus!!.contains("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            connectionTestDetails.forEach { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Section 2: Permissions
            item {
                Text(
                    text = "SMS & Contacts Permissions",
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
                                text = "SMS & Contacts Scanning",
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
                            text = "Used to scan SMS messages for scams and resolve sender phone numbers to contact names. Local pattern classification runs offline instantly.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!hasPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            PrimaryButton(
                                text = "Request SMS & Contact Permissions",
                                onClick = onRequestSmsPermissions
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Section 3: App Info
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
                        Text(text = "Active Base URL: ${configStore.getBaseUrl()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
