package com.decklotus.companion.ui.capture

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.decklotus.companion.camera.CameraController
import com.decklotus.companion.data.AppSettings
import com.decklotus.companion.data.ScannedCardItem
import com.decklotus.companion.data.SettingsRepository
import com.decklotus.companion.matcher.LocalCardResolver
import com.decklotus.companion.network.*
import com.decklotus.companion.util.SoundFeedback
import com.decklotus.companion.vision.CardDetector
import com.decklotus.companion.vision.CardGeometry
import com.decklotus.companion.vision.CardHasher
import com.decklotus.companion.vision.CardSettleDetector
import com.decklotus.companion.vision.CollectorOcr
import com.decklotus.companion.vision.DetectedCardQuad
import com.decklotus.companion.vision.SettleState
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class PriceBand(val minPrice: Double, val colorArgb: Int, val bandName: String) {
    PURPLE(20.0, 0xFFC084FC.toInt(), "purple"),
    BLUE(10.0, 0xFF60A5FA.toInt(), "blue"),
    GREEN(5.0, 0xFF4ADE80.toInt(), "green"),
    YELLOW(1.0, 0xFFFBBF24.toInt(), "yellow"),
    GREY(Double.NEGATIVE_INFINITY, 0xFFCBD5E1.toInt(), "grey"),

    /**
     * No price at all, which is a different statement from "cheap".
     *
     * 15,361 printings have no tcgplayer row, and grey is the band that says a
     * card is worth setting aside — pulsing it for a card nobody has priced
     * tells the person sorting the box something the data does not support.
     * Kept out of [fromPrice]'s threshold walk by its own branch; its
     * NEGATIVE_INFINITY minimum never gets compared.
     */
    UNKNOWN(Double.NEGATIVE_INFINITY, 0xFF64748B.toInt(), "unknown");

    companion object {
        fun fromPrice(price: Double?): PriceBand {
            if (price == null) return UNKNOWN
            return entries.firstOrNull { it != UNKNOWN && price >= it.minPrice } ?: GREY
        }
    }
}

/**
 * Identity of one committed line, for matching a server response back to the
 * cards on the device.
 *
 * The finish is part of it because it is part of the server's unique key — a
 * foil and a non-foil of the same printing are separate rows in
 * `owned_printings` and must stay separate lines here. It is nullable only for
 * the return trip: a rejection reports the fields the line was entered with and
 * does not echo isFoil, so a null there means "either finish" rather than
 * "non-foil", which would leave a rejected foil in the session forever.
 */
data class CommitKey(
    val name: String?,
    val setCode: String?,
    val collectorNumber: String?,
    val isFoil: Boolean?
) {
    fun matches(name: String, setCode: String, collectorNumber: String, isFoil: Boolean): Boolean =
        (this.name == null || this.name.equals(name, ignoreCase = true)) &&
            (this.setCode == null || this.setCode.equals(setCode, ignoreCase = true)) &&
            (this.collectorNumber == null || this.collectorNumber.equals(collectorNumber, ignoreCase = true)) &&
            (this.isFoil == null || this.isFoil == isFoil)
}

enum class CommitDestination(val label: String) {
    COLLECTION("Collection"),
    DECK("Deck")
}

data class CaptureTimings(
    val captureMs: Long = 0,
    val hashMs: Long = 0,
    val ocrMs: Long = 0,
    val networkMs: Long = 0,
    val totalMs: Long = 0
)

data class CaptureUiState(
    val isCapturing: Boolean = false,
    val lastResponse: IngestResponse? = null,
    val lastTimings: CaptureTimings? = null,
    val lastError: String? = null,
    val rectifiedCardBitmap: Bitmap? = null,
    val cradleState: SettleState = SettleState.WAITING_FOR_CARD,
    val shutterFlashTrigger: Long = 0L,
    val matchPulseTrigger: Long = 0L,
    val matchPulseColor: Int = 0xFFCBD5E1.toInt(),
    val isMiss: Boolean = false,
    val detectedCard: DetectedCardQuad? = null,
    val isCommitting: Boolean = false
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    val settingsFlow: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _sessionCards = MutableStateFlow<List<ScannedCardItem>>(emptyList())
    val sessionCards: StateFlow<List<ScannedCardItem>> = _sessionCards.asStateFlow()

    val totalCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Only the cards that have a price. See [unpricedCardsCount]. */
    val totalSessionValueUsd: StateFlow<Double> = _sessionCards.map { list ->
        list.sumOf { it.totalItemPriceUsd ?: 0.0 }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /**
     * Copies the session total could not account for.
     */
    val unpricedCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.marketPriceUsd == null }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val foilCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.isFoil }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val mainboardCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.boardType == "mainboard" }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val sideboardCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.boardType == "sideboard" }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val maybeboardCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.boardType == "maybeboard" }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val commanderCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.isCommander }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Destination & Deck Management
    private val _commitDestination = MutableStateFlow(CommitDestination.COLLECTION)
    val commitDestination: StateFlow<CommitDestination> = _commitDestination.asStateFlow()

    private val _userDecks = MutableStateFlow<List<DeckSummary>>(emptyList())
    val userDecks: StateFlow<List<DeckSummary>> = _userDecks.asStateFlow()

    private val _selectedDeck = MutableStateFlow<DeckSummary?>(null)
    val selectedDeck: StateFlow<DeckSummary?> = _selectedDeck.asStateFlow()

    private val _isLoadingDecks = MutableStateFlow(false)
    val isLoadingDecks: StateFlow<Boolean> = _isLoadingDecks.asStateFlow()

    private val _deckErrorMessage = MutableStateFlow<String?>(null)
    val deckErrorMessage: StateFlow<String?> = _deckErrorMessage.asStateFlow()

    private val _isCheckingShortfall = MutableStateFlow(false)
    val isCheckingShortfall: StateFlow<Boolean> = _isCheckingShortfall.asStateFlow()

    private val _shortfalls = MutableStateFlow<List<OwnershipShortfall>?>(null)
    val shortfalls: StateFlow<List<OwnershipShortfall>?> = _shortfalls.asStateFlow()

    val apiClient = DeckLotusApiClient()
    private val localResolver = LocalCardResolver(application)
    private val soundFeedback = SoundFeedback(application)
    private val settleDetector = CardSettleDetector()
    private val cardDetector = CardDetector()

    val isSessionTrayOpen = MutableStateFlow(false)

    fun setSessionTrayOpen(isOpen: Boolean) {
        isSessionTrayOpen.value = isOpen
    }

    private val _isPortalOpen = MutableStateFlow(false)
    val isPortalOpen: StateFlow<Boolean> = _isPortalOpen.asStateFlow()

    fun openCloudflarePortal() {
        _isPortalOpen.value = true
    }

    fun closeCloudflarePortal() {
        _isPortalOpen.value = false
    }

    fun onCloudflareAuthSuccess() {
        _isPortalOpen.value = false
        Toast.makeText(getApplication(), "✓ Cloudflare Access authenticated. Tap Commit to sync batch.", Toast.LENGTH_LONG).show()
    }

    private var autoScanJob: Job? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            localResolver.initialize()
        }
    }

    fun reloadResolver() {
        viewModelScope.launch(Dispatchers.IO) {
            localResolver.reload()
        }
    }

    fun startAutoScanLoop(cameraController: CameraController, previewView: PreviewView) {
        autoScanJob?.cancel()
        autoScanJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(33) // ~30 FPS preview analysis
                val settings = settingsFlow.value
                if (_uiState.value.isCapturing || isSessionTrayOpen.value) {
                    delay(80)
                    continue
                }

                val frameBitmap = withContext(Dispatchers.Main) { previewView.bitmap } ?: continue
                
                val detected = cardDetector.detectCard(frameBitmap)
                val settled = settleDetector.processFrame(frameBitmap)

                _uiState.update { 
                    it.copy(
                        cradleState = settleDetector.state,
                        detectedCard = detected
                    ) 
                }

                if (settled && settings.autoScanEnabled && !_uiState.value.isCapturing) {
                    withContext(Dispatchers.Main) {
                        triggerCapture(cameraController, previewView, isAutoTriggered = true)
                    }
                }
            }
        }
    }

    fun stopAutoScanLoop() {
        autoScanJob?.cancel()
        autoScanJob = null
        settleDetector.reset()
    }

    fun removeCardFromSession(id: String) {
        _sessionCards.update { it.filterNot { item -> item.id == id } }
    }

    fun incrementQuantity(id: String) {
        _sessionCards.update { list ->
            list.map { item ->
                if (item.id == id) item.copy(quantity = item.quantity + 1) else item
            }
        }
    }

    fun decrementQuantity(id: String) {
        _sessionCards.update { list ->
            list.mapNotNull { item ->
                if (item.id == id) {
                    val next = item.quantity - 1
                    if (next <= 0) null else item.copy(quantity = next)
                } else item
            }
        }
    }

    fun toggleFoil(id: String) {
        _sessionCards.update { list ->
            list.map { item ->
                if (item.id == id) item.copy(isFoil = !item.isFoil) else item
            }
        }
    }

    fun clearSession() {
        _sessionCards.value = emptyList()
        _uiState.update { it.copy(lastResponse = null, lastError = null) }
    }

    fun toggleAutoScan() {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(autoScanEnabled = !it.autoScanEnabled) }
        }
    }

    fun toggleSound() {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(soundFeedbackEnabled = !it.soundFeedbackEnabled) }
        }
    }

    fun toggleTorch() {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(torchEnabled = !it.torchEnabled) }
        }
    }

    fun toggleAutoFocus() {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(autoFocus = !it.autoFocus) }
        }
    }

    fun selectActiveProfile(profileId: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(activeProfileId = profileId) }
        }
    }

    fun setCommitDestination(destination: CommitDestination) {
        _commitDestination.value = destination
        if (destination == CommitDestination.DECK && _userDecks.value.isEmpty()) {
            loadUserDecks()
        }
    }

    fun selectDeck(deck: DeckSummary) {
        _selectedDeck.value = deck
    }

    fun loadUserDecks() {
        val settings = settingsFlow.value
        if (settings.baseUrl.isBlank()) return

        viewModelScope.launch {
            _isLoadingDecks.value = true
            _deckErrorMessage.value = null
            val result = apiClient.fetchUserDecks(settings.baseUrl, settings.effectiveToken)
            _isLoadingDecks.value = false
            result.fold(
                onSuccess = { list ->
                    _userDecks.value = list
                    if (_selectedDeck.value == null && list.isNotEmpty()) {
                        _selectedDeck.value = list.first()
                    } else if (_selectedDeck.value != null) {
                        // Refresh selected deck reference with updated count
                        _selectedDeck.value = list.firstOrNull { it.id == _selectedDeck.value?.id } ?: list.firstOrNull()
                    }
                },
                onFailure = { error ->
                    _deckErrorMessage.value = error.localizedMessage ?: "Failed to load decks"
                }
            )
        }
    }

    fun createNewDeck(
        name: String,
        format: String = "commander",
        description: String? = null,
        onCreated: (DeckSummary) -> Unit = {}
    ) {
        val settings = settingsFlow.value
        if (settings.baseUrl.isBlank()) {
            Toast.makeText(getApplication(), "Set Server URL in Settings first", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isLoadingDecks.value = true
            val result = apiClient.createDeck(settings.baseUrl, settings.effectiveToken, name, format, description)
            _isLoadingDecks.value = false
            result.fold(
                onSuccess = { newDeck ->
                    _userDecks.update { listOf(newDeck) + it }
                    _selectedDeck.value = newDeck
                    Toast.makeText(getApplication(), "✓ Created deck \"${newDeck.name}\"", Toast.LENGTH_SHORT).show()
                    onCreated(newDeck)
                },
                onFailure = { error ->
                    Toast.makeText(getApplication(), "Failed to create deck: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun updateCardBoardType(id: String, boardType: String) {
        _sessionCards.update { list ->
            list.map { item ->
                if (item.id == id) item.copy(boardType = boardType) else item
            }
        }
    }

    fun toggleCardCommander(id: String) {
        _sessionCards.update { list ->
            list.map { item ->
                if (item.id == id) item.copy(isCommander = !item.isCommander) else item
            }
        }
    }

    private suspend fun syncServerPrintingIds(baseUrl: String, token: String?): List<ScannedCardItem> {
        val currentCards = _sessionCards.value
        if (currentCards.isEmpty() || baseUrl.isBlank()) return currentCards

        val scanItems = currentCards.map { card ->
            BatchResolveScanItem(
                id = card.id,
                name = card.name,
                setCode = card.setCode,
                collectorNumber = card.collectorNumber
            )
        }

        val resolveResult = apiClient.resolveBatchScans(baseUrl, token, scanItems)
        return resolveResult.fold(
            onSuccess = { idMap ->
                if (idMap.isEmpty()) {
                    currentCards
                } else {
                    val updated = currentCards.map { card ->
                        val serverId = idMap[card.id]
                        if (serverId != null && serverId != card.printingId) {
                            card.copy(printingId = serverId)
                        } else card
                    }
                    _sessionCards.value = updated
                    updated
                }
            },
            onFailure = { error ->
                Log.w("CaptureViewModel", "Server printing ID batch resolution failed: ${error.message}, using local IDs")
                currentCards
            }
        )
    }

    fun checkShortfallForSession() {
        val currentCards = _sessionCards.value
        if (currentCards.isEmpty()) {
            _shortfalls.value = emptyList()
            return
        }
        val settings = settingsFlow.value
        if (settings.baseUrl.isBlank()) return

        viewModelScope.launch {
            _isCheckingShortfall.value = true
            val syncedCards = syncServerPrintingIds(settings.baseUrl, settings.effectiveToken)

            val items = syncedCards.groupBy { "${it.printingId}:${it.isFoil}" }.map { (_, group) ->
                val first = group.first()
                ScanShortfallItem(
                    printingId = first.printingId,
                    quantity = group.sumOf { it.quantity },
                    isFoil = first.isFoil
                )
            }

            val result = apiClient.checkShortfall(settings.baseUrl, settings.effectiveToken, items)
            _isCheckingShortfall.value = false
            result.fold(
                onSuccess = { _shortfalls.value = it },
                onFailure = { _shortfalls.value = emptyList() }
            )
        }
    }

    fun commitBatchToDeck(deckId: Int, alsoAddToCollection: Boolean) {
        val currentCards = _sessionCards.value
        if (currentCards.isEmpty()) return

        val settings = settingsFlow.value
        if (settings.baseUrl.isBlank()) {
            Toast.makeText(getApplication(), "Set Server URL in Settings first", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true) }

            val maintenance = apiClient.maintenanceStatus(settings.baseUrl)
            if (maintenance?.blocksWrites == true) {
                _uiState.update { it.copy(isCommitting = false) }
                val what = maintenance.label ?: "A card data update"
                Toast.makeText(
                    getApplication(),
                    "$what is in progress — session kept, try again when it finishes",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val syncedCards = syncServerPrintingIds(settings.baseUrl, settings.effectiveToken)

            val items = syncedCards.map { card ->
                ScanCommitItem(
                    printingId = card.printingId,
                    quantity = card.quantity,
                    isFoil = card.isFoil,
                    boardType = card.boardType,
                    isCommander = card.isCommander
                )
            }

            val targetDeckName = _selectedDeck.value?.name ?: "Deck #$deckId"
            val outcome = apiClient.commitBatchToDeck(
                baseUrl = settings.baseUrl,
                token = settings.effectiveToken,
                deckId = deckId,
                items = items,
                alsoAddToCollection = alsoAddToCollection
            )
            _uiState.update { it.copy(isCommitting = false) }

            if (outcome.isCleanSuccess) {
                val addedToColMsg = if (outcome.addedToCollectionCopies > 0) {
                    " (and added ${outcome.addedToCollectionCopies} missing to collection)"
                } else ""
                Toast.makeText(
                    getApplication(),
                    "✓ Added ${outcome.totalCopies} cards to \"$targetDeckName\"$addedToColMsg",
                    Toast.LENGTH_LONG
                ).show()
                clearSession()
                isSessionTrayOpen.value = false
                loadUserDecks() // Refresh deck card counts
                return@launch
            }

            if (outcome.isCloudflareAuthRequired) {
                _isPortalOpen.value = true
                Toast.makeText(
                    getApplication(),
                    "Cloudflare Access login required. Opening login portal...",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            Toast.makeText(
                getApplication(),
                "Failed to commit to deck: ${outcome.transportError ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun commitBatchToCollection() {
        val currentCards = _sessionCards.value
        if (currentCards.isEmpty()) return

        val settings = settingsFlow.value
        if (settings.baseUrl.isBlank()) {
            Toast.makeText(getApplication(), "Set Server URL in Settings first", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCommitting = true) }

            // A commit landing while the server rebuilds its card tables comes
            // back as a batch of "No printing found" — every scan in the box
            // reported as unreadable when nothing was wrong with them. Hold the
            // session instead; it is still on the device and still valid in
            // twenty minutes.
            val maintenance = apiClient.maintenanceStatus(settings.baseUrl)
            if (maintenance?.blocksWrites == true) {
                _uiState.update { it.copy(isCommitting = false) }
                val what = maintenance.label ?: "A card data update"
                Toast.makeText(
                    getApplication(),
                    "$what is in progress — session kept, try again when it finishes",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // One line per printing and finish, which is the server's own unique
            // key. Grouping on name alone would merge a foil into its non-foil.
            val itemsByKey = currentCards.groupBy {
                CommitKey(it.name, it.setCode, it.collectorNumber, it.isFoil)
            }
            // Read off the grouped cards rather than the key: CommitKey's
            // fields are nullable for the return trip, and the cards they came
            // from are not.
            val items = itemsByKey.map { (_, group) ->
                val first = group.first()
                InventoryBulkAddItem(
                    cardName = first.name,
                    setCode = first.setCode,
                    collectorNumber = first.collectorNumber,
                    quantity = group.sumOf { it.quantity },
                    isFoil = first.isFoil
                )
            }

            val targetName = settings.activeProfile?.name ?: "Collection"
            val outcome = apiClient.commitBatchToCollection(settings.baseUrl, settings.effectiveToken, items)
            _uiState.update { it.copy(isCommitting = false) }

            if (outcome.isCleanSuccess) {
                Toast.makeText(
                    getApplication(),
                    "✓ Committed ${outcome.addedCopies} cards to $targetName's collection!",
                    Toast.LENGTH_LONG
                ).show()
                clearSession()
                isSessionTrayOpen.value = false
                return@launch
            }

            if (outcome.isCloudflareAuthRequired) {
                _isPortalOpen.value = true
                Toast.makeText(
                    getApplication(),
                    "Cloudflare Access login required. Opening login portal...",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            // Anything the server wrote is gone from the session; anything it
            // refused, or never answered about, stays. Clearing the whole thing
            // because part of it failed loses good scans; keeping the whole
            // thing invites a second commit of cards already added.
            val keptKeys = (
                outcome.rejected.map { CommitKey(it.cardName, it.setCode, it.collectorNumber, null) } +
                outcome.uncommitted.map { CommitKey(it.cardName, it.setCode, it.collectorNumber, it.isFoil) }
            ).toSet()

            _sessionCards.update { cards ->
                cards.filter { card ->
                    keptKeys.any { it.matches(card.name, card.setCode, card.collectorNumber, card.isFoil) }
                }
            }

            val message = buildString {
                if (outcome.addedCopies > 0) append("Added ${outcome.addedCopies} to $targetName. ")
                if (outcome.rejected.isNotEmpty()) {
                    append("${outcome.rejected.size} not recognised — kept for review. ")
                }
                if (outcome.uncommitted.isNotEmpty()) {
                    append(
                        "${outcome.uncommitted.size} unsent (${outcome.transportError ?: "no reply"}) — " +
                        "kept, but check the collection before resending. "
                    )
                }
            }.trim()

            Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun addCardToSession(
        printing: IngestResolvedPrinting,
        isFoil: Boolean,
        marketPrice: Double?,
        priceType: String?,
        thumbnail: Bitmap?,
        tier: String
    ) {
        _sessionCards.update { list ->
            listOf(
                ScannedCardItem(
                    id = UUID.randomUUID().toString(),
                    printingId = printing.printingId ?: 0,
                    name = printing.name,
                    setCode = printing.setCode,
                    collectorNumber = printing.collector,
                    language = "EN",
                    isFoil = isFoil,
                    quantity = 1,
                    marketPriceUsd = marketPrice,
                    priceType = priceType,
                    thumbnail = thumbnail,
                    tier = tier,
                    timestamp = System.currentTimeMillis()
                )
            ) + list
        }
    }

    fun triggerCapture(cameraController: CameraController, previewView: PreviewView? = null, isAutoTriggered: Boolean = false) {
        if (_uiState.value.isCapturing) return

        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isCapturing = true, 
                    lastError = null,
                    shutterFlashTrigger = System.currentTimeMillis()
                ) 
            }
            val totalStart = System.nanoTime()
            val settings = settingsFlow.value

            try {
                // 1. Instant frame grab
                val capStart = System.nanoTime()
                val rawBitmap = previewView?.bitmap ?: cameraController.takePictureBitmap()
                val capMs = (System.nanoTime() - capStart) / 1_000_000

                // 2. Warp / Rectify using detected card corners directly on captured frame
                val detected = cardDetector.detectCard(rawBitmap)
                val w = rawBitmap.width.toFloat()
                val h = rawBitmap.height.toFloat()

                val quad = listOf(
                    PointF(detected.topLeft.x * w, detected.topLeft.y * h),
                    PointF(detected.topRight.x * w, detected.topRight.y * h),
                    PointF(detected.bottomRight.x * w, detected.bottomRight.y * h),
                    PointF(detected.bottomLeft.x * w, detected.bottomLeft.y * h)
                )

                val rectified = withContext(Dispatchers.Default) {
                    CardGeometry.warpQuad(rawBitmap, quad, CardGeometry.HASH_WIDTH, CardGeometry.HASH_HEIGHT)
                }

                // 3. Parallel Execution: 256-bit DCT Hashing + Full-Card OCR
                val procStart = System.nanoTime()
                val (hashes, fullOcr) = coroutineScope {
                    val hashDeferred = async(Dispatchers.Default) {
                        CardHasher.hashRectified(rectified)
                    }

                    val ocrDeferred = async(Dispatchers.Default) {
                        val visionText = CollectorOcr.recognizeText(rectified)
                        CollectorOcr.parseFromVisionText(visionText)
                    }

                    hashDeferred.await() to ocrDeferred.await()
                }
                val procMs = (System.nanoTime() - procStart) / 1_000_000

                // 4. Resolve: On-Device Matcher across 112,815 MTG cards (100% Offline)
                val respStart = System.nanoTime()
                val setTally = _sessionCards.value.groupingBy { it.setCode.uppercase() }.eachCount()
                val resp = localResolver.resolve(hashes.artHash, hashes.frameHash, fullOcr, fullOcr.isFoil, setTally)
                val respMs = (System.nanoTime() - respStart) / 1_000_000
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000

                val timings = CaptureTimings(
                    captureMs = capMs,
                    hashMs = procMs / 2,
                    ocrMs = procMs,
                    networkMs = respMs,
                    totalMs = totalMs
                )

                if (settings.saveDebugCaptures) {
                    withContext(Dispatchers.IO) {
                        saveDebugCapture(rawBitmap, rectified, fullOcr, hashes, resp, timings)
                    }
                }

                if (resp.printing != null && resp.tier != "unresolved") {
                    // Null all the way through when nothing priced this
                    // printing, so the band reads UNKNOWN rather than pulsing
                    // grey for "cheap".
                    val priceVal = resp.marketPriceUsd ?: resp.printing.marketPriceUsd
                    val priceType = resp.priceType ?: resp.printing.priceType
                    val band = PriceBand.fromPrice(priceVal)
                    val isConfident = resp.tier == "confident"

                    triggerHaptic(isConfident)
                    if (settings.soundFeedbackEnabled) {
                        if (isConfident) soundFeedback.playSuccessChime() else soundFeedback.playReviewChime()
                    }
                    settleDetector.markCaptured()

                    addCardToSession(
                        printing = resp.printing,
                        isFoil = fullOcr.isFoil || resp.printing.isFoil,
                        marketPrice = priceVal,
                        priceType = priceType,
                        thumbnail = rectified,
                        tier = resp.tier
                    )

                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastResponse = resp,
                            lastTimings = timings,
                            rectifiedCardBitmap = rectified,
                            matchPulseTrigger = System.currentTimeMillis(),
                            matchPulseColor = band.colorArgb,
                            isMiss = false,
                            cradleState = SettleState.LOCKED_AFTER_SCAN
                        )
                    }
                } else {
                    settleDetector.reset()
                    if (!isAutoTriggered) {
                        triggerHaptic(isSuccess = false)
                        if (settings.soundFeedbackEnabled) soundFeedback.playErrorTone()
                    }
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastResponse = resp,
                            lastError = resp.error ?: "Card not recognized",
                            lastTimings = timings,
                            matchPulseTrigger = System.currentTimeMillis(),
                            matchPulseColor = 0xFFF87171.toInt(),
                            isMiss = true,
                            cradleState = SettleState.WAITING_FOR_CARD
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("DeckLotusDebug", "Capture failed: ${e.message}", e)
                settleDetector.reset()
                triggerHaptic(isSuccess = false)
                if (settings.soundFeedbackEnabled) soundFeedback.playErrorTone()
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastError = e.localizedMessage ?: e.javaClass.simpleName,
                        lastTimings = CaptureTimings(totalMs = totalMs),
                        matchPulseTrigger = System.currentTimeMillis(),
                        matchPulseColor = 0xFFF87171.toInt(),
                        isMiss = true,
                        cradleState = SettleState.WAITING_FOR_CARD
                    )
                }
            }
        }
    }

    private fun saveDebugCapture(
        rawBitmap: Bitmap,
        rectifiedBitmap: Bitmap,
        ocr: CollectorOcr.ParsedCardOcr,
        hashes: CardHasher.CardHashes,
        resp: IngestResponse,
        timings: CaptureTimings
    ) {
        try {
            val dir = File(getApplication<Application>().getExternalFilesDir(null), "debug_captures")
            if (!dir.exists()) dir.mkdirs()

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

            // 1. Save raw unwarped image
            val rawFile = File(dir, "raw_${ts}.png")
            FileOutputStream(rawFile).use { out ->
                rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 2. Save rectified warped crop
            val rectFile = File(dir, "rectified_${ts}.png")
            FileOutputStream(rectFile).use { out ->
                rectifiedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // 3. Save diagnostic dump file
            val logFile = File(dir, "debug_${ts}.txt")
            val content = buildString {
                appendLine("Timestamp: $ts")
                appendLine("Raw Frame: ${rawBitmap.width}x${rawBitmap.height}")
                appendLine("Rectified Crop: ${rectifiedBitmap.width}x${rectifiedBitmap.height}")
                appendLine()
                appendLine("--- OCR PARSED ---")
                appendLine("Title Name: ${ocr.name}")
                appendLine("Set Code: ${ocr.setCode}")
                appendLine("Collector Number: ${ocr.collectorNumber}")
                appendLine("Candidate Numbers: ${ocr.candidateNumbers}")
                appendLine("Language: ${ocr.language}")
                appendLine("Is Foil: ${ocr.isFoil}")
                appendLine("Confidence: ${ocr.confidence}")
                appendLine("Raw OCR Lines (${ocr.rawLines.size}):")
                ocr.rawLines.forEach { appendLine("  - \"$it\"") }
                appendLine()
                appendLine("--- PERCEPTUAL HASHES ---")
                appendLine("Art Hash: ${hashes.artHash}")
                appendLine("Frame Hash: ${hashes.frameHash}")
                appendLine()
                appendLine("--- RESOLUTION RESULT ---")
                appendLine("Tier: ${resp.tier}")
                appendLine("Matched: ${resp.printing?.name} [${resp.printing?.setCode} #${resp.printing?.collector}] (ID=${resp.printing?.printingId})")
                appendLine("Market Price: ${resp.marketPriceUsd} (${resp.priceType})")
                appendLine("Hash Distance Bits: ${resp.hashDistanceBits}")
                appendLine("Error: ${resp.error}")
                appendLine()
                appendLine("--- TIMINGS ---")
                appendLine("Capture: ${timings.captureMs}ms")
                appendLine("Hash: ${timings.hashMs}ms")
                appendLine("OCR: ${timings.ocrMs}ms")
                appendLine("Resolve: ${timings.networkMs}ms")
                appendLine("Total: ${timings.totalMs}ms")
            }
            logFile.writeText(content)

            Log.i("DeckLotusDebug", "Saved debug capture to ${dir.absolutePath} ($ts):\n$content")
        } catch (e: Exception) {
            Log.e("DeckLotusDebug", "Failed to save debug capture: ${e.message}", e)
        }
    }

    private fun srcWOrFallback(w: Float): Float = if (w > 0) w else 1080f

    private fun triggerHaptic(isSuccess: Boolean) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (isSuccess) {
                VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40), -1)
            }
            vib.vibrate(effect)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoScanLoop()
        soundFeedback.release()
        localResolver.close()
    }
}