package ai.koi.alarmhelper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object NativeAlarmStore {
    private const val PREFS = "koi_alarm_store"
    private const val KEY_ALARMS = "alarms"

    fun list(context: Context): List<NativeAlarm> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ALARMS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<NativeAlarm>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out += obj.toAlarm()
        }
        return out
    }

    fun get(context: Context, id: String): NativeAlarm? = list(context).firstOrNull { it.id == id }

    fun upsert(context: Context, alarm: NativeAlarm) {
        val alarms = list(context).toMutableList()
        val idx = alarms.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) alarms[idx] = alarm else alarms += alarm
        write(context, alarms)
    }

    fun remove(context: Context, id: String) {
        val alarms = list(context).filterNot { it.id == id }
        write(context, alarms)
    }

    fun enabled(context: Context): List<NativeAlarm> = list(context).filter { it.enabled }

    private fun write(context: Context, alarms: List<NativeAlarm>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, arr.toString())
            .apply()
    }

    private fun JSONObject.toAlarm(): NativeAlarm {
        val daysArr = optJSONArray("repeatDays") ?: JSONArray()
        val days = mutableListOf<Int>()
        for (i in 0 until daysArr.length()) days += daysArr.optInt(i)
        return NativeAlarm(
            id = optString("id"),
            hour = optInt("hour"),
            minute = optInt("minute"),
            label = optString("label"),
            repeatDays = days,
            vibrate = optBoolean("vibrate", true),
            soundType = optString("soundType", "alarm"),
            enabled = optBoolean("enabled", true),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            source = optString("source", "manual")
        )
    }

    private fun NativeAlarm.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("label", label)
        put("repeatDays", JSONArray(repeatDays))
        put("vibrate", vibrate)
        put("soundType", soundType)
        put("enabled", enabled)
        put("createdAt", createdAt)
        put("source", source)
    }
}
