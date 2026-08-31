package com.decklotus.companion.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decklotus.companion.data.ScannedCardItem
import com.decklotus.companion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTrayBottomSheet(
    cards: List<ScannedCardItem>,
    totalCount: Int,
    totalValueUsd: Double,
    totalFoils: Int,
    isCommitting: Boolean = false,
    onDismiss: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onToggleFoil: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onCommitToCollection: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF14171D),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF3B4352))
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Title + Clear Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SESSION BATCH TRAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LotusCyan,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$totalCount Cards Scanned",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (cards.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stat Summary Cards (Total Value, Total Cards, Total Foils)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatCard(
                    title = "BATCH VALUE",
                    value = String.format("$%.2f", totalValueUsd),
                    accentColor = LotusCyan,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    title = "CARDS",
                    value = "$totalCount",
                    accentColor = TierConfident,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    title = "FOILS",
                    value = "$totalFoils",
                    accentColor = TierConflict,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of Scanned Cards
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            tint = Color(0xFF4C566A),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No cards scanned in this session yet",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Feed cards into the cradle to begin batch",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cards, key = { it.id }) { item ->
                        ScannedCardRowItem(
                            item = item,
                            onIncrement = { onIncrement(item.id) },
                            onDecrement = { onDecrement(item.id) },
                            onToggleFoil = { onToggleFoil(item.id) },
                            onRemove = { onRemove(item.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Commit to Collection Button
                Button(
                    onClick = onCommitToCollection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TierConfident),
                    enabled = !isCommitting
                ) {
                    if (isCommitting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing to Server...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Commit $totalCount Cards to Collection (${String.format("$%.2f", totalValueUsd)})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF1E232B), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A8499),
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ScannedCardRowItem(
    item: ScannedCardItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleFoil: () -> Unit,
    onRemove: () -> Unit
) {
    val tierBorderColor = when (item.tier) {
        "confident" -> TierConfident
        "pick-printing" -> TierPickPrinting
        "conflict" -> TierConflict
        else -> TierUnsure
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B2028), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail Image
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 64.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, tierBorderColor, RoundedCornerShape(6.dp))
                .background(Color(0xFF111318)),
            contentAlignment = Alignment.Center
        ) {
            if (item.thumbnail != null) {
                Image(
                    bitmap = item.thumbnail.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF4C566A), modifier = Modifier.size(24.dp))
            }
        }

        // Details (Name, Set Code, Collector #, Unit & Total Price)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Set Code Tag
                Surface(
                    color = Color(0xFF262C36),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.setCode,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LotusCyan,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Collector Number Tag
                Text(
                    text = "#${item.collectorNumber}",
                    fontSize = 11.sp,
                    color = Color(0xFF9AA5B8),
                    fontFamily = FontFamily.Monospace
                )

                // Foil Pill Toggle
                Surface(
                    color = if (item.isFoil) Color(0xFF3B2F10) else Color(0xFF20252D),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onToggleFoil() }
                ) {
                    Text(
                        text = if (item.isFoil) "★ FOIL" else "NORMAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isFoil) Color(0xFFFFD700) else Color(0xFF7A8499),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Price Line
            Text(
                text = "${String.format("$%.2f", item.marketPriceUsd)} each • Total: ${String.format("$%.2f", item.totalItemPriceUsd)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9ECE6A),
                fontFamily = FontFamily.Monospace
            )
        }

        // Stepper Quantity Controls: [-] [qty] [+] and Remove
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier
                    .size(30.dp)
                    .background(Color(0xFF262C36), CircleShape)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
            }

            Text(
                text = "${item.quantity}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            IconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(30.dp)
                    .background(LotusPurple, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFF7A8499), modifier = Modifier.size(16.dp))
            }
        }
    }
}