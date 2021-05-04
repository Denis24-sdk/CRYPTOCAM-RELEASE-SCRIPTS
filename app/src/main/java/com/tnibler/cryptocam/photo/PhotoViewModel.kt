package com.tnibler.cryptocam.photo

import android.os.Parcelable
import android.util.Log
import com.tnibler.cryptocam.VolumeKeyPressListener
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
class PhotoViewModel : VolumeKeyPressListener, ScopedServices.Registered, Parcelable {
    private val TAG = javaClass.simpleName
    private val eventChannel = Channel<Unit>(Channel.BUFFERED)
    private val scope = CoroutineScope(Job())
    val volumeKeyPressed: Flow<Unit> = eventChannel.receiveAsFlow()
    override fun onVolumeKeyDown() {
        scope.launch {
            Log.d(TAG, "onVolumeKeyDown")
            eventChannel.send(Unit)
        }
    }

    override fun onServiceRegistered() {
    }

    override fun onServiceUnregistered() {
        scope.cancel()
    }
}