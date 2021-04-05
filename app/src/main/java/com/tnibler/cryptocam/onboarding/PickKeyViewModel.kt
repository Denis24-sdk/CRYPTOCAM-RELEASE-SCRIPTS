package com.tnibler.cryptocam.onboarding

import com.tnibler.cryptocam.keys.KeyManager
import com.tnibler.cryptocam.keys.OnKeyScannedListener
import com.zhuinden.simplestack.ScopedServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PickKeyViewModel : OnKeyScannedListener, ScopedServices.Registered {
    private val eventChannel = Channel<KeyManager.X25519Recipient>(Channel.BUFFERED)
    private val scope = CoroutineScope(Job())
    val keyScanned: Flow<KeyManager.X25519Recipient> = eventChannel.receiveAsFlow()
    override fun onKeyScanned(recipient: KeyManager.X25519Recipient) {
        scope.launch {
            eventChannel.send(recipient)
        }
    }

    override fun onServiceRegistered() {
    }

    override fun onServiceUnregistered() {
        scope.cancel()
    }
}