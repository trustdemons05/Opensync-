package ai.koi.alarmhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NativeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != NativeAlarmScheduler.ACTION_FIRE) return

        val alarmId = intent.getStringExtra(NativeAlarmScheduler.EXTRA_ALARM_ID) ?: return
        val transient = intent.getBooleanExtra(NativeAlarmScheduler.EXTRA_TRANSIENT, false)

        val stored = NativeAlarmStore.get(context, alarmId)
        val label = stored?.label
            ?: intent.getStringExtra(NativeAlarmScheduler.EXTRA_LABEL)
            ?: "Koi Alarm"
        val vibrate = stored?.vibrate
            ?: intent.getBooleanExtra(NativeAlarmScheduler.EXTRA_VIBRATE, true)

        if (stored != null && stored.repeatDays.isNotEmpty()) {
            NativeAlarmScheduler.schedule(context, stored)
        } else if (stored != null && stored.repeatDays.isEmpty()) {
            NativeAlarmStore.remove(context, alarmId)
        }

        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_LABEL, label)
            putExtra(AlarmRingingService.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmRingingService.EXTRA_TRANSIENT, transient)
        }

        context.startForegroundService(serviceIntent)
    }
}
