package com.tnibler.system.keys

import android.net.Uri
import android.util.Log

fun parseImportUri(uri: String): KeyManager.X25519Recipient? {
    val uri = Uri.parse(uri) ?: return null
    if (uri.scheme != "cryptocam" || uri.host != "import_key") {
        Log.d("parseImportUri", "invalid uri: scheme ${uri.scheme}, path ${uri.path}")
        return null
    }
    val name = uri.getQueryParameter("key_name") ?: return null
    val publicKey = uri.getQueryParameter("public_key") ?: return null
    return KeyManager.parseRecipient(name, publicKey)
}