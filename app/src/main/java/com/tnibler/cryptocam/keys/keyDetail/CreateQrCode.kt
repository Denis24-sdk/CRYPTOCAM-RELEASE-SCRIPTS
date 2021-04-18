package com.tnibler.cryptocam.keys.keyDetail

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.tnibler.cryptocam.keys.KeyManager
import java.net.URLEncoder

fun createQrCode(recipient: KeyManager.X25519Recipient, size: Int): Bitmap? {
    val encodedName = URLEncoder.encode(recipient.name, "UTF-8")
    val uri = "cryptocam://import_key?key_name=$encodedName&public_key=${recipient.publicKey}"
    val bitMatrix = try {
        QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
    }
    catch (e: WriterException) {
        Log.w("createQrCode", "Error creating QR code: $e")
        return null
    }


    val pixels = IntArray(bitMatrix.height * bitMatrix.width)
    for (y in 0 until bitMatrix.height) {
        for (x in 0 until bitMatrix.width) {
            pixels[y * bitMatrix.width + x] = when (bitMatrix.get(x, y)) {
                true -> 0xFFFFFFFF.toInt()
                false -> 0xFF000000.toInt()
            }
        }
    }
    val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, bitMatrix.width, bitMatrix.height)
    return bitmap
}