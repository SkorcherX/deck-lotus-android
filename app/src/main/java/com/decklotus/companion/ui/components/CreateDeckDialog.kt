package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Style
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
import com.decklotus.companion.ui.theme.*

private val FORMAT_OPTIONS = listOf(
    "commander" to "Commander / EDH",
    "modern" to "Modern",
    "standard" to "Standard",
    "pioneer" to "Pioneer",
    "pauper" to "Pauper",
    "legacy" to "Legacy",
    "vintage" to "Vintage",
    "casual" to "Casual"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDeckDialog(
    isCreating: Boolean = false,
    onCreate: (name: String, format: String, description: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var deckName by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("commander") }
    var description by remember { mutableStateOf("") }
    var formatDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isCreating) onDismiss() }) {
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
                            text = "DECK CATALOG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LotusCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Create New Deck",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }

                // Deck Name Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DECK NAME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = deckName,
                        onValueChange = { deckName = it },
                        placeholder = { Text("e.g. Atraxa Superfriends", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LotusCyan,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF1C222B),
                            unfocusedContainerColor = Color(0xFF1C222B),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Format Selector Dropdown
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FORMAT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = Color(0xFF1C222B),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { formatDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = FORMAT_OPTIONS.firstOrNull { it.first == selectedFormat }?.second ?: selectedFormat,
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF8B949E))
                            }
                        }

                        DropdownMenu(
                            expanded = formatDropdownExpanded,
                            onDismissRequest = { formatDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E242E))
                        ) {
                            FORMAT_OPTIONS.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            color = if (selectedFormat == code) LotusCyan else TextPrimary,
                                            fontWeight = if (selectedFormat == code) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedFormat = code
                                        formatDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Description Optional
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DESCRIPTION (OPTIONAL)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Notes or theme", color = TextMuted) },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LotusCyan,
                            unfocusedBorderColor = Color(0xFF30363D),
                            focusedContainerColor = Color(0xFF1C222B),
                            unfocusedContainerColor = Color(0xFF1C222B),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCreating
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (deckName.isNotBlank()) {
                                onCreate(deckName.trim(), selectedFormat, description.trim().ifBlank { null })
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = LotusCyan),
                        shape = RoundedCornerShape(12.dp),
                        enabled = deckName.isNotBlank() && !isCreating
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creating...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Deck", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
