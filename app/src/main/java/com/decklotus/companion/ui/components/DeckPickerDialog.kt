package com.decklotus.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.decklotus.companion.ui.theme.*

@Composable
fun DeckPickerDialog(
    decks: List<DeckSummary>,
    selectedDeckId: Int?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onSelectDeck: (DeckSummary) -> Unit,
    onRefreshDecks: () -> Unit,
    onCreateNewDeck: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredDecks = remember(decks, searchQuery) {
        if (searchQuery.isBlank()) decks
        else decks.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                (it.format?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF161B22),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header + Refresh Button
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
                            color = LotusPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Style, contentDescription = null, tint = LotusPurple, modifier = Modifier.size(22.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "TARGET DESTINATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LotusPurple,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Select Deck",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onRefreshDecks,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Decks", tint = LotusCyan, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Search Bar + Create New Deck Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search ${decks.size} decks...", color = TextMuted, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
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

                    Button(
                        onClick = onCreateNewDeck,
                        colors = ButtonDefaults.buttonColors(containerColor = LotusCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Error / Loading State
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = LotusCyan, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Fetching user decks...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF85149), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage, color = Color(0xFFF85149), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = onRefreshDecks) {
                                Text("Retry", color = LotusCyan)
                            }
                        }
                    }
                } else if (filteredDecks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No decks match \"$searchQuery\"" else "No decks found on server",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onCreateNewDeck,
                                colors = ButtonDefaults.buttonColors(containerColor = LotusCyan)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Deck", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Deck List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDecks, key = { it.id }) { deck ->
                            val isSelected = deck.id == selectedDeckId
                            Surface(
                                color = if (isSelected) Color(0xFF1E2837) else Color(0xFF1B2028),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) LotusCyan else Color(0xFF2E3440)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectDeck(deck)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Deck Icon
                                    Surface(
                                        color = if (isSelected) LotusCyan.copy(alpha = 0.2f) else Color(0xFF262C36),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Style,
                                                contentDescription = null,
                                                tint = if (isSelected) LotusCyan else Color(0xFF8B949E),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Deck Info
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = deck.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else TextPrimary
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Format Tag
                                            Surface(
                                                color = Color(0xFF262C36),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = deck.formatDisplayName.uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = LotusCyan,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }

                                            // Card Count Tag
                                            Text(
                                                text = "${deck.totalCount} cards",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )

                                            // Status Tag (if building or idea)
                                            deck.status?.let { status ->
                                                if (status != "ready") {
                                                    Text(
                                                        text = "• $status",
                                                        fontSize = 11.sp,
                                                        color = if (status == "building") TierPickPrinting else TextMuted
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Selection Indicator
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = LotusCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
