package com.tnibler.cryptocam.videoProcessing

import android.util.Log
import java.nio.charset.Charset

class VideoAudioMuxer() {
    init {
        listOf("avformat", "video-muxer")
            .forEach { System.loadLibrary(it) }
    }

    fun setup(fd: Int, key: String, videoInfo: VideoInfo, audioInfo: AudioInfo) {
        Log.d("CryptoCam", "Initializing muxer with videoInfo $videoInfo and audioInfo $audioInfo")
        nativeInit(
            fd = fd,
            width = videoInfo.width,
            height = videoInfo.height,
            bitrate = videoInfo.bitrate,
            framerate = videoInfo.framerate,
            rotation = videoInfo.rotation,
            audioBitrate = audioInfo.bitrate,
            audioChannelCount = audioInfo.channelCount,
            audioSampleRate = audioInfo.sampleRate,
            key = key)
    }

    fun writeEncodedVideoBuffer(buffer: ByteArray, presentationTimeUs: Long) {
        nativeWriteEncodedVideoBuffer(buffer, buffer.size, presentationTimeUs)
    }

    fun writeEncodedAudioBuffer(buffer: ByteArray, presentationTimeUs: Long) {
        nativeWriteEncodedAudioBuffer(buffer, buffer.size, presentationTimeUs)
    }

    fun close() {
        nativeClose()
    }

    private external fun nativeWriteEncodedVideoBuffer(buffer: ByteArray, size: Int, presentationTimeUs: Long)

    private external fun nativeWriteEncodedAudioBuffer(buffer: ByteArray, size: Int, presentationTimeUs: Long)

    private external fun nativeClose()

    private external fun nativeInit(fd: Int,
                                    width: Int, height: Int, bitrate: Int, framerate: Int, rotation: Int,
                                    audioChannelCount: Int, audioSampleRate: Int, audioBitrate: Int, key: String)

    data class VideoInfo (
        val width: Int,
        val height: Int,
        val bitrate: Int,
        val framerate: Int,
        val rotation: Int
    )

    data class AudioInfo (
        val bitrate: Int,
        val channelCount: Int,
        val sampleRate: Int
    )

}