package com.intelliverse.croman.data.reminder

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
import com.intelliverse.croman.CromanApp
import com.intelliverse.croman.MainActivity
import com.intelliverse.croman.R
import com.intelliverse.croman.domain.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReminderScheduler(private val context: Context) {
    fun schedule(task: TaskItem) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = pending(task.id)
        val trigger = task.reminderAt
        if (task.deletedAt != null || task.completedAt != null || trigger == null || trigger <= System.currentTimeMillis()) {
            manager.cancel(pending)
            return
        }
        if (AlarmPolicy.choose(Build.VERSION.SDK_INT, canExact(manager)) == AlarmPolicy.EXACT) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    private fun canExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun cancel(taskId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(taskId))
    }

    private fun pending(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object AlarmPolicy {
    const val EXACT = "exact"
    const val WHILE_IDLE = "while-idle"

    fun choose(sdk: Int, canScheduleExact: Boolean): String =
        if (sdk < 31 || canScheduleExact) EXACT else WHILE_IDLE
}

object TaskNotifications {
    const val CHANNEL_ID = "task_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notifs_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notifs_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, task: TaskItem) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_TASK_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext as CromanApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.container.store.getTask(id) ?: return@launch
                if (task.deletedAt != null || task.completedAt != null) return@launch
                TaskNotifications.show(app, task)
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
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as CromanApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = app.container.reminders
                app.container.store.reminderTasks().forEach { scheduler.schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
