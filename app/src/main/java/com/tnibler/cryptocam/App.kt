package com.tnibler.cryptocam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.preference.PreferenceManager
import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.preference.SettingsFragment
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestackextensions.servicesktx.add

class App : Application() {
    var startedRecordingOnLaunch = false
    lateinit var globalServices: GlobalServices
        private set

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            channel.enableLights(false)
            channel.enableVibration(false)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sharedPreferences.getBoolean(SettingsFragment.PREF_RECORD_ON_START, false)) {
            startedRecordingOnLaunch = true
        }

        val keyManager = KeyManager(this, sharedPreferences)
        globalServices = GlobalServices.builder()
            .add(keyManager)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "backgroundRecording"
    }
}