package com.tnibler.system.video

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalCameraFilter
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.tnibler.system.App
import com.tnibler.system.R
import com.tnibler.system.preference.SettingsFragment

@OptIn(ExperimentalCameraFilter::class)
fun notificationBuilder(context: Context): NotificationCompat.Builder {

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val customTitle = prefs.getString(
        SettingsFragment.PREF_NOTIFICATION_TITLE,
        context.getString(R.string.notification_title)
    ) ?: context.getString(R.string.notification_title)

    val customText = prefs.getString(
        SettingsFragment.PREF_NOTIFICATION_TEXT,
        context.getString(R.string.notification_text)
    ) ?: context.getString(R.string.notification_text)

    val iconType = prefs.getString(SettingsFragment.PREF_NOTIFICATION_ICON, "info")

    val iconResId = when (iconType) {

        "alert"    -> android.R.drawable.ic_dialog_alert
        "email"    -> android.R.drawable.ic_dialog_email
        "info"     -> android.R.drawable.ic_dialog_info


        "cloud"    -> android.R.drawable.ic_menu_upload
        "lock"     -> android.R.drawable.ic_lock_lock
        "settings" -> android.R.drawable.ic_menu_preferences
        "save"     -> android.R.drawable.ic_menu_save
        "search"   -> android.R.drawable.ic_menu_search


        "map"      -> android.R.drawable.ic_dialog_map
        "call"     -> android.R.drawable.ic_menu_call
        "camera"   -> android.R.drawable.ic_menu_camera
        "play"     -> android.R.drawable.ic_media_play

        // Fallback
        else -> {
            if (iconType == "lock") android.R.drawable.ic_secure else android.R.drawable.ic_dialog_info
        }
    }


    val emptyIntent = Intent()
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    val pendingIntent = PendingIntent.getActivity(context, 0, emptyIntent, flags)

    return NotificationCompat.Builder(context, App.CHANNEL_ID)
        .setContentTitle(customTitle)
        .setContentText(customText)
        .setSmallIcon(iconResId)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setLocalOnly(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setUsesChronometer(false)
}