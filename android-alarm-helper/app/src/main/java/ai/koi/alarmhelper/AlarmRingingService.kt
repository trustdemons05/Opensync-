package ai.koi.alarmhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class AlarmRingingService : Service() {

    private var ringtone: Ringtone? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Koi Alarm" }
                val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                val transient = intent.getBooleanExtra(EXTRA_TRANSIENT, false)

                startForeground(notificationId(alarmId), buildNotification(alarmId, label, transient))
                startRinging(vibrate)
            }

            ACTION_DISMISS -> {
                stopRinging()
                stopSelf()
            }

            ACTION_SNOOZE -> {
                val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty().ifBlank { "manual" }
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Koi Alarm" }
                val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                NativeAlarmScheduler.scheduleSnooze(this, alarmId, label, vibrate, minutes = 10)
                stopRinging()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun buildNotification(alarmId: String, label: String, transient: Boolean): Notification {
        ensureChannel()

        val dismissIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val snoozeIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_VIBRATE, true)
        }

        val dismissPending = PendingIntent.getService(
            this,
            (alarmId + ":dismiss").hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePending = PendingIntent.getService(
            this,
            (alarmId + ":snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringIntent = Intent(this, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_TRANSIENT, transient)
        }
        val ringPending = PendingIntent.getActivity(
            this,
            (alarmId + ":ring").hashCode(),
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(ringPending)
            .setFullScreenIntent(ringPending, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)
            .addAction(android.R.drawable.ic_media_pause, "Snooze 10m", snoozePending)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Koi Alarm Ringing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ringing alarms"
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startRinging(vibrate: Boolean) {
        stopRinging()

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone?.play()

        if (vibrate) {
            val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 400, 500), 0))
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
        } catch (_: Throwable) {
        }
        ringtone = null

        val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        try {
            vib?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun notificationId(alarmId: String): Int = ("ring:" + alarmId).hashCode()

    companion object {
        const val CHANNEL_ID = "koi_alarm_ringing"

        const val ACTION_START = "ai.koi.alarmhelper.ringing.START"
        const val ACTION_DISMISS = "ai.koi.alarmhelper.ringing.DISMISS"
        const val ACTION_SNOOZE = "ai.koi.alarmhelper.ringing.SNOOZE"

        const val EXTRA_ALARM_ID = "alarmId"
        const val EXTRA_LABEL = "label"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_TRANSIENT = "transient"
    }
}
