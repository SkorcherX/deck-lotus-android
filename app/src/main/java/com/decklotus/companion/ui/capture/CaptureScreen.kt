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
import com.decklotus.companion.ui.components.SessionTrayBottomSheet
import com.decklotus.companion.ui.theme.LotusCyan
import com.decklotus.companion.ui.theme.LotusPurple
import com.decklotus.companion.ui.theme.TierConfident
import com.decklotus.companion.ui.theme.TierPickPrinting
import com.decklotus.companion.vision.SettleState

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    val sessionCards by viewModel.sessionCards.collectAsState()
    val totalCount by viewModel.totalCardsCount.collectAsState()
    val totalValueUsd by viewModel.totalSessionValueUsd.collectAsState()
    val totalFoils by viewModel.foilCardsCount.collectAsState()

    var showDiagnostics by remember { mutableStateOf(false) }
    var isSessionTrayOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isSessionTrayOpen) {
        viewModel.setSessionTrayOpen(isSessionTrayOpen)
    }

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
            CameraPreviewView(previewView = previewView)
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

        // Top Status Header: Price Pill, Cradle Feed Indicator & User Target Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Target Chip (Tap to open session tray)
            Box(
                modifier = Modifier
                    .clickable { isSessionTrayOpen = true }
                    .background(Color(0xFF1E2633).copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                    .border(1.dp, LotusPurple.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = LotusPurple, modifier = Modifier.size(14.dp))
                    Text(
                        text = settings.activeProfile?.name ?: "User",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Price Pill: Clearly labeled BATCH TOTAL
            Box(
                modifier = Modifier
                    .clickable { isSessionTrayOpen = true }
                    .background(Color(0xFF262C36).copy(alpha = 0.95f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                if (totalCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BATCH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2EA043),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = String.format("$%.2f", totalValueUsd),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "READY",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7A8499),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Hands-free Auto-Feed Pacing Indicator
            if (settings.autoScanEnabled) {
                val (cradleText, cradleColor) = when (uiState.cradleState) {
                    SettleState.WAITING_FOR_CARD -> "DROP" to LotusCyan
                    SettleState.CARD_MOVING -> "SETTLING" to TierPickPrinting
                    SettleState.CARD_SETTLING -> "LOCK" to LotusPurple
                    SettleState.CARD_SETTLED -> "READ" to TierConfident
                    SettleState.LOCKED_AFTER_SCAN -> "NEXT →" to TierConfident
                }

                Box(
                    modifier = Modifier
                        .background(cradleColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .border(1.dp, cradleColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cradleText,
                        fontSize = 12.sp,
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
            // Scanned Queue Count Badge (Tap to open Session Tray)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { isSessionTrayOpen = true }
                    .background(
                        if (totalCount > 0) LotusPurple else Color(0xFF1E232B).copy(alpha = 0.9f),
                        CircleShape
                    )
                    .border(1.dp, Color(0xFF333B49), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Session Tray",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    if (totalCount > 0) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$totalCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
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
                    if (settings.soundFeedbackEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
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
            // Scan Result Card (Tap to open Session Tray)
            Box(modifier = Modifier.clickable { isSessionTrayOpen = true }) {
                ScanResultBadge(
                    response = uiState.lastResponse,
                    thumbnail = uiState.rectifiedCardBitmap,
                    timings = uiState.lastTimings,
                    errorMessage = uiState.lastError
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shutter Button
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

        // Session Batch Tray Bottom Sheet
        if (isSessionTrayOpen) {
            SessionTrayBottomSheet(
                cards = sessionCards,
                totalCount = totalCount,
                totalValueUsd = totalValueUsd,
                totalFoils = totalFoils,
                activeProfile = settings.activeProfile,
                allProfiles = settings.userProfiles,
                isCommitting = uiState.isCommitting,
                onDismiss = { isSessionTrayOpen = false },
                onIncrement = { viewModel.incrementQuantity(it) },
                onDecrement = { viewModel.decrementQuantity(it) },
                onToggleFoil = { viewModel.toggleFoil(it) },
                onRemove = { viewModel.removeCard(it) },
                onClearAll = { viewModel.clearSession() },
                onSelectProfile = { viewModel.selectActiveProfile(it) },
                onCommitToCollection = { viewModel.commitBatchToCollection() }
            )
        }
    }
}