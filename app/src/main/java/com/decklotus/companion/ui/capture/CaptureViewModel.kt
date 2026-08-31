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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

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
    val flashPromptTrigger: Long = 0L,
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

    val totalSessionValueUsd: StateFlow<Double> = _sessionCards.map { list ->
        list.sumOf { it.totalItemPriceUsd }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val foilCardsCount: StateFlow<Int> = _sessionCards.map { list ->
        list.filter { it.isFoil }.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val apiClient = DeckLotusApiClient()
    private val localResolver = LocalCardResolver(application)
    private val soundFeedback = SoundFeedback(application)
    private val settleDetector = CardSettleDetector()
    private val cardDetector = CardDetector()

    val isSessionTrayOpen = MutableStateFlow(false)

    fun setSessionTrayOpen(isOpen: Boolean) {
        isSessionTrayOpen.value = isOpen
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

    fun toggleAutoExposure() {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(autoExposure = !it.autoExposure) }
        }
    }

    fun incrementQuantity(cardId: String) {
        _sessionCards.update { list ->
            list.map { if (it.id == cardId) it.copy(quantity = it.quantity + 1) else it }
        }
    }

    fun decrementQuantity(cardId: String) {
        _sessionCards.update { list ->
            list.mapNotNull {
                if (it.id == cardId) {
                    if (it.quantity > 1) it.copy(quantity = it.quantity - 1) else null
                } else it
            }
        }
    }

    fun toggleFoil(cardId: String) {
        _sessionCards.update { list ->
            list.map { if (it.id == cardId) it.copy(isFoil = !it.isFoil) else it }
        }
    }

    fun removeCard(cardId: String) {
        _sessionCards.update { list ->
            list.filterNot { it.id == cardId }
        }
    }

    fun clearSession() {
        _sessionCards.value = emptyList()
        _uiState.update { it.copy(lastResponse = null, lastError = null) }
    }

    fun selectActiveProfile(profileId: String) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(activeProfileId = profileId) }
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
            val items = currentCards
                .groupBy { Triple(it.name, it.setCode, Pair(it.collectorNumber, it.isFoil)) }
                .map { (key, group) ->
                    InventoryBulkAddItem(
                        cardName = key.first,
                        setCode = key.second,
                        collectorNumber = key.third.first,
                        quantity = group.sumOf { it.quantity },
                        isFoil = key.third.second
                    )
                }

            val targetName = settings.activeProfile?.name ?: "Collection"
            val token = settings.effectiveToken
            val result = apiClient.commitBatchToCollection(settings.baseUrl, token, items)
            _uiState.update { it.copy(isCommitting = false) }

            if (result.isSuccess) {
                val res = result.getOrNull()
                val added = res?.added ?: currentCards.sumOf { it.quantity }
                Toast.makeText(getApplication(), "✓ Committed $added cards to $targetName's collection!", Toast.LENGTH_LONG).show()
                clearSession()
                isSessionTrayOpen.value = false
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Commit failed"
                Toast.makeText(getApplication(), "Commit error: $errorMsg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addCardToSession(
        printing: IngestResolvedPrinting,
        isFoil: Boolean,
        marketPrice: Double,
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
            _uiState.update { it.copy(isCapturing = true, lastError = null) }
            val totalStart = System.nanoTime()
            val settings = settingsFlow.value

            try {
                // 1. Instant frame grab
                val capStart = System.nanoTime()
                val rawBitmap = previewView?.bitmap ?: cameraController.takePictureBitmap()
                val capMs = (System.nanoTime() - capStart) / 1_000_000

                // 2. Warp / Rectify using detected card corners
                val w = rawBitmap.width.toFloat()
                val h = rawBitmap.height.toFloat()

                val detected = _uiState.value.detectedCard
                val quad = if (detected != null) {
                    listOf(
                        PointF(detected.topLeft.x * w, detected.topLeft.y * h),
                        PointF(detected.topRight.x * w, detected.topRight.y * h),
                        PointF(detected.bottomRight.x * w, detected.bottomRight.y * h),
                        PointF(detected.bottomLeft.x * w, detected.bottomLeft.y * h)
                    )
                } else {
                    val targetAspect = 63.0f / 88.0f
                    val cardH = h * 0.58f
                    val cardW = cardH * targetAspect
                    val left = (srcWOrFallback(w) - cardW) / 2.0f
                    val top = h * 0.15f
                    listOf(
                        PointF(left, top),
                        PointF(left + cardW, top),
                        PointF(left + cardW, top + cardH),
                        PointF(left, top + cardH)
                    )
                }

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

                if (resp.printing != null && resp.tier != "unresolved") {
                    val isConfident = resp.tier == "confident"
                    triggerHaptic(isConfident)
                    if (settings.soundFeedbackEnabled) {
                        if (isConfident) soundFeedback.playSuccessChime() else soundFeedback.playReviewChime()
                    }
                    settleDetector.markCaptured()

                    addCardToSession(
                        printing = resp.printing,
                        isFoil = fullOcr.isFoil || resp.printing.isFoil,
                        marketPrice = resp.marketPriceUsd ?: resp.printing.marketPriceUsd ?: 0.26,
                        thumbnail = rectified,
                        tier = resp.tier
                    )

                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastResponse = resp,
                            lastTimings = timings,
                            rectifiedCardBitmap = rectified,
                            flashPromptTrigger = System.currentTimeMillis(),
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
                            cradleState = SettleState.WAITING_FOR_CARD
                        )
                    }
                }

            } catch (e: Exception) {
                settleDetector.reset()
                triggerHaptic(isSuccess = false)
                if (settings.soundFeedbackEnabled) soundFeedback.playErrorTone()
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastError = e.localizedMessage ?: e.javaClass.simpleName,
                        lastTimings = CaptureTimings(totalMs = totalMs),
                        cradleState = SettleState.WAITING_FOR_CARD
                    )
                }
            }
        }
    }

    private fun srcWOrFallback(w: Float): Float = if (w > 0) w else 1080f

    private fun triggerHaptic(isSuccess: Boolean) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (isSuccess) {
                VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(if (isSuccess) 35 else 100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoScanLoop()
        soundFeedback.release()
        localResolver.close()
    }
}