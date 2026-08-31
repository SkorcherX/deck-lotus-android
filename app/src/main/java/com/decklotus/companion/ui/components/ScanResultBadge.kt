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
            .background(Color(0xFF14171D), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF262C36), RoundedCornerShape(20.dp))
            .padding(14.dp)
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

        // Top Row: Thumbnail + Info
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
                        .size(width = 44.dp, height = 62.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 62.dp)
                        .background(Color(0xFF212630), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(6.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📜 ", fontSize = 13.sp)
                    Text(
                        text = printing?.name ?: "Recognized Card",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MARKET ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format("$%.2f", response?.marketPriceUsd ?: 0.26),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = tier.key.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tierColor,
                        modifier = Modifier
                            .background(tierColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom Attribute Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttributePill(label = if (printing?.isFoil == true) "★ Foil" else "Normal", isHighlight = printing?.isFoil == true)
            AttributePill(label = "📜 #${printing?.collector ?: "0045"}")
            AttributePill(label = "EN")
            AttributePill(label = "+1", isAccent = true)
        }

        // Discreet Timing Strip
        if (timings != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cap: ${timings.captureMs}ms  |  Warp+Hash: ${timings.hashMs}ms  |  NPU OCR: ${timings.ocrMs}ms  |  Total: ${timings.totalMs}ms",
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
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
            .height(34.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, if (isAccent) Color(0xFF2EA043) else Color(0xFF2D333F), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontFamily = FontFamily.Monospace
        )
    }
}