package com.tnibler.system

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.camera.core.ExperimentalCameraFilter // <-- Убедитесь, что этот импорт есть
import androidx.preference.PreferenceManager
import com.tnibler.system.keys.KeyManager
import com.tnibler.system.video.RecordingService
import com.tnibler.system.R
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestackextensions.servicesktx.add

@ExperimentalCameraFilter
class App : Application() {
    lateinit var globalServices: GlobalServices
        private set
    var recordingService: RecordingService? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            channel.enableLights(false)
            channel.enableVibration(false)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val keyManager = KeyManager(this, sharedPreferences)

        globalServices = GlobalServices.builder()
            .add(keyManager)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "backgroundRecording"
    }
}