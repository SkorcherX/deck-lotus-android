package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import com.decklotus.companion.data.UserProfile
import com.decklotus.companion.ui.theme.*

@Composable
fun CommitConfirmationDialog(
    activeProfile: UserProfile?,
    allProfiles: List<UserProfile>,
    cardCount: Int,
    totalValueUsd: Double,
    onSelectProfile: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var isSwitchingUser by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Warning Icon + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = LotusCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = LotusCyan, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "COMMIT TO COLLECTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LotusCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Verify Target Collection",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // Target User Profile Card
                Surface(
                    color = Color(0xFF1C222B),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TARGET USER ACCOUNT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = LotusPurple,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Column {
                                    Text(
                                        text = activeProfile?.name ?: "Primary User",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    val userTag = activeProfile?.verifiedUsername?.let { "@$it" } ?: "Active Token"
                                    Text(
                                        text = userTag,
                                        fontSize = 12.sp,
                                        color = TierConfident,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (allProfiles.size > 1) {
                                TextButton(onClick = { isSwitchingUser = !isSwitchingUser }) {
                                    Text(if (isSwitchingUser) "Done" else "Switch", color = LotusCyan, fontSize = 13.sp)
                                }
                            }
                        }

                        // Profile Selector Expandable List
                        if (isSwitchingUser) {
                            HorizontalDivider(color = Color(0xFF30363D), modifier = Modifier.padding(vertical = 4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                allProfiles.forEach { profile ->
                                    val isSelected = profile.id == activeProfile?.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) Color(0xFF262C36) else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable {
                                                onSelectProfile(profile.id)
                                                isSwitchingUser = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = profile.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else TextSecondary
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = TierConfident, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Batch Stats Summary (Cards + Total Value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF14171D),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("BATCH SIZE", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            Text("$cardCount Cards", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                    Surface(
                        color = Color(0xFF14171D),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("TOTAL VALUE", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            Text(String.format("$%.2f", totalValueUsd), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TierConfident)
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = TierConfident),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Confirm & Add", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}