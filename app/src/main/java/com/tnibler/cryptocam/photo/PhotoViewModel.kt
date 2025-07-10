package com.tnibler.cryptocam.photo

import android.content.BroadcastReceiver
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import com.tnibler.cryptocam.SelectedCamera
import com.tnibler.cryptocam.VolumeKeyPressListener
import com.tnibler.cryptocam.photo.photoviewer.PhotoProvider
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.statebundle.StateBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PhotoViewModel : VolumeKeyPressListener, PhotoProvider, ScopedServices.Registered, Bundleable {
    private val TAG = javaClass.simpleName
    private val eventChannel = Channel<Unit>(Channel.BUFFERED)
    private val scope = CoroutineScope(Job())
    val volumeKeyPressed: Flow<Unit> = eventChannel.receiveAsFlow()
    val flashMode: MutableStateFlow<FlashMode> = MutableStateFlow(FlashMode.OFF)
    var selectedCamera: SelectedCamera = SelectedCamera.BACK
    var lastPhoto: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
    override val photo: StateFlow<Bitmap?>
        get() = lastPhoto.asStateFlow()
    var isTakingPhoto = MutableStateFlow(false)

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

    override fun toBundle(): StateBundle {
        return StateBundle().apply {
            putSerializable("selectedCamera", selectedCamera)
            putSerializable("flashMode", flashMode.value)
        }
    }

    override fun fromBundle(bundle: StateBundle?) {
        if (bundle != null) {
            flashMode.value = bundle.getSerializable("flashMode") as FlashMode
            selectedCamera = bundle.getSerializable("selectedCamera") as SelectedCamera
        }
    }

    fun onActivityPaused() {
        lastPhoto.value = null
    }

    enum class FlashMode {
        AUTO, ON, OFF
    }
}