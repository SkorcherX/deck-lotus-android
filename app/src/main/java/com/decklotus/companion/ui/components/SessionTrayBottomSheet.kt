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
    onDismiss: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onToggleFoil: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
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
                    accentColor = Color(0xFF2EA043),
                    modifier = Modifier.weight(1.3f)
                )
                SummaryStatCard(
                    title = "CARDS",
                    value = "$totalCount",
                    accentColor = LotusCyan,
                    modifier = Modifier.weight(0.85f)
                )
                SummaryStatCard(
                    title = "FOILS",
                    value = "$totalFoils",
                    accentColor = TierPickPrinting,
                    modifier = Modifier.weight(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of Scanned Cards
            if (cards.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            tint = Color(0xFF3B4352),
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
                        .heightIn(max = 440.dp),
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
            .border(1.dp, Color(0xFF2D333F), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 16.sp,
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
    val imgBitmap = remember(item.id, item.thumbnail) {
        item.thumbnail?.asImageBitmap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1F27), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF282E3A), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        if (imgBitmap != null) {
            Image(
                bitmap = imgBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 38.dp, height = 54.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF3B4352), RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 54.dp)
                    .background(Color(0xFF212630), RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Center Details (Name, Set, Foil Badge, Price)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Set & Collector Badge
                Text(
                    text = "${item.setCode} #${item.collectorNumber}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                // Foil Toggle Badge
                Box(
                    modifier = Modifier
                        .clickable { onToggleFoil() }
                        .background(
                            if (item.isFoil) TierPickPrinting.copy(alpha = 0.25f) else Color(0xFF262C36),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (item.isFoil) TierPickPrinting.copy(alpha = 0.6f) else Color(0xFF3B4352),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (item.isFoil) "★ FOIL" else "NORMAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isFoil) TierPickPrinting else TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Price readout
            Text(
                text = String.format("$%.2f", item.marketPriceUsd) + if (item.quantity > 1) " (ea) • " + String.format("$%.2f", item.totalItemPriceUsd) else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2EA043),
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