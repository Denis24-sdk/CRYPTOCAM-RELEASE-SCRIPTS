package com.tnibler.cryptocam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection

class App : Application() {
    var openPgpServiceConnection: OpenPgpServiceConnection? = null
    var openPgpApi: OpenPgpApi? = null
    var startedRecordingOnLaunch = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_MIN
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        openPgpServiceConnection?.unbindFromService()
    }

    companion object {
        const val CHANNEL_ID = "backgroundRecording"
    }
}