package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun UserProfilePickerDialog(
    activeProfile: UserProfile?,
    profiles: List<UserProfile>,
    onSelectProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161B22),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = LotusPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.People, contentDescription = null, tint = LotusPurple, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "SWITCH COLLECTION TARGET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LotusCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Select Family Member",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                Text(
                    text = "Scanned cards will be added to this user's collection on commit.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // List of Profiles
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == activeProfile?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) Color(0xFF1E2633) else Color(0xFF13171D),
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) LotusPurple else Color(0xFF2E3440),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onSelectProfile(profile.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectProfile(profile.id)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = LotusPurple)
                                )
                                Column {
                                    Text(
                                        text = profile.name,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimary
                                    )
                                    val userTag = profile.verifiedUsername?.let { "@$it (Verified)" } ?: "Token set"
                                    Text(
                                        text = userTag,
                                        fontSize = 11.sp,
                                        color = if (profile.verifiedUsername != null) TierConfident else TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (isSelected) {
                                Surface(
                                    color = LotusPurple.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LotusPurple,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onDismiss()
                        onOpenSettings()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = LotusCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manage in Settings", fontSize = 12.sp, color = LotusCyan)
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = TextSecondary)
                    }
                }
            }
        }
    }
}