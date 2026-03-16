package com.orionstar.openclaw

import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import java.io.File

/**
 * Minimal mic recorder to capture proof-of-sound.
 * Records from MIC into an M4A (MPEG_4 + AAC) file for better compatibility.
 */
object MicProbe {
  private const val TAG = "MicProbe"

  fun recordM4a(tmpFile: File, durationMs: Long): ByteArray {
    if (tmpFile.exists()) tmpFile.delete()
    tmpFile.parentFile?.mkdirs()

    val r = MediaRecorder()
    try {
      r.setAudioSource(MediaRecorder.AudioSource.MIC)
      r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      // Reasonable defaults
      r.setAudioSamplingRate(16000)
      r.setAudioEncodingBitRate(64000)
      r.setOutputFile(tmpFile.absolutePath)
      r.prepare()
      r.start()
      Thread.sleep(durationMs)
      try { r.stop() } catch (t: Throwable) {
        Log.w(TAG, "stop failed", t)
      }
    } finally {
      try { r.reset() } catch (_: Throwable) {}
      try { r.release() } catch (_: Throwable) {}
    }

    return tmpFile.readBytes()
  }

  fun toBase64(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }
}
