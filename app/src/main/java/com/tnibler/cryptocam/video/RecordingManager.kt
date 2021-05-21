package com.tnibler.cryptocam.video

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.EncodedBufferHandler
import androidx.camera.core.VideoStreamCapture
import com.tnibler.cryptocam.CameraSettings
import com.tnibler.cryptocam.OutputFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.Executor

class RecordingManager(
    val cameraSettings: CameraSettings,
    private val videoCapture: VideoStreamCapture,
    private val videoInfo: VideoInfo,
    private val audioInfo: AudioInfo,
    private val executor: Executor,
    private val coroutineScope: CoroutineScope,
    private val outputFileManager: OutputFileManager,
    private val recordingStoppedCallback: (() -> Unit),
    private val videoPacketCallback: (() -> Unit)? = null
) {
    private val TAG = javaClass.simpleName
    var state: State = State.NOT_RECORDING
        private set
    private val encryptingThread = HandlerThread("EncryptingThread").apply { start() }
    private val encryptingHandler = Handler(encryptingThread.looper)
    private var recordingStartMillis: Long = 0
    private var videoFile: OutputFileManager.VideoFile? = null

    fun setUp() {
        Log.d(TAG, "setting up muxer")
        videoCapture.setEncodedBufferHandler(object : EncodedBufferHandler {
            override fun audioBufferReady(data: ByteArray, presentationTimeUs: Long) {
                encryptingHandler.post {
                    videoFile?.writeAudioBuffer(data, presentationTimeUs)
                }
            }

            override fun videoBufferReady(data: ByteArray, presentationTimeUs: Long) {
                encryptingHandler.post {
                    videoFile?.writeVideoBuffer(data, presentationTimeUs)
                    videoPacketCallback?.invoke()
                }
            }

            override fun recordingStopped() {
                onRecordingFinished()
            }
        })
    }

    private fun onRecordingFinished() {
        videoFile?.close()
        Log.d(TAG, "muxer closed")
        recordingStoppedCallback()
        coroutineScope.launch {
            setUp()
        }
    }

    fun recordButtonClicked(): State {
        when (state) {
            State.NOT_RECORDING -> {
                this.videoFile = outputFileManager.newVideoFile(videoInfo, audioInfo)
                Log.d(TAG, "new file ready, setting up muxer")
                videoCapture.startRecording(
                    executor,
                    object : VideoStreamCapture.OnVideoSavedCallback {
                        override fun onError(p0: Int, p1: String, p2: Throwable?) {
                        }
                    })
                recordingStartMillis = System.currentTimeMillis()
                state = State.RECORDING
            }
            State.RECORDING -> {
                Log.d(TAG, "stopping recording")
                videoCapture.stopRecording()
                state = State.NOT_RECORDING
            }
        }
        return state
    }

    val recordingTime
        get() = Duration.ofMillis(System.currentTimeMillis() - recordingStartMillis)

    enum class State {
        RECORDING,
        NOT_RECORDING
    }
}

data class VideoInfo(val width: Int, val height: Int, val rotation: Int, val bitrate: Int)

data class AudioInfo(val channelCount: Int, val bitrate: Int, val sampleRate: Int)