package ai.koi.alarmhelper

data class NativeAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatDays: List<Int>,
    val vibrate: Boolean,
    val soundType: String = "alarm",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "manual"
)
