package utils

import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat

fun encodeAvatarToByteArray(avatarPath: String, maxSize: Int = 8192): ByteArray {
    val bytes = java.io.File(avatarPath).readBytes()
    val skiaImage = Image.makeFromEncoded(bytes)

    // Resize: dùng makeImageSnapshot vì Skia JVM không có withDimensions/ext!
    val targetW = 128
    val targetH = 128
    val resized = skiaImage.resize(targetW, targetH)
    // JPEG encode, quality 80
    val jpegBytes = resized.encodeToData(EncodedImageFormat.JPEG, 64)!!.bytes
    require(jpegBytes.size <= maxSize) { "Ảnh quá lớn, vui lòng chọn ảnh nhỏ hoặc thấp chất lượng hơn." }
    return jpegBytes
}

// Extension resize cho org.jetbrains.skia.Image
fun Image.resize(width: Int, height: Int): Image {
    val scaled = this.scalePixels(width, height)
    return scaled
}

// Thêm extension cho scale:
fun Image.scalePixels(newWidth: Int, newHeight: Int): Image {
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(newWidth, newHeight)
    val canvas = surface.canvas
    val paint = org.jetbrains.skia.Paint()
    canvas.drawImageRect(
        this,
        org.jetbrains.skia.Rect.makeXYWH(0f, 0f, this.width.toFloat(), this.height.toFloat()),
        org.jetbrains.skia.Rect.makeXYWH(0f, 0f, newWidth.toFloat(), newHeight.toFloat()),
        paint
    )
    return surface.makeImageSnapshot()
}