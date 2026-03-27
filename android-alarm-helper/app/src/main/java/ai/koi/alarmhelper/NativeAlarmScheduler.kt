package ai.koi.alarmhelper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object NativeAlarmScheduler {
    const val ACTION_FIRE = "ai.koi.alarmhelper.NATIVE_ALARM_FIRE"
    const val EXTRA_ALARM_ID = "alarmId"
    const val EXTRA_LABEL = "label"
    const val EXTRA_VIBRATE = "vibrate"
    const val EXTRA_TRANSIENT = "transient"
    const val EXTRA_SOUND_TYPE = "soundType"

    fun schedule(context: Context, alarm: NativeAlarm): Long {
        val triggerAt = computeNextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmPendingIntent(
            context,
            alarm.id,
            alarm.label,
            alarm.vibrate,
            alarm.soundType,
            transient = false
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        return triggerAt
    }

    fun scheduleSnooze(
        context: Context,
        baseAlarmId: String,
        label: String,
        vibrate: Boolean,
        soundType: String,
        minutes: Int = 10
    ): Long {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeId = "$baseAlarmId#snooze#$triggerAt"
        val pending = alarmPendingIntent(
            context,
            snoozeId,
            "$label (Snooze)",
            vibrate,
            soundType,
            transient = true
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        return triggerAt
    }

    fun cancel(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmPendingIntent(context, alarmId, "", true, "alarm", transient = false)
        alarmManager.cancel(pending)
    }

    fun computeNextTriggerMillis(hour: Int, minute: Int, repeatDays: List<Int>): Long {
        val now = System.currentTimeMillis()
        for (offset in 0..14) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayAllowed = repeatDays.isEmpty() || repeatDays.contains(cal.get(Calendar.DAY_OF_WEEK))
            if (!dayAllowed) continue
            if (cal.timeInMillis > now + 1000) return cal.timeInMillis
        }
        return now + 60_000L
    }

    fun formatTriggerForUi(triggerAt: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val hh = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$y-$m-$d $hh:$mm"
    }

    private fun alarmPendingIntent(
        context: Context,
        alarmId: String,
        label: String,
        vibrate: Boolean,
        soundType: String,
        transient: Boolean
    ): PendingIntent {
        val i = Intent(context, NativeAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_VIBRATE, vibrate)
            putExtra(EXTRA_SOUND_TYPE, soundType)
            putExtra(EXTRA_TRANSIENT, transient)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, alarmId.hashCode(), i, flags)
    }
}
