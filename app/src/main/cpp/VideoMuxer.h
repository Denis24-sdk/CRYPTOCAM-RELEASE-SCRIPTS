#ifndef CRYPTOCAM_VIDEOMUXER_H
#define CRYPTOCAM_VIDEOMUXER_H

#include "VideoInfo.h"

extern "C" {
#include <libavutil/avassert.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>
};

#include "CustomAVIOContext.h"
#include "AudioInfo.h"

class VideoMuxer {
    int fd{};
    AVOutputFormat *format{};
    AVFormatContext *outputContext{};
    AVDictionary *options = NULL;
    CustomAVIOContext customAvioContext;
    bool running;
    int frame;

    int videoStreamIndex;
    int audioStreamIndex;

    bool writtenVideo;
    bool writtenAudio;
    int firstVideoPts;
public:
    VideoMuxer();
    int init(int fd, VideoInfo info, AudioInfo audioInfo, const char key[16]);
    int writeVideoFrame(uint8_t* data, int size, int64_t presentationTimeUs);
    int writeAudioFrame(uint8_t* data, int size, int64_t presentationTimeUs);
    void close();
};


#endif //CRYPTOCAM_VIDEOMUXER_H
