package com.mantelmakher.crm.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mantelmakher.crm.CrmApplication
import com.mantelmakher.crm.MainActivity
import com.mantelmakher.crm.R
import com.mantelmakher.crm.domain.model.CrmTask
import com.mantelmakher.crm.domain.repository.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {
    override fun schedule(task: CrmTask) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(task.id)
        val trigger = task.reminderAt
        if (task.deletedAt != null || task.completedAt != null || trigger == null || trigger <= System.currentTimeMillis()) {
            alarmManager.cancel(pending)
            return
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    override fun cancel(taskId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(taskId))
    }

    private fun pendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object TaskNotifications {
    const val CHANNEL_ID = "task_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notifs_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notifs_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, task: CrmTask) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_TASK_ID, task.id)
        }
        val content = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = task.details.ifBlank { context.getString(R.string.task_reminder) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(task.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext as CrmApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.container.tasks.get(taskId) ?: return@launch
                if (task.deletedAt != null || task.completedAt != null) return@launch
                TaskNotifications.show(context.applicationContext, task)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as CrmApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.tasks.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
