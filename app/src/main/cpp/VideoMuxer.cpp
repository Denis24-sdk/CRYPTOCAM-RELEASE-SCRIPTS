//
// Created by thomas on 5/31/20.
//


#include "CustomAVIOContext.h"
#include "VideoMuxer.h"
#include "VideoInfo.h"
#include "log.h"
#include "AudioInfo.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
};

int VideoMuxer::init(int fd, VideoInfo videoInfo, AudioInfo audioInfo, const char key[16]) {
    LOGD("VideoMuxer::init");
    if (running) {
        LOGE("Init: Muxer already running");
        return 1;
    }
    if (!customAvioContext.openFromDescriptor(fd, "rw+b")) {
        LOGE("Error opening AVIO context from file descriptor.");
        return 1;
    }
    LOGD("Opened AVIO context");
    AVOutputFormat *format = av_guess_format("mp4", nullptr, nullptr);
    format->flags = AVFMT_NOTIMESTAMPS;
    avformat_alloc_output_context2(&outputContext, format, nullptr, nullptr);
    if (!outputContext) {
        LOGE("Error allocating output format context.");
        return 1;
    }
    outputContext->pb = customAvioContext.getAVIOContext();
    outputContext->oformat = format;
    outputContext->oformat->flags |= AVFMT_SEEK_TO_PTS;


    /* Set up video stream */
    AVStream *videoStream = avformat_new_stream(outputContext, nullptr);
    videoStreamIndex = videoStream->index;

    videoStream->codecpar->codec_type = AVMEDIA_TYPE_VIDEO;
    videoStream->codecpar->codec_id = AV_CODEC_ID_H264;
//    codecContext->bit_rate = videoInfo.bitrate;
    LOGD("bitrate: %lld, resolution: %dx%d", videoInfo.bitrate, videoInfo.width, videoInfo.height);
    videoStream->codecpar->bit_rate = videoInfo.bitrate;
    videoStream->codecpar->width = videoInfo.width;
    videoStream->codecpar->height = videoInfo.height;
    videoStream->time_base.num = 1;
    videoStream->time_base.den = 90000;
    LOGD("setting rotation to %d, time base den to %d", videoInfo.rotation, videoStream->time_base.den);
    av_dict_set_int(&videoStream->metadata, "rotate", (videoInfo.rotation + 360) % 360, 0);

    /* Set up audio stream */
    AVStream* audioStream = avformat_new_stream(outputContext, nullptr);
    LOGD("Created new stream");
    audioStreamIndex = audioStream->index;
    audioStream->codecpar->codec_type = AVMEDIA_TYPE_AUDIO;
    audioStream->codecpar->codec_id = AV_CODEC_ID_MP4ALS;
    audioStream->codecpar->bit_rate = audioInfo.bitrate;
    audioStream->codecpar->sample_rate = audioInfo.sampleRate;
    audioStream->codecpar->channels = audioInfo.channelCount;
    audioStream->time_base.num = 1;
    audioStream->time_base.den = 90000;

    AVDictionary *opts = nullptr;
    LOGD("Setting options");
    av_dict_set(&opts, "encryption_scheme", "cenc-aes-ctr", 0);
    av_dict_set(&opts, "encryption_key", key, 0);
    av_dict_set(&opts, "encryption_kid", "a7e61c373e219033c21091fa607bf3b8", 0);
    LOGD("Writing header");
    if (avformat_write_header(outputContext, &opts) < 0) {
        LOGE("Error writing header.");
        return 1;
    }
    writtenAudio = writtenVideo = false;
    running = true;
    LOGD("Successfully initialized muxer.");
    return 0;
}

int VideoMuxer::writeVideoFrame(uint8_t *data, int size, int64_t presentationTimeUs) {
//    LOGD("Start writing frame, presentationTimeUs: %lld", presentationTimeUs);
    if (videoStreamIndex < 0 || !running) {
        LOGD("Not writing frame");
        return 1;
    }
    bool isIframe = false;
    if (data[4] == 0x65) {
        isIframe = true;
    }

    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = data;
    pkt.size = size;
    pkt.pts = presentationTimeUs;
    if (writtenVideo) {
        pkt.pts -= firstVideoPts;
    }
    av_packet_rescale_ts(&pkt, AVRational{1, 1000000},
                         outputContext->streams[videoStreamIndex]->time_base);
    pkt.dts = AV_NOPTS_VALUE;
    pkt.stream_index = videoStreamIndex;
    if (!writtenVideo && isIframe) {
        firstVideoPts = presentationTimeUs;
    }
    if (av_interleaved_write_frame(outputContext, &pkt) < 0) {
        LOGE("Error writing frame");
        return 1;
    }
    if (!writtenVideo && isIframe) {
        writtenVideo = true;
    }
    return 0;
}

int VideoMuxer::writeAudioFrame(uint8_t *data, int size, int64_t presentationTimeUs) {
    if (!writtenVideo) {
        return 0;
    }
    if (audioStreamIndex < 0 || !running) {
        LOGD("Not writing audio frame");
        return 1;
    }
    AVPacket pkt;
    av_init_packet(&pkt);
    pkt.data = data;
    pkt.size = size;
    pkt.stream_index = audioStreamIndex;
    pkt.pts = presentationTimeUs;
    if (writtenAudio) {
        pkt.pts -= firstAudioPts;
    }
    av_packet_rescale_ts(&pkt, AVRational{1, 1000000},
                         outputContext->streams[audioStreamIndex]->time_base);
    pkt.flags |= AV_PKT_FLAG_KEY;
    pkt.dts = AV_NOPTS_VALUE;
    if (!writtenAudio) {
        firstAudioPts = presentationTimeUs;
    }
    if (av_interleaved_write_frame(outputContext, &pkt) < 0) {
        LOGE("Error writing audio frame");
        return 1;
    }
    writtenAudio = true;
    return 0;
}

void VideoMuxer::close() {
    LOGD("VideoMuxer::Close");
    if (!running) {
        return;
    }
    LOGD("Writing trailer");
    av_write_trailer(outputContext);
    LOGD("Freeing AVIO context");
    customAvioContext.close();
    LOGD("Freeing output context");
    avformat_free_context(outputContext);
    running = false;
    LOGD("Closed muxer");
}

VideoMuxer::VideoMuxer() {
    running = false;
    frame = 0;
    videoStreamIndex = -1;
    audioStreamIndex = -1;
}
