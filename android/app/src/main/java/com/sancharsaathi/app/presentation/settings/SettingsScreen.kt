package com.sancharsaathi.app.presentation.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.BuildConfig
import com.sancharsaathi.app.R
import com.sancharsaathi.app.data.local.AppLanguage
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
    val langStore = remember { AppModule.languageConfigStore }
    val currentLang by langStore.currentLanguageFlow.collectAsState()
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
            AppTopBar(title = stringResource(id = R.string.settings_title), onBackClick = onNavigateBack)
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Section 0: Language Selection
            item {
                Text(
                    text = stringResource(id = R.string.language_section),
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { langStore.currentLanguage = AppLanguage.ENGLISH }
                        ) {
                            RadioButton(
                                selected = currentLang == AppLanguage.ENGLISH,
                                onClick = { langStore.currentLanguage = AppLanguage.ENGLISH }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.lang_english),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { langStore.currentLanguage = AppLanguage.HINDI }
                        ) {
                            RadioButton(
                                selected = currentLang == AppLanguage.HINDI,
                                onClick = { langStore.currentLanguage = AppLanguage.HINDI }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.lang_hindi),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Section 1: Backend Connection Settings
            item {
                Text(
                    text = stringResource(id = R.string.backend_settings),
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
                            text = stringResource(id = R.string.current_base_url, configStore.getBaseUrl()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Connection Mode Selectors
                        Text(text = stringResource(id = R.string.connection_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                                Text(text = stringResource(id = R.string.usb_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = stringResource(id = R.string.usb_mode_desc), style = MaterialTheme.typography.bodySmall)
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
                                Text(text = stringResource(id = R.string.wifi_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = stringResource(id = R.string.wifi_mode_desc), style = MaterialTheme.typography.bodySmall)
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
                                label = { Text(stringResource(id = R.string.laptop_host)) },
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
                                label = { Text(stringResource(id = R.string.fastapi_port)) },
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

                                            val dbText = when (body.database) {
                                                "persistent_sqlite" -> "Persistent SQLite"
                                                "connected" -> "Connected"
                                                else -> "In-Memory Fallback"
                                            }
                                            val ti = body.threatIntel
                                            val threatIntelText = if (ti?.reachable == true) {
                                                "✓ PhishDestroy Connected"
                                            } else {
                                                "✗ PhishDestroy Unavailable (${ti?.details ?: "Unreachable"})"
                                            }
                                            val identityText = when (body.identityProvider) {
                                                "dlt_trai_registry" -> "TRAI DLT Registry"
                                                else -> "Mock Identity Engine"
                                            }

                                            connectionTestStatus = "● Backend: Connected (v${body.version})"
                                            connectionTestDetails = listOf(
                                                "Database: $dbText",
                                                "Threat Intel: $threatIntelText",
                                                "Identity Engine: $identityText",
                                                mlStatusText,
                                                "Pipeline: Fully Operational"
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
                            Text(if (isTestingConnection) stringResource(id = R.string.testing_connection) else stringResource(id = R.string.test_connection))
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
                    text = stringResource(id = R.string.sms_contacts_permissions),
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
                                text = stringResource(id = R.string.sms_contacts_scanning),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (hasPermission) stringResource(id = R.string.granted) else stringResource(id = R.string.not_granted),
                                color = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.permissions_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!hasPermission) {
                            Spacer(modifier = Modifier.height(12.dp))
                            PrimaryButton(
                                text = stringResource(id = R.string.request_permissions_button),
                                onClick = onRequestSmsPermissions
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Section 3: Link Protection
            item {
                Text(
                    text = stringResource(id = R.string.link_protection),
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
                                text = stringResource(id = R.string.web_link_security_gate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "● " + stringResource(id = R.string.active),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.link_gate_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                                    } else {
                                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        android.widget.Toast.makeText(context, "Could not open system link settings", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(id = R.string.set_as_link_handler))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Section 4: App Info
            item {
                Text(
                    text = stringResource(id = R.string.about_app),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(id = R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stringResource(id = R.string.active_base_url, configStore.getBaseUrl()), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
