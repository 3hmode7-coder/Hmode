package com.rk.terminal.ui.screens.opencode

import androidx.compose.runtime.mutableStateOf
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

object HeadlessTerminalState {
    var latestLog = mutableStateOf("")
    var isInstalling = mutableStateOf(false)
    var isServerReady = mutableStateOf(false)
    var installFailed = mutableStateOf(false)
    var statusMessage = mutableStateOf("جاري إعداد بيئة لينكس...")
}

/** Starts the Alpine session without attaching a visible TerminalView. */
class HeadlessTerminalClient(private val activity: MainActivity) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        val transcript = changedSession.emulator?.screen?.transcriptText ?: return
        HeadlessTerminalState.latestLog.value = transcript
        com.rk.terminal.service.OpenCodeInstallationObserver.onTerminalOutput(transcript)
        if (transcript.contains("Installing OpenCode")) {
            HeadlessTerminalState.statusMessage.value = "جاري تثبيت حزم Node.js و OpenCode..."
        } else if (transcript.contains("Starting OpenCode")) {
            HeadlessTerminalState.statusMessage.value = "جاري بدء تشغيل خادم واجهة الدردشة..."
        } else if (transcript.contains("OpenCode installed successfully")) {
            HeadlessTerminalState.statusMessage.value = "تم تثبيت OpenCode بنجاح، جاري تشغيل الخادم..."
        } else if (transcript.contains("OpenCode installation failed")) {
            HeadlessTerminalState.installFailed.value = true
            HeadlessTerminalState.statusMessage.value = "تعذر تثبيت OpenCode. تحقق من اتصال الإنترنت."
        }
    }
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) {
        HeadlessTerminalState.isInstalling.value = false
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int = 0
    override fun logError(tag: String, message: String) = Unit
    override fun logWarn(tag: String, message: String) = Unit
    override fun logInfo(tag: String, message: String) = Unit
    override fun logDebug(tag: String, message: String) = Unit
    override fun logVerbose(tag: String, message: String) = Unit
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
    override fun logStackTrace(tag: String, e: Exception) = Unit
}

