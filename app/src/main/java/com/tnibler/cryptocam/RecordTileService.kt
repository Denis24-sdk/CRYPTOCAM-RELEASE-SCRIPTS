package com.tnibler.cryptocam

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalCameraFilter
import com.tnibler.cryptocam.video.RecordingService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class RecordTileService : TileService() {
    private val TAG = javaClass.simpleName

    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening")
        update()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening")
    }

    @OptIn(ExperimentalCameraFilter::class)
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick")
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_TOGGLE_RECORDING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // TileService.requestListeningState() seems to work quite unreliably sometimes
        // This seems to work alright most of the time
        GlobalScope.launch {
            delay(300)
            update()
            // Starting the service takes a while, so the first delay isn't enough to catch that
            delay(400)
            update()
        }
    }

    @OptIn(ExperimentalCameraFilter::class)
    private fun update() {
        val service = (application as App).recordingService
        if (service != null) {
            val serviceState = service.state.value
            Log.d(TAG, "service state: $serviceState")
            when (serviceState) {
                is RecordingService.State.Recording -> {
                    qsTile.label = getString(R.string.tile_stop_recording)
                    qsTile.icon = Icon.createWithResource(this, R.drawable.ic_stop)
                }
                else -> {
                    qsTile.label = getString(R.string.tile_start_recording)
                    qsTile.icon = Icon.createWithResource(this, R.drawable.ic_videocam)
                }
            }
            qsTile.updateTile()
        } else {

            qsTile.label = getString(R.string.tile_start_recording)
            qsTile.icon = Icon.createWithResource(this, R.drawable.ic_videocam)
        }
    }
}