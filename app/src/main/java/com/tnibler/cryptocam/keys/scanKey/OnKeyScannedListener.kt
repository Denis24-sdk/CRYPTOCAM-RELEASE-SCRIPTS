package com.tnibler.cryptocam.keys.scanKey

import com.tnibler.cryptocam.keys.KeyManager

interface OnKeyScannedListener {
    fun onKeyScanned(recipient: KeyManager.X25519Recipient)
}