package com.sancharsaathi.app.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LoadingIndicatorStep(
    modifier: Modifier = Modifier,
    onAllStepsCompleted: (() -> Unit)? = null
) {
    val steps = remember {
        listOf(
            "Checking this message...",
            "Analyzing message",
            "Checking link",
            "Verifying sender"
        )
    }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        for (i in steps.indices) {
            currentStepIndex = i
            delay(700)
        }
        onAllStepsCompleted?.invoke()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(24.dp)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Crossfade(targetState = steps[currentStepIndex], label = "LoadingStep") { stepText ->
            Text(
                text = stepText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
