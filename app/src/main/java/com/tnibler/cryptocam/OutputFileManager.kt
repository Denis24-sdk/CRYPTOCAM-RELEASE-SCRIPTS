package com.tnibler.cryptocam

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.util.JsonWriter
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContentResolverCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.lang.StringBuilder
import java.math.BigInteger
import java.nio.charset.Charset
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.crypto.KeyGenerator
import kotlin.random.Random

// TODO only create new file if previous was actually used
class OutputFileManager(private val openPgpManager: OpenPgpManager, var outputLocation: Uri, var keyIds: Collection<Long>, private val contentResolver: ContentResolver, private val context: Context) {
    private val TAG = javaClass.simpleName
    private var currentFile: EncryptedVideoFile? = null
    private var currentFileName: String? = null
    /**
     * Creates new file to record video into, along with an AES-128 key used to encrypt the media streams.
     */
    fun newFile(): EncryptedVideoFile {
        val out = DocumentFile.fromTreeUri(context, outputLocation) ?: throw RuntimeException("Error opening output directory")
        val filename = randomFilename()
        val keyFile = out.createFile("application/text", "$filename.pgp") ?: throw RuntimeException("Error creating keyfile")
        val keyOut = contentResolver.openOutputStream(keyFile.uri) ?: throw RuntimeException("Error opening keyfile for writing")
        val key = keyGen.generateKey()
        val json = JSONObject()
        val hexKey = String.format("%032X", BigInteger(+1, key.encoded))
        json.put("timestamp", dateTime())
        json.put("encryptionKey",  hexKey)

        openPgpManager.encryptText(json.toString(), keyOut, keyIds)

        val outFile = out.createFile("video/mp4", "$filename.mp4") ?: throw RuntimeException("Error creating output file")
        val outFd = contentResolver.openFileDescriptor(outFile.uri, "rwt", null)?.detachFd() ?: throw RuntimeException("Error opening file descriptor")
//        val outFile = context.externalMediaDirs.first().toPath().resolve("$filename.mp4").toFile()
        val file = EncryptedVideoFile(hexKey, outFd)
        currentFile = file
        currentFileName = filename
        Log.d(TAG, "new file: $filename")
//        emit(file)
        return file
    }

    fun fileUsed() {
        Log.d(TAG, "used: $currentFileName")
        currentFile = null
        currentFileName = null
    }

    fun fileNotUsed() {
//        val out = DocumentFile.fromTreeUri(context, outputLocation) ?: throw RuntimeException("Error opening output directory")
//        val cfn = currentFileName ?: return
//        Log.d(TAG, "deleting: $cfn")
//        out.listFiles().filter { f ->
//            f.name?.startsWith(cfn) ?: false
//        }.forEach { f ->
//            f.delete()
//        }
    }

    private val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
    private fun dateTime() = dateTimeFormatter.format(LocalDateTime.now())

    private val random = Random(System.currentTimeMillis())
    private fun randomFilename(): String = UUID.randomUUID().toString()
    private val keyGen = KeyGenerator.getInstance("AES")

    data class EncryptedVideoFile(val key: String, val fd: Int)
}