package com.decklotus.companion.ui.capture

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.decklotus.companion.camera.CameraController
import com.decklotus.companion.data.AppSettings
import com.decklotus.companion.data.SettingsRepository
import com.decklotus.companion.network.*
import com.decklotus.companion.vision.CardGeometry
import com.decklotus.companion.vision.CardHasher
import com.decklotus.companion.vision.CollectorOcr
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
    val sessionScanCount: Int = 0
)

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    val settingsFlow: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val ingestApi = IngestApi()
    private val mockServer = MockIngestServer()

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
            settingsFlow.collect { settings ->
                if (settings.useMockServer && !mockServer.isRunning) {
                    mockServer.start(8088)
                } else if (!settings.useMockServer && mockServer.isRunning) {
                    mockServer.stop()
                }
            }
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

    fun triggerCapture(cameraController: CameraController, previewView: PreviewView? = null) {
        if (_uiState.value.isCapturing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, lastError = null) }
            val totalStart = System.nanoTime()

            try {
                val settings = settingsFlow.value

                // 1. Instant zero-lag frame acquisition
                val capStart = System.nanoTime()
                val rawBitmap = previewView?.bitmap ?: cameraController.takePictureBitmap()
                val capMs = (System.nanoTime() - capStart) / 1_000_000

                // 2. Warp / Rectify to standard 487x680 card frame
                val geomStart = System.nanoTime()
                val w = rawBitmap.width.toFloat()
                val h = rawBitmap.height.toFloat()

                val targetAspect = 63.0f / 88.0f
                var cardH = h * 0.72f
                var cardW = cardH * targetAspect
                if (cardW > w * 0.88f) {
                    cardW = w * 0.88f
                    cardH = cardW / targetAspect
                }
                val left = (w - cardW) / 2.0f
                val top = (h - cardH) / 2.0f

                val quad = listOf(
                    PointF(left, top),
                    PointF(left + cardW, top),
                    PointF(left + cardW, top + cardH),
                    PointF(left, top + cardH)
                )

                val rectified = withContext(Dispatchers.Default) {
                    CardGeometry.warpQuad(rawBitmap, quad, CardGeometry.HASH_WIDTH, CardGeometry.HASH_HEIGHT)
                }

                // 3. Parallel Execution: DCT Hashing (CPU/NEON) + Dual-Region OCR (Tensor G5 NPU)
                val procStart = System.nanoTime()
                val (hashes, fullOcr) = coroutineScope {
                    val hashDeferred = async(Dispatchers.Default) {
                        CardHasher.hashRectified(rectified)
                    }

                    val ocrDeferred = async(Dispatchers.Default) {
                        val titleCrop = CollectorOcr.cropTitleRegion(rectified)
                        val collectorCrop = CollectorOcr.cropCollectorRegion(rectified)

                        val titleTask = async { CollectorOcr.recognizeText(titleCrop) }
                        val collectorTask = async { CollectorOcr.recognizeText(collectorCrop) }

                        CollectorOcr.parseFullCardOcr(titleTask.await(), collectorTask.await())
                    }

                    hashDeferred.await() to ocrDeferred.await()
                }
                val procMs = (System.nanoTime() - procStart) / 1_000_000

                // 4. Ingest API POST
                val liveMeta = cameraController.liveMetadata.value
                val request = IngestRequest(
                    artHash = hashes.artHash,
                    frameHash = hashes.frameHash,
                    ocr = IngestOcrData(
                        name = fullOcr.name,
                        setCode = fullOcr.setCode,
                        collector = fullOcr.collectorNumber,
                        language = fullOcr.language,
                        rawLines = fullOcr.rawLines,
                        confidence = fullOcr.confidence
                    ),
                    capture = IngestCaptureMetadata(
                        exposureNs = liveMeta.exposureTimeNs,
                        iso = liveMeta.isoSensitivity,
                        focusDist = liveMeta.focusDistanceDiopters
                    ),
                    commit = IngestCommitOptions(isFoil = fullOcr.isFoil)
                )

                val targetUrl = if (settings.useMockServer) "http://127.0.0.1:8088" else settings.baseUrl
                val netResult = ingestApi.postIngest(targetUrl, settings.apiToken, request)
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000

                val timings = CaptureTimings(
                    captureMs = capMs,
                    hashMs = procMs / 2,
                    ocrMs = procMs,
                    networkMs = netResult.latencyMs,
                    totalMs = totalMs
                )

                if (netResult.response != null) {
                    triggerHaptic(netResult.response.tier == "confident")
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastResponse = netResult.response,
                            lastTimings = timings,
                            rectifiedCardBitmap = rectified,
                            sessionScanCount = it.sessionScanCount + 1
                        )
                    }
                } else {
                    triggerHaptic(isSuccess = false)
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            lastError = netResult.errorMessage ?: "Network request failed",
                            lastTimings = timings
                        )
                    }
                }

            } catch (e: Exception) {
                triggerHaptic(isSuccess = false)
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000
                _uiState.update {
                    it.copy(
                        isCapturing = false,
                        lastError = e.localizedMessage ?: e.javaClass.simpleName,
                        lastTimings = CaptureTimings(totalMs = totalMs)
                    )
                }
            }
        }
    }

    private fun triggerHaptic(isSuccess: Boolean) {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (isSuccess) {
                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            } else {
                VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(if (isSuccess) 30 else 100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mockServer.stop()
    }
}