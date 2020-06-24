#include <jni.h>
#include "VideoInfo.h"
#include "VideoMuxer.h"
#include "log.h"
#include "AudioInfo.h"
#include <jni.h>
#include <jni.h>

static VideoMuxer videoMuxer;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tnibler_cryptocam_videoProcessing_VideoAudioMuxer_nativeInit(JNIEnv* env, jobject thiz,
                                                                      jint fd, jint width, jint height,
                                                                      jint bitrate, jint framerate, jint rotation, jint audioChannelCount,
                                                                      jint audioSampleRate, jint audioBitrate,
                                                                      jstring keyString) {
//    LOGD("JNI call: nativeInit");
    VideoInfo videoInfo {};
    videoInfo.width = width;
    videoInfo.height = height;
    videoInfo.bitrate = bitrate;
    videoInfo.framerate = framerate;
    videoInfo.rotation = rotation;

    AudioInfo audioInfo {};
    audioInfo.bitrate = audioBitrate;
    audioInfo.channelCount = audioChannelCount;
    audioInfo.sampleRate = audioSampleRate;
    const char* chars = env->GetStringUTFChars(keyString, nullptr);
    videoMuxer.init(fd, videoInfo, audioInfo, chars);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tnibler_cryptocam_videoProcessing_VideoAudioMuxer_nativeClose(JNIEnv
* env,
                                                                       jobject thiz
) {
//    LOGD("JNI call: nativeClose");
    videoMuxer.close();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tnibler_cryptocam_videoProcessing_VideoAudioMuxer_nativeWriteEncodedVideoBuffer(JNIEnv *env,
                                                                                         jobject thiz, jbyteArray
buffer, jint size, jlong presentationTimeUs) {
//    LOGD("JNI call: nativeWriteEncodedVideoBuffer %lld", presentationTimeUs);
    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    videoMuxer.writeVideoFrame((uint8_t*)data, size, presentationTimeUs);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_tnibler_cryptocam_videoProcessing_VideoAudioMuxer_nativeWriteEncodedAudioBuffer(JNIEnv *env,
                                                                                         jobject thiz, jbyteArray buffer,
                                                                                         jint size, jlong presentationTimeUs) {
    jbyte* data = env->GetByteArrayElements(buffer, nullptr);
    videoMuxer.writeAudioFrame((uint8_t*)data, size, presentationTimeUs);
}