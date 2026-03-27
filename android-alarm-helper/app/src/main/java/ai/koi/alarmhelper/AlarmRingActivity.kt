package ai.koi.alarmhelper

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ai.koi.alarmhelper.databinding.ActivityAlarmRingBinding

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(AlarmRingingService.EXTRA_LABEL).orEmpty().ifBlank { "Koi Alarm" }

        binding.alarmTitle.text = label

        binding.dismissButton.setOnClickListener {
            stopService(Intent(this, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_DISMISS
                putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            })
            finish()
        }

        binding.snoozeButton.setOnClickListener {
            startService(Intent(this, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_SNOOZE
                putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingService.EXTRA_LABEL, label)
                putExtra(AlarmRingingService.EXTRA_VIBRATE, true)
            })
            Toast.makeText(this, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
