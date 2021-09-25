package com.tnibler.cryptocam.util

import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

class ByteBufferInputStream(private val buffer: ByteBuffer) : InputStream() {
    override fun read(): Int =
        if (!buffer.hasRemaining()) -1
        else buffer.get().toInt()

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (b == null) {
            return 0
        }
        return if (!buffer.hasRemaining()) {
            -1
        }
        else {
            val l = min(len, buffer.remaining())
            buffer.get(b, off, l)
            l
        }
    }
}