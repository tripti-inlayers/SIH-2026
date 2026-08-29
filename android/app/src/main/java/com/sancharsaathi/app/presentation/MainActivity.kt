package com.sancharsaathi.app.presentation

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.sancharsaathi.app.di.AppModule
import com.sancharsaathi.app.permissions.SmsPermissionManager
import com.sancharsaathi.app.presentation.navigation.SancharSaathiNavGraph
import com.sancharsaathi.app.presentation.theme.SancharSaathiTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SmsPermissionManager.hasSmsPermissions(this)) {
            permissionLauncher.launch(SmsPermissionManager.REQUIRED_PERMISSIONS)
        }

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
                        val navController = rememberNavController()
                        SancharSaathiNavGraph(
                            navController = navController,
                            intent = intent,
                            onRequestSmsPermissions = {
                                permissionLauncher.launch(SmsPermissionManager.REQUIRED_PERMISSIONS)
                            }
                        )
                    }
                }
            }
        }
    }
}
