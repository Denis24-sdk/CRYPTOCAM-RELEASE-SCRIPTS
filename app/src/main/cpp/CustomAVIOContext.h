#ifndef CRYPTOCAM_CUSTOMAVIOCONTEXT_H
#define CRYPTOCAM_CUSTOMAVIOCONTEXT_H


#include <stdint.h>
#include <stdio.h>
extern "C" {
#include <libavformat/avformat.h>
#include <libavformat/avio.h>
};

class CustomAVIOContext {

    static int readCallback(void *opaque, uint8_t *buf, int bufSize);
    static int writeCallback(void *opaque, uint8_t *buf, int bufSize);
    static int64_t seekCallback(void *opaque, int64_t offset, int whence);

public:
    virtual int read(uint8_t *buf, int bufSize);
    virtual int write(uint8_t *buf, int bufSize);
    virtual int64_t seek(int64_t offset, int whence);
    AVIOContext *getAVIOContext() const;
    bool openFromDescriptor(int fd, const char *mode);
    CustomAVIOContext();
    virtual ~CustomAVIOContext();
    void close();

protected:
    AVIOContext *avioCtx;
    FILE *file;
};


#endif //CRYPTOCAM_CUSTOMAVIOCONTEXT_H
