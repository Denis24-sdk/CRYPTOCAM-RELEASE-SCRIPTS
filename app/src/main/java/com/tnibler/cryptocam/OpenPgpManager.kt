package com.tnibler.cryptocam

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class OpenPgpManager {
    private val TAG = javaClass.simpleName
    private val scope = GlobalScope
    lateinit var api: OpenPgpApi

    enum class OpenPgpStatus {
        BOUND,
        NOT_BOUND
    }

    fun chooseKey(
        chooseKeyActivityResult: ActivityResultLauncher<IntentSenderRequest>,
        setChooseKeyActivityResultListener: ((ActivityResult) -> Unit) -> Unit,
        onKeyChosen: (Boolean, List<Long>) -> Unit
    ) {
        scope.launch {
            getKeyIds(
                Intent(),
                onKeyChosen,
                chooseKeyActivityResult,
                setChooseKeyActivityResultListener
            )
        }
    }

    fun encryptText(text: String, out: OutputStream, keyIds: Collection<Long>) {
        val intent = Intent()
        intent.action = OpenPgpApi.ACTION_ENCRYPT
        intent.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keyIds.toLongArray())
        intent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)
        val result = api.executeApi(intent, text.byteInputStream(), out)
        when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_ERROR -> {
                Log.e(TAG, "Error encrypting data")
                throw EncryptionException("Error encrypting data.")
            }
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                Log.d(TAG, "successfully encrypted data")
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                Log.w(TAG, "user interaction required")
                throw EncryptionException("User interaction required")
            }
        }
    }

    fun checkKeyIdIsValid(keyId: Long, intent: Intent = Intent()): Boolean {
        val data = intent.apply {
            action = OpenPgpApi.ACTION_ENCRYPT
        }
        data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, longArrayOf(keyId))
        val result =
            api.executeApi(data, ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        return when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_ERROR -> {
                false
            }
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                true
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                Log.w(TAG, "user interaction required")
                return checkKeyIdIsValid(keyId, intent)
            }
            else -> false
        }
    }

    private fun getKeyIds(
        data: Intent,
        onKeyChosen: (Boolean, List<Long>) -> Unit,
        chooseKeyActivityResult: ActivityResultLauncher<IntentSenderRequest>,
        setChooseKeyActivityResultListener: ((ActivityResult) -> Unit) -> Unit
    ) {
        Log.d(TAG, "getKeyIds: $data")
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        val result = api.executeApi(data, null as InputStream?, null)
        when (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            OpenPgpApi.RESULT_CODE_ERROR -> {
                Log.e(
                    TAG,
                    "OpenPgp error: ${result.getParcelableExtra<OpenPgpError>(OpenPgpApi.RESULT_ERROR)}"
                )
                onKeyChosen(false, listOf())
            }
            OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                val pi: PendingIntent =
                    result.getParcelableExtra<PendingIntent>(OpenPgpApi.RESULT_INTENT) ?: run {
                        Log.w(TAG, "User interaction required but pending intent null.")
                        onKeyChosen(false, listOf())
                        return
                    }
                val intentSenderRequest = IntentSenderRequest.Builder(pi).build()
                setChooseKeyActivityResultListener { result ->
                    val data = result.data ?: run {
                        onKeyChosen(
                            false,
                            listOf()
                        ); return@setChooseKeyActivityResultListener
                    }
                    getKeyIds(
                        data,
                        onKeyChosen,
                        chooseKeyActivityResult,
                        setChooseKeyActivityResultListener
                    )
                }
                chooseKeyActivityResult.launch(intentSenderRequest)
            }
            OpenPgpApi.RESULT_CODE_SUCCESS -> {
                val ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)
                Log.d(TAG, "success key ids: ${ids?.map { OpenPgpUtils.convertKeyIdToHex(it) }}")
                onKeyChosen(true, ids?.toList() ?: listOf())
            }
        }
    }
}