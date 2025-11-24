package com.tnibler.system

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.tnibler.system.keys.KeyManager
import com.tnibler.system.preference.SettingsFragment
import com.tnibler.system.video.AudioInfo
import com.tnibler.system.video.VideoInfo
import cryptocam_age_encryption.Cryptocam_age_encryption
import cryptocam_age_encryption.EncryptedWriter
import org.json.JSONObject
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class OutputFileManager(
    var outputLocation: Uri,
    var recipients: Collection<KeyManager.X25519Recipient>,
    private val contentResolver: ContentResolver,
    private val sharedPreferences: SharedPreferences,
    private val context: Context
) {
    private val TAG = javaClass.simpleName
    private val BUSY_PREFIX = "busy_"

    data class VideoFileCreationResult(val videoFile: VideoFile, val documentFile: DocumentFile)

    fun newVideoFile(videoInfo: VideoInfo, audioInfo: AudioInfo, onLowMemory: (() -> Unit)? = null): VideoFileCreationResult {
        val out = DocumentFile.fromTreeUri(context, outputLocation)
            ?: throw RuntimeException("Error opening output directory")

        val finalFileName = nextFileName()
        val tempFileName = "$BUSY_PREFIX$finalFileName"

        val metadata = buildVideoMetadata(videoInfo, audioInfo)

        val outFile = out.createFile("application/binary", tempFileName)
            ?: throw RuntimeException("Error creating output file: $tempFileName")

        contentResolver.openOutputStream(outFile.uri)?.use { outStream ->
            writePlainTextHeader(outStream)
        } ?: throw RuntimeException("Error opening output file")

        val encryptedWriter = contentResolver.openFileDescriptor(outFile.uri, "wa", null)?.use { pfd ->
            val outFd = pfd.detachFd()
            val recipientsConcat = recipients.joinToString("\n") { it.publicKey }
            Cryptocam_age_encryption.createWriterWithX25519Recipients(
                outFd.toLong(),
                recipientsConcat
            )
        } ?: throw RuntimeException("Error opening file descriptor")

        val ef = EncryptedFile(encryptedWriter)
        writeHeader(ef, metadata.size, FileType.VIDEO)
        ef.write(metadata)

        return VideoFileCreationResult(VideoFile(ef, onLowMemory), outFile)
    }

    fun finalizeVideoFile(tempFile: DocumentFile, isCorrupted: Boolean = false) {
        val tempName = tempFile.name ?: return
        if (tempName.startsWith(BUSY_PREFIX)) {
            if (isCorrupted) {
                // Delete corrupted file instead of finalizing
                try {
                    if (tempFile.delete()) {
                        Log.w(TAG, "Deleted corrupted temporary file: $tempName")
                    } else {
                        Log.e(TAG, "Failed to delete corrupted temporary file: $tempName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting corrupted temporary file", e)
                }
            } else {
                val finalName = tempName.removePrefix(BUSY_PREFIX)
                try {
                    if (tempFile.renameTo(finalName)) {
                        Log.d(TAG, "File successfully renamed to $finalName")
                    } else {
                        Log.e(TAG, "Failed to rename file from $tempName to $finalName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error renaming file", e)
                }
            }
        }
    }


    fun newImageFile(): ImageFile {
        Log.d(TAG, "newImageFile()")
        val out = DocumentFile.fromTreeUri(context, outputLocation)
            ?: throw RuntimeException("Error opening output directory")
        val filename = nextFileName()
        val metadata = buildImageMetadata()

        val outFile = out.createFile("application/binary", filename)
            ?: throw RuntimeException("Error creating output file")

        contentResolver.openOutputStream(outFile.uri)?.use {
            writePlainTextHeader(it)
        } ?: throw RuntimeException("Error opening output stream for image")

        val encryptedWriter = contentResolver.openFileDescriptor(outFile.uri, "wa", null)?.use { pfd ->
            val outFd = pfd.detachFd()
            val recipientsConcat = recipients.joinToString("\n") { it.publicKey }
            Cryptocam_age_encryption.createWriterWithX25519Recipients(outFd.toLong(), recipientsConcat)
        } ?: throw RuntimeException("Error opening file descriptor for image")

        val ef = EncryptedFile(encryptedWriter)
        writeHeader(ef, metadata.size, FileType.IMAGE)
        ef.write(metadata)
        return ImageFile(ef)
    }

    private fun nextFileName(): String {
        val pattern = sharedPreferences.getString(SettingsFragment.PREF_OUTPUT_FILE_NAME, "cryptocam-\$\$num.age") ?: "cryptocam-\$\$num.age"
        val currentNum = sharedPreferences.getInt("fileNum", 0)
        val newNum = currentNum + 1
        sharedPreferences.edit {
            putInt("fileNum", newNum)
            commit()
        }
        val now = ZonedDateTime.now()
        val baseName = pattern
            .replace("\$year", now.year.toString())
            .replace("\$month", String.format(Locale.US, "%02d", now.month.value))
            .replace("\$day", String.format(Locale.US, "%02d", now.dayOfMonth))
            .replace("\$hour", String.format(Locale.US, "%02d", now.hour))
            .replace("\$min", String.format(Locale.US, "%02d", now.minute))
            .replace("\$sec", String.format(Locale.US, "%02d", now.second))
            .replace("\$uuid", UUID.randomUUID().toString())
            .replace("\$\$num", String.format(Locale.US, "%04d", newNum))

        return if (baseName.contains(".")) baseName else "$baseName.age"
    }

    private fun writePlainTextHeader(out: OutputStream) {
        val bb = ByteBuffer.allocate(4 + 2 + 1)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.put(byteArrayOfInts(0x1c, 0x5a, 0x8e, 0x9f))
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(1)
        if (recipients.size > 20) {
            throw IllegalStateException("more than 20 age recipients are not possible")
        }
        bb.put(recipients.size.toByte())
        out.write(bb.array())
        recipients.forEach { recipient ->
            out.write(recipient.fingerprint)
        }
    }

    private fun writeHeader(encryptedFile: EncryptedFile, metadataSize: Int, type: FileType) {
        val bb = ByteBuffer.allocate(1 + 4)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        val t: Byte = when (type) {
            FileType.VIDEO -> 1
            FileType.IMAGE -> 2
        }
        bb.put(t)
        val offsetToData: Int = bb.capacity() + metadataSize
        bb.putInt(offsetToData)
        encryptedFile.write(bb.array())
    }

    private fun buildVideoMetadata(videoInfo: VideoInfo, audioInfo: AudioInfo): ByteArray {
        val json = JSONObject()
        json.put("timestamp", dateTime())
        json.put("width", videoInfo.width)
        json.put("height", videoInfo.height)
        json.put("rotation", videoInfo.rotation)
        json.put("video_bitrate", videoInfo.bitrate)
        json.put("audio_sample_rate", audioInfo.sampleRate)
        json.put("audio_channel_count", audioInfo.channelCount)
        json.put("audio_bitrate", audioInfo.bitrate)

        // Rust ожидает "hevc" или "h265"
        json.put("codec", videoInfo.codec)

        return json.toString().toByteArray()
    }

    private fun buildImageMetadata(): ByteArray {
        val json = JSONObject()
        json.put("timestamp", dateTime())
        json.put("format", "jpg")
        return json.toString().toByteArray()
    }

    private fun byteArrayOfInts(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toTypedArray().toByteArray()

    private val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
    private fun dateTime() = dateTimeFormatter.format(LocalDateTime.now())

    private fun getAvailableStorageBytes(): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return stat.availableBytes
    }

    class EncryptedFile(private val encryptedWriter: EncryptedWriter) {
        fun write(buffer: ByteArray) {
            var written = 0L
            while (written < buffer.size) {
                val bytesWritten = encryptedWriter.write(buffer.sliceArray(written.toInt()..buffer.size - 1))
                if (bytesWritten <= 0) {
                    throw RuntimeException("Failed to write to encrypted file: disk full or I/O error")
                }
                written += bytesWritten
            }
        }

        fun close() {
            encryptedWriter.close()
        }
    }

    inner class VideoFile(private val encryptedFile: EncryptedFile, private val onLowMemory: (() -> Unit)? = null) {
        @Volatile private var isCorrupted = false
        @Volatile private var stoppedDueToLowMemory = false

        fun writeVideoBuffer(data: ByteArray, presentationTimeStampUs: Long) {
            if (stoppedDueToLowMemory) return
            if (getAvailableStorageBytes() < 100L * 1024 * 1024) {
                Log.w("VideoFile", "Low memory detected, stopping recording")
                stoppedDueToLowMemory = true
                onLowMemory?.invoke()
                return
            }
            try {
                val bb = ByteBuffer.allocate(1 + 8 + 4)
                bb.order(ByteOrder.LITTLE_ENDIAN)
                bb.put(1)
                bb.putLong(presentationTimeStampUs)
                bb.putInt(data.size)
                encryptedFile.write(bb.array())
                encryptedFile.write(data)
            } catch (e: Exception) {
                Log.e("VideoFile", "Error writing video buffer", e)
                isCorrupted = true
            }
        }

        fun writeAudioBuffer(data: ByteArray, presentationTimeStampUs: Long) {
            if (stoppedDueToLowMemory) return
            if (getAvailableStorageBytes() < 100L * 1024 * 1024) {
                Log.w("VideoFile", "Low memory detected, stopping recording")
                stoppedDueToLowMemory = true
                onLowMemory?.invoke()
                return
            }
            try {
                val bb = ByteBuffer.allocate(1 + 8 + 4)
                bb.order(ByteOrder.LITTLE_ENDIAN)
                bb.put(2)
                bb.putLong(presentationTimeStampUs)
                bb.putInt(data.size)
                encryptedFile.write(bb.array())
                encryptedFile.write(data)
            } catch (e: Exception) {
                Log.e("VideoFile", "Error writing audio buffer", e)
                isCorrupted = true
            }
        }

        fun close() {
            try {
                encryptedFile.close()
            } catch (e: Exception) {
                Log.e("VideoFile", "Error closing encrypted file", e)
                isCorrupted = true
            }
        }

        fun isCorrupted(): Boolean = isCorrupted
    }

    class ImageFile(private val encryptedFile: EncryptedFile) : OutputStream() {
        override fun write(b: Int) {
            encryptedFile.write(byteArrayOf(b.toByte()))
        }
        override fun write(b: ByteArray?) { if (b != null) { encryptedFile.write(b) } }
        override fun close() { encryptedFile.close() }
    }

    enum class FileType { IMAGE, VIDEO }
}
