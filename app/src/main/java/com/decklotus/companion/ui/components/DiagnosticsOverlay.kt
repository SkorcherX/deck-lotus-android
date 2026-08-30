package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decklotus.companion.camera.LiveCaptureMetadata
import com.decklotus.companion.ui.theme.*

@Composable
fun DiagnosticsOverlay(
    metadata: LiveCaptureMetadata,
    useMock: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(SurfaceDark.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RIG: Pixel 10 Pro + Slinger 3.0",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LotusPurple,
                fontFamily = FontFamily.Monospace
            )

            if (useMock) {
                Text(
                    text = "MOCK SERVER ACTIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TierPickPrinting,
                    modifier = Modifier
                        .background(TierPickPrinting.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LockBadge(label = "AF LOCK", value = metadata.focusDistanceCm, locked = metadata.isAfLocked)
            LockBadge(label = "SHUTTER", value = metadata.shutterSpeedFraction, locked = metadata.isAeLocked)
            LockBadge(label = "ISO", value = "${metadata.isoSensitivity}", locked = metadata.isAeLocked)
            LockBadge(label = "FPS", value = String.format("%.0f", metadata.fps), locked = true)
        }
    }
}

@Composable
private fun LockBadge(label: String, value: String, locked: Boolean) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (locked) TierConfident else TierUnresolved, RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}