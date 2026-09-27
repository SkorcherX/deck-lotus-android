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
import androidx.compose.runtime.*
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
import com.decklotus.companion.data.UserProfile
import com.decklotus.companion.network.DeckSummary
import com.decklotus.companion.network.OwnershipShortfall
import com.decklotus.companion.ui.capture.CommitDestination
import com.decklotus.companion.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTrayBottomSheet(
    cards: List<ScannedCardItem>,
    totalCount: Int,
    totalValueUsd: Double,
    totalFoils: Int,
    mainboardCount: Int = 0,
    sideboardCount: Int = 0,
    maybeboardCount: Int = 0,
    commanderCount: Int = 0,
    destination: CommitDestination,
    selectedDeck: DeckSummary?,
    userDecks: List<DeckSummary>,
    isLoadingDecks: Boolean = false,
    deckErrorMessage: String? = null,
    shortfalls: List<OwnershipShortfall>?,
    isCheckingShortfall: Boolean = false,
    activeProfile: UserProfile?,
    allProfiles: List<UserProfile>,
    isCommitting: Boolean = false,
    onSetDestination: (CommitDestination) -> Unit,
    onSelectDeck: (DeckSummary) -> Unit,
    onRefreshDecks: () -> Unit,
    onCreateNewDeck: (name: String, format: String, description: String?) -> Unit,
    onDismiss: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onToggleFoil: (String) -> Unit,
    onUpdateBoardType: (String, String) -> Unit,
    onToggleCommander: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onRequestCheckShortfall: () -> Unit,
    onCommitToCollection: () -> Unit = {},
    onCommitToDeck: (deckId: Int, alsoAddToCollection: Boolean) -> Unit = { _, _ -> }
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCommitToDeckDialog by remember { mutableStateOf(false) }
    var showDeckPickerDialog by remember { mutableStateOf(false) }
    var showCreateDeckDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

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
            // Header: Title + Action Buttons
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Quick Export Button
                        TextButton(
                            onClick = { showExportDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = LotusCyan)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Quick Discard All Button
                        TextButton(
                            onClick = { showDiscardDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF85149))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Discard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Destination Switcher: [ To Collection ] | [ To Deck ]
            Surface(
                color = Color(0xFF1B2028),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3440)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option: Collection
                    val isCollection = destination == CommitDestination.COLLECTION
                    Surface(
                        color = if (isCollection) LotusPurple else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetDestination(CommitDestination.COLLECTION) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = if (isCollection) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "To Collection",
                                fontSize = 13.sp,
                                fontWeight = if (isCollection) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCollection) Color.White else TextSecondary
                            )
                        }
                    }

                    // Option: Deck
                    val isDeck = destination == CommitDestination.DECK
                    Surface(
                        color = if (isDeck) LotusCyan else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetDestination(CommitDestination.DECK) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Style,
                                contentDescription = null,
                                tint = if (isDeck) Color.Black else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "To Deck",
                                fontSize = 13.sp,
                                fontWeight = if (isDeck) FontWeight.Bold else FontWeight.Normal,
                                color = if (isDeck) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                    title = if (destination == CommitDestination.DECK) "DECK / BATCH" else "CARDS",
                    value = if (destination == CommitDestination.DECK && selectedDeck != null) "${selectedDeck.totalCount} +$totalCount" else "$totalCount",
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

            Spacer(modifier = Modifier.height(10.dp))

            // Destination Target Info Card
            if (destination == CommitDestination.COLLECTION) {
                // Active User Target Pill
                Surface(
                    color = Color(0xFF1B2028),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3440)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = LotusPurple, modifier = Modifier.size(18.dp))
                            Text("Target Collection:", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = activeProfile?.name ?: "Primary User",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            activeProfile?.verifiedUsername?.let {
                                Text("(@$it)", fontSize = 11.sp, color = TierConfident, fontFamily = FontFamily.Monospace)
                            }
                        }

                        if (allProfiles.size > 1) {
                            Surface(
                                color = LotusPurple.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { showConfirmDialog = true }
                            ) {
                                Text(
                                    text = "SWITCH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LotusPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Active Deck Target Pill
                Surface(
                    color = Color(0xFF1B2028),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LotusCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null, tint = LotusCyan, modifier = Modifier.size(18.dp))
                            Text("Target Deck:", fontSize = 12.sp, color = TextSecondary)
                            if (selectedDeck != null) {
                                Column {
                                    Text(
                                        text = selectedDeck.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${selectedDeck.formatDisplayName} • ${selectedDeck.totalCount} cards",
                                        fontSize = 10.sp,
                                        color = LotusCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Text(
                                    text = "None Selected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TierPickPrinting
                                )
                            }
                        }

                        Surface(
                            color = LotusCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { showDeckPickerDialog = true }
                        ) {
                            Text(
                                text = if (selectedDeck == null) "SELECT" else "CHANGE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LotusCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cards, key = { it.id }) { item ->
                        ScannedCardRowItem(
                            item = item,
                            isDeckMode = destination == CommitDestination.DECK,
                            onIncrement = { onIncrement(item.id) },
                            onDecrement = { onDecrement(item.id) },
                            onToggleFoil = { onToggleFoil(item.id) },
                            onUpdateBoard = { onUpdateBoardType(item.id, it) },
                            onToggleCommander = { onToggleCommander(item.id) },
                            onRemove = { onRemove(item.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action Button
                if (destination == CommitDestination.COLLECTION) {
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TierConfident),
                        enabled = !isCommitting
                    ) {
                        if (isCommitting) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing to Collection...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Commit $totalCount Cards to ${activeProfile?.name ?: "Collection"} (${String.format("$%.2f", totalValueUsd)})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (selectedDeck != null) {
                                onRequestCheckShortfall()
                                showCommitToDeckDialog = true
                            } else {
                                showDeckPickerDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LotusCyan),
                        enabled = !isCommitting
                    ) {
                        if (isCommitting) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Adding to Deck...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Style, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedDeck != null) {
                                    "Add $totalCount Cards to ${selectedDeck.name}"
                                } else {
                                    "Select Deck to Commit ($totalCount Cards)"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Row: [Export & Share] + [Discard All]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Export / Share Button
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LotusCyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LotusCyan.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export / Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Discard All Button
                    OutlinedButton(
                        onClick = { showDiscardDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF85149)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF85149).copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Discard All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Safety Confirmation Warning Dialog (Collection)
    if (showConfirmDialog) {
        CommitConfirmationDialog(
            activeProfile = activeProfile,
            allProfiles = allProfiles,
            cardCount = totalCount,
            totalValueUsd = totalValueUsd,
            onSelectProfile = onSelectProfile,
            onConfirm = {
                showConfirmDialog = false
                onCommitToCollection()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    // Commit to Deck Confirmation Dialog
    if (showCommitToDeckDialog && selectedDeck != null) {
        CommitToDeckDialog(
            deck = selectedDeck,
            cardCount = totalCount,
            mainboardCount = mainboardCount,
            sideboardCount = sideboardCount,
            maybeboardCount = maybeboardCount,
            commanderCount = commanderCount,
            totalValueUsd = totalValueUsd,
            shortfalls = shortfalls,
            isCheckingShortfall = isCheckingShortfall,
            isCommitting = isCommitting,
            onConfirm = { alsoAdd ->
                showCommitToDeckDialog = false
                onCommitToDeck(selectedDeck.id, alsoAdd)
            },
            onDismiss = { showCommitToDeckDialog = false }
        )
    }

    // Deck Picker Modal
    if (showDeckPickerDialog) {
        DeckPickerDialog(
            decks = userDecks,
            selectedDeckId = selectedDeck?.id,
            isLoading = isLoadingDecks,
            errorMessage = deckErrorMessage,
            onSelectDeck = {
                onSelectDeck(it)
                showDeckPickerDialog = false
            },
            onRefreshDecks = onRefreshDecks,
            onCreateNewDeck = {
                showDeckPickerDialog = false
                showCreateDeckDialog = true
            },
            onDismiss = { showDeckPickerDialog = false }
        )
    }

    // Create New Deck Modal
    if (showCreateDeckDialog) {
        CreateDeckDialog(
            isCreating = isLoadingDecks,
            onCreate = { name, format, desc ->
                onCreateNewDeck(name, format, desc)
                showCreateDeckDialog = false
            },
            onDismiss = { showCreateDeckDialog = false }
        )
    }

    // Discard All Confirmation Dialog
    if (showDiscardDialog) {
        DiscardConfirmationDialog(
            cardCount = totalCount,
            onConfirmDiscard = {
                showDiscardDialog = false
                onClearAll()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }

    // Export & Share Dialog
    if (showExportDialog) {
        ExportBatchDialog(
            cards = cards,
            onDismiss = { showExportDialog = false }
        )
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
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun ScannedCardRowItem(
    item: ScannedCardItem,
    isDeckMode: Boolean = false,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleFoil: () -> Unit,
    onUpdateBoard: (String) -> Unit = {},
    onToggleCommander: () -> Unit = {},
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

        // Details (Name, Set Code, Collector #, Unit & Total Price, Board & Cmdr)
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

            // Deck Mode Controls: Board Selector ('mainboard' | 'sideboard' | 'maybeboard') + Cmdr flag
            if (isDeckMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // Board Tag (Tapping cycles Main -> Side -> Maybe -> Main)
                    val nextBoard = when (item.boardType) {
                        "mainboard" -> "sideboard"
                        "sideboard" -> "maybeboard"
                        else -> "mainboard"
                    }
                    val boardLabel = when (item.boardType) {
                        "sideboard" -> "SIDE"
                        "maybeboard" -> "MAYBE"
                        else -> "MAIN"
                    }
                    val boardColor = when (item.boardType) {
                        "sideboard" -> TierPickPrinting
                        "maybeboard" -> TextMuted
                        else -> LotusCyan
                    }

                    Surface(
                        color = boardColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, boardColor.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onUpdateBoard(nextBoard) }
                    ) {
                        Text(
                            text = boardLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = boardColor,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }

                    // Commander Toggle
                    Surface(
                        color = if (item.isCommander) LotusPurple.copy(alpha = 0.3f) else Color(0xFF20252D),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (item.isCommander) LotusPurple else Color.Transparent
                        ),
                        modifier = Modifier.clickable { onToggleCommander() }
                    ) {
                        Text(
                            text = if (item.isCommander) "👑 CMDR" else "CMDR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isCommander) LotusPurple else Color(0xFF7A8499),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Price Line
            val each = item.marketPriceUsd
            val total = item.totalItemPriceUsd
            Text(
                text = if (each == null || total == null) {
                    "No price on record"
                } else {
                    val dagger = if (item.isFoilDerivedPrice) "†" else ""
                    "${String.format("$%.2f", each)}$dagger each • Total: ${String.format("$%.2f", total)}$dagger"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (each == null) Color(0xFF7A8499) else Color(0xFF9ECE6A),
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
                    .background(if (isDeckMode) LotusCyan else LotusPurple, CircleShape)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = if (isDeckMode) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
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