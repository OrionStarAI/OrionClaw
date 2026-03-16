package com.orionstar.openclaw

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

object AudioProbe {
  data class Stats(
    val sampleRate: Int,
    val ms: Long,
    val rms: Double,
    val peak: Int,
    val samples: Int
  )

  /** Record PCM16 from MIC and compute RMS/peak. */
  fun recordRms(durationMs: Long, sampleRate: Int = 16000): Stats {
    val channel = AudioFormat.CHANNEL_IN_MONO
    val fmt = AudioFormat.ENCODING_PCM_16BIT
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channel, fmt).coerceAtLeast(sampleRate)
    val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channel, fmt, minBuf)

    val buf = ShortArray(minBuf / 2)
    var sumSq = 0.0
    var peak = 0
    var n = 0

    rec.startRecording()
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < durationMs) {
      val read = rec.read(buf, 0, buf.size)
      if (read > 0) {
        for (i in 0 until read) {
          val v = buf[i].toInt()
          val av = kotlin.math.abs(v)
          if (av > peak) peak = av
          sumSq += (v.toDouble() * v.toDouble())
        }
        n += read
      }
    }
    try { rec.stop() } catch (_: Throwable) {}
    try { rec.release() } catch (_: Throwable) {}

    val rms = if (n > 0) sqrt(sumSq / n) else 0.0
    return Stats(sampleRate = sampleRate, ms = durationMs, rms = rms, peak = peak, samples = n)
  }
}
