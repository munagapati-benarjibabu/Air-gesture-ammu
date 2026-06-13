package com.example.projectammu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

class AirGestureController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView?,
    private val onGesture: (AirGesture) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private var gestureRecognizer: GestureRecognizer? = null
    private var anchorHandCenterX = 0f
    private var anchorHandCenterY = 0f
    private var hasAnchorHandCenter = false
    private var lastHandSeenTime = 0L
    private var lastGestureTime = 0L
    private var lastStatus = ""
    private var lastStatusTime = 0L

    fun start() {
        initializeRecognizer()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(analyzerExecutor, ::analyzeFrame)
                    }

                val preview = previewView?.let { view ->
                    Preview.Builder().build().also {
                        it.surfaceProvider = view.surfaceProvider
                    }
                }

                cameraProvider.unbindAll()
                if (preview == null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analyzer
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analyzer
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun stop() {
        ProcessCameraProvider.getInstance(context).addListener(
            {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
                gestureRecognizer?.close()
                gestureRecognizer = null
                analyzerExecutor.shutdown()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun initializeRecognizer() {
        if (gestureRecognizer != null) {
            return
        }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()

        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .build()

        gestureRecognizer = GestureRecognizer.createFromOptions(context, options)
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val recognizer = gestureRecognizer

        if (recognizer == null) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxy.toBitmapSafely()

        if (bitmap == null) {
            imageProxy.close()
            return
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestampMs = SystemClock.uptimeMillis()
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
            .build()
        val result = recognizer.recognizeForVideo(mpImage, imageOptions, timestampMs)

        detectBuiltInGesture(result.gestures())
        detectSwipeGesture(result.landmarks())

        imageProxy.close()
    }

    private fun detectBuiltInGesture(gestures: List<List<com.google.mediapipe.tasks.components.containers.Category>>) {
        val topGesture = gestures.firstOrNull()?.maxByOrNull { it.score() } ?: return

        if (topGesture.score() < CATEGORY_CONFIDENCE_THRESHOLD) {
            return
        }

        when (topGesture.categoryName()) {
            "Open_Palm" -> emitGesture(AirGesture.OpenPalm)
            "Closed_Fist" -> emitGesture(AirGesture.ClosedFist)
        }
    }

    private fun detectSwipeGesture(
        hands: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>
    ) {
        val landmarks = hands.firstOrNull()

        if (landmarks.isNullOrEmpty()) {
            if (SystemClock.elapsedRealtime() - lastHandSeenTime > HAND_LOST_TIMEOUT_MS) {
                hasAnchorHandCenter = false
                emitStatus("No hand detected")
            }
            return
        }

        lastHandSeenTime = SystemClock.elapsedRealtime()
        val centerX = landmarks.map { it.x() }.average().toFloat()
        val centerY = landmarks.map { it.y() }.average().toFloat()
        emitStatus("Hand detected")

        if (!hasAnchorHandCenter) {
            resetSwipeAnchor(centerX, centerY)
            return
        }

        val dx = centerX - anchorHandCenterX
        val dy = centerY - anchorHandCenterY

        when {
            abs(dx) > SWIPE_THRESHOLD && abs(dx) > abs(dy) -> {
                emitGesture(if (dx > 0f) AirGesture.SwipeRight else AirGesture.SwipeLeft)
                resetSwipeAnchor(centerX, centerY)
            }

            abs(dy) > SWIPE_THRESHOLD -> {
                emitGesture(if (dy > 0f) AirGesture.SwipeDown else AirGesture.SwipeUp)
                resetSwipeAnchor(centerX, centerY)
            }
        }
    }

    private fun resetSwipeAnchor(centerX: Float, centerY: Float) {
        anchorHandCenterX = centerX
        anchorHandCenterY = centerY
        hasAnchorHandCenter = true
    }

    private fun emitGesture(gesture: AirGesture) {
        val now = SystemClock.elapsedRealtime()

        if (now - lastGestureTime < GESTURE_COOLDOWN_MS) {
            return
        }

        lastGestureTime = now
        ContextCompat.getMainExecutor(context).execute {
            onStatus(gesture.label)
            onGesture(gesture)
        }
    }

    private fun emitStatus(status: String) {
        val now = SystemClock.elapsedRealtime()

        if (status == lastStatus && now - lastStatusTime < STATUS_COOLDOWN_MS) {
            return
        }

        lastStatus = status
        lastStatusTime = now

        ContextCompat.getMainExecutor(context).execute {
            onStatus(status)
        }
    }

    private fun ImageProxy.toBitmapSafely(): Bitmap? {
        return runCatching {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }

    companion object {
        private const val MODEL_ASSET_PATH = "gesture_recognizer.task"
        private const val GESTURE_COOLDOWN_MS = 700L
        private const val SWIPE_THRESHOLD = 0.12f
        private const val CATEGORY_CONFIDENCE_THRESHOLD = 0.55f
        private const val JPEG_QUALITY = 75
        private const val HAND_LOST_TIMEOUT_MS = 900L
        private const val STATUS_COOLDOWN_MS = 1_000L
    }
}

sealed class AirGesture(val label: String) {
    data object SwipeLeft : AirGesture("Swipe left")
    data object SwipeRight : AirGesture("Swipe right")
    data object SwipeUp : AirGesture("Swipe up")
    data object SwipeDown : AirGesture("Swipe down")
    data object OpenPalm : AirGesture("Open palm")
    data object ClosedFist : AirGesture("Closed fist")
}
