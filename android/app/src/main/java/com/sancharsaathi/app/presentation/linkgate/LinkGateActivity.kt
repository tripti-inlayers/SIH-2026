package com.sancharsaathi.app.presentation.linkgate

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.domain.model.*
import com.sancharsaathi.app.presentation.theme.SancharSaathiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale

sealed class LinkGateState {
    object Checking : LinkGateState()
    data class HighRisk(val result: RiskResult, val uri: Uri) : LinkGateState()
    data class Suspicious(val result: RiskResult, val uri: Uri) : LinkGateState()
    data class LowRisk(val result: RiskResult, val uri: Uri) : LinkGateState()
    data class Unavailable(val reason: String) : LinkGateState()
    data class UnsupportedScheme(val scheme: String) : LinkGateState()
    data class NoExternalBrowser(val uri: Uri) : LinkGateState()
}

class LinkGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingUri = intent?.data

        setContent {
            val langStore = remember { AppModule.languageConfigStore }
            val currentLang by langStore.currentLanguageFlow.collectAsState()

            val locale = remember(currentLang) { Locale(currentLang.code) }
            val config = remember(currentLang) {
                Configuration(this.resources.configuration).apply {
                    setLocale(locale)
                }
            }
            val contextWithLocale = remember(currentLang) {
                this.createConfigurationContext(config)
            }

            CompositionLocalProvider(LocalContext provides contextWithLocale) {
                SancharSaathiTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LinkGateScreen(
                            incomingUri = incomingUri,
                            onClose = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkGateScreen(
    incomingUri: Uri?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<LinkGateState>(LinkGateState.Checking) }

    LaunchedEffect(incomingUri) {
        if (incomingUri == null) {
            state = LinkGateState.UnsupportedScheme("No URL provided")
            return@LaunchedEffect
        }

        val scheme = incomingUri.scheme?.lowercase() ?: ""
        Log.d("LinkGate", "LINK_GATE_RECEIVED scheme=$scheme host=${incomingUri.host}")

        if (scheme != "http" && scheme != "https") {
            Log.w("LinkGate", "LINK_REJECTED unsupported_scheme=$scheme")
            state = LinkGateState.UnsupportedScheme(scheme)
            return@LaunchedEffect
        }

        val urlString = incomingUri.toString()
        Log.d("LinkGate", "LINK_ANALYSIS_START host=${incomingUri.host}")

        withContext(Dispatchers.IO) {
            val req = AnalysisRequest(
                messageId = "LINK-${System.currentTimeMillis()}",
                text = urlString,
                urls = listOf(urlString),
                senderId = "LINK_GATE",
                claimedOrganization = null,
                language = "en",
                timestampEpochMillis = System.currentTimeMillis(),
                source = CaptureSource.URL_ANALYSIS
            )

            val netResult = try {
                withTimeout(8000L) {
                    AppModule.analyzeContentUseCase(req)
                }
            } catch (e: Exception) {
                Log.e("LinkGate", "Link analysis network check failed: ${e.message}")
                com.sancharsaathi.app.data.remote.NetworkResult.Failure(
                    reason = com.sancharsaathi.app.data.remote.FailureReason.UNKNOWN,
                    message = e.message ?: "Network error"
                )
            }

            val finalResult: RiskResult = when (netResult) {
                is com.sancharsaathi.app.data.remote.NetworkResult.Success -> {
                    netResult.data.copy(detectedUrl = urlString, smsBody = urlString)
                }
                is com.sancharsaathi.app.data.remote.NetworkResult.Failure -> {
                    val onDevice = com.sancharsaathi.app.domain.engine.OnDeviceSecurityEngine.analyze(
                        analysisId = "LINK-${System.currentTimeMillis()}",
                        text = urlString,
                        sender = "LINK_GATE",
                        timestamp = System.currentTimeMillis(),
                        source = CaptureSource.URL_ANALYSIS
                    )
                    onDevice.copy(
                        degraded = true,
                        degradedReason = "backend_unreachable (${netResult.message})"
                    )
                }
            }

            Log.d("LinkGate", "PHISHDESTROY_RESULT threat=${finalResult.threatIntel?.threat} risk=${finalResult.threatIntel?.riskScore}")
            Log.d("LinkGate", "LINK_FINAL_RESULT score=${finalResult.riskScore} level=${finalResult.riskLevel}")

            withContext(Dispatchers.Main) {
                if (finalResult.degraded && finalResult.riskScore <= 0 && finalResult.threatIntel?.checked != true) {
                    state = LinkGateState.Unavailable("Security check service unavailable.")
                } else if (finalResult.riskLevel == RiskLevel.HIGH || finalResult.riskScore >= 70 || finalResult.threatIntel?.threat == true) {
                    Log.w("LinkGate", "LINK_BLOCKED host=${incomingUri.host}")
                    state = LinkGateState.HighRisk(finalResult, incomingUri)
                } else if (finalResult.riskLevel == RiskLevel.SUSPICIOUS || finalResult.riskScore >= 40) {
                    state = LinkGateState.Suspicious(finalResult, incomingUri)
                } else {
                    state = LinkGateState.LowRisk(finalResult, incomingUri)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.link_gate_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(id = R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val currState = state) {
                is LinkGateState.Checking -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(id = R.string.checking_link_security),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = incomingUri?.toString() ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is LinkGateState.HighRisk -> {
                    HighRiskGateContent(
                        result = currState.result,
                        uri = currState.uri,
                        onGoBack = onClose
                    )
                }

                is LinkGateState.Suspicious -> {
                    SuspiciousGateContent(
                        result = currState.result,
                        uri = currState.uri,
                        onGoBack = onClose,
                        onContinue = {
                            openExternalBrowser(context, currState.uri) {
                                state = LinkGateState.NoExternalBrowser(currState.uri)
                            }
                        }
                    )
                }

                is LinkGateState.LowRisk -> {
                    LowRiskGateContent(
                        result = currState.result,
                        uri = currState.uri,
                        onGoBack = onClose,
                        onContinue = {
                            openExternalBrowser(context, currState.uri) {
                                state = LinkGateState.NoExternalBrowser(currState.uri)
                            }
                        }
                    )
                }

                is LinkGateState.Unavailable -> {
                    UnavailableGateContent(
                        reason = currState.reason,
                        onGoBack = onClose
                    )
                }

                is LinkGateState.UnsupportedScheme -> {
                    UnsupportedSchemeContent(
                        scheme = currState.scheme,
                        onGoBack = onClose
                    )
                }

                is LinkGateState.NoExternalBrowser -> {
                    NoExternalBrowserContent(
                        uri = currState.uri,
                        onGoBack = onClose
                    )
                }
            }
        }
    }
}

@Composable
fun HighRiskGateContent(
    result: RiskResult,
    uri: Uri,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Danger",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.dangerous_link_blocked),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.high_risk_gate_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = uri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(id = R.string.risk_score_label), fontWeight = FontWeight.Bold)
                    Text(
                        "${result.riskScore}/100 — ${result.riskLevel}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (result.reasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(id = R.string.reasons_label), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    result.reasons.forEach { reason ->
                        Text("• $reason", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                result.threatIntel?.let { ti ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Threat Intelligence (${ti.provider.uppercase()}):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Threat Detected: ${if (ti.threat) "YES" else "NO"}", fontSize = 13.sp)
                    if (!ti.flags.isEmpty()) {
                        Text("Flags: ${ti.flags.joinToString()}", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGoBack,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.back_safe), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SuspiciousGateContent(
    result: RiskResult,
    uri: Uri,
    onGoBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.suspicious_gate_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.suspicious_gate_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = uri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(id = R.string.risk_score_label), fontWeight = FontWeight.Bold)
                    Text("${result.riskScore}/100 — ${stringResource(id = R.string.risk_suspicious)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                if (result.reasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(id = R.string.reasons_label), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    result.reasons.forEach { reason ->
                        Text("• $reason", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onGoBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(stringResource(id = R.string.back))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(stringResource(id = R.string.continue_anyway), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LowRiskGateContent(
    result: RiskResult,
    uri: Uri,
    onGoBack: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Safe",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.low_risk_gate_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.low_risk_gate_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color.Black.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = uri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(id = R.string.risk_score_label), fontWeight = FontWeight.Bold)
                Text("${result.riskScore}/100 — ${stringResource(id = R.string.risk_low)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(stringResource(id = R.string.continue_to_webpage), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun UnavailableGateContent(
    reason: String,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Unavailable",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.sec_check_unavailable),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.sec_check_unavailable_desc, reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGoBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(id = R.string.back_safe))
        }
    }
}

@Composable
fun UnsupportedSchemeContent(
    scheme: String,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Blocked",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.unsupported_scheme),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.unsupported_scheme_desc, scheme),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGoBack,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(id = R.string.back))
        }
    }
}

@Composable
fun NoExternalBrowserContent(
    uri: Uri,
    onGoBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "No Browser",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.no_browser_available),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.no_browser_available_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGoBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(stringResource(id = R.string.back))
        }
    }
}

fun openExternalBrowser(context: Context, originalUri: Uri, onNoBrowser: () -> Unit = {}) {
    val host = originalUri.host ?: "unknown"
    Log.d("LinkGate", "FORWARDING_EXTERNAL_BROWSER host=$host")

    try {
        val targetIntent = Intent(Intent.ACTION_VIEW, originalUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val packageManager = context.packageManager
        val candidates = mutableListOf<android.content.pm.ResolveInfo>()

        try {
            val defaultList = packageManager.queryIntentActivities(targetIntent, PackageManager.MATCH_DEFAULT_ONLY)
            candidates.addAll(defaultList)
        } catch (e: Exception) { }

        try {
            val allList = packageManager.queryIntentActivities(targetIntent, PackageManager.MATCH_ALL)
            candidates.addAll(allList)
        } catch (e: Exception) { }

        val externalCandidate = candidates.firstOrNull {
            it.activityInfo != null && it.activityInfo.packageName != context.packageName
        }

        if (externalCandidate != null) {
            val targetPkg = externalCandidate.activityInfo.packageName
            val activityName = externalCandidate.activityInfo.name
            Log.d("LinkGate", "EXTERNAL_BROWSER_PACKAGE=$targetPkg")

            val explicitIntent = Intent(Intent.ACTION_VIEW, originalUri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setClassName(targetPkg, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(explicitIntent)
            Log.d("LinkGate", "FORWARD_SUCCESS")
            (context as? Activity)?.finish()
        } else {
            val knownBrowsers = listOf(
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "org.mozilla.firefox",
                "com.opera.browser",
                "com.microsoft.emmx",
                "com.brave.browser"
            )

            var launched = false
            for (pkg in knownBrowsers) {
                if (pkg != context.packageName) {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            val directIntent = Intent(Intent.ACTION_VIEW, originalUri).apply {
                                addCategory(Intent.CATEGORY_BROWSABLE)
                                setPackage(pkg)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            Log.d("LinkGate", "EXTERNAL_BROWSER_PACKAGE=$pkg")
                            context.startActivity(directIntent)
                            Log.d("LinkGate", "FORWARD_SUCCESS")
                            launched = true
                            (context as? Activity)?.finish()
                            break
                        }
                    } catch (e: Exception) { }
                }
            }

            if (!launched) {
                Log.w("LinkGate", "NO_EXTERNAL_BROWSER")
                onNoBrowser()
            }
        }
    } catch (e: ActivityNotFoundException) {
        Log.w("LinkGate", "NO_EXTERNAL_BROWSER")
        onNoBrowser()
    } catch (e: Exception) {
        Log.e("LinkGate", "Error opening external browser: ${e.message}", e)
    }
}
