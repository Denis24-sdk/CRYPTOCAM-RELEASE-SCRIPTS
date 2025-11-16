package com.tnibler.cryptocam.video

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.EncodedBufferHandler
import androidx.camera.core.VideoStreamCapture
import androidx.documentfile.provider.DocumentFile
import com.tnibler.cryptocam.OutputFileManager
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
    // [НОВОЕ] Храним ссылку на созданный DocumentFile
    private var currentDocumentFile: DocumentFile? = null

    fun setUp() {
        Log.d(TAG, "setting up muxer")
        videoCapture.setEncodedBufferHandler(object : EncodedBufferHandler {
            override fun audioBufferReady(data: ByteArray, presentationTimeUs: Long) {
                encryptingHandler.post { videoFile?.writeAudioBuffer(data, presentationTimeUs) }
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
        // [ИЗМЕНЕНО] Логика финализации файла
        videoFile?.close()
        Log.d(TAG, "muxer closed")

        // [НОВОЕ] Переименовываем файл ПОСЛЕ его закрытия
        currentDocumentFile?.let {
            outputFileManager.finalizeVideoFile(it)
            currentDocumentFile = null // Очищаем ссылку
        }

        recordingStoppedCallback()
        coroutineScope.launch {
            setUp()
        }
    }

    fun recordButtonClicked(): State {
        when (state) {
            State.NOT_RECORDING -> {
                // [ИЗМЕНЕНО] Получаем результат создания файла
                val creationResult = outputFileManager.newVideoFile(videoInfo, audioInfo)
                this.videoFile = creationResult.videoFile
                this.currentDocumentFile = creationResult.documentFile // Сохраняем DocumentFile

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

// Убедитесь, что эти data классы определены (здесь или в другом файле)
data class VideoInfo(val width: Int, val height: Int, val rotation: Int, val bitrate: Int)
data class AudioInfo(val channelCount: Int, val bitrate: Int, val sampleRate: Int)