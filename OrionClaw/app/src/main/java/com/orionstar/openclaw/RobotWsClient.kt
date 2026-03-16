package com.orionstar.openclaw

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal WebSocket client for OpenClaw robot-ws-ingress.
 *
 * Protocol:
 * - Connect: ws://<gateway-ip>:18795/robot/ws?token=...
 * - On open, send: {type:"hello", deviceId, app, ver, capabilities}
 * - Receive cmd: {type:"cmd", id, cmd, args}
 * - Reply result: {type:"result", id, ok, data|error}
 *
 * Auto-reconnect with exponential backoff:
 *   attempt 1: 1s, 2: 2s, 3: 4s, 4: 8s, 5+: 16s (max)
 */
class RobotWsClient(
  private val deviceIdProvider: () -> String,
  private val onLog: (String) -> Unit,
  private val onStatus: (String) -> Unit,
  private val onCmd: (id: String, cmd: String, args: JSONObject?) -> Unit,
) {

  private val client = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)
    .build()

  private var ws: WebSocket? = null

  // Reconnect state
  private val mainHandler = Handler(Looper.getMainLooper())
  private var reconnectAttempt = 0
  private var autoReconnect = false
  private var lastHost: String = ""
  private var lastToken: String = ""

  companion object {
    private const val TAG = "RobotWsClient"
    private val BACKOFF_DELAYS_MS = longArrayOf(1000, 2000, 4000, 8000, 16000)
  }

  fun isConnected(): Boolean = ws != null

  fun getDeviceId(): String = deviceIdProvider()

  fun connect(gatewayHostOrIp: String, token: String) {
    // Store for reconnect
    lastHost = gatewayHostOrIp
    lastToken = token
    autoReconnect = true
    reconnectAttempt = 0
    connectInternal()
  }

  private fun connectInternal() {
    cancelPendingReconnect()
    // Close existing connection if any
    val old = ws
    ws = null
    try { old?.close(1000, "reconnecting") } catch (_: Throwable) {}

    val host = lastHost.trim().removePrefix("http://").removePrefix("https://").trim()
    val url = "ws://$host:18795/robot/ws?token=${encodeQuery(lastToken.trim())}"

    onStatus("connecting")
    onLog("WS connect: $url (attempt=${reconnectAttempt + 1})")

    val req = Request.Builder().url(url).build()
    ws = client.newWebSocket(req, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        reconnectAttempt = 0  // reset backoff on successful connect
        onStatus("open")
        onLog("WS opened")
        sendHello(webSocket)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        handleText(text)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        onLog("WS closing code=$code reason=$reason")
        onStatus("closing")
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        onLog("WS closed code=$code reason=$reason")
        onStatus("closed")
        ws = null
        scheduleReconnect()
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        onLog("WS failure: ${t.message}")
        Log.e(TAG, "ws failure", t)
        onStatus("failed")
        ws = null
        scheduleReconnect()
      }
    })
  }

  private fun scheduleReconnect() {
    if (!autoReconnect) return
    val delayMs = BACKOFF_DELAYS_MS[minOf(reconnectAttempt, BACKOFF_DELAYS_MS.size - 1)]
    reconnectAttempt++
    onLog("WS reconnect in ${delayMs}ms (attempt=$reconnectAttempt)")
    mainHandler.postDelayed({ connectInternal() }, delayMs)
  }

  private fun cancelPendingReconnect() {
    mainHandler.removeCallbacksAndMessages(null)
  }

  fun disconnect() {
    autoReconnect = false
    reconnectAttempt = 0
    cancelPendingReconnect()
    val cur = ws
    ws = null
    try {
      cur?.close(1000, "bye")
    } catch (_: Throwable) {
    }
    onStatus("disconnected")
  }

  fun sendResultOk(id: String, data: Any?) {
    val payload = JSONObject().apply {
      put("type", "result")
      put("id", id)
      put("ok", true)
      if (data != null) {
        put("data", wrapJsonValue(data))
      }
    }
    sendJson(payload)
  }

  /**
   * Device -> Gateway event reporting (not a cmd result).
   * Used for ASR final text upstream.
   */
  fun sendEvent(event: String, data: JSONObject) {
    val payload = JSONObject().apply {
      put("type", "event")
      put("event", event)
      put("data", data)
    }
    sendJson(payload)
  }

  fun sendResultError(id: String, error: String) {
    val payload = JSONObject().apply {
      put("type", "result")
      put("id", id)
      put("ok", false)
      put("error", error)
    }
    sendJson(payload)
  }

  private fun sendHello(webSocket: WebSocket) {
    val devId = deviceIdProvider().trim()
    val hello = JSONObject().apply {
      put("type", "hello")
      put("deviceId", devId)
      put("app", "HelloApkDemo")
      put("ver", "1.0")
      put("capabilities", JSONArray(listOf("nav", "position")))
    }
    webSocket.send(hello.toString())
  }

  private fun handleText(text: String) {
    val msg = try {
      JSONObject(text)
    } catch (_: Throwable) {
      onLog("WS recv non-json: $text")
      return
    }

    val type = msg.optString("type", "")
    when (type) {
      "hello_ack" -> {
        onLog("hello_ack deviceId=${msg.optString("deviceId")}")
      }

      "cmd" -> {
        val id = msg.optString("id", "").trim()
        val cmd = msg.optString("cmd", "").trim()
        val args = msg.optJSONObject("args")
        if (id.isBlank() || cmd.isBlank()) {
          onLog("WS recv invalid cmd: $text")
          return
        }
        onCmd(id, cmd, args)
      }

      else -> {
        onLog("WS recv type=$type raw=$text")
      }
    }
  }

  private fun sendJson(obj: JSONObject) {
    val cur = ws
    if (cur == null) {
      onLog("WS send failed: not connected")
      return
    }
    cur.send(obj.toString())
  }

  private fun encodeQuery(s: String): String {
    return s
      .replace("%", "%25")
      .replace(" ", "%20")
      .replace("+", "%2B")
      .replace("?", "%3F")
      .replace("&", "%26")
      .replace("=", "%3D")
  }

  private fun wrapJsonValue(v: Any): Any {
    return when (v) {
      is JSONObject -> v
      is JSONArray -> v
      is Map<*, *> -> JSONObject(v)
      is List<*> -> JSONArray(v)
      is String, is Number, is Boolean -> v
      else -> v.toString()
    }
  }
}
