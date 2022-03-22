package com.tnibler.cryptocam.video

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.App
import com.tnibler.cryptocam.MainActivity
import com.tnibler.cryptocam.R
import com.tnibler.cryptocam.preference.SettingsFragment

fun notificationBuilder(context: Context): Notification.Builder {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val resultIntent = Intent(context, MainActivity::class.java)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT > 23) PendingIntent.FLAG_IMMUTABLE else 0
    val pendingIntent =
        PendingIntent.getActivity(context, 0, resultIntent, flags)
    val useCustomNotification = sharedPreferences.getBoolean(SettingsFragment.PREF_CUSTOMIZE_NOTIFICATION, false)
    return if (!useCustomNotification || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        defaultNotification(context).apply {
            setContentIntent(pendingIntent)
        }
    } else {
        val styleValue = sharedPreferences.getString(SettingsFragment.PREF_CUSTOM_NOTIFICATION_STYLE, null)
        val style = NotificationStyle.values().find { it.entryValue == styleValue } ?: NotificationStyle.Samsung
        customNotification(style, context, sharedPreferences).apply {
            setContentIntent(pendingIntent)
        }
    }
}

private fun defaultNotification(context: Context): Notification.Builder {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            Notification.Builder(context, App.CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text))
                .setOngoing(true)
                .setLocalOnly(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
        }
        else -> {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_text))
                .setOngoing(true)
                .setLocalOnly(true)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N)
private fun customNotification(style: NotificationStyle, context: Context, sharedPreferences: SharedPreferences): Notification.Builder {
    val resources = context.resources
    val appName = sharedPreferences.getString(SettingsFragment.PREF_CUSTOM_NOTIFICATION_APP_NAME, resources.getString(R.string.app_name))
    val title = sharedPreferences.getString(SettingsFragment.PREF_CUSTOM_NOTIFICATION_TITLE, resources.getString(R.string.notification_title))
    val text = sharedPreferences.getString(SettingsFragment.PREF_CUSTOM_NOTIFICATION_TEXT, "")
    val iconName = sharedPreferences.getString(SettingsFragment.PREF_CUSTOM_NOTIFICATION_ICON, null)
    val icon = NotificationIcon.values().find { it.entryValue == iconName }?.drawable ?: NotificationIcon.Cloud.drawable
    val layoutId = when(style) {
        NotificationStyle.Samsung -> R.layout.custom_notification_samsung
        NotificationStyle.GooglePixel -> R.layout.custom_notification_pixel
    }
    val remoteViews = RemoteViews(context.packageName, layoutId)
        .apply {
            setTextViewText(R.id.customNotificationTitleView, title)
            setTextViewText(R.id.customNotificationAppName, appName)
            setTextViewText(R.id.customNotificationTextView, text)
            setImageViewResource(R.id.customNotificationIcon, icon)
        }
    val builder =  when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> Notification.Builder(context, App.CHANNEL_ID)
        else -> @Suppress("DEPRECATION") Notification.Builder(context)
    }
    return builder
        .setCustomContentView(remoteViews)
        .setOngoing(true)
        .setLocalOnly(true)
        .setSmallIcon(icon)
}

enum class NotificationIcon(val entryValue: String, val entryName: String, @DrawableRes val drawable: Int) {
    Alarm("alarm", "Alarm", R.drawable.notification_ic_alarm),
    Audiotrack("audiotrack", "Audio track", R.drawable.notification_ic_audiotrack),
    Cloud("cloud", "Cloud", R.drawable.notification_ic_cloud),
    Backup("backup", "Cloud backup", R.drawable.notification_ic_backup),
    Bluetooth("bluetooth", "Bluetooth", R.drawable.notification_ic_bluetooth),
    Directions("directions", "Navigation", R.drawable.notification_ic_directions),
    None("none", "None", R.drawable.notification_ic_none),
}

enum class NotificationStyle(val entryValue: String, val entryName: String) {
    Samsung("samsung_s9_10", "Samsung"),
    GooglePixel("google_p2_10", "Pixel")
}
