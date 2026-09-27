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
import com.decklotus.companion.network.DeckSummary
import com.decklotus.companion.network.OwnershipShortfall
import com.decklotus.companion.ui.theme.*

@Composable
fun CommitToDeckDialog(
    deck: DeckSummary,
    cardCount: Int,
    mainboardCount: Int,
    sideboardCount: Int,
    maybeboardCount: Int,
    commanderCount: Int,
    totalValueUsd: Double,
    shortfalls: List<OwnershipShortfall>?,
    isCheckingShortfall: Boolean,
    isCommitting: Boolean,
    onConfirm: (alsoAddToCollection: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var alsoAddToCollection by remember { mutableStateOf(false) }
    val totalShortfallCopies = remember(shortfalls) {
        shortfalls?.sumOf { it.short } ?: 0
    }

    Dialog(onDismissRequest = { if (!isCommitting) onDismiss() }) {
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
                // Header
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
                            Icon(Icons.Default.Style, contentDescription = null, tint = LotusCyan, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "COMMIT TO DECK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LotusCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Add Cards to Deck",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // Target Deck Summary Card
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
                            text = "TARGET DECK",
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
                            Column {
                                Text(
                                    text = deck.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = deck.formatDisplayName,
                                        fontSize = 12.sp,
                                        color = LotusCyan,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "• Currently ${deck.totalCount} cards",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                // Batch Composition Summary (Mainboard, Sideboard, Commander, Value)
                Surface(
                    color = Color(0xFF14171D),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Incoming Scans:", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = "+$cardCount cards (${String.format("$%.2f", totalValueUsd)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TierConfident,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (mainboardCount > 0) {
                                Text("Main: $mainboardCount", fontSize = 11.sp, color = LotusCyan, fontFamily = FontFamily.Monospace)
                            }
                            if (sideboardCount > 0) {
                                Text("Side: $sideboardCount", fontSize = 11.sp, color = TierPickPrinting, fontFamily = FontFamily.Monospace)
                            }
                            if (maybeboardCount > 0) {
                                Text("Maybe: $maybeboardCount", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                            }
                            if (commanderCount > 0) {
                                Text("Cmdr: $commanderCount", fontSize = 11.sp, color = LotusPurple, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Shortfall Analysis Section
                if (isCheckingShortfall) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LotusCyan, strokeWidth = 2.dp)
                        Text("Verifying collection ownership...", fontSize = 12.sp, color = TextSecondary)
                    }
                } else if (shortfalls != null) {
                    if (totalShortfallCopies == 0) {
                        // All cards covered in collection
                        Surface(
                            color = TierConfident.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TierConfident.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TierConfident, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Your collection covers all cards in this deck. Collection inventory counts will not be modified.",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    } else {
                        // Shortfall exists
                        Surface(
                            color = Color(0xFF2E2315),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD29922).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE3B341), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "$totalShortfallCopies card copy/copies across ${shortfalls.size} printing(s) are not currently logged in your collection.",
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        lineHeight = 16.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { alsoAddToCollection = !alsoAddToCollection }
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = alsoAddToCollection,
                                        onCheckedChange = { alsoAddToCollection = it },
                                        colors = CheckboxDefaults.colors(checkedColor = LotusCyan)
                                    )
                                    Column {
                                        Text(
                                            text = "Also add missing cards to my collection",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alsoAddToCollection) LotusCyan else TextPrimary
                                        )
                                        Text(
                                            text = "Leave unchecked if cards are borrowed or already accounted for.",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
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
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCommitting
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = { onConfirm(alsoAddToCollection) },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = LotusCyan),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCommitting && !isCheckingShortfall
                    ) {
                        if (isCommitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Committing...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add to Deck", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
