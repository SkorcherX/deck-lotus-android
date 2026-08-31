package com.decklotus.companion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decklotus.companion.network.ServerConnectionStatus
import com.decklotus.companion.ui.components.CloudflarePortalDialog
import com.decklotus.companion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val isPortalOpen by viewModel.isPortalOpen.collectAsState()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }

    if (isPortalOpen) {
        CloudflarePortalDialog(
            url = baseUrl.ifBlank { "https://deck-lotus.example.com" },
            onDismiss = { viewModel.closeCloudflarePortal() },
            onAuthSuccess = { viewModel.onCloudflareAuthSuccess() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Rig Tuning", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section 1: Feeder & Automation
            Text(
                text = "FEEDER & SCANNER PACING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TierConfident,
                fontFamily = FontFamily.Monospace
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Continuous Auto-Scan Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Hands-Free Scan", fontWeight = FontWeight.Medium)
                            Text("Detects card settle in cradle and ingests automatically", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoScanEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(autoScanEnabled = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Sound Cue Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audio Feed Prompts", fontWeight = FontWeight.Medium)
                            Text("Chimes on card ingest to prompt feeding the next card", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.soundFeedbackEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(soundFeedbackEnabled = checked) }
                            }
                        )
                    }
                }
            }

            // Section 2: Server Connection & Cloudflare Tunnel
            Text(
                text = "SERVER & CLOUDFLARE TUNNEL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LotusCyan,
                fontFamily = FontFamily.Monospace
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Connection Status Pill Banner
                    when (val s = connStatus) {
                        is ServerConnectionStatus.Connected -> {
                            Surface(
                                color = TierConfident.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TierConfident, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Connected to Deck Lotus (${s.latencyMs}ms)", color = TierConfident, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        s.username?.let { Text("Authenticated as $it", color = TextSecondary, fontSize = 11.sp) }
                                    }
                                }
                            }
                        }
                        is ServerConnectionStatus.CloudflareAuthRequired -> {
                            Surface(
                                color = TierConflict.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = TierConflict, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Cloudflare Access Login Required", color = TierConflict, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Session expired or new device. Tap below to log in.", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        is ServerConnectionStatus.DeckLotusAuthRequired -> {
                            Surface(
                                color = TierUnsure.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = TierUnsure, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Deck Lotus Bearer Token Invalid", color = TierUnsure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Check your API token below.", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        is ServerConnectionStatus.Unreachable -> {
                            Surface(
                                color = SurfaceBorder,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("Server Unreachable", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(s.errorMessage, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        is ServerConnectionStatus.Checking -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LotusCyan)
                        }
                        is ServerConnectionStatus.Idle -> {}
                    }

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            viewModel.updateSettings { s -> s.copy(baseUrl = it) }
                        },
                        label = { Text("Server Base URL (or Cloudflare Tunnel)") },
                        placeholder = { Text("https://cards.yourdomain.com") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LotusCyan,
                            unfocusedBorderColor = SurfaceBorder
                        )
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            viewModel.updateSettings { s -> s.copy(apiToken = it) }
                        },
                        label = { Text("Deck Lotus API Token") },
                        placeholder = { Text("Optional if tunnel handles auth") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LotusCyan,
                            unfocusedBorderColor = SurfaceBorder
                        )
                    )

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testConnection() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LotusCyan)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Ping")
                        }

                        Button(
                            onClick = { viewModel.openCloudflarePortal() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = LotusPurple)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CF Portal")
                        }
                    }
                }
            }

            // Section 3: Camera Manual Controls
            Text(
                text = "OPTICAL CONTROLS & RIG LOCKS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LotusPurple,
                fontFamily = FontFamily.Monospace
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Continuous AF Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Auto-Focus", fontWeight = FontWeight.Medium)
                            Text("Turn OFF to lock fixed cradle focal distance", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoFocus,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(autoFocus = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Continuous AE Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Auto-Exposure", fontWeight = FontWeight.Medium)
                            Text("Turn OFF to lock manual 1/500s & fixed ISO", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoExposure,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(autoExposure = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Torch Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rig Illumination Torch", fontWeight = FontWeight.Medium)
                            Text("Continuous LED illumination for shadow reduction", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.torchEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(torchEnabled = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Manual Shutter Speed
                    Column {
                        Text("Manual Shutter Speed", fontWeight = FontWeight.Medium)
                        Text(if (settings.autoExposure) "Inactive while Auto-Exposure is ON" else "1/500s eliminates hand & drop motion blur", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val speeds = listOf(
                                "1/60s" to 16_666_666L,
                                "1/125s" to 8_000_000L,
                                "1/250s" to 4_000_000L,
                                "1/500s" to 2_000_000L,
                                "1/1000s" to 1_000_000L
                            )
                            speeds.forEach { (label, ns) ->
                                val selected = settings.exposureTimeNs == ns
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSettings { it.copy(exposureTimeNs = ns, autoExposure = false) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Manual ISO Gain
                    Column {
                        Text("Manual ISO Sensitivity", fontWeight = FontWeight.Medium)
                        Text(if (settings.autoExposure) "Inactive while Auto-Exposure is ON" else "Locked ISO guarantees deterministic values", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100, 200, 400, 800, 1600).forEach { iso ->
                                val selected = settings.isoSensitivity == iso
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSettings { it.copy(isoSensitivity = iso, autoExposure = false) } },
                                    label = { Text("ISO $iso") }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Fixed Focal Distance (Diopters)
                    Column {
                        val currentDpt = settings.focusDistanceDiopters
                        val approxCm = if (currentDpt > 0) (100.0 / currentDpt).toInt() else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fixed Focal Distance", fontWeight = FontWeight.Medium)
                            Text("~${approxCm}cm (${String.format("%.1f", currentDpt)} dpt)", color = LotusCyan, fontWeight = FontWeight.Bold)
                        }
                        Text(if (settings.autoFocus) "Inactive while Auto-Focus is ON" else "Calibrated for Card Slinger 3.0 cradle distance", fontSize = 12.sp, color = TextSecondary)
                        Slider(
                            value = currentDpt,
                            onValueChange = { dpt ->
                                viewModel.updateSettings { it.copy(focusDistanceDiopters = dpt, autoFocus = false) }
                            },
                            valueRange = 1.0f..12.0f,
                            steps = 22,
                            colors = SliderDefaults.colors(
                                thumbColor = LotusPurple,
                                activeTrackColor = LotusPurple
                            )
                        )
                    }
                }
            }
        }
    }
}