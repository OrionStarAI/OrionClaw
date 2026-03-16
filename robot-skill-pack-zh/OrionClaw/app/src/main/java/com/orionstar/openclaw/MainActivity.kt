package com.orionstar.openclaw

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ainirobot.agent.AgentCore
import java.io.File

class MainActivity : Activity() {

  private val TAG = "HelloMain"

  private val REQ_CAMERA = 100
  private val REQ_AUDIO = 101

  private lateinit var logView: TextView
  private lateinit var destEdit: EditText

  private lateinit var wsHostEdit: EditText
  private lateinit var wsTokenEdit: EditText
  private lateinit var wsDeviceIdEdit: EditText
  private lateinit var wsStatusView: TextView

  private val robot = RobotOsBridge()

  // ===== Lobster standby overlay =====
  private lateinit var standbyOverlay: View
  private lateinit var standbyPager: ViewPager2
  private val standbyHandler = Handler(Looper.getMainLooper())
  private var standbyAutoScrollRunning = false
  private val standbyImages = intArrayOf(
    R.drawable.lobster_slide_1
  )

  // ===== Root frame (set in onCreate) =====
  private lateinit var rootFrame: FrameLayout

  // ===== Dance screen overlay =====
  private var danceOverlay: android.widget.FrameLayout? = null
  private var danceTextView: android.widget.TextView? = null
  private var danceSubText: android.widget.TextView? = null
  private val danceHandler = Handler(Looper.getMainLooper())

  // ===== Audio player =====
  private var audioPlayer: android.media.MediaPlayer? = null

  // ===== Video recorder =====
  private var videoRecorder: android.media.MediaRecorder? = null
  private var videoCamera: android.hardware.Camera? = null
  private var videoOutputPath: String? = null

  // ===== Navigation overlay =====
  private lateinit var navOverlay: View
  private lateinit var navTitle: TextView
  private lateinit var navSubtitle: TextView
  private val navHandler = Handler(Looper.getMainLooper())
  private var navDots = 0
  private val navDotsRunnable = object : Runnable {
    override fun run() {
      if (!this@MainActivity::navOverlay.isInitialized || navOverlay.visibility != View.VISIBLE) return
      navDots = (navDots + 1) % 4
      val dots = "".padEnd(navDots, '.')
      navTitle.text = "正在导航$dots"
      navHandler.postDelayed(this, 500)
    }
  }

  private val standbyAutoScrollRunnable = object : Runnable {
    override fun run() {
      if (!standbyAutoScrollRunning) return
      if (standbyOverlay.visibility != View.VISIBLE) {
        standbyHandler.postDelayed(this, 1500)
        return
      }
      val next = (standbyPager.currentItem + 1) % standbyImages.size
      standbyPager.setCurrentItem(next, true)
      standbyHandler.postDelayed(this, 3500)
    }
  }

  private fun showStandbyOverlay() {
    standbyOverlay.visibility = View.VISIBLE
    startStandbyAutoScroll()
  }

  private fun hideStandbyOverlay() {
    standbyOverlay.visibility = View.GONE
    stopStandbyAutoScroll()
  }

  private fun startStandbyAutoScroll() {
    // Disabled: single image, no carousel
    return
  }

  private fun stopStandbyAutoScroll() {
    standbyAutoScrollRunning = false
    standbyHandler.removeCallbacks(standbyAutoScrollRunnable)
  }

  private fun showNavOverlay(destName: String?) {
    if (!this::navOverlay.isInitialized) return
    // Keep lobster carousel visible during navigation
    if (standbyOverlay.visibility != View.VISIBLE) showStandbyOverlay()
    navSubtitle.text = if (destName.isNullOrBlank()) "任务：导航中" else "目的地：$destName"
    navOverlay.visibility = View.VISIBLE
    navHandler.removeCallbacks(navDotsRunnable)
    navDots = 0
    navHandler.post(navDotsRunnable)
  }

  private fun hideNavOverlay() {
    if (!this::navOverlay.isInitialized) return
    navOverlay.visibility = View.GONE
    navHandler.removeCallbacks(navDotsRunnable)
  }

  private val wsClient = RobotWsClient(
    deviceIdProvider = { wsDeviceIdEdit.text?.toString()?.trim().orEmpty() },
    onLog = { appendLog(it) },
    onStatus = { s ->
      runOnUiThread { wsStatusView.text = "WS: $s" }
      if (s == "open") {
        // Start connecting RobotApi proactively; actual commands will wait until connected.
        ensureRobotApiConnected()
      }
    },
    onCmd = { id, cmd, args -> handleWsCmd(id, cmd, args) }
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    // Remove the default Android title bar (black bar) to maximize usable UI space.
    requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    super.onCreate(savedInstanceState)

    ensureCameraPermission()
    ensureAudioPermission()

    // ===== Main content (existing UI, unchanged logic) =====
    val rootScroll = ScrollView(this)
    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 48, 48, 48)
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }

    val title = TextView(this).apply {
      text = "RobotOS + Robot WS Ingress Demo"
      textSize = 22f
    }

    wsStatusView = TextView(this).apply {
      text = "WS: disconnected"
      textSize = 14f
    }

    val extraHost = intent?.getStringExtra("gatewayHost")
    val extraToken = intent?.getStringExtra("token")
    val extraDeviceId = intent?.getStringExtra("deviceId")

    wsHostEdit = EditText(this).apply {
      hint = "Gateway host/ip (e.g. <YOUR_GATEWAY_IP>)"
      inputType = InputType.TYPE_CLASS_TEXT
      setText(extraHost ?: "<YOUR_GATEWAY_IP>")
    }

    wsTokenEdit = EditText(this).apply {
      hint = "robot-ws token"
      inputType = InputType.TYPE_CLASS_TEXT
      // Demo default: use local gateway robot-ws-ingress token unless overridden by intent extra
      setText(extraToken ?: "robot-demo-token-change-me")
    }

    wsDeviceIdEdit = EditText(this).apply {
      hint = "deviceId (e.g. <YOUR_DEVICE_ID>)"
      inputType = InputType.TYPE_CLASS_TEXT
      setText(extraDeviceId ?: "robot-demo")
    }

    val btnWsConnect = Button(this).apply {
      text = "Connect WS (/robot/ws)"
      setOnClickListener {
        connectWsFromUi()
      }
    }

    val btnWsDisconnect = Button(this).apply {
      text = "Disconnect WS"
      setOnClickListener { wsClient.disconnect() }
    }

    // Auto-connect WS on startup for demo (so ADB automation doesn't need to type).
    Handler(Looper.getMainLooper()).postDelayed({
      connectWsFromUi()
    }, 600)

    // Ensure mic permission early for always-on ASR
    ensureAudioPermission()

    val btnConnect = Button(this).apply {
      text = "Connect RobotApi"
      setOnClickListener {
        appendLog("RobotApi: connecting...")
        robot.connect(this@MainActivity,
          onConnected = { appendLog("RobotApi connected") },
          onDisabled = { appendLog("RobotApi disabled") },
          onDisconnected = { appendLog("RobotApi disconnected") }
        )
      }
    }

    destEdit = EditText(this).apply {
      hint = "Destination place name (e.g. 接待点)"
      inputType = InputType.TYPE_CLASS_TEXT
    }

    val btnGoto = Button(this).apply {
      text = "Start Navigation (startNavigation)"
      setOnClickListener {
        val dest = destEdit.text?.toString()?.trim().orEmpty()
        if (dest.isBlank()) {
          appendLog("destination is empty")
          return@setOnClickListener
        }
        appendLog("startNavigation dest=\"$dest\"")
        robot.startNavigationToPlace(
          reqId = robot.nextReqId(),
          destName = dest,
          coordinateDeviation = 0.2,
          timeoutMs = 30_000,
          onStatus = { code, data, extra ->
            appendLog("nav status=$code data=$data extra=$extra")
          },
          onResult = { status, resp, extra ->
            appendLog("nav result status=$status resp=$resp extra=$extra")
          },
          onError = { errCode, errStr, extra ->
            appendLog("nav error code=$errCode err=$errStr extra=$extra")
          }
        )
      }
    }

    val btnStop = Button(this).apply {
      text = "Stop Navigation (stopNavigation)"
      setOnClickListener {
        appendLog("stopNavigation")
        robot.stopNavigation(reqId = robot.nextReqId())
      }
    }

    val btnGetPos = Button(this).apply {
      text = "Get Position (getPosition)"
      setOnClickListener {
        robot.getPosition(reqId = robot.nextReqId()) { result, msg, extra ->
          appendLog("getPosition result=$result msg=$msg extra=$extra")
        }
      }
    }

    logView = TextView(this).apply {
      text = "\nLogs:\n"
      textSize = 14f
    }

    val btnBackToLobster = Button(this).apply {
      text = "Back to Lobster"
      setOnClickListener { showStandbyOverlay() }
    }

    content.addView(title)
    content.addView(btnBackToLobster)

    content.addView(wsStatusView)
    content.addView(wsHostEdit)
    content.addView(wsTokenEdit)
    content.addView(wsDeviceIdEdit)
    content.addView(btnWsConnect)
    content.addView(btnWsDisconnect)

    content.addView(btnConnect)
    content.addView(destEdit)
    content.addView(btnGoto)
    content.addView(btnStop)
    content.addView(btnGetPos)
    content.addView(logView)

    rootScroll.addView(content)

    // ===== Root frame: main content + standby overlay =====
    rootFrame = FrameLayout(this)
    rootFrame.addView(rootScroll, FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    ))

    standbyOverlay = FrameLayout(this).apply {
      setBackgroundColor(0xFF000000.toInt())
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }

    standbyPager = ViewPager2(this).apply {
      adapter = StandbyImageAdapter(standbyImages)
      offscreenPageLimit = standbyImages.size
      // No left/right swipe. We will auto-advance with a fade in/out effect.
      isUserInputEnabled = false
      // Fade in / fade out (feed-in / fade-out) style.
      setPageTransformer { page, position ->
        val absPos = kotlin.math.abs(position)
        page.translationX = 0f
        page.alpha = 1f - absPos.coerceIn(0f, 1f)
      }
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }

    val btnGear = ImageButton(this).apply {
      setImageResource(R.drawable.ic_gear_24)
      // Make gear highly visible: dark translucent circle + white stroke.
      setBackgroundResource(R.drawable.gear_bg)
      contentDescription = "Settings"
      scaleType = ImageView.ScaleType.CENTER
      setPadding(24, 24, 24, 24)
      setOnClickListener {
        hideStandbyOverlay()
      }
    }

    val gearLp = FrameLayout.LayoutParams(
      dp(56),
      dp(56)
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.END
      rightMargin = dp(24)
      bottomMargin = dp(24)
    }

    (standbyOverlay as FrameLayout).addView(standbyPager)
    (standbyOverlay as FrameLayout).addView(btnGear, gearLp)

    rootFrame.addView(standbyOverlay)

    // ===== Navigation overlay (shown during nav.start) =====
    navOverlay = FrameLayout(this).apply {
      // Dark glass + subtle blue tint
      setBackgroundColor(0x660B1020.toInt())
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
      visibility = View.GONE
    }

    val navContainer = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(dp(24), dp(24), dp(24), dp(24))
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }

    val navIcon = ImageView(this).apply {
      setImageResource(android.R.drawable.ic_dialog_map)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
        bottomMargin = dp(16)
        gravity = Gravity.CENTER_HORIZONTAL
      }
    }

    navTitle = TextView(this).apply {
      text = "正在导航"
      textSize = 22f
      setTextColor(0xFFFFFFFF.toInt())
      gravity = Gravity.CENTER
    }

    navSubtitle = TextView(this).apply {
      text = "任务：导航中"
      textSize = 14f
      setTextColor(0xFFB5C7FF.toInt())
      gravity = Gravity.CENTER
    }

    val navHint = TextView(this).apply {
      text = "请注意避让 · 正在安全行驶"
      textSize = 12f
      setTextColor(0x99FFFFFF.toInt())
      gravity = Gravity.CENTER
    }

    val navProgress = android.widget.ProgressBar(this).apply {
      isIndeterminate = true
      layoutParams = LinearLayout.LayoutParams(
        dp(36), dp(36)
      ).apply {
        topMargin = dp(16)
        gravity = Gravity.CENTER_HORIZONTAL
      }
    }

    navContainer.addView(navIcon)
    navContainer.addView(navTitle)
    navContainer.addView(navSubtitle)
    navContainer.addView(navProgress)
    navContainer.addView(navHint)

    (navOverlay as FrameLayout).addView(navContainer)
    rootFrame.addView(navOverlay)

    setContentView(rootFrame)

    // Default: show lobster standby overlay (does not change startup/WS logic)
    showStandbyOverlay()
  }

  override fun onResume() {
    super.onResume()
    if (standbyOverlay.visibility == View.VISIBLE) startStandbyAutoScroll()
  }

  override fun onPause() {
    super.onPause()
    stopStandbyAutoScroll()
  }

  override fun onBackPressed() {
    // Back: if currently in settings/main page, return to lobster standby instead of exiting.
    if (this::standbyOverlay.isInitialized && standbyOverlay.visibility != View.VISIBLE) {
      showStandbyOverlay()
      return
    }
    super.onBackPressed()
  }

  private fun dp(v: Int): Int {
    return (v * resources.displayMetrics.density).toInt()
  }

  private class StandbyImageAdapter(private val images: IntArray) : RecyclerView.Adapter<StandbyImageAdapter.VH>() {
    class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val iv = ImageView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(0xFF000000.toInt())
        adjustViewBounds = false
      }
      return VH(iv)
    }

    override fun getItemCount(): Int = images.size

    override fun onBindViewHolder(holder: VH, position: Int) {
      holder.iv.setImageResource(images[position])
    }
  }

  private fun appendLog(line: String) {
    runOnUiThread {
      if (!this::logView.isInitialized) return@runOnUiThread
      logView.append(line)
      logView.append("\n")
    }
  }

  private fun connectWsFromUi() {
    val host = wsHostEdit.text?.toString()?.trim().orEmpty()
    val token = wsTokenEdit.text?.toString()?.trim().orEmpty()
    val deviceId = wsDeviceIdEdit.text?.toString()?.trim().orEmpty()
    if (host.isBlank() || token.isBlank() || deviceId.isBlank()) {
      appendLog("WS connect: host/token/deviceId required")
      return
    }

    // bind ASR reporter to current WS client
    AsrEventBus.setReporter { text ->
      val data = org.json.JSONObject().apply {
        put("text", text)
        put("ts", System.currentTimeMillis())
      }
      wsClient.sendEvent("asr.final", data)
    }

    wsClient.connect(host, token)
  }

  private fun hasCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  }

  private fun hasAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
  }

  private fun ensureCameraPermission() {
    if (hasCameraPermission()) return
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
  }

  private fun ensureAudioPermission() {
    if (hasAudioPermission()) return
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQ_CAMERA) {
      appendLog("CAMERA permission: " + if (hasCameraPermission()) "granted" else "denied")
    }
    if (requestCode == REQ_AUDIO) {
      appendLog("RECORD_AUDIO permission: " + if (hasAudioPermission()) "granted" else "denied")
    }
  }

  private data class PendingCmd(val id: String, val cmd: String, val args: org.json.JSONObject?)

  @Volatile private var connectingRobotApi: Boolean = false
  private val pendingCmds = java.util.concurrent.ConcurrentLinkedQueue<PendingCmd>()

  private fun handleWsCmd(id: String, cmd: String, args: org.json.JSONObject?) {
    appendLog("WS cmd id=$id cmd=$cmd args=${args?.toString()}")

    // TTS does not depend on RobotApi; allow it even when RobotApi is not connected.
    if (cmd == "tts.play" || cmd == "tts.stop") {
      runCmdNow(id, cmd, args)
      return
    }

    // Ensure RobotApi is connected before executing vendor SDK calls.
    if (!robot.isConnected()) {
      pendingCmds.add(PendingCmd(id, cmd, args))
      ensureRobotApiConnected()
      return
    }

    runCmdNow(id, cmd, args)
  }

  private fun ensureRobotApiConnected() {
    if (connectingRobotApi) return
    connectingRobotApi = true

    runOnUiThread {
      appendLog("RobotApi: connecting (queued=${pendingCmds.size})")
      robot.connect(this@MainActivity,
        onConnected = {
          appendLog("RobotApi connected (delay 2s before running queued cmds)")
          connectingRobotApi = false
          val h = Handler(Looper.getMainLooper())
          h.postDelayed({ flushPendingCmds() }, 2_000)
        },
        onDisabled = {
          appendLog("RobotApi disabled")
          connectingRobotApi = false
          failPending("RobotApi disabled")
        },
        onDisconnected = {
          appendLog("RobotApi disconnected")
          connectingRobotApi = false
          // keep pending; next cmd will re-trigger connect
        }
      )
    }
  }

  private fun flushPendingCmds() {
    while (true) {
      val p = pendingCmds.poll() ?: break
      runCmdNow(p.id, p.cmd, p.args)
    }
  }

  private fun failPending(msg: String) {
    while (true) {
      val p = pendingCmds.poll() ?: break
      wsClient.sendResultError(p.id, msg)
    }
  }

  private fun runCmdNow(id: String, cmd: String, args: org.json.JSONObject?) {
    try {
      when (cmd) {
        "nav.start" -> {
          val dest = args?.optString("destName", "")?.trim().orEmpty()
          if (dest.isBlank()) {
            wsClient.sendResultError(id, "args.destName required")
            return
          }

          // ── Pre-flight checks ──────────────────────────────────────────
          // Check 1: RobotApi connected
          if (!robot.isConnected()) {
            wsClient.sendResultError(id, "nav.start rejected: RobotApi not connected. Try again in a moment.")
            return
          }

          val snap = robot.statusSnapshot()

          // Check 2: Navigation subsystem ready
          if (!snap.optBoolean("isNavigationReady", false)) {
            wsClient.sendResultError(id, "nav.start rejected: navigation subsystem not ready (isNavigationReady=false). Robot may still be initializing.")
            return
          }

          // Check 3: Robot is not already navigating
          if (snap.optBoolean("isInNavigation_result", false)) {
            wsClient.sendResultError(id, "nav.start rejected: robot is already navigating. Call nav.stop first, then retry.")
            return
          }

          // Check 4: Robot is not charging (charging dock blocks movement)
          if (snap.optBoolean("isCharging", false)) {
            wsClient.sendResultError(id, "nav.start rejected: robot is on charging dock (isCharging=true). Call charge.leave first, then retry nav.start.")
            return
          }

          // Check 5: Destination must not be a charging dock point (typeId=2).
          // Charging dock points require special docking procedure, not plain nav.start.
          // Query place list async, then proceed or reject.
          val coordinateDeviation = args?.optDouble("coordinateDeviation", 0.2) ?: 0.2
          val timeoutMs = args?.optLong("timeoutMs", 30_000) ?: 30_000

          robot.getPlaceList(reqId = robot.nextReqId()) { plResult, plMsg, _ ->
            if (plResult == 1 && plMsg != null) {
              try {
                val places = org.json.JSONArray(plMsg)
                for (i in 0 until places.length()) {
                  val place = places.getJSONObject(i)
                  if (place.optString("name") == dest && place.optInt("typeId", 0) == 2) {
                    // Charging dock — cannot navigate directly
                    val alternatives = buildString {
                      for (j in 0 until places.length()) {
                        val p = places.getJSONObject(j)
                        if (p.optInt("typeId", 0) == 1 || p.optInt("typeId", 0) == 6) {
                          if (isNotEmpty()) append(", ")
                          append("'${p.optString("name")}'")
                        }
                      }
                    }
                    val hint = if (alternatives.isNotEmpty()) " Navigate to $alternatives first, then call charge.goCharge to dock." else " Call charge.goCharge to dock instead."
                    wsClient.sendResultError(id, "nav.start rejected: '$dest' is a charging dock point (typeId=2) and cannot be navigated to directly.$hint")
                    return@getPlaceList
                  }
                }
              } catch (e: Exception) {
                appendLog("nav.start placeList parse error: ${e.message}, proceeding anyway")
              }
            }
            // ── All checks passed, start navigation ──────────────────────
            runOnUiThread { showNavOverlay(dest) }

            val navReqId = robot.nextReqId()
            robot.startNavigationToPlace(
              reqId = navReqId,
              destName = dest,
              coordinateDeviation = coordinateDeviation,
              timeoutMs = timeoutMs,
              onStatus = { status, data, extra ->
                appendLog("nav status=$status data=$data extra=$extra")
                // Forward real-time nav status to gateway so LLM can track progress
                val evt = org.json.JSONObject().apply {
                  put("status", status)
                  if (data != null) put("data", data)
                  if (extra != null) put("extra", extra)
                  put("destName", dest)
                }
                wsClient.sendEvent("nav.status", evt)
              },
              onResult = { status, resp, extra ->
                val data = org.json.JSONObject().apply {
                  put("status", status)
                  put("response", resp)
                  put("extra", extra)
                  put("destName", dest)
                }
                runOnUiThread { hideNavOverlay() }
                wsClient.sendResultOk(id, data)
              },
              onError = { errCode, errStr, extra ->
                runOnUiThread { hideNavOverlay() }
                // Enrich error with current robot status so LLM knows where it got stuck
                val posSnapshot = try { robot.statusSnapshot().toString() } catch (_: Throwable) { "{}" }
                wsClient.sendResultError(id, "nav error code=$errCode err=$errStr extra=$extra | robotStatus=$posSnapshot")
              }
            )
          }
        }

        "nav.stop" -> {
          robot.stopNavigation(reqId = robot.nextReqId())
          runOnUiThread { hideNavOverlay() }
          wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
        }

        // ===== Map management =====
        // Switch to a different map: args: { mapName: String, timeoutMs?: Long }
        "robot.switchMap" -> {
          val mapName = args?.optString("mapName", "")?.trim().orEmpty()
          if (mapName.isBlank()) {
            wsClient.sendResultError(id, "robot.switchMap: mapName is required")
          } else {
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            val timeoutMs = args?.optLong("timeoutMs") ?: 15_000L
            val timer = java.util.Timer()
            timer.schedule(object : java.util.TimerTask() {
              override fun run() {
                if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "robot.switchMap timeout")
              }
            }, timeoutMs)
            robot.switchMap(reqId = robot.nextReqId(), mapName = mapName) { result, msg, extra ->
              if (done.compareAndSet(false, true)) {
                timer.cancel()
                wsClient.sendResultOk(id, org.json.JSONObject()
                  .put("result", result)
                  .put("message", msg ?: "")
                  .put("extra", extra ?: ""))
              }
            }
          }
        }

        // Get place list by map name: args: { mapName: String, timeoutMs?: Long }
        "robot.getPlaceListByMapName" -> {
          val mapName = args?.optString("mapName", "")?.trim().orEmpty()
          if (mapName.isBlank()) {
            wsClient.sendResultError(id, "robot.getPlaceListByMapName: mapName is required")
          } else {
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            val timeoutMs = args?.optLong("timeoutMs") ?: 10_000L
            val timer = java.util.Timer()
            timer.schedule(object : java.util.TimerTask() {
              override fun run() {
                if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "robot.getPlaceListByMapName timeout")
              }
            }, timeoutMs)
            robot.getPlaceListByMapName(reqId = robot.nextReqId(), mapName = mapName) { result, msg, extra ->
              if (done.compareAndSet(false, true)) {
                timer.cancel()
                wsClient.sendResultOk(id, org.json.JSONObject()
                  .put("result", result)
                  .put("message", msg ?: "")
                  .put("extra", extra ?: ""))
              }
            }
          }
        }

        // ===== Cross-floor elevator navigation =====
        // args: { destName: String, floorIndex: Int, timeoutMs?: Long, callbackSessionKey?: String }
        "nav.startElevator" -> {
          val destName = args?.optString("destName", "")?.trim().orEmpty()
          val floorIndex = args?.optInt("floorIndex", 0) ?: 0
          val timeoutMs = args?.optLong("timeoutMs") ?: 180_000L
          val callbackSessionKey = args?.optString("callbackSessionKey", "") ?: ""
          if (destName.isBlank()) {
            wsClient.sendResultError(id, "nav.startElevator: destName is required")
          } else if (floorIndex == 0) {
            wsClient.sendResultError(id, "nav.startElevator: floorIndex must be non-zero (use robot.getMultiFloorConfig to get valid values)")
          } else {
            // Immediately acknowledge — async mode B
            wsClient.sendResultOk(id, org.json.JSONObject()
              .put("status", "started")
              .put("destName", destName)
              .put("floorIndex", floorIndex))
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            val timer = java.util.Timer()
            timer.schedule(object : java.util.TimerTask() {
              override fun run() {
                if (done.compareAndSet(false, true)) {
                  robot.stopElevatorNavigation(reqId = robot.nextReqId())
                  runOnUiThread { hideNavOverlay() }
                  wsClient.sendEvent("nav.error", org.json.JSONObject()
                    .put("deviceId", wsClient.getDeviceId())
                    .put("error", "timeout")
                    .put("destName", destName)
                    .put("floorIndex", floorIndex)
                    .put("callbackSessionKey", callbackSessionKey))
                }
              }
            }, timeoutMs)
            runOnUiThread { showNavOverlay(destName) }
            robot.startElevatorNavigationToPlace(
              reqId = robot.nextReqId(),
              destName = destName,
              floorIndex = floorIndex,
              onStatus = { status, data, extra ->
                android.util.Log.i(TAG, "nav.startElevator status=$status data=$data extra=$extra")
                wsClient.sendEvent("nav.status", org.json.JSONObject()
                  .put("deviceId", wsClient.getDeviceId())
                  .put("status", status)
                  .put("data", data ?: "")
                  .put("extra", extra ?: "")
                  .put("destName", destName)
                  .put("floorIndex", floorIndex)
                  .put("callbackSessionKey", callbackSessionKey))
              },
              onResult = { status, resp, extra ->
                if (done.compareAndSet(false, true)) {
                  timer.cancel()
                  runOnUiThread { hideNavOverlay() }
                  wsClient.sendEvent("nav.done", org.json.JSONObject()
                    .put("deviceId", wsClient.getDeviceId())
                    .put("status", status)
                    .put("response", resp ?: "")
                    .put("extra", extra ?: "")
                    .put("destName", destName)
                    .put("floorIndex", floorIndex)
                    .put("callbackSessionKey", callbackSessionKey))
                }
              },
              onError = { errCode, errStr, extra ->
                if (done.compareAndSet(false, true)) {
                  timer.cancel()
                  runOnUiThread { hideNavOverlay() }
                  wsClient.sendEvent("nav.error", org.json.JSONObject()
                    .put("deviceId", wsClient.getDeviceId())
                    .put("errorCode", errCode)
                    .put("errorString", errStr ?: "")
                    .put("extra", extra ?: "")
                    .put("destName", destName)
                    .put("floorIndex", floorIndex)
                    .put("callbackSessionKey", callbackSessionKey))
                }
              }
            )
          }
        }

        // Stop elevator navigation specifically
        "nav.stopElevator" -> {
          robot.stopElevatorNavigation(reqId = robot.nextReqId())
          runOnUiThread { hideNavOverlay() }
          wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
        }

        // Get multi-floor map config (returns floor list with FloorIndex values)
        "robot.getMultiFloorConfig" -> {
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val timeoutMs = args?.optLong("timeoutMs") ?: 10_000L
          val timer = java.util.Timer()
          timer.schedule(object : java.util.TimerTask() {
            override fun run() {
              if (done.compareAndSet(false, true)) {
                wsClient.sendResultError(id, "robot.getMultiFloorConfig timeout")
              }
            }
          }, timeoutMs)
          robot.getMultiFloorConfig(reqId = robot.nextReqId()) { result, msg, extra ->
            if (done.compareAndSet(false, true)) {
              timer.cancel()
              wsClient.sendResultOk(id, org.json.JSONObject()
                .put("result", result)
                .put("message", msg ?: "")
                .put("extra", extra ?: ""))
            }
          }
        }

        "robot.getPosition" -> {
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())

          // Local timeout guard to avoid blocking gateway wait forever
          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) {
              wsClient.sendResultError(id, "getPosition timeout")
            }
          }, 12_000)

          // Call getPosition after a small delay.
          mainHandler.postDelayed({
            if (!done.compareAndSet(false, true)) return@postDelayed // will be flipped back on callback below
            done.set(false)
            robot.getPosition(reqId = robot.nextReqId()) { result, msg, extra ->
              if (!done.compareAndSet(false, true)) return@getPosition
              val data = org.json.JSONObject().apply {
                put("result", result)
                put("message", msg)
                put("extra", extra)
              }
              wsClient.sendResultOk(id, data)
            }
          }, 2_000)
        }

        // Manually set robot's pose estimate (force relocalization).
        // args: { x: Double, y: Double, theta: Double }
        // Use when robot's internal position has drifted from reality.
        "robot.setPoseEstimate" -> {
          val x = args?.optDouble("x") ?: Double.NaN
          val y = args?.optDouble("y") ?: Double.NaN
          val theta = args?.optDouble("theta") ?: Double.NaN
          if (x.isNaN() || y.isNaN() || theta.isNaN()) {
            wsClient.sendResultError(id, "robot.setPoseEstimate: args x, y, theta (all Double) are required")
          } else {
            robot.setPoseEstimate(reqId = robot.nextReqId(), x = x, y = y, theta = theta) { result, msg, extra ->
              val data = org.json.JSONObject().apply {
                put("result", result)
                put("message", msg)
                if (extra != null) put("extra", extra)
              }
              if (msg == "succeed" || result == 1) {
                wsClient.sendResultOk(id, data)
              } else {
                wsClient.sendResultError(id, "setPoseEstimate failed: result=$result msg=$msg extra=$extra")
              }
            }
          }
        }

        "robot.getPlaceList" -> {
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())

          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) {
              wsClient.sendResultError(id, "getPlaceList timeout")
            }
          }, 12_000)

          mainHandler.postDelayed({
            robot.getPlaceList(reqId = robot.nextReqId()) { result, msg, extra ->
              if (!done.compareAndSet(false, true)) return@getPlaceList
              val data = org.json.JSONObject().apply {
                put("result", result)
                put("message", msg)
                put("extra", extra)
              }
              wsClient.sendResultOk(id, data)
            }
          }, 2_000)
        }

        "robot.status" -> {
          // Snapshot + a couple of quick command queries.
          val base = robot.statusSnapshot()
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val lock = Any()
          val pending = java.util.concurrent.atomic.AtomicInteger(2)
          val mainHandler = Handler(Looper.getMainLooper())

          fun finishIfReady() {
            if (pending.get() != 0) return
            if (!done.compareAndSet(false, true)) return
            wsClient.sendResultOk(id, org.json.JSONObject().put("status", base))
          }

          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) {
              // Best-effort: return whatever we have
              wsClient.sendResultOk(id, org.json.JSONObject().put("status", base).put("partial", true))
            }
          }, 3_000)

          // Query: isInNavigation
          robot.isInNavigation(reqId = robot.nextReqId()) { result, msg, extra ->
            synchronized(lock) {
              base.put("isInNavigation_result", result)
              if (msg != null) base.put("isInNavigation", msg)
              if (extra != null) base.put("isInNavigation_extra", extra)
            }
            pending.decrementAndGet()
            finishIfReady()
          }

          // Query: getChargeStatus (cmd)
          robot.getChargeStatusCmd(reqId = robot.nextReqId()) { result, msg, extra ->
            synchronized(lock) {
              base.put("chargeStatus_result", result)
              if (msg != null) base.put("chargeStatus", msg)
              if (extra != null) base.put("chargeStatus_extra", extra)
            }
            pending.decrementAndGet()
            finishIfReady()
          }
        }

        "tts.play" -> {
          val text = args?.optString("text", "")?.trim().orEmpty()
          if (text.isBlank()) {
            wsClient.sendResultError(id, "args.text required")
            return
          }
          val timeoutMs = args?.optLong("timeoutMs", 60_000) ?: 60_000

          android.util.Log.i(TAG, "tts.play text=\"$text\" timeoutMs=$timeoutMs")
          appendLog("tts.play text=\"$text\" timeoutMs=$timeoutMs")

          // Agent SDK TTS: async
          try {
            AgentCore.tts(text, timeoutMs, null)
            wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
          } catch (t: Throwable) {
            android.util.Log.e(TAG, "tts.play exception", t)
            appendLog("tts.play exception: ${t.message}")
            wsClient.sendResultError(id, "tts.play failed: ${t.message}")
          }
        }

        "tts.stop" -> {
          try {
            AgentCore.stopTTS()
            wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
          } catch (t: Throwable) {
            wsClient.sendResultError(id, "tts.stop failed: ${t.message}")
          }
        }

        // Proof command: record mic while playing TTS, return a small 3gp audio base64.
        // args: { text?:string, recordMs?:number }
        "tts.probe" -> {
          if (!hasAudioPermission()) {
            wsClient.sendResultError(id, "RECORD_AUDIO permission not granted")
            ensureAudioPermission()
            return
          }

          val text = args?.optString("text", "1 2 3 4")?.trim().orEmpty().ifBlank { "1 2 3 4" }
          val recordMs = (args?.optLong("recordMs", 5000L) ?: 5000L).coerceIn(1000L, 10_000L)

          // record on a worker thread; do not block UI
          Thread {
            try {
              // Start TTS (async) then record mic
              try { AgentCore.tts(text, 60_000L, null) } catch (_: Throwable) {}
              val f = File(cacheDir, "tts_probe.m4a")
              val bytes = MicProbe.recordM4a(f, recordMs)
              val b64 = MicProbe.toBase64(bytes)
              val data = org.json.JSONObject().apply {
                put("mime", "audio/mp4")
                put("bytes", bytes.size)
                put("base64", b64)
                put("text", text)
                put("recordMs", recordMs)
              }
              wsClient.sendResultOk(id, data)
            } catch (t: Throwable) {
              wsClient.sendResultError(id, "tts.probe failed: ${t.message}")
            }
          }.start()
        }

        // Proof command (numeric): record mic RMS/peak while TTS plays.
        // args: { text?:string, recordMs?:number }
        "tts.probeLevel" -> {
          if (!hasAudioPermission()) {
            wsClient.sendResultError(id, "RECORD_AUDIO permission not granted")
            ensureAudioPermission()
            return
          }
          val text = args?.optString("text", "1 2 3 4")?.trim().orEmpty().ifBlank { "1 2 3 4" }
          val recordMs = (args?.optLong("recordMs", 4000L) ?: 4000L).coerceIn(1000L, 10_000L)

          Thread {
            try {
              // Start TTS then record mic level
              try { AgentCore.tts(text, 60_000L, null) } catch (_: Throwable) {}
              val st = AudioProbe.recordRms(recordMs)
              val data = org.json.JSONObject().apply {
                put("text", text)
                put("recordMs", recordMs)
                put("sampleRate", st.sampleRate)
                put("samples", st.samples)
                put("rms", st.rms)
                put("peak", st.peak)
              }
              wsClient.sendResultOk(id, data)
            } catch (t: Throwable) {
              wsClient.sendResultError(id, "tts.probeLevel failed: ${t.message}")
            }
          }.start()
        }

        "camera.takePhoto" -> {
          val mainHandler = Handler(Looper.getMainLooper())

          if (!hasCameraPermission()) {
            wsClient.sendResultError(id, "camera permission not granted")
            ensureCameraPermission()
            return
          }

          val maxWidth = args?.optInt("maxWidth", 640) ?: 640
          val quality = args?.optInt("quality", 80) ?: 80

          // Must run on main thread for Camera1 on many devices.
          mainHandler.post {
            appendLog("camera.takePhoto maxWidth=$maxWidth quality=$quality")
            Camera1Capture.takePhoto(maxWidth = maxWidth, jpegQuality = quality) { ok, result, err ->
              if (!ok || result == null) {
                wsClient.sendResultError(id, err ?: "takePhoto failed")
                return@takePhoto
              }

              val data = org.json.JSONObject().apply {
                put("mime", "image/jpeg")
                put("width", result.width)
                put("height", result.height)
                put("bytes", result.jpegBytes.size)
                put("base64", result.base64)
              }
              wsClient.sendResultOk(id, data)
            }
          }
        }

        "head.move" -> {
          val hMode = args?.optString("hMode", "absolute") ?: "absolute"
          val vMode = args?.optString("vMode", "absolute") ?: "absolute"
          val yawDeg = args?.optDouble("yawDeg", 0.0) ?: 0.0
          val pitchDeg = args?.optDouble("pitchDeg", 0.0) ?: 0.0

          // Clamp per RobotAPI.md: hAngle -120~120, vAngle 0~90
          val hAngle = yawDeg.coerceIn(-120.0, 120.0).toInt()
          val vAngle = pitchDeg.coerceIn(0.0, 90.0).toInt()

          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())
          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "head.move timeout")
          }, 12_000)

          robot.moveHead(reqId = robot.nextReqId(), hMode = hMode, vMode = vMode, hAngle = hAngle, vAngle = vAngle) { result, msg, extra ->
            if (!done.compareAndSet(false, true)) return@moveHead
            val data = org.json.JSONObject().apply {
              put("result", result)
              put("message", msg)
              put("extra", extra)
              put("hMode", hMode)
              put("vMode", vMode)
              put("yawDeg", hAngle)
              put("pitchDeg", vAngle)
            }
            wsClient.sendResultOk(id, data)
          }
        }

        "head.reset" -> {
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())
          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "head.reset timeout")
          }, 12_000)

          robot.resetHead(reqId = robot.nextReqId()) { result, msg, extra ->
            if (!done.compareAndSet(false, true)) return@resetHead
            val data = org.json.JSONObject().apply {
              put("result", result)
              put("message", msg)
              put("extra", extra)
            }
            wsClient.sendResultOk(id, data)
          }
        }

        "base.turn" -> {
          // Turn body left/right. Prefer angle turn when provided.
          val dir = args?.optString("dir", "left") ?: "left" // left|right
          val speed = (args?.optDouble("speedDegPerSec", 20.0) ?: 20.0).toFloat().coerceIn(0f, 50f)
          val angleOpt = if (args?.has("angleDeg") == true) (args.optDouble("angleDeg") as Double?) else null
          val angle = angleOpt?.toFloat()?.let { if (it > 0f) it else null }

          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())
          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "base.turn timeout")
          }, 20_000)

          val cb: (Int, String?, String?) -> Unit = { result, msg, extra ->
            if (done.compareAndSet(false, true)) {
              val data = org.json.JSONObject().apply {
                put("result", result)
                put("message", msg)
                put("extra", extra)
                put("dir", dir)
                // JSONObject on some Android builds doesn't have put(String, Float); use Double
                put("speedDegPerSec", speed.toDouble())
                if (angle != null) put("angleDeg", angle.toDouble())
              }
              wsClient.sendResultOk(id, data)
            }
          }

          if (dir == "right") {
            robot.turnRight(reqId = robot.nextReqId(), speedDegPerSec = speed, angleDeg = angle, onResult = cb)
          } else {
            robot.turnLeft(reqId = robot.nextReqId(), speedDegPerSec = speed, angleDeg = angle, onResult = cb)
          }
        }

        // ===== Charging: go to charger (proper auto-charge docking) =====
        "charge.start" -> {
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())
          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "charge.start timeout")
          }, 20_000)
          mainHandler.post {
            try {
              com.ainirobot.coreservice.client.RobotApi.getInstance().startNaviToAutoChargeAction(
                robot.nextReqId(), 120_000L,
                object : com.ainirobot.coreservice.client.listener.ActionListener() {
                  override fun onResult(status: Int, response: String?, extraData: String?) {
                    if (done.compareAndSet(false, true))
                      wsClient.sendResultOk(id, org.json.JSONObject().apply {
                        put("result", status); put("message", response)
                      })
                  }
                  override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
                    if (done.compareAndSet(false, true))
                      wsClient.sendResultError(id, "charge.start error $errorCode: $errorString")
                  }
                  override fun onStatusUpdate(status: Int, data: String?, extraData: String?) {}
                }
              )
            } catch (e: Exception) {
              if (done.compareAndSet(false, true))
                wsClient.sendResultError(id, "charge.start failed: ${e.message}")
            }
          }
        }

        // ===== Charging: leave pile (proper implementation) =====
        // Must call disableBattery() first, then leaveChargingPile()
        "charge.leave" -> {
          val speed    = (args?.optDouble("speed",    0.7) ?: 0.7).toFloat().coerceIn(0f, 1f)
          val distance = (args?.optDouble("distance", 0.3) ?: 0.3).toFloat().coerceIn(0.1f, 1f)
          val done = java.util.concurrent.atomic.AtomicBoolean(false)
          val mainHandler = Handler(Looper.getMainLooper())
          mainHandler.postDelayed({
            if (done.compareAndSet(false, true)) wsClient.sendResultError(id, "charge.leave timeout")
          }, 20_000)
          mainHandler.post {
            try {
              // Step 1: disable battery UI so we can leave
              com.ainirobot.coreservice.client.RobotApi.getInstance().disableBattery()
              Thread.sleep(500)
              // Step 2: leave charging pile
              com.ainirobot.coreservice.client.RobotApi.getInstance().leaveChargingPile(
                robot.nextReqId(), speed, distance,
                object : com.ainirobot.coreservice.client.listener.CommandListener() {
                  override fun onResult(result: Int, message: String?) {
                    if (done.compareAndSet(false, true)) {
                      wsClient.sendResultOk(id, org.json.JSONObject().apply {
                        put("result", result)
                        put("message", message)
                        put("speed", speed.toDouble())
                        put("distance", distance.toDouble())
                      })
                    }
                  }
                  override fun onError(errorCode: Int, errorString: String?) {
                    if (done.compareAndSet(false, true))
                      wsClient.sendResultError(id, "charge.leave error $errorCode: $errorString")
                  }
                }
              )
            } catch (e: Exception) {
              if (done.compareAndSet(false, true))
                wsClient.sendResultError(id, "charge.leave failed: ${e.message}")
            }
          }
        }

        // ===== Charging: stop charging =====
        "charge.stop" -> {
          try {
            com.ainirobot.coreservice.client.RobotApi.getInstance().stopAutoChargeAction(robot.nextReqId(), true)
            wsClient.sendResultOk(id, org.json.JSONObject().put("stopped", true))
          } catch (e: Exception) {
            wsClient.sendResultError(id, "charge.stop failed: ${e.message}")
          }
        }

        // ===== Audio file playback =====
        // args: { "url": "http://..../music.mp3" }  OR  { "path": "/sdcard/music.mp3" }
        // Returns immediately with ok=true; sends audio.done / audio.error event when finished.
        "audio.play" -> {
          val url  = args?.optString("url",  "").orEmpty()
          val path = args?.optString("path", "").orEmpty()
          if (url.isEmpty() && path.isEmpty()) {
            wsClient.sendResultError(id, "audio.play: requires 'url' or 'path'")
          } else {
            // Stop any currently playing audio first
            runCatching { audioPlayer?.stop(); audioPlayer?.release() }
            audioPlayer = null

            val player = android.media.MediaPlayer()
            audioPlayer = player
            val src = url.ifEmpty { path }

            try {
              if (url.isNotEmpty()) {
                player.setDataSource(url)
              } else {
                player.setDataSource(path)
              }
              player.setOnPreparedListener { mp ->
                mp.start()
                wsClient.sendResultOk(id, org.json.JSONObject().apply {
                  put("ok", true)
                  put("src", src)
                  put("durationMs", mp.duration)
                })
              }
              player.setOnCompletionListener {
                wsClient.sendEvent("audio.done", org.json.JSONObject().apply { put("src", src) })
                audioPlayer = null
              }
              player.setOnErrorListener { _, what, extra ->
                wsClient.sendEvent("audio.error", org.json.JSONObject().apply {
                  put("src", src); put("what", what); put("extra", extra)
                })
                audioPlayer = null
                true
              }
              player.prepareAsync()
            } catch (e: Exception) {
              wsClient.sendResultError(id, "audio.play failed: ${e.message}")
            }
          }
        }

        "audio.stop" -> {
          runCatching { audioPlayer?.stop(); audioPlayer?.release() }
          audioPlayer = null
          wsClient.sendResultOk(id, org.json.JSONObject().put("stopped", true))
        }

        // ===== Video recording =====
        // camera.startRecord: start recording video
        //   args: { "durationMs": 30000, "quality": "low|high" }
        //   returns immediately; sends camera.recordDone event when stopped or duration elapsed
        "camera.startRecord" -> {
          if (!hasCameraPermission()) {
            wsClient.sendResultError(id, "camera permission not granted")
            ensureCameraPermission()
          } else {
            runOnUiThread {
              // Stop any existing recording
              runCatching { videoRecorder?.stop(); videoRecorder?.release() }
              runCatching { videoCamera?.lock(); videoCamera?.release() }
              videoRecorder = null
              videoCamera = null

              val durationMs = (args?.optLong("durationMs", 30000L) ?: 30000L).coerceIn(1000L, 120000L)
              val outFile = java.io.File(cacheDir, "robot_video_${System.currentTimeMillis()}.mp4")
              videoOutputPath = outFile.absolutePath

              try {
                val cam = android.hardware.Camera.open()
                videoCamera = cam

                val params = cam.parameters
                runCatching {
                  val sizes = params.supportedPreviewSizes
                  val chosen = sizes?.filter { it.width <= 640 }?.maxByOrNull { it.width * it.height }
                  if (chosen != null) params.setPreviewSize(chosen.width, chosen.height)
                }
                cam.parameters = params
                cam.setDisplayOrientation(0)

                val recorder = android.media.MediaRecorder()
                videoRecorder = recorder

                cam.unlock()
                recorder.setCamera(cam)
                recorder.setAudioSource(android.media.MediaRecorder.AudioSource.CAMCORDER)
                recorder.setVideoSource(android.media.MediaRecorder.VideoSource.CAMERA)
                recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                recorder.setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264)
                recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                recorder.setVideoSize(640, 480)
                recorder.setVideoFrameRate(15)
                recorder.setVideoEncodingBitRate(1_000_000)
                recorder.setOutputFile(outFile.absolutePath)
                recorder.prepare()
                recorder.start()

                appendLog("camera.startRecord → ${outFile.absolutePath} durationMs=$durationMs")
                wsClient.sendResultOk(id, org.json.JSONObject().apply {
                  put("ok", true)
                  put("path", outFile.absolutePath)
                  put("durationMs", durationMs)
                })

                // Auto-stop after durationMs
                Handler(Looper.getMainLooper()).postDelayed({
                  val path = videoOutputPath
                  runCatching { videoRecorder?.stop(); videoRecorder?.release() }
                  runCatching { videoCamera?.lock(); videoCamera?.release() }
                  videoRecorder = null
                  videoCamera = null
                  videoOutputPath = null
                  wsClient.sendEvent("camera.recordDone", org.json.JSONObject().apply {
                    put("path", path ?: "")
                    put("autoStop", true)
                  })
                  appendLog("camera.startRecord auto-stopped → $path")
                }, durationMs)

              } catch (e: Exception) {
                runCatching { videoRecorder?.release() }
                runCatching { videoCamera?.lock(); videoCamera?.release() }
                videoRecorder = null
                videoCamera = null
                videoOutputPath = null
                wsClient.sendResultError(id, "camera.startRecord failed: ${e.message}")
              }
            }
          }
        }

        // camera.stopRecord: stop recording early
        "camera.stopRecord" -> {
          val path = videoOutputPath
          runCatching { videoRecorder?.stop(); videoRecorder?.release() }
          runCatching { videoCamera?.lock(); videoCamera?.release() }
          videoRecorder = null
          videoCamera = null
          videoOutputPath = null
          wsClient.sendResultOk(id, org.json.JSONObject().apply {
            put("stopped", true)
            put("path", path ?: "")
          })
          if (!path.isNullOrEmpty()) {
            wsClient.sendEvent("camera.recordDone", org.json.JSONObject().apply {
              put("path", path)
              put("autoStop", false)
            })
          }
        }

        // ===== Dance screen overlay =====
        // screen.show: display fullscreen dance overlay
        //   args: {
        //     "text": "🎵 欢乐颂",          // main big text
        //     "subText": "♪ 大家一起欢乐",   // subtitle (optional)
        //     "bg": "#FF4081",              // background color hex (optional)
        //     "textColor": "#FFFFFF",        // text color hex (optional)
        //     "fontSize": 80,               // sp (optional, default 80)
        //     "emoji": "🎵🎶🎤",            // emoji strip at bottom (optional)
        //   }
        "screen.show" -> {
          runOnUiThread {
            val text      = args?.optString("text",      "🎵") ?: "🎵"
            val subText   = args?.optString("subText",   "") ?: ""
            val bgHex     = args?.optString("bg",        "#E91E63") ?: "#E91E63"
            val fgHex     = args?.optString("textColor", "#FFFFFF") ?: "#FFFFFF"
            val fontSize  = (args?.optDouble("fontSize", 80.0) ?: 80.0).toFloat()
            val emoji     = args?.optString("emoji",     "") ?: ""

            val bgColor = try { android.graphics.Color.parseColor(bgHex) }
                          catch (_: Exception) { 0xFFE91E63.toInt() }
            val fgColor = try { android.graphics.Color.parseColor(fgHex) }
                          catch (_: Exception) { 0xFFFFFFFF.toInt() }

            // Remove old overlay if exists
            danceOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }

            val overlay = android.widget.FrameLayout(this@MainActivity)
            overlay.setBackgroundColor(bgColor)
            overlay.layoutParams = android.widget.FrameLayout.LayoutParams(
              android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
              android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )

            val container = android.widget.LinearLayout(this@MainActivity).apply {
              orientation = android.widget.LinearLayout.VERTICAL
              gravity = android.view.Gravity.CENTER
              layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
              )
            }

            val mainTv = android.widget.TextView(this@MainActivity).apply {
              this.text = text
              textSize = fontSize
              setTextColor(fgColor)
              gravity = android.view.Gravity.CENTER
              setPadding(dp(24), dp(16), dp(24), dp(8))
            }

            val subTv = android.widget.TextView(this@MainActivity).apply {
              this.text = subText
              textSize = fontSize * 0.4f
              setTextColor(fgColor)
              gravity = android.view.Gravity.CENTER
              setPadding(dp(24), dp(4), dp(24), dp(8))
              alpha = 0.9f
            }

            val emojiTv = android.widget.TextView(this@MainActivity).apply {
              this.text = if (emoji.isNotEmpty()) emoji else "🎵 🎶 🎤 🎼 🎹"
              textSize = fontSize * 0.35f
              gravity = android.view.Gravity.CENTER
              setPadding(dp(16), dp(8), dp(16), dp(24))
              alpha = 0.85f
            }

            container.addView(mainTv)
            container.addView(subTv)
            container.addView(emojiTv)
            overlay.addView(container)

            // Pulsing animation on main text
            val scaleAnim = android.view.animation.ScaleAnimation(
              1.0f, 1.08f, 1.0f, 1.08f,
              android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
              android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
              duration = 600; repeatMode = android.view.animation.Animation.REVERSE
              repeatCount = android.view.animation.Animation.INFINITE
            }
            mainTv.startAnimation(scaleAnim)

            rootFrame.addView(overlay)
            danceOverlay  = overlay
            danceTextView = mainTv
            danceSubText  = subTv

            wsClient.sendResultOk(id, org.json.JSONObject().apply {
              put("ok", true); put("text", text); put("bg", bgHex)
            })
          }
        }

        // screen.update: change text/color on existing overlay without recreating
        //   args: { "text": "🎶", "subText": "...", "bg": "#color" }
        "screen.update" -> {
          runOnUiThread {
            val newText = args?.optString("text", null)
            val newSub  = args?.optString("subText", null)
            val newBg   = args?.optString("bg", null)
            newText?.let { danceTextView?.text = it }
            newSub?.let  { danceSubText?.text  = it }
            newBg?.let   {
              try { danceOverlay?.setBackgroundColor(android.graphics.Color.parseColor(it)) }
              catch (_: Exception) {}
            }
            wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
          }
        }

        // screen.flash: rapid color flash effect (beat sync)
        //   args: { "color": "#FF4081", "durationMs": 200 }
        "screen.flash" -> {
          runOnUiThread {
            val flashColor = try {
              android.graphics.Color.parseColor(args?.optString("color", "#FFFFFF") ?: "#FFFFFF")
            } catch (_: Exception) { 0xFFFFFFFF.toInt() }
            val dur = (args?.optLong("durationMs", 150L) ?: 150L).coerceIn(50L, 1000L)
            val overlay = danceOverlay
            if (overlay != null) {
              val orig = overlay.background
              overlay.setBackgroundColor(flashColor)
              danceHandler.postDelayed({ overlay.background = orig }, dur)
            }
            wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
          }
        }

        // screen.hide: remove dance overlay, restore standby
        "screen.hide" -> {
          runOnUiThread {
            danceOverlay?.let {
              it.clearAnimation()
              danceTextView?.clearAnimation()
              (it.parent as? android.view.ViewGroup)?.removeView(it)
            }
            danceOverlay  = null
            danceTextView = null
            danceSubText  = null
            wsClient.sendResultOk(id, org.json.JSONObject().put("ok", true))
          }
        }

        else -> {
          wsClient.sendResultOk(id, org.json.JSONObject().apply {
            put("echo", true)
            put("cmd", cmd)
          })
        }
      }
    } catch (t: Throwable) {
      wsClient.sendResultError(id, t.message ?: "unknown error")
    }
  }
}
