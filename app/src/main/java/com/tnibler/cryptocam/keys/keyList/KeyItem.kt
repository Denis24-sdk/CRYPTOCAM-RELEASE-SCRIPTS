package com.tnibler.cryptocam.keys.keyList

import com.tnibler.cryptocam.keys.KeyManager
import org.apache.commons.codec.binary.Hex

data class KeyItem(
    val recipient: KeyManager.X25519Recipient,
    val fingerprint: String,
    val isSelected: Boolean
)

fun KeyManager.toDisplayItem(recipient: KeyManager.X25519Recipient): KeyItem {
    val fingerprint = String(Hex.encodeHex(recipient.fingerprint))
    val isSelected = selectedRecipients.value.contains(recipient)
    return KeyItem(recipient, fingerprint, isSelected)
}
