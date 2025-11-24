package com.tnibler.system.video

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.EncodedBufferHandler
import androidx.camera.core.VideoStreamCapture
import androidx.documentfile.provider.DocumentFile
import com.tnibler.system.OutputFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.Executor

class RecordingManager(
    private val videoCapture: VideoStreamCapture,
    private val videoInfo: VideoInfo,
    private val audioInfo: AudioInfo,
    private val executor: Executor,
    private val coroutineScope: CoroutineScope,
    private val outputFileManager: OutputFileManager,
    private val recordingStoppedCallback: (() -> Unit),
    private val onRecordingStarted: () -> Unit = {},
    private val onRecordingStopped: () -> Unit = {},
) {
    private val TAG = javaClass.simpleName
    var state: State = State.NOT_RECORDING
        private set
    private val encryptingThread = HandlerThread("EncryptingThread").apply { start() }
    private val encryptingHandler = Handler(encryptingThread.looper)
    private var recordingStartMillis: Long = 0
    private var videoFile: OutputFileManager.VideoFile? = null
    private var currentDocumentFile: DocumentFile? = null

    private fun getSampleRateIndex(sampleRate: Int): Int {
        return when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4
        }
    }

    private fun addAdtsHeader(packet: ByteArray, packetLen: Int, sampleRate: Int, channelCount: Int): ByteArray {
        val header = ByteArray(7)
        val profile = 2
        val freqIdx = getSampleRateIndex(sampleRate)
        val chanCfg = channelCount
        val fullLen = packetLen + 7

        header[0] = 0xFF.toByte()
        header[1] = 0xF9.toByte()
        header[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        header[3] = (((chanCfg and 3) shl 6) + (fullLen shr 11)).toByte()
        header[4] = ((fullLen and 0x7FF) shr 3).toByte()
        header[5] = (((fullLen and 7) shl 5) + 0x1F).toByte()
        header[6] = 0xFC.toByte()

        val result = ByteArray(fullLen)
        System.arraycopy(header, 0, result, 0, 7)
        System.arraycopy(packet, 0, result, 7, packet.size)

        return result
    }

    fun setUp() {
        Log.d(TAG, "setting up muxer")
        videoCapture.setEncodedBufferHandler(object : EncodedBufferHandler {
            override fun audioBufferReady(data: ByteArray, presentationTimeUs: Long) {
                encryptingHandler.post {
                    val adtsData = addAdtsHeader(
                        packet = data,
                        packetLen = data.size,
                        sampleRate = audioInfo.sampleRate,
                        channelCount = audioInfo.channelCount
                    )
                    videoFile?.writeAudioBuffer(adtsData, presentationTimeUs)
                }
            }

            override fun videoBufferReady(data: ByteArray, presentationTimeUs: Long) {
                encryptingHandler.post { videoFile?.writeVideoBuffer(data, presentationTimeUs) }
            }

            override fun recordingStopped() {
                onRecordingFinished()
            }
        })
    }

    private fun onRecordingFinished() {
        val isCorrupted = videoFile?.isCorrupted() ?: false
        videoFile?.close()
        Log.d(TAG, "muxer closed, corrupted: $isCorrupted")

        currentDocumentFile?.let {
            outputFileManager.finalizeVideoFile(it, isCorrupted)
            currentDocumentFile = null
        }

        recordingStoppedCallback()
        coroutineScope.launch {
            setUp()
        }
    }

    fun recordButtonClicked(): State {
        when (state) {
            State.NOT_RECORDING -> {
                val creationResult = outputFileManager.newVideoFile(videoInfo, audioInfo) {
                    // onLowMemory callback
                    Log.d(TAG, "Stopping recording due to low memory")
                    videoCapture.stopRecording()
                }
                this.videoFile = creationResult.videoFile
                this.currentDocumentFile = creationResult.documentFile

                Log.d(TAG, "new file ready, setting up muxer")
                videoCapture.startRecording(
                    executor,
                    object : VideoStreamCapture.OnVideoSavedCallback {
                        override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                            Log.e(TAG, "VideoStreamCapture.onError: $videoCaptureError $message", cause)
                            onRecordingFinished()
                            state = State.NOT_RECORDING
                            onRecordingStopped()
                        }
                    })
                recordingStartMillis = System.currentTimeMillis()
                state = State.RECORDING
                onRecordingStarted()
            }
            State.RECORDING -> {
                Log.d(TAG, "stopping recording")
                videoCapture.stopRecording()
                state = State.NOT_RECORDING
                onRecordingStopped()
            }
        }
        return state
    }

    val recordingTime get() = Duration.ofMillis(System.currentTimeMillis() - recordingStartMillis)

    enum class State {
        RECORDING,
        NOT_RECORDING
    }
}

data class VideoInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val bitrate: Int,
    val codec: String
)

data class AudioInfo(
    val channelCount: Int,
    val bitrate: Int,
    val sampleRate: Int
)
