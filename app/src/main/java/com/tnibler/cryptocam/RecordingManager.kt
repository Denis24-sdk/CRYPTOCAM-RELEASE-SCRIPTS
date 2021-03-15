package com.tnibler.cryptocam

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.EncodedBufferHandler
import androidx.camera.core.VideoStreamCapture
import com.tnibler.cryptocam.videoProcessing.VideoAudioMuxer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.Executor

class RecordingManager(
    val cameraSettings: CameraSettings,
    private val videoCapture: VideoStreamCapture,
    private val videoInfo: VideoAudioMuxer.VideoInfo,
    private val audioInfo: VideoAudioMuxer.AudioInfo,
    private val executor: Executor,
    private val coroutineScope: CoroutineScope,
    private val outputFileManager: OutputFileManager,
    private val videoPacketCallback: (() -> Unit)? = null
) {
    private val TAG = javaClass.simpleName
    var state: State = State.NOT_RECORDING
        private set
    private val muxer = VideoAudioMuxer()
    private val muxingThread = HandlerThread("MuxingThread").apply { start() }
    private val muxingHandler = Handler(muxingThread.looper)
    private var firstFrameTimestamp: Long = 0
    private var recordingStartMillis: Long = 0

    fun setUp() {
        Log.d(TAG, "setting up muxer")
        firstFrameTimestamp = 0L
        videoCapture.setEncodedBufferHandler(object : EncodedBufferHandler {
            override fun audioBufferReady(data: ByteArray, presentationTimeUs: Long) {
                if (firstFrameTimestamp == 0L) {
                    firstFrameTimestamp = presentationTimeUs
                }
                muxingHandler.post {
                    muxer.writeEncodedAudioBuffer(data, presentationTimeUs - firstFrameTimestamp)
                }
            }

            override fun videoBufferReady(data: ByteArray, presentationTimeUs: Long) {
                if (firstFrameTimestamp == 0L) {
                    firstFrameTimestamp = presentationTimeUs
                }
                muxingHandler.post {
                    muxer.writeEncodedVideoBuffer(data, presentationTimeUs - firstFrameTimestamp)
                    videoPacketCallback?.invoke()
                }
            }

            override fun recordingStopped() {
                onRecordingFinished()
            }
        })
    }

    private fun onRecordingFinished() {
        muxer.close()
        Log.d(TAG, "muxer closed")
        coroutineScope.launch {
            setUp()
        }
    }

    fun recordButtonClicked(): State {
        when (state) {
            State.NOT_RECORDING -> {
                val encryptedFile = outputFileManager.newFile()

                Log.d(TAG, "new file ready, setting up muxer")
                muxer.setup(encryptedFile.fd, encryptedFile.key, videoInfo, audioInfo)
                videoCapture.startRecording(executor, object : VideoStreamCapture.OnVideoSavedCallback {
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