package com.tnibler.system.keys

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import android.util.Log
import androidx.core.content.edit
import com.tnibler.system.preference.SettingsFragment
import cryptocam_age_encryption.Cryptocam_age_encryption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.lang.Exception
import java.security.MessageDigest

class KeyManager(private val context: Context, private val sharedPreferences: SharedPreferences) {
    private val TAG = javaClass.simpleName
    private val _keys = MutableStateFlow<List<X25519Recipient>>(listOf())
    private val _selectedKeys = MutableStateFlow<Set<X25519Recipient>>(setOf())
    private val keysDir by lazy { File(context.filesDir.canonicalPath + File.separator + DIRNAME) }
    val availableKeys: StateFlow<List<X25519Recipient>> = _keys
    val selectedRecipients: StateFlow<Set<X25519Recipient>> = _selectedKeys

    init {
        readAvailableKeysFromStorage()
        readSelectedKeysFromSharedPref()
    }

    fun setRecipientSelected(recipient: X25519Recipient, selected: Boolean) {
        val currentlySelected = _selectedKeys.value
        val newSelected = if (selected) {
            if (!_keys.value.contains(recipient)) {
                throw IllegalStateException("Selected recipient that does not exist!")
            }
            currentlySelected + recipient
        } else {
            currentlySelected - recipient
        }
        sharedPreferences.edit {
            putStringSet(
                SettingsFragment.PREF_SELECTED_RECIPIENTS,
                newSelected.map { r ->
                    String(Hex.encodeHex(r.fingerprint))
                }.toSet()
            )
            commit()
        }
        _selectedKeys.value = newSelected
        Log.d(TAG, "selected recipients: ${_selectedKeys.value}")
    }

    fun importRecipient(recipient: X25519Recipient): Boolean {
        val newFilePath = keyfilePath(recipient)
        Log.d(TAG, "new file path: $newFilePath")
        val newFile = File(newFilePath)
        return if (newFile.createNewFile()) {
            newFile.writeText("# ${recipient.name}\n${recipient.publicKey}")
            _keys.value = _keys.value + recipient
            setRecipientSelected(recipient, true)
            true
        } else {
            false
        }
    }

    fun deleteRecipient(recipient: X25519Recipient) {
        val keys = _keys.value - recipient
        val selectedKeys = _selectedKeys.value - recipient
        try {
            File(keyfilePath(recipient)).delete()
        }
        catch (e: Exception) {
            Log.e(TAG, "Error deleting keyfile: $e")
        }
        _selectedKeys.value = selectedKeys
        _keys.value = keys
    }

    private fun keyfilePath(recipient: X25519Recipient): String {
        val filename = String(Hex.encodeHex(recipient.fingerprint))
        return keysDir.path + File.separator + "$filename.txt"
    }

    private fun readAvailableKeysFromStorage() {
        if (keysDir.mkdir()) {
            Log.d(TAG, "created keys directory")
        }
        val keys = keysDir.listFiles().map { file ->
            tryReadKey(file)
        }
            .filterNotNull()
        _keys.value = keys
        Log.d(TAG, "read keys from storage: $keys")
    }

    private fun readSelectedKeysFromSharedPref() {
        val prefStrings =
            sharedPreferences.getStringSet(SettingsFragment.PREF_SELECTED_RECIPIENTS, setOf())
                ?: setOf()
        val availableFingerprints = availableKeys.value
            .associateBy { String(Hex.encodeHex(it.fingerprint)) }
        val recipients = prefStrings
            .map { fingerprint ->
                availableFingerprints[fingerprint]
            }
            .filterNotNull()
            .toSet()
        _selectedKeys.value = recipients
    }

    private fun tryReadKey(file: File): X25519Recipient? {
        val lines = file.readLines().filter { line -> line.isNotBlank() }
        if (lines.size != 2) {
            return null
        }
        val name = lines[0].trim().removePrefix("#").trim()
        val publicKey = lines[1].trim()
        return parseRecipient(name, publicKey)
    }


    @Parcelize
    data class X25519Recipient(
        val name: String,
        val publicKey: String,
        val fingerprint: ByteArray
    ) :
        Parcelable

    companion object {
        private const val DIRNAME = "keys"
        private val sha256 by lazy { MessageDigest.getInstance("SHA256") }

        fun parseRecipient(name: String, publicKey: String): X25519Recipient? {
            val isValid = Cryptocam_age_encryption.checkIsX25519PubKey(publicKey)
            return if (isValid) {
                val fingerprint = sha256.digest(publicKey.toByteArray()).copyOfRange(16, 32)
                X25519Recipient(name, publicKey, fingerprint)
            } else {
                null
            }
        }
    }
}

