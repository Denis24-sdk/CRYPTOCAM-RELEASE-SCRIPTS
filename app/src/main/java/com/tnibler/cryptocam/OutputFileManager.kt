package com.tnibler.cryptocam

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.tnibler.cryptocam.keys.KeyManager
import cryptocam_age_encryption.Cryptocam_age_encryption
import cryptocam_age_encryption.EncryptedWriter
import org.json.JSONObject
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class OutputFileManager(
    var outputLocation: Uri,
    var recipients: Collection<KeyManager.X25519Recipient>,
    private val contentResolver: ContentResolver,
    private val context: Context
) {
    private val TAG = javaClass.simpleName

    fun newVideoFile(videoInfo: VideoInfo, audioInfo: AudioInfo): VideoFile {
        val out = DocumentFile.fromTreeUri(context, outputLocation)
            ?: throw RuntimeException("Error opening output directory")
        val filename = randomFilename()

        val metadata = buildVideoMetadata(videoInfo, audioInfo)

        val outFile = out.createFile("application/binary", "$filename.cryptocam.age")
            ?: throw RuntimeException("Error creating output file")
        val outStream = contentResolver.openOutputStream(outFile.uri)
            ?: throw RuntimeException("Error opening output file")
        writePlainTextHeader(outStream)
        outStream.close()

        val outFd = contentResolver.openFileDescriptor(outFile.uri, "wa", null)?.detachFd()
            ?: throw RuntimeException("Error opening file descriptor")
        val recipientsConcat = recipients.joinToString("\n") { it.publicKey }
        val encryptedWriter = Cryptocam_age_encryption.createWriterWithX25519Recipients(
            outFd.toLong(),
            recipientsConcat
        )
        val ef = EncryptedFile(encryptedWriter)
        writeHeader(ef, metadata.size, FileType.VIDEO)
        ef.write(metadata)
        return VideoFile(ef)
    }

    fun newImageFile(): ImageFile {
        Log.d(TAG, "newImageFile()")
        val out = DocumentFile.fromTreeUri(context, outputLocation)
            ?: throw RuntimeException("Error opening output directory")
        val filename = randomFilename()
        val metadata = buildImageMetadata()

        val outFile = out.createFile("application/binary", "$filename.cryptocam.age")
            ?: throw RuntimeException("Error creating output file")
        val outStream = contentResolver.openOutputStream(outFile.uri)
            ?: throw RuntimeException("Error opening output file")
        writePlainTextHeader(outStream)
        Log.d(TAG, "Wrote plaintext header")
        outStream.close()

        val outFd = contentResolver.openFileDescriptor(outFile.uri, "wa", null)?.detachFd()
            ?: throw RuntimeException("Error opening file descriptor")
        val recipientsConcat = recipients.joinToString("\n") { it.publicKey }
        val encryptedWriter = Cryptocam_age_encryption.createWriterWithX25519Recipients(
            outFd.toLong(),
            recipientsConcat
        )
        Log.d(TAG, "Created encrypted writer")
        val ef = EncryptedFile(encryptedWriter)
        writeHeader(ef, metadata.size, FileType.IMAGE)
        ef.write(metadata)
        Log.d(TAG, "Wrote metadata")
        return ImageFile(ef)
    }

    private fun writePlainTextHeader(out: OutputStream) {
        val bb = ByteBuffer.allocate(4 + 2 + 1)

        bb.order(ByteOrder.BIG_ENDIAN)
        // magic number
        bb.put(byteArrayOfInts(0x1c, 0x5a, 0x8e, 0x9f))
        bb.order(ByteOrder.LITTLE_ENDIAN)
        // version
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
        // file type: only video for now
        val t: Byte = when (type) {
            FileType.VIDEO -> 1
            FileType.IMAGE -> 2
        }
        bb.put(t)
        // offset to data
        val offsetToData: Int = bb.capacity() + metadataSize
        // this is a signed int but we're just going to assume we're not writing 2GB of metadata
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

    private fun randomFilename(): String = UUID.randomUUID().toString()

    class EncryptedFile(private val encryptedWriter: EncryptedWriter) {
        fun write(buffer: ByteArray) {
            var written = 0L
            while (written < buffer.size) {
                written += encryptedWriter.write(buffer)
            }
        }

        fun close() {
            encryptedWriter.close()
        }
    }

    class VideoFile(private val encryptedFile: EncryptedFile) {

        fun writeVideoBuffer(data: ByteArray, presentationTimeStampUs: Long) {
            val bb = ByteBuffer.allocate(1 + 8 + 4)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.put(1)
            bb.putLong(presentationTimeStampUs)
            bb.putInt(data.size)
            encryptedFile.write(bb.array())
            encryptedFile.write(data)
        }


        fun writeAudioBuffer(data: ByteArray, presentationTimeStampUs: Long) {
            val bb = ByteBuffer.allocate(1 + 8 + 4)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.put(2)
            bb.putLong(presentationTimeStampUs)
            bb.putInt(data.size)
            encryptedFile.write(bb.array())
            encryptedFile.write(data)
        }

        fun close() {
            encryptedFile.close()
        }
    }

    class ImageFile(private val encryptedFile: EncryptedFile) : OutputStream() {
        override fun write(b: Int) {
            encryptedFile.write(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray?) {
            if (b != null) {
                encryptedFile.write(b)
            }
        }

        override fun close() {
            encryptedFile.close()
        }
    }

    enum class FileType {
        IMAGE, VIDEO
    }
}