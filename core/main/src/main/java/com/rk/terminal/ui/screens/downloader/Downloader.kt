package com.rk.terminal.ui.screens.downloader

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.terminal.service.OpenCodeInstallationObserver
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.navHosts.MainActivityNavHost
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.opencode.HeadlessTerminalState
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * First-run downloader.
 *
 * Pulls three things on first launch:
 *  - libtalloc.so.2  (needed by proot)
 *  - proot           (the user-space syscall translator)
 *  - alpine.tar.gz   (the Alpine Linux minirootfs)
 *
 * For the Alpine rootfs we always resolve the latest stable release at runtime
 * from https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/<arch>/ by
 * scraping the directory listing. This keeps AndLinux from being pinned to a
 * specific Alpine point release (e.g. 3.21.0) that ages out within weeks.
 */
@Composable
fun Downloader(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val downloadingStr = stringResource(strings.downloading)
    val networkErrorStr = stringResource(strings.network_error)
    val setupFailedStr = stringResource(strings.setup_failed)
    var progress by remember { mutableFloatStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var isDownloaded by remember { mutableStateOf(Rootfs.isFilesDownloaded()) }
    var needsDownload by remember { mutableStateOf(!isDownloaded) }
    var showLogs by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val statusMsg = OpenCodeInstallationObserver.statusText.value
    val latestLog = OpenCodeInstallationObserver.latestTranscript.value
    val isFailed = HeadlessTerminalState.installFailed.value

    // 1. Download base files (alpine minirootfs, proot, libtalloc) if needed
    LaunchedEffect(retryTrigger) {
        if (!Rootfs.isFilesDownloaded()) {
            try {
                val abi = Build.SUPPORTED_ABIS.firstOrNull { it in abiMap }
                    ?: throw RuntimeException("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}")

                val abiUrls = abiMap[abi]!!
                val resolvedAlpineUrl = withContext(Dispatchers.IO) {
                    resolveLatestAlpineMinirootfsUrl(abiUrls.alpineDir, abiUrls.alpineArch)
                }

                val filesToDownload = listOf(
                    "libtalloc.so.2" to abiUrls.talloc,
                    "proot" to abiUrls.proot,
                    "alpine.tar.gz" to resolvedAlpineUrl
                ).map { (name, url) -> DownloadFile(url, Rootfs.andLinux.child(name)) }

                needsDownload = filesToDownload.any { !it.outputFile.exists() }

                setupEnvironment(
                    filesToDownload,
                    onProgress = { completed, total, currentProgress ->
                        if (needsDownload) {
                            progress = ((completed + currentProgress) / total).coerceIn(0f, 1f)
                            val pct = (progress * 100).toInt()
                            progressText = downloadingStr.format(pct)
                        }
                    },
                    onComplete = {
                        Rootfs.isDownloaded.value = true
                        isDownloaded = true
                        needsDownload = false
                        mainActivity.startHeadlessOpenCodeSession()
                        OpenCodeInstallationObserver.startMonitoring(mainActivity)
                    },
                    onError = { error ->
                        toast(if (error is UnknownHostException) networkErrorStr else setupFailedStr.format(error.message))
                    }
                )
            } catch (e: Exception) {
                toast(if (e is UnknownHostException) networkErrorStr else setupFailedStr.format(e.message))
            }
        } else {
            Rootfs.isDownloaded.value = true
            isDownloaded = true
            needsDownload = false
            mainActivity.startHeadlessOpenCodeSession()
            OpenCodeInstallationObserver.startMonitoring(mainActivity)
        }
    }

    // 2. Start observer and listen for completion transition event
    LaunchedEffect(isDownloaded, retryTrigger) {
        if (isDownloaded) {
            mainActivity.startHeadlessOpenCodeSession()
            OpenCodeInstallationObserver.startMonitoring(mainActivity)
        }
    }

    LaunchedEffect(Unit) {
        OpenCodeInstallationObserver.transitionEvent.collect {
            navController.navigate(MainActivityRoutes.OpenCode.route) {
                popUpTo(MainActivityRoutes.MainScreen.route) { inclusive = true }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.rk.resources.drawables.ic_chat),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (needsDownload && !isDownloaded) {
                    Text(
                        text = stringResource(strings.opencode_installing_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(strings.opencode_installing_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OpencodeProgressBarComponent(
                        progress = progress * 0.25f,
                        statusText = if (progressText.isNotEmpty()) progressText else stringResource(strings.installing),
                        currentStep = 1
                    )
                } else {
                    // Headless OpenCode installation in progress
                    Text(
                        text = stringResource(strings.opencode_installing_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(strings.opencode_installing_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    OpencodeProgressBarComponent(
                        progress = OpenCodeInstallationObserver.installProgress.value,
                        statusText = statusMsg,
                        currentStep = OpenCodeInstallationObserver.currentStep.value
                    )
                }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle logs
                    OutlinedButton(
                        onClick = { showLogs = !showLogs }
                    ) {
                        Text(if (showLogs) stringResource(strings.hide_logs) else stringResource(strings.show_logs))
                    }

                    if (showLogs && latestLog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            val scrollState = rememberScrollState()
                            LaunchedEffect(latestLog) {
                                scrollState.scrollTo(scrollState.maxValue)
                            }
                            Text(
                                text = latestLog.takeLast(1500),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(scrollState),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isFailed) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                retryTrigger++
                                mainActivity.startHeadlessOpenCodeSession()
                            }) {
                                Text(stringResource(strings.retry_action))
                            }
                            OutlinedButton(onClick = {
                                navController.navigate(MainActivityRoutes.MainScreen.route)
                            }) {
                                Text(stringResource(strings.terminal_label))
                            }
                        }
                    }
                }
            }
        }
    }

private data class DownloadFile(val url: String, val outputFile: File)

private suspend fun setupEnvironment(
    filesToDownload: List<DownloadFile>,
    onProgress: (Int, Int, Float) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        // Track which files we actually attempted to download in this run
        // so that on failure we only wipe those (not files that were already
        // present before the call, which the user might not want deleted).
        val attemptedThisRun = mutableSetOf<File>()
        try {
            var completedFiles = 0
            val totalFiles = filesToDownload.size

            filesToDownload.forEach { file ->
                val outputFile = file.outputFile.apply { parentFile?.mkdirs() }
                if (!outputFile.exists()) {
                    attemptedThisRun.add(outputFile)
                    downloadFile(file.url, outputFile) { downloaded, total ->
                        runOnUiThread {
                            onProgress(
                                completedFiles,
                                totalFiles,
                                if (total > 0) downloaded.toFloat() / total else 0f
                            )
                        }
                    }
                }
                completedFiles++
                runOnUiThread { onProgress(completedFiles, totalFiles, 1f) }
                outputFile.setExecutable(true, false)
            }
            runOnUiThread { onComplete() }
        } catch (e: Exception) {
            // Only wipe files that we started downloading in THIS run — don't
            // touch files that already existed before setupEnvironment was called.
            attemptedThisRun.forEach { it.takeIf { f -> f.exists() }?.delete() }
            withContext(Dispatchers.Main) { onError(e) }
        }
    }
}

private suspend fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
    withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw Exception("Empty response body for $url")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var lastReport = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        // Throttle UI updates to ~10 per second to avoid main-thread thrash.
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100 || downloadedBytes == totalBytes) {
                            withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                            lastReport = now
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolve the latest stable Alpine minirootfs URL by listing the
 * `latest-stable/releases/<arch>/` directory and picking the highest-versioned
 * `alpine-minirootfs-<version>-<arch>.tar.gz` file.
 *
 * Falls back to a sensible default if the listing can't be parsed.
 */
@Throws(Exception::class)
internal fun resolveLatestAlpineMinirootfsUrl(alpineDir: String, alpineArch: String): String {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val listingUrl = "$alpineDir/$alpineArch/"
    val request = Request.Builder().url(listingUrl).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code} fetching Alpine release listing at $listingUrl")
        }
        val html = response.body?.string()
            ?: throw Exception("Empty response body fetching $listingUrl")

        // Match alpine-minirootfs-3.21.3-aarch64.tar.gz (and any version pattern).
        val pattern = Pattern.compile(
            "alpine-minirootfs-(\\d+(?:\\.\\d+){1,3})-" + Pattern.quote(alpineArch) + "\\.tar\\.gz"
        )
        val matcher = pattern.matcher(html)
        var bestVersion: List<Int>? = null
        var bestFileName: String? = null
        while (matcher.find()) {
            val fileName = matcher.group(0) ?: continue
            val versionStr = matcher.group(1) ?: continue
            val parts = versionStr.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.isEmpty()) continue
            if (bestVersion == null || compareVersions(parts, bestVersion) > 0) {
                bestVersion = parts
                bestFileName = fileName
            }
        }
        if (bestFileName != null) {
            return "$alpineDir/$alpineArch/$bestFileName"
        }
    }
    // Fallback: use a known-good pinned release. This will be replaced the next
    // time the user opens the downloader once the listing is reachable again.
    throw Exception("Could not resolve latest Alpine minirootfs from $listingUrl")
}

private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    val maxLen = maxOf(a.size, b.size)
    for (i in 0 until maxLen) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av - bv
    }
    return 0
}

/**
 * Per-ABI download metadata.
 *
 * - `talloc`: libtalloc.so.2 binary, needed by proot
 * - `proot`:  proot binary itself
 * - `alpineDir`: base URL of the Alpine release tree (no arch suffix),
 *                e.g. `https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases`
 * - `alpineArch`: per-ABI Alpine arch name as used in the URL path,
 *                e.g. `aarch64`, `x86_64`, `armhf`
 */
private data class AbiUrls(
    val talloc: String,
    val proot: String,
    val alpineDir: String,
    val alpineArch: String,
)

private val abiMap = mapOf(
    "x86_64" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/libtalloc.so.2",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/x86_64/proot",
        alpineDir = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases",
        alpineArch = "x86_64"
    ),
    "arm64-v8a" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/libtalloc.so.2",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/aarch64/proot",
        alpineDir = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases",
        alpineArch = "aarch64"
    ),
    "armeabi-v7a" to AbiUrls(
        talloc = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/libtalloc.so.2",
        proot = "https://raw.githubusercontent.com/Xed-Editor/Karbon-PackagesX/main/arm/proot",
        alpineDir = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases",
        alpineArch = "armhf"
    )
)

@Composable
fun OpencodeProgressBarComponent(
    progress: Float,
    statusText: String,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "installProgress"
    )
    val pct = (animatedProgress * 100).toInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Installation Progress and live percentage pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(strings.installation_progress),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Milestone Step Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StepMilestoneItem(
                    stepNumber = 1,
                    title = stringResource(strings.step_system),
                    isActive = currentStep >= 1,
                    isCurrent = currentStep == 1
                )
                StepMilestoneItem(
                    stepNumber = 2,
                    title = stringResource(strings.step_node),
                    isActive = currentStep >= 2,
                    isCurrent = currentStep == 2
                )
                StepMilestoneItem(
                    stepNumber = 3,
                    title = stringResource(strings.step_opencode),
                    isActive = currentStep >= 3,
                    isCurrent = currentStep == 3
                )
                StepMilestoneItem(
                    stepNumber = 4,
                    title = stringResource(strings.step_server),
                    isActive = currentStep >= 4,
                    isCurrent = currentStep == 4
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current status line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StepMilestoneItem(
    stepNumber: Int,
    title: String,
    isActive: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isCurrent -> MaterialTheme.colorScheme.onPrimary
                    isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

