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

package com.teixeira.vcspace.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.teixeira.vcspace.activities.TerminalActivity
import com.teixeira.vcspace.agent.AgentBridgeServer
import com.teixeira.vcspace.agent.BuiltInAgentTools
import com.teixeira.vcspace.app.drawables
import com.teixeira.vcspace.extensions.makePluralIf
import com.teixeira.vcspace.pi.PiExtensionInstaller
import com.teixeira.vcspace.terminal.Session
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.runBlocking

// https://github.com/Xed-Editor/Xed-Editor/blob/main/core/main/src/main/java/com/rk/xededitor/service/SessionService.kt
class TerminalService : Service() {
    private val sessions = hashMapOf<String, TerminalSession>()
    val sessionList = mutableStateListOf<String>()
    var currentSession = mutableStateOf("main")

    @Suppress("PrivatePropertyName")
    private val ACTION_EXIT by lazy { "com.teixeira.vcspace.action.ACTION_EXIT" }

    @Suppress("PrivatePropertyName")
    private val ACTION_START_PI_BRIDGE by lazy { "com.teixeira.vcspace.action.ACTION_START_PI_BRIDGE" }

    @Suppress("PrivatePropertyName")
    private val ACTION_STOP_PI_BRIDGE by lazy { "com.teixeira.vcspace.action.ACTION_STOP_PI_BRIDGE" }

    @Suppress("PrivatePropertyName")
    private val ACTION_RESTART_PI_BRIDGE by lazy { "com.teixeira.vcspace.action.ACTION_RESTART_PI_BRIDGE" }

    private val notificationId = 46536745
    private var bridgeServer: AgentBridgeServer? = null

    val piBridgeStatus = mutableStateOf("Pi bridge inactive")
    val lastPiBridgeError = mutableStateOf<String?>(null)

    val piBridgeUrl: String?
        get() = bridgeServer?.bridgeUrl

    val piBridgeToken: String?
        get() = bridgeServer?.token

    val isPiBridgeActive: Boolean
        get() = bridgeServer?.isAlive == true

    inner class TerminalBinder : Binder() {
        val service
            get() = this@TerminalService

        fun createSession(
            id: String,
            client: TerminalSessionClient,
            activity: TerminalActivity,
            prootCommand: String? = null,
            workingDirectory: String? = null
        ): TerminalSession {
            return Session.createSession(
                activity = activity,
                sessionClient = client,
                sessionId = id,
                prootCommandOverride = prootCommand,
                workingDirectoryOverride = workingDirectory
            ).also {
                sessions[id] = it
                sessionList.add(id)
                updateNotification()
            }
        }

        fun getSession(id: String): TerminalSession? {
            return sessions[id]
        }

        fun terminateSession(id: String) {
            sessions[id]?.finishIfRunning()
            sessions.remove(id)
            sessionList.remove(id)
            if (sessions.isEmpty()) {
                stopSelf()
            } else {
                updateNotification()
            }
        }
    }

    private val binder = TerminalBinder()
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(notificationId, notification)
        startPiBridge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> {
                sessions.forEach { session -> session.value.finishIfRunning() }
                stopSelf()
            }

            ACTION_START_PI_BRIDGE -> ensurePiBridgeStarted()
            ACTION_STOP_PI_BRIDGE -> stopPiBridge()
            ACTION_RESTART_PI_BRIDGE -> restartPiBridge()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sessions.forEach { session -> session.value.finishIfRunning() }
        stopPiBridgeInternal()
        super.onDestroy()
    }

    fun ensurePiBridgeStarted() {
        startPiBridge()
    }

    fun restartPiBridge() {
        stopPiBridgeInternal()
        startPiBridge()
    }

    fun stopPiBridge() {
        stopPiBridgeInternal()
        updateNotification()
    }

    @Synchronized
    private fun startPiBridge() {
        if (bridgeServer?.isAlive == true) {
            piBridgeStatus.value = "Pi bridge active on ${bridgeServer?.bridgeUrl.orEmpty()}"
            updateNotification()
            return
        }

        piBridgeStatus.value = "Starting Pi bridge..."
        lastPiBridgeError.value = null
        updateNotification()

        runCatching {
            runBlocking { BuiltInAgentTools.register(applicationContext) }
            PiExtensionInstaller.installOrUpdate()
            AgentBridgeServer(applicationContext).also { server ->
                server.ensureStarted()
                bridgeServer = server
            }
        }.onSuccess {
            piBridgeStatus.value = "Pi bridge active on ${bridgeServer?.bridgeUrl.orEmpty()}"
            updateNotification()
        }.onFailure {
            bridgeServer = null
            lastPiBridgeError.value = it.message
            piBridgeStatus.value = "Pi bridge failed: ${it.message ?: it::class.java.simpleName}"
            updateNotification()
            it.printStackTrace()
        }
    }

    @Synchronized
    private fun stopPiBridgeInternal() {
        bridgeServer?.let { server ->
            runCatching {
                server.closeAllConnections()
                server.stop()
            }
        }
        bridgeServer = null
        piBridgeStatus.value = "Pi bridge inactive"
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val bridgeAction = if (isPiBridgeActive) ACTION_STOP_PI_BRIDGE else ACTION_START_PI_BRIDGE
        val bridgeActionText = if (isPiBridgeActive) "Stop Pi Bridge" else "Start Pi Bridge"
        val bridgeActionIntent = Intent(this, TerminalService::class.java).apply { action = bridgeAction }
        val bridgeActionPendingIntent = PendingIntent.getService(
            this,
            notificationId + 1,
            bridgeActionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val restartBridgeIntent = Intent(this, TerminalService::class.java).apply {
            action = ACTION_RESTART_PI_BRIDGE
        }
        val restartBridgePendingIntent = PendingIntent.getService(
            this,
            notificationId + 2,
            restartBridgeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, TerminalService::class.java).apply { action = ACTION_EXIT }
        val exitPendingIntent = PendingIntent.getService(
            this,
            notificationId + 3,
            exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Visual Code Space")
            .setContentText(getNotificationContentText())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    bridgeActionText,
                    bridgeActionPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "Restart Bridge",
                    restartBridgePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "Exit",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)

        return builder.build()
    }

    private val CHANNEL_ID = "session_service_channel"

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Terminal Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(notificationId, notification)
    }

    private fun getNotificationContentText(): String {
        val count = sessions.size
        val sessionText = "$count${" session" makePluralIf (count > 1)} running"
        val bridgeText = when {
            isPiBridgeActive -> "Pi bridge active"
            piBridgeStatus.value.startsWith("Pi bridge failed") -> "Pi bridge failed"
            piBridgeStatus.value.startsWith("Starting") -> "Pi bridge starting"
            else -> "Pi bridge inactive"
        }
        return "$sessionText · $bridgeText"
    }
}
