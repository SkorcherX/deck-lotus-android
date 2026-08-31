package com.decklotus.companion.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import com.decklotus.companion.data.ScannedCardItem
import com.decklotus.companion.ui.theme.*

enum class ExportFormat(val displayName: String) {
    STANDARD_DECKLIST("Standard MTG"),
    CSV("CSV Table"),
    PLAIN("Quantity + Name")
}

@Composable
fun ExportBatchDialog(
    cards: List<ScannedCardItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.STANDARD_DECKLIST) }

    val formattedText = remember(cards, selectedFormat) {
        buildString {
            when (selectedFormat) {
                ExportFormat.STANDARD_DECKLIST -> {
                    // Standard MTG format: 1 Card Name (SET) 123 *Foil*
                    cards.forEach { card ->
                        val foilTag = if (card.isFoil) " *Foil*" else ""
                        val setTag = if (card.setCode.isNotBlank()) " (${card.setCode.uppercase()})" else ""
                        val colTag = if (card.collectorNumber.isNotBlank()) " ${card.collectorNumber}" else ""
                        appendLine("${card.quantity} ${card.name}$setTag$colTag$foilTag")
                    }
                }
                ExportFormat.CSV -> {
                    // CSV format: Quantity, Card Name, Publishing Code, Collector Number, Foil
                    appendLine("Quantity,Card Name,Publishing Code,Collector Number,Foil")
                    cards.forEach { card ->
                        val escapedName = if (card.name.contains(",")) "\"${card.name}\"" else card.name
                        appendLine("${card.quantity},$escapedName,${card.setCode.uppercase()},${card.collectorNumber},${card.isFoil}")
                    }
                }
                ExportFormat.PLAIN -> {
                    // Simple Quantity + Name: 1 Card Name
                    cards.forEach { card ->
                        val foilTag = if (card.isFoil) " (Foil)" else ""
                        appendLine("${card.quantity} ${card.name}$foilTag")
                    }
                }
            }
        }.trimEnd()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF161B22),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
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
                        color = LotusCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = LotusCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(
                            text = "EXPORT BATCH LIST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LotusCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${cards.sumOf { it.quantity }} Cards Total",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // Format Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF101318), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExportFormat.values().forEach { format ->
                        val isSelected = format == selectedFormat
                        Surface(
                            color = if (isSelected) LotusCyan.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, LotusCyan.copy(alpha = 0.5f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFormat = format }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = format.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) LotusCyan else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Text Preview Box
                Surface(
                    color = Color(0xFF0F1217),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C36)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = formattedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFC9D1D9),
                            lineHeight = 18.sp
                        )
                    }
                }

                // Action Buttons (Copy to Clipboard & Share)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Copy to Clipboard Button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Deck Lotus Card Export", formattedText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "✓ Copied ${cards.size} card types to clipboard!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LotusCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Native Share Sheet Button
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, formattedText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Scanned Cards")
                            context.startActivity(shareIntent)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LotusPurple),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share to App", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Close", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}