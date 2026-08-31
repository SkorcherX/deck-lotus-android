package com.decklotus.companion.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    // Bind camera once when permission is available
    DisposableEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindCamera(previewView, settings)
        }
        onDispose {
            cameraController.shutdown()
        }
    }

    // Reactively update manual controls when settings change without unbinding camera
    LaunchedEffect(settings) {
        if (hasCameraPermission) {
            cameraController.updateManualControls(settings)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewView(previewView = previewView)
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
            }
        }

        // Top HUD with Diagnostics & Quick Action Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiagnosticsOverlay(
                    metadata = liveMeta,
                    useMock = settings.useMockServer,
                    isAutoFocus = settings.autoFocus,
                    isAutoExposure = settings.autoExposure,
                    isTorchOn = settings.torchEnabled,
                    modifier = Modifier.weight(1f)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    // Torch Quick Toggle
                    IconButton(
                        onClick = { viewModel.toggleTorch() },
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (settings.torchEnabled) TierPickPrinting else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (settings.torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = if (settings.torchEnabled) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // AF vs Fixed Lock Quick Toggle
                    IconButton(
                        onClick = { viewModel.toggleAutoFocus() },
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (settings.autoFocus) LotusPurple else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (settings.autoFocus) Icons.Default.CenterFocusStrong else Icons.Default.CenterFocusWeak,
                            contentDescription = "Focus Mode",
                            tint = if (settings.autoFocus) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // AE vs Rig Lock Quick Toggle
                    IconButton(
                        onClick = { viewModel.toggleAutoExposure() },
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (settings.autoExposure) LotusCyan else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            if (settings.autoExposure) Icons.Default.BrightnessAuto else Icons.Default.Lock,
                            contentDescription = "AE Mode",
                            tint = if (settings.autoExposure) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Settings Navigation Button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Bottom Result Badge + Capture Trigger Floating Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScanResultBadge(
                response = uiState.lastResponse,
                timings = uiState.lastTimings,
                errorMessage = uiState.lastError
            )

            Spacer(modifier = Modifier.height(16.dp))

            FloatingActionButton(
                onClick = { viewModel.triggerCapture(cameraController) },
                shape = CircleShape,
                containerColor = LotusPurple,
                contentColor = Color.White,
                modifier = Modifier.size(76.dp)
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                } else {
                    Icon(Icons.Default.Camera, contentDescription = "Capture", modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}