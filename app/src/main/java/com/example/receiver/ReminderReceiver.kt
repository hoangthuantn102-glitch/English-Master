package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.utils.ReminderScheduler

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("reminder_type") ?: "popup"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_study_reminder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Nhắc nhở học tập hàng ngày"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Thông báo nhắc nhở ôn luyện từ vựng hàng ngày"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⏰ Đến giờ học tiếng Anh rồi!"
        val message = "Hãy dành 5 phút ôn luyện từ vựng hôm nay để duy trì chuỗi lửa Streak và tăng Cấp độ nhé! 🔥"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (type == "sound") {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Popup banner style
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        notificationManager.notify(2001, builder.build())

        // Reschedule for tomorrow
        val prefs = context.getSharedPreferences(ReminderScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(ReminderScheduler.KEY_REMINDER_ENABLED, false)
        if (enabled) {
            val hour = prefs.getInt(ReminderScheduler.KEY_REMINDER_HOUR, 20)
            val minute = prefs.getInt(ReminderScheduler.KEY_REMINDER_MINUTE, 0)
            val savedType = prefs.getString(ReminderScheduler.KEY_REMINDER_TYPE, "popup") ?: "popup"
            ReminderScheduler.scheduleReminder(context, hour, minute, savedType)
        }
    }
}
