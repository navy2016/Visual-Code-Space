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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
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
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysConstants
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysInfo
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysListener
import com.teixeira.vcspace.ui.virtualkeys.VirtualKeysView
import com.teixeira.vcspace.utils.showShortToast
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

// https://github.com/RohitKushvaha01/ReTerminal/blob/main/app/src/main/java/com/rk/terminal/terminal/Terminal.kt

private var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)
var virtualKeysId = View.generateViewId()

@SuppressLint("MaterialDesignInsteadOrbitDesign")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Terminal(modifier: Modifier = Modifier, terminalActivity: TerminalActivity) {
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val foregroundColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val context = LocalContext.current
    val terminalFontSize = Settings.Terminal.rememberFontSize()
    val terminalOutputWidth = Settings.Terminal.rememberOutputWidthPercent()
    val terminalOutputWidthFraction = (terminalOutputWidth.value / 100f).coerceIn(0.6f, 1f)

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
        val drawerWidth = (screenWidthDp * 0.84).dp

        var sessionPendingDelete by remember { mutableStateOf<String?>(null) }

        sessionPendingDelete?.let { sessionId ->
            AlertDialog(
                onDismissRequest = { sessionPendingDelete = null },
                title = { Text("Delete session?") },
                text = { Text("Terminate and remove session '$sessionId'?") },
                confirmButton = {
                    TextButton(onClick = {
                        deleteSession(terminalActivity, sessionId)
                        sessionPendingDelete = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sessionPendingDelete = null }) {
                        Text("Cancel")
                    }
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
                        terminalActivity.terminalBinder?.service?.let { service ->
                            PiBridgePanel(
                                installStatus = piInstallStatus,
                                bridgeStatus = service.piBridgeStatus.value,
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
                                items(it) { session_id ->
                                    SelectableCard(
                                        selected = session_id == terminalActivity.terminalBinder?.service?.currentSession?.value,
                                        onSelect = { changeSession(terminalActivity, session_id) },
                                        onLongClick = { sessionPendingDelete = session_id },
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
                Scaffold(topBar = {
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
                }) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        AndroidView(
                            factory = { context ->
                                TerminalView(context, null).apply {
                                    terminalView = WeakReference(this)
                                    val client = TerminalBackend(this, terminalActivity)
                                    setTextSize(terminalFontSize.value.toInt())
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
                                    setTypeface(
                                        Typeface.createFromAsset(
                                            context.assets,
                                            "fonts/JetBrainsMono-Regular.ttf"
                                        )
                                    )

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
                                .fillMaxWidth(terminalOutputWidthFraction)
                                .weight(1f)
                                .align(Alignment.CenterHorizontally),
                            update = { terminalView ->
                                terminalView.setTextSize(terminalFontSize.value.toInt())
                                terminalView.onScreenUpdated()
                            },
                        )

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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )

    Card(
        modifier = modifier.combinedClickable(
            enabled = enabled,
            onClick = onSelect,
            onLongClick = onLongClick
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
}

@Composable
private fun PiBridgePanel(
    installStatus: PiInstallStatus,
    bridgeStatus: String,
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
        Text(
            text = "Pi / Agent",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = installStatus.summary,
            style = MaterialTheme.typography.bodyMedium
        )
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

private fun generateUniqueSessionId(existingStrings: List<String>, prefix: String): String {
    var index = 1
    var newString: String

    do {
        newString = "$prefix$index"
        index++
    } while (newString in existingStrings)

    return newString
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

private fun deleteSession(terminalActivity: TerminalActivity, sessionId: String) {
    val binder = terminalActivity.terminalBinder ?: return
    val service = binder.service
    val wasCurrent = service.currentSession.value == sessionId
    binder.terminateSession(sessionId)

    if (service.sessionList.isEmpty()) {
        terminalActivity.finish()
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
