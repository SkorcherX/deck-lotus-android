package com.decklotus.companion.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.decklotus.companion.ui.theme.LotusCyan
import com.decklotus.companion.ui.theme.LotusPurple
import com.decklotus.companion.ui.theme.TierConfident
import com.decklotus.companion.vision.DetectedCardQuad
import com.decklotus.companion.vision.SettleState

@Composable
fun CameraPreviewView(
    previewView: PreviewView,
    detectedCard: DetectedCardQuad?,
    cradleState: SettleState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            if (detectedCard != null) {
                // Active dynamic card tracking: Snap border directly to the real card!
                val p0 = Offset(detectedCard.topLeft.x * canvasW, detectedCard.topLeft.y * canvasH)
                val p1 = Offset(detectedCard.topRight.x * canvasW, detectedCard.topRight.y * canvasH)
                val p2 = Offset(detectedCard.bottomRight.x * canvasW, detectedCard.bottomRight.y * canvasH)
                val p3 = Offset(detectedCard.bottomLeft.x * canvasW, detectedCard.bottomLeft.y * canvasH)

                val isSettled = cradleState == SettleState.CARD_SETTLED || cradleState == SettleState.LOCKED_AFTER_SCAN
                val borderColor = if (isSettled) TierConfident else LotusCyan

                // Draw bounding card polygon
                val cardPath = Path().apply {
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }

                drawPath(
                    path = cardPath,
                    color = borderColor.copy(alpha = 0.85f),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draw corner accent brackets
                val bracketLen = 24.dp.toPx()
                // Top-Left
                drawLine(borderColor, p0, p0 + Offset(bracketLen, 0f), strokeWidth = 5.dp.toPx())
                drawLine(borderColor, p0, p0 + Offset(0f, bracketLen), strokeWidth = 5.dp.toPx())
                // Top-Right
                drawLine(borderColor, p1, p1 - Offset(bracketLen, 0f), strokeWidth = 5.dp.toPx())
                drawLine(borderColor, p1, p1 + Offset(0f, bracketLen), strokeWidth = 5.dp.toPx())
                // Bottom-Right
                drawLine(borderColor, p2, p2 - Offset(bracketLen, 0f), strokeWidth = 5.dp.toPx())
                drawLine(borderColor, p2, p2 - Offset(0f, bracketLen), strokeWidth = 5.dp.toPx())
                // Bottom-Left
                drawLine(borderColor, p3, p3 + Offset(bracketLen, 0f), strokeWidth = 5.dp.toPx())
                drawLine(borderColor, p3, p3 - Offset(0f, bracketLen), strokeWidth = 5.dp.toPx())

                // Draw OCR Box directly over the card's real collector block!
                if (detectedCard.collectorBox.size == 4) {
                    val c0 = Offset(detectedCard.collectorBox[0].x * canvasW, detectedCard.collectorBox[0].y * canvasH)
                    val c1 = Offset(detectedCard.collectorBox[1].x * canvasW, detectedCard.collectorBox[1].y * canvasH)
                    val c2 = Offset(detectedCard.collectorBox[2].x * canvasW, detectedCard.collectorBox[2].y * canvasH)
                    val c3 = Offset(detectedCard.collectorBox[3].x * canvasW, detectedCard.collectorBox[3].y * canvasH)

                    val ocrPath = Path().apply {
                        moveTo(c0.x, c0.y)
                        lineTo(c1.x, c1.y)
                        lineTo(c2.x, c2.y)
                        lineTo(c3.x, c3.y)
                        close()
                    }

                    drawPath(
                        path = ocrPath,
                        color = LotusPurple.copy(alpha = 0.9f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

            } else {
                // Subtle static cradle guide when waiting for card
                val targetAspect = 63.0f / 88.0f
                var cardH = canvasH * 0.68f
                var cardW = cardH * targetAspect

                if (cardW > canvasW * 0.85f) {
                    cardW = canvasW * 0.85f
                    cardH = cardW / targetAspect
                }

                val left = (canvasW - cardW) / 2.0f
                val top = (canvasH - cardH) / 2.0f

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = Size(cardW, cardH),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), 0f)
                    )
                )
            }
        }
    }
}