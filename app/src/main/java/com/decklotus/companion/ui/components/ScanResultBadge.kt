package com.decklotus.companion.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    thumbnail: Bitmap?,
    timings: CaptureTimings?,
    errorMessage: String?,
    showDebugInfo: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (response == null && errorMessage == null) return

    val printing = response?.printing
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
            .padding(horizontal = 16.dp)
            .background(Color(0xFF14171D), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF262C36), RoundedCornerShape(16.dp))
            .padding(if (showDebugInfo) 14.dp else 10.dp)
    ) {
        if (errorMessage != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TierUnresolved, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Scan Failed", fontWeight = FontWeight.Bold, color = TierUnresolved, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = errorMessage, fontSize = 11.sp, color = TextSecondary)
            return
        }

        // Top Row: Thumbnail + Info + Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Card thumbnail",
                    modifier = Modifier
                        .size(width = 38.dp, height = 53.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(5.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 53.dp)
                        .background(Color(0xFF212630), RoundedCornerShape(5.dp))
                        .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(5.dp))
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center details
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = printing?.name ?: "Recognized Card",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Set code tag
                    if (printing != null && printing.setCode.isNotBlank()) {
                        Surface(
                            color = Color(0xFF262C36),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = printing.setCode.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LotusCyan,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Collector number
                    if (printing != null && printing.collector.isNotBlank()) {
                        Text(
                            text = "#${printing.collector}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Foil indicator if foil
                    if (printing?.isFoil == true) {
                        Surface(
                            color = Color(0xFF3B2F10),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "★ FOIL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Market price
                    Text(
                        text = String.format("$%.2f", response?.marketPriceUsd ?: printing?.marketPriceUsd ?: 0.0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9ECE6A),
                        fontFamily = FontFamily.Monospace
                    )

                    if (showDebugInfo) {
                        Text(
                            text = tier.key.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = tierColor,
                            modifier = Modifier
                                .background(tierColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Batch Tray",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Debug Details & Timing Strip (Only shown when showDebugInfo is enabled)
        if (showDebugInfo) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttributePill(label = if (printing?.isFoil == true) "★ Foil" else "Normal", isHighlight = printing?.isFoil == true)
                AttributePill(label = "#${printing?.collector ?: "—"}")
                AttributePill(label = "EN")
                AttributePill(label = "+1", isAccent = true)
            }

            if (timings != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cap: ${timings.captureMs}ms | Hash: ${timings.hashMs}ms | OCR: ${timings.ocrMs}ms | Total: ${timings.totalMs}ms",
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun RowScope.AttributePill(label: String, isHighlight: Boolean = false, isAccent: Boolean = false) {
    val bg = when {
        isAccent -> Color(0xFF238636)
        isHighlight -> TierPickPrinting.copy(alpha = 0.25f)
        else -> Color(0xFF1E232B)
    }
    val textColor = when {
        isAccent -> Color.White
        isHighlight -> TierPickPrinting
        else -> TextSecondary
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, if (isAccent) Color(0xFF2EA043) else Color(0xFF2D333F), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontFamily = FontFamily.Monospace
        )
    }
}