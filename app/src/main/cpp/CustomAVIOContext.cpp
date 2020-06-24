#include "CustomAVIOContext.h"
#include "log.h"

/* https://stackoverflow.com/a/60702386 */

CustomAVIOContext::CustomAVIOContext() {
}

CustomAVIOContext::~CustomAVIOContext() {
    close();
}

void CustomAVIOContext::close() {
    if (file != nullptr) {
        fclose(file);
        file = nullptr;
    }
    file = nullptr;
    avio_flush(avioCtx);
    if (avioCtx != nullptr) { av_free(avioCtx); }
}

bool CustomAVIOContext::openFromDescriptor(int fd, const char *mode) {
    const int bufferSize = 32768;
    unsigned char *bufferIO = (unsigned char *) av_malloc(
            bufferSize + AV_INPUT_BUFFER_PADDING_SIZE);
    avioCtx = avio_alloc_context(bufferIO, bufferSize, 1, (void*)this, &readCallback,
                                 &writeCallback, &seekCallback);
    file = fdopen(fd, mode);
    return file != nullptr;
}

AVIOContext *CustomAVIOContext::getAVIOContext() const {
    return avioCtx;
}

int CustomAVIOContext::read(uint8_t *buf, int bufSize) {
    if (file == nullptr) { return -1; }

    int aNbRead = (int) fread(buf, 1, bufSize, file);
    if (aNbRead == 0 && feof(file) != 0) { return AVERROR_EOF; }
    return aNbRead;
}

int CustomAVIOContext::write(uint8_t *buf, int bufSize) {
    if (file == nullptr) { return -1; }
    int r = (int) fwrite(buf, 1, bufSize, file);
//    fflush(file);
    return r;
}

int64_t CustomAVIOContext::seek(int64_t offset, int whence) {
    if (whence == AVSEEK_SIZE || file == NULL) { return -1; }
    bool isOk = fseeko(file, offset, whence) == 0;
    if (!isOk) { return -1; }
    return ::ftello(file);
}

int CustomAVIOContext::readCallback(void *opaque, uint8_t *buf, int bufSize) {
    return opaque != nullptr
           ? ((CustomAVIOContext * )opaque)->read(buf, bufSize)
           : 0;
}

int CustomAVIOContext::writeCallback(void *opaque,
                         uint8_t *buf,
                         int bufSize) {
    return opaque != nullptr
           ? ((CustomAVIOContext * )opaque)->write(buf, bufSize)
           : 0;
}

int64_t CustomAVIOContext::seekCallback(void *opaque, int64_t offset, int whence) {
    return opaque != nullptr
           ? ((CustomAVIOContext * )opaque)->seek(offset, whence)
           : -1;
}
