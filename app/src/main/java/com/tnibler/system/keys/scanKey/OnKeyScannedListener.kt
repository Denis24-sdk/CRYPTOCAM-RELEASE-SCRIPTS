package com.tnibler.system.keys.scanKey

import com.tnibler.system.keys.KeyManager

interface OnKeyScannedListener {
    fun onKeyScanned(recipient: KeyManager.X25519Recipient)
}