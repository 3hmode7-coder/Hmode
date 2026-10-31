package com.rk.terminal.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.terminal.service.DeveloperManager
import com.rk.terminal.ui.activities.terminal.MainActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsSheet(
    mainActivity: MainActivity,
    onDismissRequest: () -> Unit,
    onSwitchPort: (Int) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(strings.ai_providers_title),
        stringResource(strings.quick_packages_title),
        stringResource(strings.quick_commands_title),
        stringResource(strings.port_manager_title)
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = drawables.ic_dev_tools),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(strings.dev_tools_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismissRequest()
                    }
                }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            // Tab Content Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> AiKeysTab(mainActivity = mainActivity)
                    1 -> PackagesTab(mainActivity = mainActivity)
                    2 -> QuickCommandsTab(mainActivity = mainActivity)
                    3 -> PortsTab(
                        mainActivity = mainActivity,
                        onSwitchPort = { port ->
                            onSwitchPort(port)
                            scope.launch {
                                sheetState.hide()
                                onDismissRequest()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiKeysTab(mainActivity: MainActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var anthropicKey by remember { mutableStateOf(DeveloperManager.getStoredKey(context, DeveloperManager.KEY_ANTHROPIC)) }
    var openaiKey by remember { mutableStateOf(DeveloperManager.getStoredKey(context, DeveloperManager.KEY_OPENAI)) }
    var geminiKey by remember { mutableStateOf(DeveloperManager.getStoredKey(context, DeveloperManager.KEY_GEMINI)) }
    var deepseekKey by remember { mutableStateOf(DeveloperManager.getStoredKey(context, DeveloperManager.KEY_DEEPSEEK)) }
    var groqKey by remember { mutableStateOf(DeveloperManager.getStoredKey(context, DeveloperManager.KEY_GROQ)) }
    var baseUrl by remember { mutableStateOf(DeveloperManager.getStoredKey(context, DeveloperManager.KEY_BASE_URL)) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(strings.ai_providers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = anthropicKey,
            onValueChange = { anthropicKey = it },
            label = { Text(stringResource(strings.api_key_anthropic)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("sk-ant-...") }
        )

        OutlinedTextField(
            value = openaiKey,
            onValueChange = { openaiKey = it },
            label = { Text(stringResource(strings.api_key_openai)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("sk-proj-...") }
        )

        OutlinedTextField(
            value = geminiKey,
            onValueChange = { geminiKey = it },
            label = { Text(stringResource(strings.api_key_gemini)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("AIzaSy...") }
        )

        OutlinedTextField(
            value = deepseekKey,
            onValueChange = { deepseekKey = it },
            label = { Text(stringResource(strings.api_key_deepseek)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("sk-...") }
        )

        OutlinedTextField(
            value = groqKey,
            onValueChange = { groqKey = it },
            label = { Text(stringResource(strings.api_key_groq)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("gsk_...") }
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(strings.api_base_url)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("http://127.0.0.1:11434") }
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                isSaving = true
                DeveloperManager.saveStoredKey(context, DeveloperManager.KEY_ANTHROPIC, anthropicKey)
                DeveloperManager.saveStoredKey(context, DeveloperManager.KEY_OPENAI, openaiKey)
                DeveloperManager.saveStoredKey(context, DeveloperManager.KEY_GEMINI, geminiKey)
                DeveloperManager.saveStoredKey(context, DeveloperManager.KEY_DEEPSEEK, deepseekKey)
                DeveloperManager.saveStoredKey(context, DeveloperManager.KEY_GROQ, groqKey)
                DeveloperManager.saveStoredKey(context, DeveloperManager.KEY_BASE_URL, baseUrl)

                scope.launch {
                    val synced = DeveloperManager.syncKeysToAlpine(context)
                    isSaving = false
                    val msg = if (synced) {
                        context.getString(strings.sync_keys_success)
                    } else {
                        "Saved to settings"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(strings.save_keys))
        }
    }
}

@Composable
private fun PackagesTab(mainActivity: MainActivity) {
    val context = LocalContext.current
    val groups = DeveloperManager.devPackageGroups

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(strings.quick_packages_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(groups) { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(group.nameResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "apk add ${group.packages}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val sent = DeveloperManager.installPackages(mainActivity, group.packages)
                            if (sent) {
                                Toast.makeText(
                                    context,
                                    "Installing ${group.id} in background…",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Could not connect to session",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text(stringResource(strings.install_package))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCommandsTab(mainActivity: MainActivity) {
    val context = LocalContext.current
    var customCommand by remember { mutableStateOf("") }

    val presetCommands = listOf(
        "git status",
        "git pull",
        "git log -n 5 --oneline",
        "apk update",
        "ps aux",
        "free -m",
        "df -h",
        "node -v && npm -v",
        "python3 --version",
        "ls -la"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(strings.quick_commands_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Custom command input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customCommand,
                onValueChange = { customCommand = it },
                label = { Text(stringResource(strings.custom_command_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (customCommand.isNotBlank()) {
                        val sent = DeveloperManager.sendCommandToAlpine(mainActivity, customCommand)
                        if (sent) {
                            Toast.makeText(context, context.getString(strings.command_sent), Toast.LENGTH_SHORT).show()
                            customCommand = ""
                        }
                    }
                },
                enabled = customCommand.isNotBlank()
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            }
        }

        Text(
            text = "Preset Commands",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // Preset command chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presetCommands.forEach { cmd ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    onClick = {
                        val sent = DeveloperManager.sendCommandToAlpine(mainActivity, cmd)
                        if (sent) {
                            Toast.makeText(context, "$cmd executed", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortsTab(
    mainActivity: MainActivity,
    onSwitchPort: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activePort by remember { mutableIntStateOf(4123) }
    var customPortInput by remember { mutableStateOf("") }
    val defaultPorts = listOf(4123, 3000, 5173, 8000, 8080)

    val portLabels = mapOf(
        4123 to "OpenCode Chat",
        3000 to "React / Node.js",
        5173 to "Vite Dev Server",
        8000 to "Python / FastAPI",
        8080 to "Web Server"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(strings.port_manager_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(defaultPorts) { port ->
                FilterChip(
                    selected = activePort == port,
                    onClick = { activePort = port },
                    label = { Text("Port $port (${portLabels[port] ?: ""})") }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = customPortInput,
                onValueChange = { customPortInput = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(strings.custom_port)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("e.g. 5000") }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val p = customPortInput.toIntOrNull()
                    if (p != null && p in 1..65535) {
                        activePort = p
                    }
                },
                enabled = customPortInput.isNotBlank()
            ) {
                Text("Set")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Selected Port: $activePort",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "URL: http://127.0.0.1:$activePort",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    onSwitchPort(activePort)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Preview In App")
            }

            OutlinedButton(
                onClick = {
                    DeveloperManager.openInExternalBrowser(context, "http://127.0.0.1:$activePort")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = drawables.ic_browser),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(strings.open_in_browser))
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                DeveloperManager.restartOpenCodeServer(mainActivity)
                Toast.makeText(context, context.getString(strings.server_restarted), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(strings.restart_opencode))
        }
    }
}
