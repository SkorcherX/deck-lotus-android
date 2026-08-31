package com.decklotus.companion.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.decklotus.companion.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    private val _liveMetadata = MutableStateFlow(LiveCaptureMetadata())
    val liveMetadata: StateFlow<LiveCaptureMetadata> = _liveMetadata.asStateFlow()

    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    fun bindCamera(
        previewView: PreviewView,
        settings: AppSettings,
        onCameraBound: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val previewBuilder = Preview.Builder()
            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

            val previewExtender = Camera2Interop.Extender(previewBuilder)
            val captureExtender = Camera2Interop.Extender(imageCaptureBuilder)

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                    val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                    val focus = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0.0f
                    val afMode = result.get(CaptureResult.CONTROL_AF_MODE)
                    val aeMode = result.get(CaptureResult.CONTROL_AE_MODE)

                    frameCount++
                    val now = System.currentTimeMillis()
                    val dt = now - lastFpsTimestamp
                    var currentFps = _liveMetadata.value.fps
                    if (dt >= 1000) {
                        currentFps = (frameCount * 1000.0) / dt
                        frameCount = 0
                        lastFpsTimestamp = now
                    }

                    _liveMetadata.value = LiveCaptureMetadata(
                        exposureTimeNs = exp,
                        isoSensitivity = iso,
                        focusDistanceDiopters = focus,
                        isAfLocked = afMode == CaptureRequest.CONTROL_AF_MODE_OFF,
                        isAeLocked = aeMode == CaptureRequest.CONTROL_AE_MODE_OFF,
                        fps = currentFps
                    )
                }
            }

            previewExtender.setSessionCaptureCallback(captureCallback)
            applyManualControls(previewExtender, settings)
            applyManualControls(captureExtender, settings)

            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = imageCaptureBuilder.build().also {
                imageCapture = it
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                camera = boundCamera

                if (settings.torchEnabled) {
                    boundCamera.cameraControl.enableTorch(true)
                }

                onCameraBound()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, mainExecutor)
    }

    private fun applyManualControls(
        extender: Camera2Interop.Extender<*>,
        settings: AppSettings
    ) {
        if (settings.autoFocus) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF
            )
            extender.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                settings.focusDistanceDiopters
            )
        }

        if (settings.autoExposure) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                settings.exposureTimeNs
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                settings.isoSensitivity
            )
        }

        extender.setCaptureRequestOption(
            CaptureRequest.TONEMAP_MODE,
            CaptureRequest.TONEMAP_MODE_FAST
        )
    }

    fun updateManualControls(settings: AppSettings) {
        val cam = camera ?: return
        val control = Camera2CameraControl.from(cam.cameraControl)
        val builder = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)

        if (settings.autoFocus) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, settings.focusDistanceDiopters)
        }

        if (settings.autoExposure) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.exposureTimeNs)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, settings.isoSensitivity)
        }

        control.setCaptureRequestOptions(builder.build())
        cam.cameraControl.enableTorch(settings.torchEnabled)
    }

    suspend fun takePictureBitmap(): Bitmap {
        val capture = imageCapture ?: throw IllegalStateException("ImageCapture not bound")

        val proxy = suspendCancellableCoroutine<ImageProxy> { continuation ->
            capture.takePicture(
                mainExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        continuation.resume(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }

        return withContext(Dispatchers.Default) {
            try {
                imageProxyToBitmap(proxy)
            } finally {
                proxy.close()
            }
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotationDegrees = image.imageInfo.rotationDegrees

        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
    }
}