package com.orionstar.openclaw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Minimal still capture using legacy Camera1 API.
 * Works on many RobotOS devices and is quickest to integrate for a one-shot photo test.
 */
object Camera1Capture {
  private const val tag = "Camera1Capture"

  data class PhotoResult(
    val jpegBytes: ByteArray,
    val base64: String,
    val width: Int,
    val height: Int
  )

  fun takePhoto(
    maxWidth: Int = 640,
    jpegQuality: Int = 80,
    onDone: (ok: Boolean, result: PhotoResult?, error: String?) -> Unit
  ) {
    var cam: Camera? = null
    try {
      // Try to open the first available camera.
      cam = Camera.open()

      val params = cam.parameters
      // Best-effort: choose a small preview size to reduce overhead.
      try {
        val sizes = params.supportedPreviewSizes
        if (!sizes.isNullOrEmpty()) {
          val chosen = sizes.minByOrNull { it.width * it.height }
          if (chosen != null) {
            params.setPreviewSize(chosen.width, chosen.height)
          }
        }
      } catch (t: Throwable) {
        Log.w(tag, "setPreviewSize failed", t)
      }
      cam.parameters = params

      // Some devices require a preview texture + startPreview before takePicture.
      try {
        cam.setPreviewTexture(android.graphics.SurfaceTexture(0))
      } catch (t: Throwable) {
        Log.w(tag, "setPreviewTexture failed", t)
      }
      try { cam.startPreview() } catch (t: Throwable) { Log.w(tag, "startPreview failed", t) }

      cam.takePicture(null, null) { data, _ ->
        try {
          if (data == null || data.isEmpty()) {
            Log.e(tag, "takePicture got empty jpeg")
            onDone(false, null, "empty jpeg")
            return@takePicture
          }

          val scaled = scaleJpegIfNeeded(data, maxWidth, jpegQuality)
          val bmp = BitmapFactory.decodeByteArray(scaled, 0, scaled.size)
          val w = bmp?.width ?: -1
          val h = bmp?.height ?: -1
          val b64 = Base64.encodeToString(scaled, Base64.NO_WRAP)

          onDone(true, PhotoResult(scaled, b64, w, h), null)
        } catch (t: Throwable) {
          Log.e(tag, "takePicture handler failed", t)
          onDone(false, null, t.message ?: t.toString())
        } finally {
          try { cam.release() } catch (_: Throwable) {}
        }
      }
    } catch (t: Throwable) {
      try { cam?.release() } catch (_: Throwable) {}
      onDone(false, null, t.message ?: "camera open failed")
    }
  }

  private fun scaleJpegIfNeeded(jpeg: ByteArray, maxWidth: Int, quality: Int): ByteArray {
    if (maxWidth <= 0) return jpeg

    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    val w = opts.outWidth
    val h = opts.outHeight
    if (w <= 0 || h <= 0) return jpeg
    if (w <= maxWidth) return jpeg

    val newW = maxWidth
    val newH = (h.toDouble() * (newW.toDouble() / w.toDouble())).toInt().coerceAtLeast(1)

    val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
    val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)

    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(20, 95), baos)

    try { bmp.recycle() } catch (_: Throwable) {}
    try { scaled.recycle() } catch (_: Throwable) {}

    return baos.toByteArray()
  }
}
