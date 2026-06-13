package com.example.projectammu

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.projectammu.ui.theme.ProjectAmmuTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var airGestureController: AirGestureController? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var spokenCommand by mutableStateOf("Tap To Talk, then say a command")
    private var assistantReply by mutableStateOf("Ammu is ready")
    private var isListening by mutableStateOf(false)
    private var wakeListeningEnabled by mutableStateOf(false)
    private var lostPhoneTrackingEnabled by mutableStateOf(false)
    private var sensorControlEnabled by mutableStateOf(false)
    private var noTouchCallEnabled by mutableStateOf(false)
    private var airGestureEnabled by mutableStateOf(false)
    private var lastAirGestureLabel by mutableStateOf("Camera gestures are off")
    private var permissionStatus by mutableStateOf(PermissionStatus())
    private var secretCodeInput by mutableStateOf("")
    private var lastSensorActionTime = 0L
    private var lastShakeTime = 0L
    private var lastWaveTime = 0L
    private var proximityWasNear = false
    private var isSpeaking = false
    private var acceptNextSpeechWithoutWake = false

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startManualListening()
            } else {
                assistantReply = "Microphone permission is required"
                Toast.makeText(this, assistantReply, Toast.LENGTH_SHORT).show()
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startAirGestureService()
                speak("Air gesture control enabled")
            } else {
                assistantReply = "Camera permission is required for air gestures"
                speak(assistantReply)
            }
        }

    private val requestCallPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val callPermissionGranted =
                permissions[Manifest.permission.ANSWER_PHONE_CALLS] == true ||
                    CallController.hasCallPermission(this)

            if (callPermissionGranted) {
                enableNoTouchCallControl()
            } else {
                assistantReply = "Call permission is required for no touch call control"
                speak(assistantReply)
            }
        }

    private val requestLostPhonePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val locationGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                    hasLocationPermission()

            if (locationGranted) {
                startLostPhoneTracking()
            } else {
                assistantReply = "Location permission is required for lost phone tracking"
                speak(assistantReply)
            }
            refreshPermissionStatus()
        }

    private val requestDashboardPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupTextToSpeech()
        setupSpeechRecognizer()
        setupMotionSensors()
        refreshPermissionStatus()

        setContent {
            ProjectAmmuTheme {
                VoiceAssistantScreen(
                    spokenCommand = spokenCommand,
                    assistantReply = assistantReply,
                    isListening = isListening,
                    wakeListeningEnabled = wakeListeningEnabled,
                    lostPhoneTrackingEnabled = lostPhoneTrackingEnabled,
                    sensorControlEnabled = sensorControlEnabled,
                    noTouchCallEnabled = noTouchCallEnabled,
                    airGestureEnabled = airGestureEnabled,
                    lastAirGestureLabel = lastAirGestureLabel,
                    permissionStatus = permissionStatus,
                    secretCodeInput = secretCodeInput,
                    onSecretCodeChange = { secretCodeInput = it },
                    onRequestPermissionsClick = { requestMissingRuntimePermissions() },
                    onOpenAppSettingsClick = { openAppSettings() },
                    onStartTrackingClick = { activateLostModeWithSecret(secretCodeInput) },
                    onStopTrackingClick = { stopLostPhoneTracking() },
                    onEnableAccessibilityClick = { openAccessibilitySettings() },
                    onToggleSensorControlClick = { toggleSensorControl() },
                    onToggleCallControlClick = { toggleNoTouchCallControl() },
                    onToggleAirGestureClick = { toggleAirGestureControl() },
                    onTapToTalkClick = { startManualListening() },
                    onAirGesturePreviewReady = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    override fun onPause() {
        speechRecognizer?.cancel()
        isListening = false
        super.onPause()
    }

    private fun setupMotionSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.getDefault()
                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isSpeaking = true
                        }

                        override fun onDone(utteranceId: String?) {
                            isSpeaking = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            isSpeaking = false
                        }
                    }
                )
                assistantReply = "Tap To Talk when you need Ammu"
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            assistantReply = "Speech recognition is not available on this device"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    assistantReply = "Listening..."
                    Log.d(TAG, "Speech recognizer ready")
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    isListening = false
                    Log.d(TAG, "Speech ended")
                }

                override fun onError(error: Int) {
                    isListening = false
                    acceptNextSpeechWithoutWake = false
                    Log.w(TAG, "Speech recognizer error: ${speechErrorName(error)}")
                    acceptNextSpeechWithoutWake = false
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val commands = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val command = chooseBestWakeSpeech(commands)

                    if (command.isBlank()) {
                        return
                    }

                    spokenCommand = command
                    Log.d(TAG, "Speech result: $command")
                    val handled = if (acceptNextSpeechWithoutWake) {
                        acceptNextSpeechWithoutWake = false
                        handleManualSpeech(command)
                    } else {
                        handleWakeSpeech(command)
                    }

                    if (!handled) {
                        assistantReply = "Tap To Talk when you need Ammu"
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun checkPermissionAndListen() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startWakeListening()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startWakeListening() {
        if (!wakeListeningEnabled || isListening || isSpeaking) {
            return
        }

        mainHandler.removeCallbacksAndMessages(WAKE_LISTEN_TOKEN)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say Hey Ammu")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        }

        runCatching {
            speechRecognizer?.startListening(intent)
        }.onFailure { error ->
            isListening = false
            Log.w(TAG, "Cannot start speech recognizer", error)
            wakeListeningEnabled = false
        }
    }

    private fun scheduleWakeListening(delayMs: Long = WAKE_LISTEN_RESTART_DELAY_MS) {
        if (!wakeListeningEnabled) {
            return
        }

        mainHandler.removeCallbacksAndMessages(WAKE_LISTEN_TOKEN)
        mainHandler.postDelayed(
            { startWakeListening() },
            WAKE_LISTEN_TOKEN,
            delayMs
        )
    }

    private fun startManualListening() {
        textToSpeech?.stop()
        isSpeaking = false
        acceptNextSpeechWithoutWake = true
        wakeListeningEnabled = true
        assistantReply = "Listening now"
        speechRecognizer?.cancel()
        isListening = false
        startWakeListening()
        wakeListeningEnabled = false
    }

    private fun handleWakeSpeech(speech: String): Boolean {
        val command = extractWakeCommand(speech)

        if (command == null) {
            assistantReply = "Waiting for Hey Ammu"
            return false
        }

        if (command.isBlank()) {
            assistantReply = "Yes, I am listening. Tell me a command."
            speak(assistantReply)
            return true
        }

        handleCommand(command)
        return true
    }

    private fun handleManualSpeech(speech: String): Boolean {
        val wakeCommand = extractWakeCommand(speech)

        if (wakeCommand != null) {
            if (wakeCommand.isBlank()) {
                assistantReply = "Yes, I am listening. Tell me a command."
                speak(assistantReply)
            } else {
                handleCommand(wakeCommand)
            }
            return true
        }

        handleCommand(speech)
        return true
    }

    private fun extractWakeCommand(speech: String): String? {
        val lower = speech.lowercase(Locale.getDefault())
        val wakePhrases = listOf(
            "hey ammu",
            "hi ammu",
            "hello ammu",
            "ammu",
            "amu",
            "ammo",
            "amoo",
            "amma",
            "ammoo"
        )
        val wakePhrase = wakePhrases.firstOrNull { phrase -> lower.contains(phrase) }
            ?: return null
        val wakeIndex = lower.indexOf(wakePhrase)
        return speech.substring(wakeIndex + wakePhrase.length).trim()
    }

    private fun chooseBestWakeSpeech(commands: List<String>): String {
        return commands.firstOrNull { extractWakeCommand(it) != null }
            ?: commands.firstOrNull()
            ?: ""
    }

    private fun speechErrorName(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "audio"
            SpeechRecognizer.ERROR_CLIENT -> "client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
            SpeechRecognizer.ERROR_NETWORK -> "network"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "no match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
            SpeechRecognizer.ERROR_SERVER -> "server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
            else -> "unknown $error"
        }
    }

    private fun restartDelayForSpeechError(error: Int): Long {
        return when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> WAKE_LISTEN_BUSY_RESTART_DELAY_MS
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH -> WAKE_LISTEN_NO_MATCH_RESTART_DELAY_MS
            else -> WAKE_LISTEN_RESTART_DELAY_MS
        }
    }

    private fun handleCommand(command: String) {
        val lower = command.lowercase(Locale.getDefault())

        when {
            lower.contains("open youtube") || lower == "youtube" -> {
                assistantReply = "Opening YouTube"
                speak(assistantReply)
                openPackage("com.google.android.youtube")
            }

            lower.contains("open whatsapp") || lower == "whatsapp" -> {
                assistantReply = "Opening WhatsApp"
                speak(assistantReply)
                openPackage("com.whatsapp")
            }

            lower.contains("accessibility") ||
                lower.contains("no touch") ||
                lower.contains("enable scroll") -> {
                assistantReply = "Opening Accessibility settings. Enable Ammu No Touch Control."
                speak(assistantReply)
                openAccessibilitySettings()
            }

            lower.contains("enable sensor") ||
                lower.contains("start sensor") ||
                lower.contains("motion control") -> {
                enableSensorControl()
            }

            lower.contains("disable sensor") || lower.contains("stop sensor") -> {
                disableSensorControl()
            }

            lower.contains("enable call gesture") ||
                lower.contains("start call gesture") ||
                lower.contains("no touch call") -> {
                checkCallPermissionAndEnableGestures()
            }

            lower.contains("disable call gesture") ||
                lower.contains("stop call gesture") -> {
                disableNoTouchCallControl()
            }

            lower.contains("start air gesture") ||
                lower.contains("enable air gesture") ||
                lower.contains("camera gesture") -> {
                enableAirGestureControl()
            }

            lower.contains("stop air gesture") ||
                lower.contains("disable air gesture") -> {
                disableAirGestureControl()
            }

            lower.contains("answer call") ||
                lower.contains("lift call") ||
                lower.contains("pick up call") -> {
                answerCallByVoice()
            }

            lower.contains("cut call") ||
                lower.contains("disconnect call") ||
                lower.contains("end call") -> {
                endCallByVoice()
            }

            lower.contains("open settings") || lower == "settings" -> {
                assistantReply = "Opening settings"
                speak(assistantReply)
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }

            lower.contains("hello") || lower.contains("hi") -> {
                assistantReply = "Hello, I am Ammu. How can I help?"
                speak(assistantReply)
            }

            lower.contains("time") -> {
                assistantReply = "Please check the time at the top of your screen."
                speak(assistantReply)
            }

            lower.contains("start location") ||
                lower.contains("start tracking") ||
                lower.contains("lost phone") -> {
                activateLostModeWithSecret(command)
            }

            lower.contains("stop location") || lower.contains("stop tracking") -> {
                assistantReply = "Stopping lost phone tracking"
                speak(assistantReply)
                stopLostPhoneTracking()
            }

            lower.contains("scroll down") ||
                lower.contains("scroll forward") ||
                lower.contains("page down") -> {
                assistantReply = "Scrolling down"
                speak(assistantReply)
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_DOWN)
            }

            lower.contains("scroll up") ||
                lower.contains("scroll back") ||
                lower.contains("page up") -> {
                assistantReply = "Scrolling up"
                speak(assistantReply)
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_UP)
            }

            lower == "home" || lower.contains("go home") -> {
                assistantReply = "Going home"
                speak(assistantReply)
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_HOME)
            }

            lower.contains("go back") || lower == "back" -> {
                assistantReply = "Going back"
                speak(assistantReply)
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_BACK)
            }

            lower.contains("recent apps") || lower.contains("recents") -> {
                assistantReply = "Opening recent apps"
                speak(assistantReply)
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_RECENTS)
            }

            else -> {
                assistantReply = "You said: $command"
                speak(assistantReply)
            }
        }
    }

    private fun sendAccessibilityCommand(command: String) {
        if (AmmuAccessibilityService.performIfConnected(command)) {
            return
        }

        val intent = Intent(AmmuAccessibilityService.ACTION_ACCESSIBILITY_COMMAND)
            .setPackage(packageName)
            .putExtra(AmmuAccessibilityService.EXTRA_COMMAND, command)

        sendBroadcast(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )

        startActivity(intent)
    }

    private fun requestMissingRuntimePermissions() {
        val permissions = buildList {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (!hasPermission(Manifest.permission.CAMERA)) {
                add(Manifest.permission.CAMERA)
            }
            if (!hasLocationPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !CallController.hasCallPermission(this@MainActivity)) {
                add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
            if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isEmpty()) {
            assistantReply = "All runtime permissions are allowed"
            speak(assistantReply)
            refreshPermissionStatus()
        } else {
            requestDashboardPermissions.launch(permissions.toTypedArray())
        }
    }

    private fun refreshPermissionStatus() {
        permissionStatus = PermissionStatus(
            microphone = hasPermission(Manifest.permission.RECORD_AUDIO),
            camera = hasPermission(Manifest.permission.CAMERA),
            location = hasLocationPermission(),
            phone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CallController.hasCallPermission(this)
            } else {
                false
            },
            notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
            accessibility = isAmmuAccessibilityEnabled()
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAmmuAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        val expectedService = "$packageName/$packageName.AmmuAccessibilityService"
        val expectedShortService = "$packageName/.AmmuAccessibilityService"

        return enabledServices.contains(expectedService, ignoreCase = true) ||
            enabledServices.contains(expectedShortService, ignoreCase = true)
    }

    private fun toggleSensorControl() {
        if (sensorControlEnabled) {
            disableSensorControl()
        } else {
            enableSensorControl()
        }
    }

    private fun toggleNoTouchCallControl() {
        if (noTouchCallEnabled) {
            disableNoTouchCallControl()
        } else {
            checkCallPermissionAndEnableGestures()
        }
    }

    private fun checkCallPermissionAndEnableGestures() {
        if (CallController.hasCallPermission(this)) {
            enableNoTouchCallControl()
            return
        }

        val permissions = buildList {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            add(Manifest.permission.READ_PHONE_STATE)
        }

        requestCallPermissions.launch(permissions.toTypedArray())
    }

    private fun enableNoTouchCallControl() {
        val sensor = proximitySensor

        if (sensor == null) {
            assistantReply = "Proximity sensor is not available"
            speak(assistantReply)
            return
        }

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        noTouchCallEnabled = true
        assistantReply = "No touch call control enabled"
        speak("No touch call control enabled. Wave once near the top of the phone to answer. Wave twice quickly to disconnect.")
    }

    private fun disableNoTouchCallControl() {
        noTouchCallEnabled = false
        proximityWasNear = false
        sensorManager.unregisterListener(this, proximitySensor)
        assistantReply = "No touch call control disabled"
        speak(assistantReply)
    }

    private fun toggleAirGestureControl() {
        if (airGestureEnabled) {
            disableAirGestureControl()
        } else {
            enableAirGestureControl()
        }
    }

    private fun enableAirGestureControl() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startAirGestureService()
            speak(assistantReply)
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun disableAirGestureControl() {
        airGestureController?.stop()
        airGestureController = null
        stopService(Intent(this, AirGestureForegroundService::class.java))
        airGestureEnabled = false
        lastAirGestureLabel = "Camera gestures are off"
        assistantReply = "Air gesture control disabled"
        speak(assistantReply)
    }

    private fun startAirGestureService() {
        val intent = Intent(this, AirGestureForegroundService::class.java)
        startForegroundService(this, intent)
        airGestureEnabled = true
        lastAirGestureLabel = "Air gestures running in background"
        assistantReply = "Air gesture control enabled"
    }

    private fun startAirGestureCamera(previewView: PreviewView) {
        if (!airGestureEnabled || airGestureController != null) {
            return
        }

        airGestureController = AirGestureController(
            context = this,
            lifecycleOwner = this as LifecycleOwner,
            previewView = previewView,
            onGesture = ::handleAirGesture,
            onStatus = { status ->
                lastAirGestureLabel = status
            }
        ).also { controller ->
            controller.start()
        }
    }

    private fun handleAirGesture(gesture: AirGesture) {
        lastAirGestureLabel = gesture.label

        when (gesture) {
            AirGesture.SwipeUp -> {
                assistantReply = "Air gesture scroll down"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_DOWN)
            }

            AirGesture.SwipeDown -> {
                assistantReply = "Air gesture scroll up"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_UP)
            }

            AirGesture.SwipeLeft -> {
                assistantReply = "Air gesture back"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_BACK)
            }

            AirGesture.SwipeRight -> {
                assistantReply = "Air gesture recent apps"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_RECENTS)
            }

            AirGesture.OpenPalm -> {
                assistantReply = "Air gesture play pause"
                dispatchMediaPlayPause()
            }

            AirGesture.ClosedFist -> {
                assistantReply = "Air gesture home"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_HOME)
            }
        }
    }

    private fun enableSensorControl() {
        val sensor = accelerometer

        if (sensor == null) {
            assistantReply = "Accelerometer sensor is not available"
            speak(assistantReply)
            return
        }

        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorControlEnabled = true
        assistantReply = "Motion sensor control enabled"
        speak("Motion sensor control enabled. Tilt to scroll, tilt left to go back, tilt right for home, shake for recent apps.")
    }

    private fun disableSensorControl() {
        sensorManager.unregisterListener(this)
        sensorControlEnabled = false
        assistantReply = "Motion sensor control disabled"
        speak(assistantReply)
    }

    private fun activateLostModeWithSecret(commandOrCode: String) {
        if (!isSecretCodeValid(commandOrCode)) {
            assistantReply = "Secret code is incorrect"
            speak(assistantReply)
            return
        }

        assistantReply = "Secret code accepted. Starting lost phone tracking"
        speak(assistantReply)
        checkLocationPermissionAndStartTracking()
    }

    private fun isSecretCodeValid(commandOrCode: String): Boolean {
        val secretCode = BuildConfig.LOST_MODE_SECRET_CODE.trim()

        if (secretCode.isBlank()) {
            return false
        }

        return commandOrCode
            .lowercase(Locale.getDefault())
            .contains(secretCode.lowercase(Locale.getDefault()))
    }

    private fun checkLocationPermissionAndStartTracking() {
        if (hasLocationPermission()) {
            startLostPhoneTracking()
            return
        }

        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        requestLostPhonePermissions.launch(permissions.toTypedArray())
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    private fun startLostPhoneTracking() {
        if (!SmtpEmailSender.isConfigured()) {
            assistantReply = "Add lost phone email settings in local.properties first"
            Toast.makeText(this, assistantReply, Toast.LENGTH_LONG).show()
            speak(assistantReply)
            return
        }

        val intent = Intent(this, LostPhoneTrackingService::class.java)
        startForegroundService(this, intent)
        lostPhoneTrackingEnabled = true
        assistantReply = "Lost phone tracking started"
        speak(assistantReply)
    }

    private fun stopLostPhoneTracking() {
        stopService(Intent(this, LostPhoneTrackingService::class.java))
        lostPhoneTrackingEnabled = false
        assistantReply = "Lost phone tracking stopped"
        speak(assistantReply)
    }

    private fun openPackage(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent == null) {
            Toast.makeText(this, "App is not installed", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(launchIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(message: String) {
        mainHandler.removeCallbacksAndMessages(WAKE_LISTEN_TOKEN)
        speechRecognizer?.cancel()
        isListening = false
        isSpeaking = true

        val result = textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ammu_reply")

        if (result != TextToSpeech.SUCCESS) {
            isSpeaking = false
        }
    }

    private fun dispatchMediaPlayPause() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        audioManager.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }

    private fun answerCallByVoice() {
        if (CallController.answerCall(this)) {
            assistantReply = "Answering call"
        } else {
            assistantReply = "Call permission is needed, or there is no ringing call"
            checkCallPermissionAndEnableGestures()
        }

        speak(assistantReply)
    }

    private fun endCallByVoice() {
        if (CallController.endCall(this)) {
            assistantReply = "Disconnecting call"
        } else {
            assistantReply = "Call permission is needed, or no active call can be disconnected"
            checkCallPermissionAndEnableGestures()
        }

        speak(assistantReply)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            handleProximityGesture(event)
            return
        }

        if (!sensorControlEnabled || event.sensor.type != Sensor.TYPE_ACCELEROMETER) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()

        handleShake(x, y, z, now)

        if (now - lastSensorActionTime < SENSOR_ACTION_COOLDOWN_MS) {
            return
        }

        when {
            y < -6.5f -> {
                lastSensorActionTime = now
                assistantReply = "Sensor scroll down"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_DOWN)
            }

            y > 6.5f -> {
                lastSensorActionTime = now
                assistantReply = "Sensor scroll up"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_UP)
            }

            x > 7.5f -> {
                lastSensorActionTime = now
                assistantReply = "Sensor back"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_BACK)
            }

            x < -7.5f -> {
                lastSensorActionTime = now
                assistantReply = "Sensor home"
                sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_HOME)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleProximityGesture(event: SensorEvent) {
        if (!noTouchCallEnabled) {
            return
        }

        val distance = event.values.firstOrNull() ?: return
        val maxRange = event.sensor.maximumRange
        val isNear = distance < maxRange

        if (isNear == proximityWasNear) {
            return
        }

        proximityWasNear = isNear

        if (!isNear) {
            return
        }

        val now = System.currentTimeMillis()
        val doubleWave = now - lastWaveTime < DOUBLE_WAVE_WINDOW_MS
        lastWaveTime = now

        if (doubleWave) {
            endCallByGesture()
        } else {
            answerCallByGesture()
        }
    }

    private fun answerCallByGesture() {
        if (CallController.answerCall(this)) {
            assistantReply = "Gesture answered call"
        } else {
            assistantReply = "Wave detected"
        }
    }

    private fun endCallByGesture() {
        assistantReply = if (CallController.endCall(this)) {
            "Gesture disconnected call"
        } else {
            "Double wave detected"
        }
    }

    private fun handleShake(x: Float, y: Float, z: Float, now: Long) {
        if (now - lastShakeTime < SHAKE_COOLDOWN_MS) {
            return
        }

        val force = sqrt(x * x + y * y + z * z)

        if (abs(force - SensorManager.GRAVITY_EARTH) > SHAKE_THRESHOLD) {
            lastShakeTime = now
            lastSensorActionTime = now
            assistantReply = "Sensor recent apps"
            sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_RECENTS)
        }
    }

    override fun onDestroy() {
        wakeListeningEnabled = false
        mainHandler.removeCallbacksAndMessages(null)
        airGestureController?.stop()
        sensorManager.unregisterListener(this)
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    companion object {
        private const val SENSOR_ACTION_COOLDOWN_MS = 1_200L
        private const val SHAKE_COOLDOWN_MS = 2_000L
        private const val SHAKE_THRESHOLD = 12f
        private const val WAKE_LISTEN_RESTART_DELAY_MS = 1_500L
        private const val WAKE_LISTEN_NO_MATCH_RESTART_DELAY_MS = 4_000L
        private const val WAKE_LISTEN_BUSY_RESTART_DELAY_MS = 2_500L
        private const val WAKE_LISTEN_AFTER_SPEAK_DELAY_MS = 1_000L
        private const val WAKE_LISTEN_RESUME_DELAY_MS = 350L
        private const val DOUBLE_WAVE_WINDOW_MS = 1_200L
        private const val TAG = "AmmuVoice"
        private val WAKE_LISTEN_TOKEN = Any()
    }
}

@Composable
fun VoiceAssistantScreen(
    spokenCommand: String,
    assistantReply: String,
    isListening: Boolean,
    wakeListeningEnabled: Boolean,
    lostPhoneTrackingEnabled: Boolean,
    sensorControlEnabled: Boolean,
    noTouchCallEnabled: Boolean,
    airGestureEnabled: Boolean,
    lastAirGestureLabel: String,
    permissionStatus: PermissionStatus,
    secretCodeInput: String,
    onSecretCodeChange: (String) -> Unit,
    onRequestPermissionsClick: () -> Unit,
    onOpenAppSettingsClick: () -> Unit,
    onStartTrackingClick: () -> Unit,
    onStopTrackingClick: () -> Unit,
    onEnableAccessibilityClick: () -> Unit,
    onToggleSensorControlClick: () -> Unit,
    onToggleCallControlClick: () -> Unit,
    onToggleAirGestureClick: () -> Unit,
    onTapToTalkClick: () -> Unit,
    onAirGesturePreviewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(132.dp)
                        .background(Color.Transparent, CircleShape),
                    shape = CircleShape,
                    color = if (isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isListening) "Listening" else "Ammu",
                            color = if (isListening) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }

                Text(
                    text = assistantReply,
                    modifier = Modifier.padding(top = 32.dp),
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = spokenCommand,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = if (wakeListeningEnabled && isListening) {
                        "Listening now"
                    } else {
                        "Microphone is off. Tap To Talk when needed."
                    },
                    modifier = Modifier.padding(top = 28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(
                    onClick = onTapToTalkClick,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Tap To Talk")
                }

                PermissionDashboard(
                    permissionStatus = permissionStatus,
                    onRequestPermissionsClick = onRequestPermissionsClick,
                    onOpenAccessibilityClick = onEnableAccessibilityClick,
                    onOpenAppSettingsClick = onOpenAppSettingsClick
                )

                Button(
                    onClick = onEnableAccessibilityClick,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Enable No Touch")
                }

                Button(
                    onClick = onToggleSensorControlClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (sensorControlEnabled) "Stop Sensor Control" else "Start Sensor Control")
                }

                Button(
                    onClick = onToggleCallControlClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (noTouchCallEnabled) "Stop Call Gestures" else "Start Call Gestures")
                }

                Button(
                    onClick = onToggleAirGestureClick,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(if (airGestureEnabled) "Stop Air Gestures" else "Start Air Gestures")
                }

                if (airGestureEnabled) {
                    Text(
                        text = lastAirGestureLabel,
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = secretCodeInput,
                    onValueChange = onSecretCodeChange,
                    modifier = Modifier.padding(top = 20.dp),
                    label = { Text("Lost Mode Secret Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Button(
                    onClick = onStartTrackingClick,
                    modifier = Modifier.padding(top = 12.dp),
                    enabled = !lostPhoneTrackingEnabled
                ) {
                    Text("Start Location Email")
                }

                Button(
                    onClick = onStopTrackingClick,
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = lostPhoneTrackingEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Location Email")
                }
            }
        }
    }
}

@Composable
fun PermissionDashboard(
    permissionStatus: PermissionStatus,
    onRequestPermissionsClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onOpenAppSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(top = 20.dp)
            .width(300.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionLine("Microphone", permissionStatus.microphone)
            PermissionLine("Camera", permissionStatus.camera)
            PermissionLine("Location", permissionStatus.location)
            PermissionLine("Phone", permissionStatus.phone)
            PermissionLine("Notifications", permissionStatus.notifications)
            PermissionLine("Accessibility", permissionStatus.accessibility)

            Text(
                text = "Ready: ${permissionStatus.readyFeatureSummary()}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onRequestPermissionsClick,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text("Allow Missing")
            }

            Button(
                onClick = onOpenAccessibilityClick,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("Open Accessibility")
            }

            Button(
                onClick = onOpenAppSettingsClick,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("Open App Settings")
            }
        }
    }
}

@Composable
fun PermissionLine(label: String, allowed: Boolean) {
    Text(
        text = "$label: ${if (allowed) "Allowed" else "Not allowed"}",
        modifier = Modifier.padding(top = 6.dp),
        color = if (allowed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

data class PermissionStatus(
    val microphone: Boolean = false,
    val camera: Boolean = false,
    val location: Boolean = false,
    val phone: Boolean = false,
    val notifications: Boolean = false,
    val accessibility: Boolean = false
) {
    fun readyFeatureSummary(): String {
        val readyFeatures = buildList {
            if (microphone) add("Voice")
            if (camera && accessibility) add("Air gestures")
            if (accessibility) add("No touch scroll")
            if (location && notifications) add("Lost phone")
            if (phone) add("Call gestures")
        }

        return if (readyFeatures.isEmpty()) {
            "none yet"
        } else {
            readyFeatures.joinToString()
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun VoiceAssistantPreview() {
    ProjectAmmuTheme {
        VoiceAssistantScreen(
            spokenCommand = "Open YouTube",
            assistantReply = "Opening YouTube",
            isListening = false,
            wakeListeningEnabled = false,
            lostPhoneTrackingEnabled = false,
            sensorControlEnabled = false,
            noTouchCallEnabled = false,
            airGestureEnabled = false,
            lastAirGestureLabel = "Camera gestures are off",
            permissionStatus = PermissionStatus(),
            secretCodeInput = "",
            onSecretCodeChange = {},
            onRequestPermissionsClick = {},
            onOpenAppSettingsClick = {},
            onStartTrackingClick = {},
            onStopTrackingClick = {},
            onEnableAccessibilityClick = {},
            onToggleSensorControlClick = {},
            onToggleCallControlClick = {},
            onToggleAirGestureClick = {},
            onTapToTalkClick = {},
            onAirGesturePreviewReady = {}
        )
    }
}
