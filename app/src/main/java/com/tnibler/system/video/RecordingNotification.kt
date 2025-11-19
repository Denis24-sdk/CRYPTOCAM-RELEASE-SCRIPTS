package com.tnibler.system.video

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager // Добавлен импорт
import com.tnibler.system.App
import com.tnibler.system.R
import com.tnibler.system.preference.SettingsFragment // Добавлен импорт для ключей

@OptIn(ExperimentalCameraFilter::class)
fun notificationBuilder(context: Context): NotificationCompat.Builder {

    // 1. Получаем настройки
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    // 2. Читаем заголовок (если пусто — берем из R.string)
    val customTitle = prefs.getString(
        SettingsFragment.PREF_NOTIFICATION_TITLE,
        context.getString(R.string.notification_title)
    ) ?: context.getString(R.string.notification_title)

    // 3. Читаем текст (если пусто — берем из R.string)
    val customText = prefs.getString(
        SettingsFragment.PREF_NOTIFICATION_TEXT,
        context.getString(R.string.notification_text)
    ) ?: context.getString(R.string.notification_text)

    val emptyIntent = Intent()
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    val pendingIntent = PendingIntent.getActivity(context, 0, emptyIntent, flags)

    return NotificationCompat.Builder(context, App.CHANNEL_ID)
        .setContentTitle(customTitle) // Ставим кастомный заголовок
        .setContentText(customText)   // Ставим кастомный текст
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setLocalOnly(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setUsesChronometer(false)
}