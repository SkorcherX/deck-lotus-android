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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.decklotus.companion.ui.theme.LotusCyan
import com.decklotus.companion.ui.theme.LotusPurple

@Composable
fun CameraPreviewView(
    previewView: PreviewView,
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

            val targetAspect = 63.0f / 88.0f
            var cardH = canvasH * 0.72f
            var cardW = cardH * targetAspect

            if (cardW > canvasW * 0.88f) {
                cardW = canvasW * 0.88f
                cardH = cardW / targetAspect
            }

            val left = (canvasW - cardW) / 2.0f
            val top = (canvasH - cardH) / 2.0f

            drawRoundRect(
                color = LotusCyan.copy(alpha = 0.8f),
                topLeft = Offset(left, top),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f), 0f)
                )
            )

            val ocrLeft = left + cardW * 0.04f
            val ocrTop = top + cardH * 0.88f
            val ocrWidth = cardW * 0.48f
            val ocrHeight = cardH * 0.09f

            drawRoundRect(
                color = LotusPurple.copy(alpha = 0.9f),
                topLeft = Offset(ocrLeft, ocrTop),
                size = Size(ocrWidth, ocrHeight),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}