package com.tnibler.cryptocam.keys

interface OnKeyScannedListener {
    fun onKeyScanned(recipient: KeyManager.X25519Recipient)
}