package com.decklotus.companion.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.decklotus.companion.camera.CameraController
import com.decklotus.companion.ui.components.CameraPreviewView
import com.decklotus.companion.ui.components.DiagnosticsOverlay
import com.decklotus.companion.ui.components.ScanResultBadge
import com.decklotus.companion.ui.theme.LotusCyan
import com.decklotus.companion.ui.theme.LotusPurple
import com.decklotus.companion.ui.theme.TierConfident
import com.decklotus.companion.ui.theme.TierPickPrinting
import com.decklotus.companion.vision.SettleState
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    var showDiagnostics by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val previewView = remember { PreviewView(context) }
    val cameraController = remember { CameraController(context, lifecycleOwner) }
    val liveMeta by cameraController.liveMetadata.collectAsState()

    DisposableEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindCamera(previewView, settings) {
                viewModel.startAutoScanLoop(cameraController, previewView)
            }
        }
        onDispose {
            viewModel.stopAutoScanLoop()
            cameraController.shutdown()
        }
    }

    LaunchedEffect(settings) {
        if (hasCameraPermission) {
            cameraController.updateManualControls(settings)
        }
    }

    // Visual Flash Peripheral Cue Animation
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.flashPromptTrigger) {
        if (uiState.flashPromptTrigger > 0) {
            flashAlpha.snapTo(0.85f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreviewView(previewView = previewView, detectedCard = uiState.detectedCard, cradleState = uiState.cradleState)
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
            }
        }

        // Peripheral Green Flash Frame Border (Signal to feed next card)
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(flashAlpha.value)
                    .border(8.dp, TierConfident)
            )
        }

        // Top Status Header: Price Pill & Cradle Feed Indicator
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Price Pill
            val priceUsd = uiState.lastResponse?.marketPriceUsd ?: 0.26
            Box(
                modifier = Modifier
                    .background(Color(0xFF262C36).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = String.format("$%.2f", priceUsd),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Hands-free Auto-Feed Pacing Indicator
            if (settings.autoScanEnabled) {
                val (cradleText, cradleColor) = when (uiState.cradleState) {
                    SettleState.WAITING_FOR_CARD -> "DROP CARD" to LotusCyan
                    SettleState.CARD_MOVING -> "SETTLING..." to TierPickPrinting
                    SettleState.CARD_SETTLING -> "LOCKING..." to LotusPurple
                    SettleState.CARD_SETTLED -> "READING..." to TierConfident
                    SettleState.LOCKED_AFTER_SCAN -> "NEXT CARD →" to TierConfident
                }

                Box(
                    modifier = Modifier
                        .background(cradleColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .border(1.dp, cradleColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cradleText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = cradleColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Right Vertical Action Bar (Floating Pills)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scanned Queue Count Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E232B).copy(alpha = 0.9f), CircleShape)
                    .border(1.dp, Color(0xFF333B49), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = "Count", tint = Color.White, modifier = Modifier.size(16.dp))
                    if (uiState.sessionScanCount > 0) {
                        Text(
                            text = "${uiState.sessionScanCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TierPickPrinting
                        )
                    }
                }
            }

            // Auto-Scan Mode Toggle Button (Hands-Free Feed)
            IconButton(
                onClick = { viewModel.toggleAutoScan() },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (settings.autoScanEnabled) TierConfident else Color(0xFF1E232B).copy(alpha = 0.9f),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(
                    if (settings.autoScanEnabled) Icons.Default.AutoAwesome else Icons.Default.TouchApp,
                    contentDescription = "Auto-Scan",
                    tint = if (settings.autoScanEnabled) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Audio Chime Toggle Button
            IconButton(
                onClick = { viewModel.toggleSound() },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (settings.soundFeedbackEnabled) LotusPurple else Color(0xFF1E232B).copy(alpha = 0.9f),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(
                    if (settings.soundFeedbackEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Audio Cue",
                    tint = if (settings.soundFeedbackEnabled) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Torch Toggle Button
            IconButton(
                onClick = { viewModel.toggleTorch() },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (settings.torchEnabled) TierPickPrinting else Color(0xFF1E232B).copy(alpha = 0.9f),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(
                    if (settings.torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (settings.torchEnabled) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // AF Toggle Button
            IconButton(
                onClick = { viewModel.toggleAutoFocus() },
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (settings.autoFocus) LotusCyan else Color(0xFF1E232B).copy(alpha = 0.9f),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(
                    if (settings.autoFocus) Icons.Default.CenterFocusStrong else Icons.Default.CenterFocusWeak,
                    contentDescription = "AF Mode",
                    tint = if (settings.autoFocus) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings Navigation Button
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E232B).copy(alpha = 0.9f), CircleShape)
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Diagnostics HUD Toggle
            IconButton(
                onClick = { showDiagnostics = !showDiagnostics },
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E232B).copy(alpha = 0.9f), CircleShape)
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(Icons.Default.Info, contentDescription = "HUD", tint = if (showDiagnostics) LotusCyan else Color.Gray, modifier = Modifier.size(20.dp))
            }
        }

        // Top Left Collapsible Diagnostics HUD
        AnimatedVisibility(
            visible = showDiagnostics,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 16.dp)
                .width(220.dp)
        ) {
            DiagnosticsOverlay(
                metadata = liveMeta,
                useMock = settings.useMockServer,
                isAutoFocus = settings.autoFocus,
                isAutoExposure = settings.autoExposure,
                isTorchOn = settings.torchEnabled
            )
        }

        // Bottom Result Drawer + Shutter Trigger
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScanResultBadge(
                response = uiState.lastResponse,
                thumbnail = uiState.rectifiedCardBitmap,
                timings = uiState.lastTimings,
                errorMessage = uiState.lastError
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Shutter Button (Pulsing in Auto-Scan mode)
            FloatingActionButton(
                onClick = { viewModel.triggerCapture(cameraController, previewView) },
                shape = CircleShape,
                containerColor = if (settings.autoScanEnabled) TierConfident else LotusPurple,
                contentColor = if (settings.autoScanEnabled) Color.Black else Color.White,
                modifier = Modifier.size(72.dp)
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(color = if (settings.autoScanEnabled) Color.Black else Color.White, modifier = Modifier.size(34.dp))
                } else {
                    Icon(
                        if (settings.autoScanEnabled) Icons.Default.AutoAwesome else Icons.Default.Camera,
                        contentDescription = "Capture",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}