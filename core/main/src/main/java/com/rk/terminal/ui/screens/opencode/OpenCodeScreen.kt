package com.rk.terminal.ui.screens.opencode

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.util.Base64
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.terminal.service.DeveloperManager
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.components.DevToolsSheet
import com.rk.terminal.ui.routes.MainActivityRoutes

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenCodeScreen(
    navController: NavHostController,
    mainActivity: MainActivity
) {
    val context = LocalContext.current
    val isObservedCompleted by com.rk.terminal.service.OpenCodeInstallationObserver.isCompleted.collectAsState()
    var serverReady by remember { mutableStateOf<Boolean>(isObservedCompleted) }
    var pageLoaded by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    var showLogs by remember { mutableStateOf(false) }
    var showDevTools by remember { mutableStateOf(false) }
    var currentPort by remember { mutableIntStateOf(4123) }
    var isQuickBarExpanded by remember { mutableStateOf(false) }

    val statusMsg = com.rk.terminal.service.OpenCodeInstallationObserver.statusText.value
    val latestLog = com.rk.terminal.service.OpenCodeInstallationObserver.latestTranscript.value

    LaunchedEffect(isObservedCompleted) {
        if (isObservedCompleted) {
            serverReady = true
        }
    }

    LaunchedEffect(retryCount) {
        mainActivity.startHeadlessOpenCodeSession()
        com.rk.terminal.service.OpenCodeInstallationObserver.startMonitoring(context)
    }

    LaunchedEffect(Unit) {
        com.rk.terminal.service.OpenCodeInstallationObserver.transitionEvent.collect {
            serverReady = true
        }
    }

    if (!serverReady) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Icon(
                        painter = painterResource(id = drawables.ic_chat),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(strings.opencode_chat),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = { showLogs = !showLogs }) {
                            Text(if (showLogs) stringResource(strings.hide_logs) else stringResource(strings.show_logs))
                        }
                        Button(onClick = { retryCount++ }) {
                            Text(stringResource(strings.retry_action))
                        }
                    }

                    if (showLogs && latestLog.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = latestLog.takeLast(1200),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            navController.navigate(MainActivityRoutes.MainScreen.route)
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = drawables.terminal),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(strings.terminal_label))
                    }
                }
            }
        }
        return
    }

    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    pageLoaded = true
                }

                override fun onReceivedHttpAuthRequest(
                    view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?
                ) {
                    if (host == "127.0.0.1" || host == "localhost") {
                        handler?.proceed("opencode", "andlinux-local-opencode")
                    } else {
                        handler?.cancel()
                    }
                }
            }
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            val credentials = Base64.encodeToString(
                "opencode:andlinux-local-opencode".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            loadUrl(
                "http://127.0.0.1:4123/?auth_token=b3BlbmNvZGU6YW5kbGludXgtbG9jYWwtb3BlbmNvZGU",
                mapOf("Authorization" to "Basic $credentials")
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            mainActivity.moveTaskToBack(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = drawables.ic_chat),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(strings.opencode_chat),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.width(6.dp))
                                // Server Status Dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ComposeColor(0xFF4CAF50))
                                )
                            }
                            if (currentPort != 4123) {
                                Text(
                                    text = "Port $currentPort",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    // Developer Tools Modal
                    IconButton(onClick = { showDevTools = true }) {
                        Icon(
                            painter = painterResource(id = drawables.ic_dev_tools),
                            contentDescription = stringResource(strings.dev_tools_title),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Open in External Browser
                    IconButton(onClick = {
                        DeveloperManager.openInExternalBrowser(context, "http://127.0.0.1:$currentPort")
                    }) {
                        Icon(
                            painter = painterResource(id = drawables.ic_browser),
                            contentDescription = stringResource(strings.open_in_browser),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Reload Page
                    IconButton(onClick = {
                        webView.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload"
                        )
                    }

                    // Switch to Terminal
                    IconButton(onClick = {
                        navController.navigate(MainActivityRoutes.MainScreen.route)
                    }) {
                        Icon(
                            painter = painterResource(id = drawables.terminal),
                            contentDescription = stringResource(strings.terminal_label),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
                update = {
                    it.layoutParams = ViewGroup.LayoutParams(-1, -1)
                }
            )

            // Floating Quick-Switcher Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulse online indicator
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ComposeColor(0xFF4CAF50))
                    )

                    AnimatedVisibility(visible = isQuickBarExpanded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Dev Tools Button
                            OutlinedButton(
                                onClick = { showDevTools = true },
                                modifier = Modifier.height(36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = drawables.ic_dev_tools),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(strings.dev_tools_title), style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(Modifier.width(8.dp))

                            // Terminal Switch Button
                            Button(
                                onClick = { navController.navigate(MainActivityRoutes.MainScreen.route) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = drawables.terminal),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(strings.terminal_label), style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(Modifier.width(4.dp))
                        }
                    }

                    // Toggle expand button
                    IconButton(
                        onClick = { isQuickBarExpanded = !isQuickBarExpanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isQuickBarExpanded) Icons.Default.Close else Icons.Default.Menu,
                            contentDescription = "Quick Tools",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!pageLoaded) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = ComposeColor(0xEE202124)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("OpenCode Web Server", color = ComposeColor.White)
                        Text("URL: http://127.0.0.1:$currentPort", color = ComposeColor.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Password: andlinux-local-opencode",
                                color = ComposeColor.White,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("OpenCode password", "andlinux-local-opencode")
                                )
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy")
                            }
                        }
                    }
                }
            }

            // Developer Tools BottomSheet
            if (showDevTools) {
                DevToolsSheet(
                    mainActivity = mainActivity,
                    onDismissRequest = { showDevTools = false },
                    onSwitchPort = { port ->
                        currentPort = port
                        pageLoaded = false
                        val credentials = Base64.encodeToString(
                            "opencode:andlinux-local-opencode".toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP
                        )
                        webView.loadUrl(
                            "http://127.0.0.1:$port/",
                            mapOf("Authorization" to "Basic $credentials")
                        )
                    }
                )
            }
        }
    }
}
