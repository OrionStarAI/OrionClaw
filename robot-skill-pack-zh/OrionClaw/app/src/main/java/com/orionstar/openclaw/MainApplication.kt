package com.orionstar.openclaw

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.ainirobot.agent.AppAgent
import com.ainirobot.agent.OnTranscribeListener
import com.ainirobot.agent.action.Action
import com.ainirobot.agent.base.Transcription

/**
 * Agent SDK bootstrap (per agent-sdk/README.md Quick Start).
 *
 * For now we only need TTS capability; actions are not exposed.
 */
class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    object : AppAgent(this) {
      override fun onCreate() {
        // Minimal persona/objective to satisfy SDK expectations
        setPersona("你是机器人语音助手")
        setObjective("为机器人播报关键提示信息")

        // Always-on ASR/TTS listener
        setOnTranscribeListener(object : OnTranscribeListener {
          override fun onASRResult(transcription: Transcription): Boolean {
            try {
              if (transcription.final) {
                val text = transcription.text ?: ""
                Log.i("HelloASR", "final: $text")
                AsrEventBus.onAsrFinal(text)
              }
            } catch (t: Throwable) {
              Log.e("HelloASR", "onASRResult error", t)
            }
            // Do not intercept system UI
            return false
          }

          override fun onTTSResult(transcription: Transcription): Boolean {
            // Just observe, don't intercept
            return false
          }
        })
      }

      override fun onExecuteAction(action: Action, params: Bundle?): Boolean {
        // No externally triggered actions for now.
        return false
      }
    }
  }
}
