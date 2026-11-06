package com.stocktrack.app.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.stocktrack.app.MainActivity
import com.stocktrack.app.R
import com.stocktrack.app.data.AppSettings
import com.stocktrack.app.data.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/** What the Settings card shows. [error] is why the server could not start. */
data class WebServerStatus(val running: Boolean = false, val error: String? = null)

/**
 * Keeps the Admin web page running while it is switched on in Settings, also with the app closed
 * and the screen off. Android requires the permanent notification that goes with this.
 */
class WebServerService : Service() {

    private var server: HttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android expects this straight after the start, also when the service is about to stop again.
        goForeground()
        val settings = AppSettings(this)
        if (!settings.state.value.webServerEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) {
            val http = HttpServer(PORT, ::isLocalNetwork, WebApi(AppWebBackend(this, settings))::handle)
            try {
                http.start()
            } catch (e: Exception) {
                _status.value = WebServerStatus(error = e.message ?: e.javaClass.simpleName)
                stopSelf()
                return START_NOT_STICKY
            }
            server = http
            acquireLocks()
            _status.value = WebServerStatus(running = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.let {
            it.stop()
            _status.value = WebServerStatus()
        }
        server = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    /** Without these the tablet stops answering soon after its screen goes off. */
    private fun acquireLocks() {
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DentTrack:web").apply { acquire() }
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "DentTrack:web").apply { acquire() }
    }

    private fun goForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val localized = Language.context(this)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, localized.getString(R.string.web_server_title), NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val address = addresses().firstOrNull()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_web_server)
            .setContentTitle(localized.getString(R.string.web_server_title))
            .setContentText(address ?: localized.getString(R.string.web_server_no_network))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val PORT = 8080
        private const val CHANNEL_ID = "web_server"
        private const val NOTIFICATION_ID = 8080

        private val _status = MutableStateFlow(WebServerStatus())
        val status: StateFlow<WebServerStatus> = _status.asStateFlow()

        /** Starts or stops the server to match the setting. Android may refuse a start from the background. */
        fun apply(context: Context, enabled: Boolean) {
            val intent = Intent(context, WebServerService::class.java)
            if (enabled) {
                _status.value = _status.value.copy(error = null)
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { e -> _status.value = WebServerStatus(error = e.message ?: e.javaClass.simpleName) }
            } else {
                context.stopService(intent)
                _status.value = WebServerStatus()
            }
        }

        /** The addresses at which other devices on the network can reach the page right now. */
        fun addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { "http://${it.hostAddress}:$PORT" }
        }.getOrDefault(emptyList())
    }
}

/** Brings the server back after the tablet restarts, if it was switched on. */
class WebServerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AppSettings(context).state.value.webServerEnabled) WebServerService.apply(context, true)
    }
}
