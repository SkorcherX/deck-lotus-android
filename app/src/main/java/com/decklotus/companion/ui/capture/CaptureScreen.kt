package com.decklotus.companion.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.decklotus.companion.ui.theme.TierPickPrinting

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settingsFlow.collectAsState()
    var showDiagnostics by remember { mutableStateOf(true) }

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
            cameraController.bindCamera(previewView, settings)
        }
        onDispose {
            cameraController.shutdown()
        }
    }

    LaunchedEffect(settings) {
        if (hasCameraPermission) {
            cameraController.updateManualControls(settings)
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

        // Top Floating Price Pill (Centered)
        val priceUsd = uiState.lastResponse?.marketPriceUsd ?: 0.26
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .background(Color(0xFF262C36).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Text(
                text = String.format("$%.2f", priceUsd),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }

        // Right Vertical Action Column (Floating Pills)
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
                        if (settings.autoFocus) LotusPurple else Color(0xFF1E232B).copy(alpha = 0.9f),
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

            // Settings Navigation Gear Button
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF1E232B).copy(alpha = 0.9f), CircleShape)
                    .border(1.dp, Color(0xFF333B49), CircleShape)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Toggle Diagnostics overlay visibility
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

        // Top Left Diagnostics Overlay (Collapsible)
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
            // Scan Result Card
            ScanResultBadge(
                response = uiState.lastResponse,
                thumbnail = uiState.rectifiedCardBitmap,
                timings = uiState.lastTimings,
                errorMessage = uiState.lastError
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Shutter Button
            FloatingActionButton(
                onClick = { viewModel.triggerCapture(cameraController) },
                shape = CircleShape,
                containerColor = LotusPurple,
                contentColor = Color.White,
                modifier = Modifier.size(72.dp)
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(34.dp))
                } else {
                    Icon(Icons.Default.Camera, contentDescription = "Capture", modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}