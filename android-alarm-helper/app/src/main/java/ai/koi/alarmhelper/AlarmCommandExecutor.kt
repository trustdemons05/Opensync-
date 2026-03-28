package ai.koi.alarmhelper

import java.util.Locale
import java.util.UUID

data class AlarmCommandResult(
    val ok: Boolean,
    val message: String,
    val triggerAt: Long? = null,
    val alarmId: String? = null
)

object AlarmCommandExecutor {
    private val dayAliasToIndex = mapOf(
        "sun" to 1, "sunday" to 1,
        "mon" to 2, "monday" to 2,
        "tue" to 3, "tues" to 3, "tuesday" to 3,
        "wed" to 4, "wednesday" to 4,
        "thu" to 5, "thur" to 5, "thurs" to 5, "thursday" to 5,
        "fri" to 6, "friday" to 6,
        "sat" to 7, "saturday" to 7
    )

    fun scheduleFromMap(payload: Map<String, String>, source: String = "relay", tokenIsValid: Boolean = true, context: android.content.Context): AlarmCommandResult {
        if (!tokenIsValid) {
            return AlarmCommandResult(ok = false, message = "Rejected relay command: invalid shared token")
        }

        val hour = payload["hour"]?.toIntOrNull()
        val minute = payload["minute"]?.toIntOrNull()

        if (hour == null || minute == null) {
            return AlarmCommandResult(ok = false, message = "Missing required hour/minute")
        }

        val autoLaunch = parseBoolean(payload["autoLaunch"], default = true)
        if (!autoLaunch) {
            return AlarmCommandResult(ok = true, message = "autoLaunch=false accepted (no scheduling)")
        }

        return try {
            val parsedDays = parseDaysSpec(payload["days"].orEmpty())
            val label = payload["label"].orEmpty().trim().ifBlank { "Koi Alarm" }.take(80)
            val vibrate = parseBoolean(payload["vibrate"], default = true)
            val soundType = normalizeSoundType(payload["soundType"]) 
            val alarmId = UUID.randomUUID().toString()

            val alarm = NativeAlarm(
                id = alarmId,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                label = label,
                repeatDays = parsedDays.days,
                vibrate = vibrate,
                soundType = soundType,
                enabled = true,
                source = source
            )

            NativeAlarmStore.upsert(context, alarm)
            val triggerAt = NativeAlarmScheduler.schedule(context, alarm)
            val human = NativeAlarmScheduler.formatTriggerForUi(triggerAt)

            val suffixes = mutableListOf<String>()
            if (parsedDays.days.isNotEmpty()) suffixes += "repeats: ${parsedDays.days.joinToString(",")}"
            if (parsedDays.unknownTokens.isNotEmpty()) suffixes += "ignored: ${parsedDays.unknownTokens.joinToString(",")}"
            val suffix = if (suffixes.isEmpty()) "" else " (${suffixes.joinToString("; ")})"

            AlarmCommandResult(
                ok = true,
                message = "Scheduled native alarm for $human$suffix",
                triggerAt = triggerAt,
                alarmId = alarmId
            )
        } catch (t: Throwable) {
            AlarmCommandResult(ok = false, message = "Schedule failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun parseBoolean(raw: String?, default: Boolean): Boolean {
        if (raw.isNullOrBlank()) return default
        return when (raw.trim().lowercase(Locale.getDefault())) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> default
        }
    }

    private fun normalizeSoundType(raw: String?): String {
        val normalized = raw.orEmpty().trim().lowercase(Locale.getDefault())
        return when (normalized) {
            "alarm", "ringtone", "notification" -> normalized
            else -> "alarm"
        }
    }

    private fun parseDaysSpec(daysSpec: String): ParsedDays {
        if (daysSpec.isBlank()) return ParsedDays(emptyList(), emptyList())

        val days = linkedSetOf<Int>()
        val unknown = mutableListOf<String>()

        val tokens = daysSpec
            .split(',', ';', ' ', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        tokens.forEach { token ->
            val normalized = token.lowercase(Locale.getDefault())
            val numeric = normalized.toIntOrNull()

            when {
                numeric != null && numeric in 1..7 -> days += numeric
                dayAliasToIndex.containsKey(normalized) -> days += dayAliasToIndex.getValue(normalized)
                normalized.contains('-') -> {
                    val rangeParts = normalized.split('-').map { it.trim() }
                    if (rangeParts.size == 2) {
                        val start = parseDayToken(rangeParts[0])
                        val end = parseDayToken(rangeParts[1])
                        if (start != null && end != null) {
                            if (start <= end) {
                                (start..end).forEach { days += it }
                            } else {
                                (start..7).forEach { days += it }
                                (1..end).forEach { days += it }
                            }
                        } else {
                            unknown += token
                        }
                    } else {
                        unknown += token
                    }
                }
                else -> unknown += token
            }
        }

        return ParsedDays(days.toList().sorted(), unknown)
    }

    private fun parseDayToken(token: String): Int? {
        val normalized = token.lowercase(Locale.getDefault())
        return normalized.toIntOrNull()?.takeIf { it in 1..7 }
            ?: dayAliasToIndex[normalized]
    }

    private data class ParsedDays(
        val days: List<Int>,
        val unknownTokens: List<String>
    )
}
