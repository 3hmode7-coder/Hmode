package com.rk.terminal.service

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class InstallationState {
    object Idle : InstallationState()
    data class Downloading(val progress: Float, val statusText: String) : InstallationState()
    data class Installing(val phase: String, val latestLogs: String) : InstallationState()
    object ServerStarting : InstallationState()
    object Completed : InstallationState()
    data class Failed(val error: String) : InstallationState()
}

/**
 * Background observer that monitors the completion of the OpenCode installation
 * and server startup in the Alpine proot environment, and triggers the transition
 * to the web-based chat interface.
 */
object OpenCodeInstallationObserver {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null

    private val _state = MutableStateFlow<InstallationState>(InstallationState.Idle)
    val state = _state.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted = _isCompleted.asStateFlow()

    // SharedFlow to trigger one-shot navigation events to the web chat interface
    private val _transitionEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val transitionEvent = _transitionEvent.asSharedFlow()

    // Live state readable by Compose
    val statusText = mutableStateOf("جاري إعداد بيئة لينكس...")
    val latestTranscript = mutableStateOf("")
    val installProgress = androidx.compose.runtime.mutableFloatStateOf(0.15f)
    val currentStep = androidx.compose.runtime.mutableIntStateOf(1)

    /**
     * Start background monitoring. Checks both the local HTTP server port (4123)
     * and the marker file in the Alpine rootfs.
     */
    fun startMonitoring(context: Context) {
        if (_isCompleted.value) {
            installProgress.value = 1f
            currentStep.value = 4
            _transitionEvent.tryEmit(Unit)
            return
        }

        if (monitorJob?.isActive == true) {
            return
        }

        val markerFile = File(context.filesDir.parentFile, "local/alpine/tmp/opencode_ready")

        monitorJob = scope.launch {
            _state.value = InstallationState.Installing("Starting setup", "")

            while (isActive) {
                // 1. Check HTTP server responsiveness
                val isPortReady = runCatching {
                    (URL("http://127.0.0.1:4123").openConnection() as HttpURLConnection).run {
                        connectTimeout = 700
                        readTimeout = 700
                        requestMethod = "GET"
                        connect()
                        responseCode in 200..499
                    }
                }.getOrDefault(false)

                // 2. Check marker file or HTTP port
                if (isPortReady || markerFile.exists()) {
                    _state.value = InstallationState.Completed
                    _isCompleted.value = true
                    installProgress.value = 1f
                    currentStep.value = 4
                    statusText.value = "تم اكتمال التثبيت، جاري فتح واجهة الدردشة..."
                    _transitionEvent.tryEmit(Unit)
                    break
                }

                delay(900)
            }
        }
    }

    /**
     * Stop the background monitor job if active.
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Called by HeadlessTerminalClient whenever new text is output from the terminal session.
     */
    fun onTerminalOutput(transcript: String) {
        latestTranscript.value = transcript

        if (_isCompleted.value) {
            installProgress.value = 1f
            return
        }

        when {
            transcript.contains("Starting OpenCode") -> {
                installProgress.value = 0.95f
                currentStep.value = 4
                statusText.value = "جاري بدء تشغيل خادم واجهة الدردشة..."
                _state.value = InstallationState.ServerStarting
            }
            transcript.contains("OpenCode installed successfully") || transcript.contains("[+] OpenCode installed") -> {
                installProgress.value = 0.90f
                currentStep.value = 4
                statusText.value = "تم تثبيت OpenCode بنجاح، جاري تشغيل الخادم..."
                _state.value = InstallationState.ServerStarting
            }
            transcript.contains("@opencode/cli") || transcript.contains("npm install -g") -> {
                installProgress.value = 0.78f
                currentStep.value = 3
                statusText.value = "جاري تنزيل وتثبيت حزمة OpenCode CLI..."
                _state.value = InstallationState.Installing("Installing OpenCode CLI", transcript)
            }
            transcript.contains("Installing OpenCode") || transcript.contains("nodejs npm") -> {
                installProgress.value = 0.60f
                currentStep.value = 3
                statusText.value = "جاري تثبيت حزم Node.js و npm..."
                _state.value = InstallationState.Installing("Installing Node.js & npm", transcript)
            }
            transcript.contains("Packages installed") || transcript.contains("apk add") -> {
                installProgress.value = 0.45f
                currentStep.value = 2
                statusText.value = "جاري إعداد حزم النظام الأساسية..."
                _state.value = InstallationState.Installing("Configuring base system", transcript)
            }
            transcript.contains("Installing packages") -> {
                installProgress.value = 0.30f
                currentStep.value = 2
                statusText.value = "جاري فحص وتحديث مستودعات النظام..."
                _state.value = InstallationState.Installing("Updating repositories", transcript)
            }
            transcript.contains("OpenCode installation failed") -> {
                statusText.value = "تعذر تثبيت OpenCode. يرجى التحقق من اتصال الإنترنت."
                _state.value = InstallationState.Failed("Installation script failed")
            }
            else -> {
                if (installProgress.value < 0.25f) {
                    installProgress.value = 0.20f
                }
                currentStep.value = 1
                _state.value = InstallationState.Installing("Configuring Alpine Linux", transcript)
            }
        }
    }

    /**
     * Force mark completed and trigger the transition.
     */
    fun markCompleted() {
        _state.value = InstallationState.Completed
        _isCompleted.value = true
        _transitionEvent.tryEmit(Unit)
    }

    /**
     * Reset state for retries.
     */
    fun reset() {
        stopMonitoring()
        _isCompleted.value = false
        _state.value = InstallationState.Idle
        installProgress.value = 0.15f
        currentStep.value = 1
        statusText.value = "جاري إعداد بيئة لينكس..."
        latestTranscript.value = ""
    }
}
