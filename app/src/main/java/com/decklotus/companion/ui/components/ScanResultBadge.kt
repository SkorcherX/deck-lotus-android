package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decklotus.companion.network.IngestResponse
import com.decklotus.companion.network.ScanTier
import com.decklotus.companion.ui.capture.CaptureTimings
import com.decklotus.companion.ui.theme.*

@Composable
fun ScanResultBadge(
    response: IngestResponse?,
    timings: CaptureTimings?,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    if (response == null && errorMessage == null) return

    val tier = if (response != null) ScanTier.fromKey(response.tier) else ScanTier.UNRESOLVED
    val tierColor = when (tier) {
        ScanTier.CONFIDENT -> TierConfident
        ScanTier.PROBABLE -> TierProbable
        ScanTier.PICK_PRINTING -> TierPickPrinting
        ScanTier.UNRESOLVED -> TierUnresolved
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(SurfaceDark, RoundedCornerShape(16.dp))
            .border(1.5.dp, tierColor.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (errorMessage != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TierUnresolved)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Scan Failed", fontWeight = FontWeight.Bold, color = TierUnresolved)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = errorMessage, fontSize = 12.sp, color = TextSecondary)
            return
        }

        val printing = response?.printing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = printing?.name ?: "Unknown Card",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (printing != null) {
                        Text(
                            text = "${printing.setCode} \u2022 #${printing.collector}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = LotusCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        if (printing.isFoil) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "\u2605 FOIL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TierPickPrinting
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .background(tierColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tier.key.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = tierColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (timings != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimingPill(label = "Cap", ms = timings.captureMs)
                TimingPill(label = "Warp+Hash", ms = timings.hashMs)
                TimingPill(label = "NPU OCR", ms = timings.ocrMs)
                TimingPill(label = "Net", ms = timings.networkMs)
                TimingPill(label = "Total", ms = timings.totalMs, isBold = true)
            }
        }
    }
}

@Composable
private fun TimingPill(label: String, ms: Long, isBold: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
        Text(
            text = "${ms}ms",
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) LotusPurple else TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}