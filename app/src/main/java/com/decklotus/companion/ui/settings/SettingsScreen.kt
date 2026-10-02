package com.decklotus.companion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.decklotus.companion.data.UserProfile
import com.decklotus.companion.network.ServerConnectionStatus
import com.decklotus.companion.ui.components.AddEditProfileDialog
import com.decklotus.companion.ui.components.CloudflarePortalDialog
import com.decklotus.companion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val isPortalOpen by viewModel.isPortalOpen.collectAsState()
    val dbStats by viewModel.dbStats.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var isAddProfileOpen by remember { mutableStateOf(false) }

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Section 1: Capture & Settle Tuning
            Text(
                text = "RIG & AUTO-CAPTURE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LotusCyan,
                fontFamily = FontFamily.Monospace
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Continuous Hands-Free Ingest Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Hands-Free Scan", fontWeight = FontWeight.Medium)
                            Text("Automatically triggers capture when card settles in Card Slinger", fontSize = 12.sp, color = TextSecondary)
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

                    HorizontalDivider(color = SurfaceBorder)

                    // Show Debug Info & HUD Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Debug Info & Diagnostics", fontWeight = FontWeight.Medium)
                            Text("Shows scan latency timings and the HUD (i) button on viewfinder", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.showDebugInfo,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(showDebugInfo = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Save Debug Captures Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save Debug Scan Captures", fontWeight = FontWeight.Medium)
                            Text("Saves raw camera frames, crops, and OCR logs to storage for debugging", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.saveDebugCaptures,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(saveDebugCaptures = checked) }
                            }
                        )
                    }
                }
            }

            // Section 2: Family & User Profiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FAMILY & USER PROFILES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LotusCyan,
                    fontFamily = FontFamily.Monospace
                )

                TextButton(onClick = { isAddProfileOpen = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Member", fontSize = 12.sp, color = LotusCyan, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select active collection target. Each member's scans commit to their own collection.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    if (settings.userProfiles.isEmpty()) {
                        Text(
                            text = "No user profiles configured. Add a family member with their API key.",
                            fontSize = 13.sp,
                            color = TierUnsure
                        )
                    } else {
                        settings.userProfiles.forEach { profile ->
                            val isActive = profile.id == settings.activeProfileId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) Color(0xFF1E2633) else Color(0xFF13171D),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) LotusPurple else SurfaceBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.selectActiveProfile(profile.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = { viewModel.selectActiveProfile(profile.id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = LotusPurple)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                        if (isActive) {
                                            Surface(
                                                color = LotusPurple.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = LotusPurple,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    val verified = profile.verifiedUsername
                                    Text(
                                        text = if (!verified.isNullOrBlank()) "@$verified (Verified)" else "Token Set",
                                        fontSize = 11.sp,
                                        color = if (!verified.isNullOrBlank()) TierConfident else TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                IconButton(
                                    onClick = { profileToEdit = profile },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }

                                if (settings.userProfiles.size > 1) {
                                    IconButton(
                                        onClick = { viewModel.deleteProfile(profile.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF85149), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Server Connection & Cloudflare Tunnel
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
                                        Text("Active User Token Invalid", color = TierUnsure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Check active profile API key above.", color = TextSecondary, fontSize = 11.sp)
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
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CF Portal")
                        }
                    }
                }
            }

            // Section 4: Card Database & Art Hash Sync
            Text(
                text = "MTG DATABASE & HASH SYNC",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LotusCyan,
                fontFamily = FontFamily.Monospace
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Sync on-device MTG database and 256-bit perceptual art hashes from your server when new MTG sets or reprint prices are released.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    // Database Stats Card
                    Surface(
                        color = Color(0xFF13171D),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Database Index",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Surface(
                                    color = LotusCyan.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${java.text.NumberFormat.getNumberInstance().format(dbStats.totalPrintings)} Cards",
                                        color = LotusCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${dbStats.totalSets} MTG set codes indexed",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            val lastSyncStr = if (dbStats.lastSyncTimestamp > 0L) {
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                "Last synced: ${sdf.format(java.util.Date(dbStats.lastSyncTimestamp))}"
                            } else {
                                "Source: Bundled APK Assets (v4)"
                            }

                            Text(
                                text = lastSyncStr,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Sync Status Indicator
                    when (val s = syncStatus) {
                        is DatabaseSyncStatus.Syncing -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = s.step,
                                        fontSize = 12.sp,
                                        color = LotusCyan,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${(s.progress * 100).toInt()}%",
                                        fontSize = 12.sp,
                                        color = LotusCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = LotusCyan,
                                    trackColor = SurfaceBorder
                                )
                            }
                        }
                        is DatabaseSyncStatus.Success -> {
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
                                    Text(s.message, color = TierConfident, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        is DatabaseSyncStatus.Error -> {
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
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = TierConflict, modifier = Modifier.size(18.dp))
                                    Text(s.message, color = TierConflict, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        is DatabaseSyncStatus.Idle -> {}
                    }

                    // Sync Button
                    val isSyncing = syncStatus is DatabaseSyncStatus.Syncing
                    Button(
                        onClick = { viewModel.syncCardDatabase() },
                        enabled = !isSyncing && baseUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LotusPurple,
                            disabledContainerColor = SurfaceBorder
                        )
                    ) {
                        Icon(
                            if (isSyncing) Icons.Default.Refresh else Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSyncing) "Syncing Resources..." else "Sync Database & Hashes",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Section 5: Manual Camera2 Settings (Pixel 10 Pro Rig)
            Text(
                text = "MANUAL CAMERA2 SENSOR LOCKS",
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
                    // Auto-Focus Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Focus", fontWeight = FontWeight.Medium)
                            Text("Unlock for testing; lock fixed diopters for cradle rig", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoFocus,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(autoFocus = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Auto-Exposure Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Exposure", fontWeight = FontWeight.Medium)
                            Text("Unlock for testing; lock fixed exposure for cradle rig", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.autoExposure,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(autoExposure = checked) }
                            }
                        )
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    // Torch LED Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flashlight / Torch", fontWeight = FontWeight.Medium)
                            Text("Continuous illumination for Card Slinger capture tunnel", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.torchEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(torchEnabled = checked) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Add / Edit Profile Dialog
        if (isAddProfileOpen) {
            AddEditProfileDialog(
                onDismiss = { isAddProfileOpen = false },
                onSave = { name, token ->
                    viewModel.addProfile(name, token)
                    isAddProfileOpen = false
                }
            )
        }

        profileToEdit?.let { profile ->
            AddEditProfileDialog(
                profileToEdit = profile,
                onDismiss = { profileToEdit = null },
                onSave = { name, token ->
                    viewModel.updateProfile(profile.id, name, token)
                    profileToEdit = null
                }
            )
        }

        // Cloudflare Captive Portal Modal
        if (isPortalOpen) {
            CloudflarePortalDialog(
                url = settings.baseUrl,
                onDismiss = { viewModel.closeCloudflarePortal() },
                onAuthSuccess = { viewModel.onCloudflareAuthSuccess() }
            )
        }
    }
}