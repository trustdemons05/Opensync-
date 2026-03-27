package ai.koi.alarmhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        NativeAlarmStore.enabled(context).forEach { alarm ->
            NativeAlarmScheduler.schedule(context, alarm)
        }
    }
}
