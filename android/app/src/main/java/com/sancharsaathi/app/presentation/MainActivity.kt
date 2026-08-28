package com.sancharsaathi.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.sancharsaathi.app.permissions.SmsPermissionManager
import com.sancharsaathi.app.presentation.navigation.SancharSaathiNavGraph
import com.sancharsaathi.app.presentation.theme.SancharSaathiTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!com.sancharsaathi.app.permissions.SmsPermissionManager.hasSmsPermissions(this)) {
            permissionLauncher.launch(com.sancharsaathi.app.permissions.SmsPermissionManager.REQUIRED_PERMISSIONS)
        }

        setContent {
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
