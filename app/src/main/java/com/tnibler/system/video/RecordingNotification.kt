package com.tnibler.system.video

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.app.NotificationCompat
import com.tnibler.system.App
import com.tnibler.system.R

@OptIn(ExperimentalCameraFilter::class)
fun notificationBuilder(context: Context): NotificationCompat.Builder {

    val emptyIntent = Intent()
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    val pendingIntent = PendingIntent.getActivity(context, 0, emptyIntent, flags)

    return NotificationCompat.Builder(context, App.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.notification_title))
        // ИЗМЕНЕНИЕ ЗДЕСЬ: Убрали аргумент "00:00"
        .setContentText(context.getString(R.string.notification_text))
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setLocalOnly(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        // На всякий случай явно отключаем системный хронометр, если он был включен
        .setUsesChronometer(false)
}