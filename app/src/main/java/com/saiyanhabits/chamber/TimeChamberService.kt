package com.saiyanhabits.chamber

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.saiyanhabits.SaiyanApp
import com.saiyanhabits.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a time chamber session counting down while the app is in the
 * background or the screen is off: a foreground service with a countdown
 * notification, holding the processor awake until the session ends.
 */
class TimeChamberService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdown: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val chamber = (application as SaiyanApp).timeChamber
        if (intent?.action == ACTION_GIVE_UP) {
            chamber.cancel()
            stopSelf()
            return START_NOT_STICKY
        }

        val session = (chamber.state.value as? ChamberState.Running)?.session
        // A foreground service must show its notification promptly, even when
        // there turns out to be nothing to count down.
        ServiceCompat.startForeground(
            this,
            Notifications.CHAMBER_RUNNING_ID,
            Notifications.chamberRunning(this, session),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val remaining = (session.endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        holdAwake(remaining + WAKE_MARGIN_MILLIS)
        countdown?.cancel()
        countdown = scope.launch {
            delay(remaining)
            val outcome = chamber.finish()
            if (outcome != null) Notifications.showChamberFinished(this@TimeChamberService, session, outcome)
            stopSelf()
        }
        // If Android restarts the service, the saved session is read again.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** Without this the phone can sleep with the screen off and the countdown would stall. */
    private fun holdAwake(millis: Long) {
        wakeLock?.let { if (it.isHeld) it.release() }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saiyanhabits:time-chamber").apply {
            setReferenceCounted(false)
            acquire(millis)
        }
    }

    companion object {
        const val ACTION_GIVE_UP = "com.saiyanhabits.chamber.GIVE_UP"
        private const val WAKE_MARGIN_MILLIS = 60_000L
    }
}
