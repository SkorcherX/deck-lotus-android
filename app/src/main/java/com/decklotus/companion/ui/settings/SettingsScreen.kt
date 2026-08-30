package com.decklotus.companion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decklotus.companion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var token by remember(settings.apiToken) { mutableStateOf(settings.apiToken) }

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
            // Section 1: Server Connection
            Text(
                text = "SERVER INGEST CONFIGURATION",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Use Local Mock Server", fontWeight = FontWeight.Medium)
                            Text("Test captures offline without LAN server", fontSize = 12.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.useMockServer,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(useMockServer = checked) }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            viewModel.updateSettings { s -> s.copy(baseUrl = it) }
                        },
                        label = { Text("Deck Lotus Base URL") },
                        placeholder = { Text("http://192.168.1.100:3000") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !settings.useMockServer,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LotusPurple,
                            unfocusedBorderColor = SurfaceBorder
                        )
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            viewModel.updateSettings { s -> s.copy(apiToken = it) }
                        },
                        label = { Text("Bearer API Token") },
                        placeholder = { Text("Optional if auth disabled") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !settings.useMockServer,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LotusPurple,
                            unfocusedBorderColor = SurfaceBorder
                        )
                    )
                }
            }

            // Section 2: Camera Manual Controls
            Text(
                text = "MANUAL OPTICAL CONTROLS (PIXEL 10 PRO)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LotusPurple,
                fontFamily = FontFamily.Monospace
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Locked Shutter Speed", fontWeight = FontWeight.Medium)
                        Text("High speeds freeze falling cards without drop blur", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val speeds = listOf(
                                "1/250s" to 4_000_000L,
                                "1/500s" to 2_000_000L,
                                "1/1000s" to 1_000_000L
                            )
                            speeds.forEach { (label, ns) ->
                                val selected = settings.exposureTimeNs == ns
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSettings { it.copy(exposureTimeNs = ns) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SurfaceBorder)

                    Column {
                        Text("Locked ISO Sensitivity", fontWeight = FontWeight.Medium)
                        Text("Low ISO guarantees clean signals without sensor noise", fontSize = 12.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(50, 100, 200, 400).forEach { iso ->
                                val selected = settings.isoSensitivity == iso
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateSettings { it.copy(isoSensitivity = iso) } },
                                    label = { Text("ISO $iso") }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = SurfaceBorder)

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
                        Text("Calibrate to Card Slinger 3.0 cradle surface distance", fontSize = 12.sp, color = TextSecondary)
                        Slider(
                            value = currentDpt,
                            onValueChange = { dpt ->
                                viewModel.updateSettings { it.copy(focusDistanceDiopters = dpt) }
                            },
                            valueRange = 1.0f..10.0f,
                            steps = 18,
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