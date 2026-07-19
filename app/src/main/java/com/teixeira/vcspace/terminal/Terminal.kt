/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.terminal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.teixeira.vcspace.activities.TerminalActivity
import com.teixeira.vcspace.activities.TerminalActivity.Companion.KEY_PROOT_COMMAND
import com.teixeira.vcspace.activities.TerminalActivity.Companion.KEY_PYTHON_FILE_PATH
import com.teixeira.vcspace.activities.TerminalActivity.Companion.KEY_RUN_PI
import com.teixeira.vcspace.core.settings.Settings
import com.teixeira.vcspace.pi.PiCommands
import com.teixeira.vcspace.pi.PiInstallStatus
import com.teixeira.vcspace.pi.PiInstaller
import com.teixeira.vcspace.terminal.service.TerminalService
import com.teixeira.vcspace.ui.gestures.openDrawerOnSwipe
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysConstants
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysInfo
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysListener
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysView
import com.teixeira.vcspace.utils.showShortToast
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.WeakHashMap

// https://github.com/RohitKushvaha01/ReTerminal/blob/main/app/src/main/java/com/rk/terminal/terminal/Terminal.kt

private var terminalView = WeakReference<TerminalView?>(null)
private val terminalTextSizes = WeakHashMap<TerminalView, Int>()
private val terminalTypefaces = WeakHashMap<TerminalView, Typeface>()
var virtualKeysView = WeakReference<VirtualKeysView?>(null)
var virtualKeysId = View.generateViewId()

private enum class SessionActionType { COPY, DELETE }

private data class TerminalSessionAction(
    val sessionId: String,
    val type: SessionActionType
)

private fun TerminalView.applyTextSizeIfNeeded(textSize: Int) {
    if (terminalTextSizes[this] != textSize) {
        setTextSize(textSize)
        terminalTextSizes[this] = textSize
    }
}

private fun TerminalView.applyTypefaceIfNeeded(typeface: Typeface) {
    if (terminalTypefaces[this] !== typeface) {
        setTypeface(typeface)
        terminalTypefaces[this] = typeface
    }
}

@SuppressLint("MaterialDesignInsteadOrbitDesign")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Terminal(modifier: Modifier = Modifier, terminalActivity: TerminalActivity) {
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val foregroundColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val context = LocalContext.current
    val terminalFontSize = Settings.Terminal.rememberFontSize()
    val terminalFontSizeValue = terminalFontSize.value.coerceIn(23f, 88f).toInt()
    val terminalOutputWidth = Settings.Terminal.rememberOutputWidthPercent()
    val terminalOutputWidthFraction = (terminalOutputWidth.value / 100f).coerceIn(0.5f, 2f)
    val terminalTypeface = remember(context) {
        Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
    }

    LaunchedEffect(Unit) {
        context.startService(Intent(context, TerminalService::class.java))

        terminalView.get()?.let { terminal ->
            if (terminalActivity.intent.extras?.containsKey(KEY_PYTHON_FILE_PATH) == true) {
                terminalActivity.compilePython(terminal)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val terminalOutputWidthDp = (screenWidthDp * terminalOutputWidthFraction).dp
        val terminalHorizontalScroll = rememberScrollState()
        val drawerWidth = (screenWidthDp * 0.84).dp
        val currentSessionId = terminalActivity.terminalBinder?.service?.currentSession?.value

        var sessionPendingAction by remember { mutableStateOf<TerminalSessionAction?>(null) }
        var renameSessionId by remember { mutableStateOf<String?>(null) }

        sessionPendingAction?.let { action ->
            SessionActionDialog(
                action = action,
                onDismiss = { sessionPendingAction = null },
                onConfirm = {
                    when (action.type) {
                        SessionActionType.DELETE -> deleteSession(terminalActivity, action.sessionId)
                        SessionActionType.COPY -> copySession(terminalActivity, action.sessionId)
                    }
                    sessionPendingAction = null
                }
            )
        }

        renameSessionId?.let { sessionId ->
            RenameSessionDialog(
                sessionId = sessionId,
                existingSessionIds = terminalActivity.terminalBinder?.service?.sessionList.orEmpty(),
                onDismiss = { renameSessionId = null },
                onRename = { newName ->
                    renameSession(terminalActivity, sessionId, newName)
                    renameSessionId = null
                }
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var piInstallStatus by remember { mutableStateOf(PiInstaller.status()) }
                        var piPanelExpanded by remember { mutableStateOf(false) }
                        terminalActivity.terminalBinder?.service?.let { service ->
                            PiBridgePanel(
                                installStatus = piInstallStatus,
                                bridgeStatus = service.piBridgeStatus.value,
                                expanded = piPanelExpanded,
                                onToggleExpanded = { piPanelExpanded = !piPanelExpanded },
                                onRefresh = { piInstallStatus = PiInstaller.status() },
                                onOpenPi = {
                                    service.ensurePiBridgeStarted()
                                    startCommandSession(
                                        terminalActivity = terminalActivity,
                                        sessionPrefix = "pi",
                                        command = PiCommands.OPEN_PI,
                                        workingDirectory = terminalActivity.workingDirectory
                                    )
                                    scope.launch { drawerState.close() }
                                },
                                onInstallPi = {
                                    startCommandSession(
                                        terminalActivity = terminalActivity,
                                        sessionPrefix = "install-pi",
                                        command = PiCommands.INSTALL_PI
                                    )
                                    scope.launch { drawerState.close() }
                                },
                                onUpdatePi = {
                                    startCommandSession(
                                        terminalActivity = terminalActivity,
                                        sessionPrefix = "update-pi",
                                        command = PiCommands.UPDATE_PI
                                    )
                                    scope.launch { drawerState.close() }
                                },
                                onRepairPi = {
                                    startCommandSession(
                                        terminalActivity = terminalActivity,
                                        sessionPrefix = "repair-pi",
                                        command = PiCommands.REPAIR_PI
                                    )
                                    scope.launch { drawerState.close() }
                                },
                                onRestartBridge = { service.restartPiBridge() }
                            )
                            HorizontalDivider()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sessions",
                                style = MaterialTheme.typography.titleLarge
                            )
                            IconButton(onClick = {
                                terminalView.get()
                                    ?.let {
                                        val client = TerminalBackend(it, terminalActivity)
                                        terminalActivity.terminalBinder!!.createSession(
                                            generateUniqueSessionId(
                                                terminalActivity.terminalBinder!!.service.sessionList,
                                                "main"
                                            ),
                                            client,
                                            terminalActivity
                                        )
                                    }

                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add, // Material Design "Add" icon
                                    contentDescription = "Add Session"
                                )
                            }
                        }

                        terminalActivity.terminalBinder?.service?.sessionList?.let {
                            LazyColumn {
                                items(
                                    items = it,
                                    key = { sessionId -> sessionId }
                                ) { session_id ->
                                    SelectableCard(
                                        selected = session_id == currentSessionId,
                                        onSelect = { changeSession(terminalActivity, session_id) },
                                        onRename = { renameSessionId = session_id },
                                        onCopy = {
                                            sessionPendingAction = TerminalSessionAction(
                                                sessionId = session_id,
                                                type = SessionActionType.COPY
                                            )
                                        },
                                        onDelete = {
                                            sessionPendingAction = TerminalSessionAction(
                                                sessionId = session_id,
                                                type = SessionActionType.DELETE
                                            )
                                        },
                                        onPin = { pinSession(terminalActivity, session_id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = session_id,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            content = {
                Scaffold(
                    modifier = Modifier.openDrawerOnSwipe(drawerState = drawerState),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Terminal") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    scope.launch { drawerState.open() }
                                }) {
                                    Icon(Icons.Default.Menu, null)
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    val density = LocalDensity.current
                    val imeBottomPx = WindowInsets.ime.getBottom(density)
                    val imeOffsetPx by animateIntAsState(
                        targetValue = -imeBottomPx,
                        animationSpec = tween(durationMillis = 220),
                        label = "terminalImeOffset"
                    )

                    Column(
                        modifier = Modifier
                            .padding(paddingValues)
                            .offset { IntOffset(0, imeOffsetPx) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .horizontalScroll(terminalHorizontalScroll),
                            contentAlignment = if (terminalOutputWidthFraction <= 1f) {
                                Alignment.Center
                            } else {
                                Alignment.CenterStart
                            }
                        ) {
                            AndroidView(
                                factory = { context ->
                                    TerminalView(context, null).apply {
                                        terminalView = WeakReference(this)
                                        val client = TerminalBackend(this, terminalActivity)
                                        applyTextSizeIfNeeded(terminalFontSizeValue)
                                        setTerminalViewClient(client)
                                        val service = terminalActivity.terminalBinder!!.service
                                        val pendingCommand = terminalActivity.intent.getStringExtra(KEY_PROOT_COMMAND)
                                        val pendingSessionId = if (pendingCommand.isNullOrBlank()) {
                                            service.currentSession.value
                                        } else {
                                            generateUniqueSessionId(
                                                service.sessionList,
                                                if (terminalActivity.intent.getBooleanExtra(KEY_RUN_PI, false)) "pi" else "task"
                                            )
                                        }
                                        val session = if (pendingCommand.isNullOrBlank()) {
                                            terminalActivity.terminalBinder!!.getSession(pendingSessionId)
                                                ?: terminalActivity.terminalBinder!!.createSession(
                                                    pendingSessionId,
                                                    client,
                                                    terminalActivity
                                                )
                                        } else {
                                            terminalActivity.terminalBinder!!.createSession(
                                                pendingSessionId,
                                                client,
                                                terminalActivity
                                            )
                                        }
                                        service.currentSession.value = pendingSessionId

                                        session.updateTerminalSessionClient(client)
                                        attachSession(session)
                                        applyTypefaceIfNeeded(terminalTypeface)

                                        post {
                                            setBackgroundColor(backgroundColor)
                                            keepScreenOn = true
                                            requestFocus()
                                            setFocusableInTouchMode(true)

                                            mEmulator?.mColors?.mCurrentColors?.apply {
                                                set(256, foregroundColor)
                                                set(258, foregroundColor)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .requiredWidth(terminalOutputWidthDp)
                                    .fillMaxHeight(),
                                update = { terminalView ->
                                    terminalView.applyTextSizeIfNeeded(terminalFontSizeValue)
                                },
                            )
                        }

                        AndroidView(
                            factory = { context ->
                                VirtualKeysView(context, null).apply {
                                    virtualKeysView = WeakReference(this)
                                    id = virtualKeysId
                                    virtualKeysViewClient =
                                        terminalView.get()?.mTermSession?.let {
                                            VirtualKeysListener(
                                                it
                                            )
                                        }
                                    buttonTextColor = foregroundColor
                                    setBackgroundColor(backgroundColor)

                                    reload(
                                        VirtualKeysInfo(
                                            VIRTUAL_KEYS,
                                            "",
                                            VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(75.dp)
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MaterialDesignInsteadOrbitDesign")
@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = enabled,
                    onClick = onSelect,
                    onLongClick = { menuExpanded = true }
                ),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (selected) 8.dp else 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    menuExpanded = false
                    onCopy()
                }
            )
            DropdownMenuItem(
                text = { Text("Pin to top") },
                onClick = {
                    menuExpanded = false
                    onPin()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun PiBridgePanel(
    installStatus: PiInstallStatus,
    bridgeStatus: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPi: () -> Unit,
    onInstallPi: () -> Unit,
    onUpdatePi: () -> Unit,
    onRepairPi: () -> Unit,
    onRestartBridge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pi / Agent",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = installStatus.summary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "Hide" else "Show")
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = bridgeStatus,
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = onOpenPi,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = installStatus.piInstalled
                ) {
                    Text("Open Pi")
                }

                OutlinedButton(
                    onClick = onInstallPi,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (installStatus.piInstalled) "Reinstall Pi" else "Install Pi")
                }

                OutlinedButton(
                    onClick = onUpdatePi,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Pi")
                }

                OutlinedButton(
                    onClick = onRepairPi,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Repair Pi")
                }

                OutlinedButton(
                    onClick = onRestartBridge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restart Pi Bridge")
                }

                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Status")
                }
            }
        }
    }
}

private fun generateUniqueSessionId(existingStrings: List<String>, prefix: String): String {
    var index = 1
    var newString: String

    do {
        newString = "$prefix$index"
        index++
    } while (newString in existingStrings)

    return newString
}

@Composable
private fun RenameSessionDialog(
    sessionId: String,
    existingSessionIds: List<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember(sessionId) { mutableStateOf(sessionId) }
    val normalizedName = newName.trim()
    val errorText = when {
        normalizedName.isBlank() -> "Name cannot be empty"
        normalizedName != sessionId && normalizedName in existingSessionIds -> "Name already exists"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                isError = errorText != null,
                supportingText = { errorText?.let { Text(it) } },
                label = { Text("Session name") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = errorText == null,
                onClick = { onRename(normalizedName) }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SessionActionDialog(
    action: TerminalSessionAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isDelete = action.type == SessionActionType.DELETE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDelete) "Delete session?" else "Copy session?") },
        text = {
            Text(
                if (isDelete) {
                    "Terminate and remove session '${action.sessionId}'?"
                } else {
                    "Create a new terminal session using '${action.sessionId}' as the template?"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (isDelete) "Delete" else "Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun startCommandSession(
    terminalActivity: TerminalActivity,
    sessionPrefix: String,
    command: String,
    workingDirectory: String? = null
) {
    terminalView.get()?.apply {
        val binder = terminalActivity.terminalBinder ?: return@apply
        val service = binder.service
        val sessionId = generateUniqueSessionId(service.sessionList, sessionPrefix)
        val client = TerminalBackend(this, terminalActivity)
        val session = binder.createSession(
            id = sessionId,
            client = client,
            activity = terminalActivity,
            prootCommand = command,
            workingDirectory = workingDirectory
        )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        service.currentSession.value = sessionId
        virtualKeysView.get()?.apply {
            virtualKeysViewClient = terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }
        showShortToast(terminalActivity, sessionId)
    }
}

private fun renameSession(
    terminalActivity: TerminalActivity,
    sessionId: String,
    newName: String
) {
    val renamed = terminalActivity.terminalBinder?.renameSession(sessionId, newName) == true
    showShortToast(
        terminalActivity,
        if (renamed) "Renamed $sessionId to $newName" else "Cannot rename session"
    )
}

private fun copySession(terminalActivity: TerminalActivity, sessionId: String) {
    terminalView.get()?.apply {
        val binder = terminalActivity.terminalBinder ?: return@apply
        val service = binder.service
        val copiedSessionId = generateUniqueSessionId(service.sessionList, "$sessionId-copy")
        val client = TerminalBackend(this, terminalActivity)
        val session = binder.createSession(
            id = copiedSessionId,
            client = client,
            activity = terminalActivity,
            prootCommand = binder.getSessionCommand(sessionId),
            workingDirectory = binder.getSessionWorkingDirectory(sessionId) ?: terminalActivity.workingDirectory
        )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        service.currentSession.value = copiedSessionId
        virtualKeysView.get()?.apply {
            virtualKeysViewClient = terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }
        showShortToast(terminalActivity, copiedSessionId)
    }
}

private fun pinSession(terminalActivity: TerminalActivity, sessionId: String) {
    val pinned = terminalActivity.terminalBinder?.pinSession(sessionId) == true
    showShortToast(
        terminalActivity,
        if (pinned) "Pinned $sessionId" else "Cannot pin session"
    )
}

fun deleteSession(terminalActivity: TerminalActivity, sessionId: String) {
    val binder = terminalActivity.terminalBinder ?: return
    val service = binder.service
    val wasCurrent = service.currentSession.value == sessionId
    binder.terminateSession(sessionId)

    if (service.sessionList.isEmpty()) {
        val view = terminalView.get() ?: return
        val client = TerminalBackend(view, terminalActivity)
        val session = binder.createSession(
            id = "main1",
            client = client,
            activity = terminalActivity
        )
        session.updateTerminalSessionClient(client)
        view.attachSession(session)
        view.setTerminalViewClient(client)
        service.currentSession.value = "main1"
        showShortToast(terminalActivity, "Deleted $sessionId")
        return
    }

    if (wasCurrent) {
        changeSession(terminalActivity, service.currentSession.value)
    } else {
        showShortToast(terminalActivity, "Deleted $sessionId")
    }
}

fun changeSession(terminalActivity: TerminalActivity, session_id: String) {
    terminalView.get()?.apply {
        val client = TerminalBackend(this, terminalActivity)
        val session =
            terminalActivity.terminalBinder!!.getSession(session_id)
                ?: terminalActivity.terminalBinder!!.createSession(
                    session_id,
                    client,
                    terminalActivity
                )
        session.updateTerminalSessionClient(client)
        attachSession(session)
        setTerminalViewClient(client)
        post {
            val typedValue = TypedValue()

            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                typedValue,
                true
            )
            keepScreenOn = true
            requestFocus()
            setFocusableInTouchMode(true)

            mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, typedValue.data)
                set(258, typedValue.data)
            }
        }
        virtualKeysView.get()?.apply {
            virtualKeysViewClient =
                terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
        }
    }
    terminalActivity.terminalBinder!!.service.currentSession.value = session_id
    showShortToast(terminalActivity, session_id)
}

const val VIRTUAL_KEYS =
    ("[" +
        "\n  [" +
        "\n    \"ESC\"," +
        "\n    {" +
        "\n      \"key\": \"/\"," +
        "\n      \"popup\": \"\\\\\"" +
        "\n    }," +
        "\n    {" +
        "\n      \"key\": \"-\"," +
        "\n      \"popup\": \"|\"" +
        "\n    }," +
        "\n    \"HOME\"," +
        "\n    \"UP\"," +
        "\n    \"END\"," +
        "\n    \"PGUP\"" +
        "\n  ]," +
        "\n  [" +
        "\n    \"TAB\"," +
        "\n    \"CTRL\"," +
        "\n    \"ALT\"," +
        "\n    \"LEFT\"," +
        "\n    \"DOWN\"," +
        "\n    \"RIGHT\"," +
        "\n    \"PGDN\"" +
        "\n  ]" +
        "\n]")
