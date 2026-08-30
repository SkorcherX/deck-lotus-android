package com.decklotus.companion.ui.capture

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.decklotus.companion.camera.CameraController
import com.decklotus.companion.data.AppSettings
import com.decklotus.companion.data.SettingsRepository
import com.decklotus.companion.network.*
import com.decklotus.companion.vision.CardGeometry
import com.decklotus.companion.vision.CardHasher
import com.decklotus.companion.vision.CollectorOcr
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val collectorCropBitmap: Bitmap? = null
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
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                if (settings.useMockServer && !mockServer.isRunning) {
                    mockServer.start(8088)
                } else if (!settings.useMockServer && mockServer.isRunning) {
                    mockServer.stop()
                }
            }
        }
    }

    fun triggerCapture(cameraController: CameraController) {
        if (_uiState.value.isCapturing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, lastError = null) }
            val totalStart = System.nanoTime()

            try {
                val settings = settingsFlow.value

                // 1. Grab frame from Camera2 session
                val capStart = System.nanoTime()
                val rawBitmap = cameraController.takePictureBitmap()
                val capMs = (System.nanoTime() - capStart) / 1_000_000

                // 2. Warp / Rectify to standard 487x680 card frame
                val hashStart = System.nanoTime()
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

                val rectified = CardGeometry.warpQuad(rawBitmap, quad, CardGeometry.HASH_WIDTH, CardGeometry.HASH_HEIGHT)
                val hashes = CardHasher.hashRectified(rectified)
                val hashMs = (System.nanoTime() - hashStart) / 1_000_000

                // 3. ML Kit Text Recognition v2 on collector block (Tensor G5 NPU)
                val ocrStart = System.nanoTime()
                val collectorCrop = CollectorOcr.cropCollectorRegion(rectified)
                val recognized = CollectorOcr.recognizeText(collectorCrop)
                val parsedOcr = CollectorOcr.parseOcrResult(recognized)
                val ocrMs = (System.nanoTime() - ocrStart) / 1_000_000

                // 4. Submit to Ingest API / Mock
                val liveMeta = cameraController.liveMetadata.value
                val request = IngestRequest(
                    artHash = hashes.artHash,
                    frameHash = hashes.frameHash,
                    ocr = IngestOcrData(
                        setCode = parsedOcr.setCode,
                        collector = parsedOcr.collectorNumber,
                        language = parsedOcr.language,
                        rawLines = parsedOcr.rawLines,
                        confidence = parsedOcr.confidence
                    ),
                    capture = IngestCaptureMetadata(
                        exposureNs = liveMeta.exposureTimeNs,
                        iso = liveMeta.isoSensitivity,
                        focusDist = liveMeta.focusDistanceDiopters
                    ),
                    commit = IngestCommitOptions(isFoil = parsedOcr.isFoil)
                )

                val targetUrl = if (settings.useMockServer) "http://127.0.0.1:8088" else settings.baseUrl
                val netResult = ingestApi.postIngest(targetUrl, settings.apiToken, request)
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000

                val timings = CaptureTimings(
                    captureMs = capMs,
                    hashMs = hashMs,
                    ocrMs = ocrMs,
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
                            collectorCropBitmap = collectorCrop
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