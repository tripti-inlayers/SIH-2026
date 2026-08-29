package com.sancharsaathi.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sancharsaathi.app.R
import com.sancharsaathi.app.domain.model.RiskLevel
import com.sancharsaathi.app.presentation.theme.RiskColors

@Composable
fun RiskBadge(
    riskLevel: RiskLevel,
    riskScore: Int,
    degraded: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (riskScore == -2) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .semantics { contentDescription = "Analyzing" }
                .background(RiskColors.lowSurface, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.analyzing_badge),
                color = RiskColors.lowText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        return
    }

    if (riskScore < 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .semantics { contentDescription = "Analysis Unavailable" }
                .background(RiskColors.lowSurface, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.analysis_unavailable_badge),
                color = RiskColors.lowText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        return
    }

    val (bgColor, textColor, labelRes) = when (riskLevel) {
        RiskLevel.LOW -> Triple(RiskColors.lowSurface, RiskColors.lowText, R.string.risk_low)
        RiskLevel.SUSPICIOUS -> Triple(RiskColors.suspiciousSurface, RiskColors.suspiciousText, R.string.risk_suspicious)
        RiskLevel.HIGH -> Triple(RiskColors.highSurface, RiskColors.highText, R.string.risk_high)
    }

    val labelText = stringResource(id = labelRes)
    val announcementText = "$labelText, score $riskScore out of 100"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .semantics { contentDescription = announcementText }
            .background(bgColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = labelText,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$riskScore/100",
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
