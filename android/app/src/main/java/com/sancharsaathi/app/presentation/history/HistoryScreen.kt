package com.sancharsaathi.app.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.presentation.components.AppTopBar
import com.sancharsaathi.app.presentation.components.EmptyState
import com.sancharsaathi.app.presentation.home.RecentActivityCard

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String) -> Unit
) {
    val historyList by viewModel.historyState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(id = R.string.analysis_history), onBackClick = onNavigateBack)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (historyList.isEmpty()) {
                EmptyState(message = stringResource(id = R.string.no_messages_checked))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(historyList) { item ->
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
