package com.tnibler.cryptocam.video

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.app.NotificationCompat
import com.tnibler.cryptocam.App
import com.tnibler.cryptocam.R

@OptIn(ExperimentalCameraFilter::class)
fun notificationBuilder(context: Context): NotificationCompat.Builder {

    val emptyIntent = Intent()
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    val pendingIntent = PendingIntent.getActivity(context, 0, emptyIntent, flags)

    return NotificationCompat.Builder(context, App.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.notification_title)) // "System Service"
        .setContentText(context.getString(R.string.notification_text, "00:00")) // "Active process: 00:00"
        .setSmallIcon(android.R.drawable.ic_dialog_info) // Нейтральная системная иконка
        .setContentIntent(pendingIntent) // Устанавливаем пустой интент
        .setOngoing(true)       // Делает уведомление постоянным (не свайпаемым)
        .setLocalOnly(true)     // Уведомление не будет отображаться на спаренных устройствах (часах)
        .setPriority(NotificationCompat.PRIORITY_MIN) // Минимальный приоритет, чтобы не мешало

}
