package com.orionstar.openclaw

import android.app.Activity
import android.util.Log
import com.ainirobot.coreservice.client.ApiListener
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.ActionListener
import com.ainirobot.coreservice.client.listener.CommandListener
import com.ainirobot.coreservice.client.module.ModuleCallbackApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * RobotOS bridge using vendor SDK (robotservice.jar).
 */
class RobotOsBridge {

  private val reqIdCounter = AtomicInteger(1000)
  private val tag = "RobotOsBridge"

  @Volatile private var connected: Boolean = false

  fun nextReqId(): Int = reqIdCounter.incrementAndGet()

  fun isConnected(): Boolean = connected

  fun statusSnapshot(): org.json.JSONObject {
    val api = RobotApi.getInstance()
    val o = org.json.JSONObject()

    // Connection
    o.put("connected", connected)
    try { o.put("isApiConnectedService", api.isApiConnectedService()) } catch (_: Throwable) {}

    // Navigation / estimate
    try { o.put("isActive", api.isActive()) } catch (_: Throwable) {}
    try { o.put("isNavigationReady", api.isNavigationReady()) } catch (_: Throwable) {}
    try { o.put("isRobotEstimate", api.isRobotEstimate()) } catch (_: Throwable) {}

    // Map (fast path)
    try {
      val mapName = api.mapName
      if (!mapName.isNullOrBlank()) o.put("mapName", mapName)
    } catch (_: Throwable) {}

    // Battery/charge (fast path)
    try { o.put("batteryLevel", api.batteryLevel) } catch (_: Throwable) {}
    try { o.put("isCharging", api.chargeStatus) } catch (_: Throwable) {}

    return o
  }

  fun isInNavigation(reqId: Int, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "isInNavigation onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "isInNavigation onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }

        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "isInNavigation onResult result=$result msg=$message")
          onResult(result, message, null)
        }

        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "isInNavigation onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "isInNavigation() calling... reqId=$reqId")
      RobotApi.getInstance().isInNavigation(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "isInNavigation failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun turnLeft(reqId: Int, speedDegPerSec: Float, angleDeg: Float?, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "turnLeft onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "turnLeft onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }
        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "turnLeft onResult result=$result msg=$message")
          onResult(result, message, null)
        }
        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "turnLeft onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "turnLeft() calling... reqId=$reqId speed=$speedDegPerSec angle=$angleDeg")
      if (angleDeg != null) RobotApi.getInstance().turnLeft(reqId, speedDegPerSec, angleDeg, listener)
      else RobotApi.getInstance().turnLeft(reqId, speedDegPerSec, listener)
    } catch (t: Throwable) {
      Log.e(tag, "turnLeft failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun turnRight(reqId: Int, speedDegPerSec: Float, angleDeg: Float?, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "turnRight onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "turnRight onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }
        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "turnRight onResult result=$result msg=$message")
          onResult(result, message, null)
        }
        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "turnRight onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "turnRight() calling... reqId=$reqId speed=$speedDegPerSec angle=$angleDeg")
      if (angleDeg != null) RobotApi.getInstance().turnRight(reqId, speedDegPerSec, angleDeg, listener)
      else RobotApi.getInstance().turnRight(reqId, speedDegPerSec, listener)
    } catch (t: Throwable) {
      Log.e(tag, "turnRight failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun stopMove(reqId: Int, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "stopMove onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "stopMove onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }
        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "stopMove onResult result=$result msg=$message")
          onResult(result, message, null)
        }
        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "stopMove onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "stopMove() calling... reqId=$reqId")
      RobotApi.getInstance().stopMove(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "stopMove failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun getChargeStatusCmd(reqId: Int, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "getChargeStatus onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "getChargeStatus onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }

        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "getChargeStatus onResult result=$result msg=$message")
          onResult(result, message, null)
        }

        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "getChargeStatus onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "getChargeStatus() calling... reqId=$reqId")
      RobotApi.getInstance().getChargeStatus(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "getChargeStatus failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun connect(
    activity: Activity,
    onDisabled: () -> Unit,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit,
  ) {
    try {
      Log.i(tag, "connectServer() calling...")

      // Fast-path: already connected
      try {
        if (RobotApi.getInstance().isApiConnectedService()) {
          connected = true
          Log.i(tag, "RobotApi already connected (isApiConnectedService=true)")
          onConnected()
          return
        }
      } catch (t: Throwable) {
        Log.w(tag, "isApiConnectedService check failed", t)
      }

      RobotApi.getInstance().connectServer(activity, object : ApiListener {
        override fun handleApiDisabled() {
          Log.w(tag, "handleApiDisabled")
          connected = false
          onDisabled()
        }

        override fun handleApiConnected() {
          Log.i(tag, "handleApiConnected")
          connected = true

          // Important: register module callback so CoreService can route hardware/control callbacks to this app.
          try {
            RobotApi.getInstance().setCallback(object : ModuleCallbackApi() {
              override fun onRecovery() {
                Log.i(tag, "ModuleCallback onRecovery")
              }

              override fun onSuspend() {
                Log.w(tag, "ModuleCallback onSuspend")
              }
            })
            Log.i(tag, "RobotApi.setCallback(ModuleCallbackApi) done")
          } catch (t: Throwable) {
            Log.w(tag, "RobotApi.setCallback failed", t)
          }

          onConnected()
        }

        override fun handleApiDisconnected() {
          Log.w(tag, "handleApiDisconnected")
          connected = false
          onDisconnected()
        }
      })

      // Some OS builds don't reliably deliver ApiListener callbacks to third-party apps.
      // Add a best-effort polling fallback.
      val h = android.os.Handler(android.os.Looper.getMainLooper())
      val start = android.os.SystemClock.uptimeMillis()
      val poll = object : Runnable {
        override fun run() {
          if (connected) return
          val elapsed = android.os.SystemClock.uptimeMillis() - start
          if (elapsed > 10_000) {
            Log.w(tag, "connect poll timeout (isApiConnectedService never became true)")
            return
          }
          try {
            if (RobotApi.getInstance().isApiConnectedService()) {
              connected = true
              Log.i(tag, "connect poll: isApiConnectedService=true")
              try {
                RobotApi.getInstance().setCallback(object : ModuleCallbackApi() {
                  override fun onRecovery() {
                    Log.i(tag, "ModuleCallback onRecovery")
                  }

                  override fun onSuspend() {
                    Log.w(tag, "ModuleCallback onSuspend")
                  }
                })
                Log.i(tag, "RobotApi.setCallback(ModuleCallbackApi) done (poll path)")
              } catch (t: Throwable) {
                Log.w(tag, "RobotApi.setCallback failed (poll path)", t)
              }
              onConnected()
              return
            }
          } catch (t: Throwable) {
            Log.w(tag, "connect poll check failed", t)
          }
          h.postDelayed(this, 200)
        }
      }
      h.postDelayed(poll, 200)
    } catch (t: Throwable) {
      Log.e(tag, "connect failed", t)
      connected = false
      onDisconnected()
    }
  }

  fun startNavigationToPlace(
    reqId: Int,
    destName: String,
    coordinateDeviation: Double,
    timeoutMs: Long,
    onStatus: (status: Int, data: String?, extraData: String?) -> Unit,
    onResult: (status: Int, response: String?, extraData: String?) -> Unit,
    onError: (errorCode: Int, errorString: String?, extraData: String?) -> Unit,
  ) {
    try {
      if (!connected) {
        onError(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : ActionListener() {
        override fun onStatusUpdate(status: Int, data: String?, extraData: String?) {
          onStatus(status, data, extraData)
        }

        override fun onResult(status: Int, response: String?, extraData: String?) {
          onResult(status, response, extraData)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          onError(errorCode, errorString, extraData)
        }
      }

      RobotApi.getInstance().startNavigation(reqId, destName, coordinateDeviation, timeoutMs, listener)
    } catch (t: Throwable) {
      Log.e(tag, "startNavigation failed", t)
      onError(-1, t.message, null)
    }
  }

  fun stopNavigation(reqId: Int) {
    try {
      if (!connected) return
      RobotApi.getInstance().stopNavigation(reqId)
    } catch (t: Throwable) {
      Log.e(tag, "stopNavigation failed", t)
    }
  }

  // ===== Map management =====

  /**
   * Switch to a different map by map name (e.g. "test_一楼电梯-1107112047").
   * After switching, getPlaceList will return places on the new map.
   */
  fun switchMap(
    reqId: Int,
    mapName: String,
    onResult: (result: Int, message: String?, extraData: String?) -> Unit
  ) {
    try {
      if (!connected) { onResult(-2, "RobotApi not connected", null); return }
      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) { onResult(errorCode, errorString, null) }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) { onResult(errorCode, errorString, extraData) }
        override fun onResult(result: Int, message: String?) { onResult(result, message, null) }
        override fun onResult(result: Int, message: String?, extraData: String?) { onResult(result, message, extraData) }
      }
      Log.i(tag, "switchMap() calling... reqId=$reqId mapName=$mapName")
      RobotApi.getInstance().switchMap(reqId, mapName, listener)
    } catch (t: Throwable) {
      Log.e(tag, "switchMap failed", t)
      onResult(-1, t.message, null)
    }
  }

  /**
   * Get place list for a specific map (not necessarily the current map).
   */
  fun getPlaceListByMapName(
    reqId: Int,
    mapName: String,
    onResult: (result: Int, message: String?, extraData: String?) -> Unit
  ) {
    try {
      if (!connected) { onResult(-2, "RobotApi not connected", null); return }
      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) { onResult(errorCode, errorString, null) }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) { onResult(errorCode, errorString, extraData) }
        override fun onResult(result: Int, message: String?) { onResult(result, message, null) }
        override fun onResult(result: Int, message: String?, extraData: String?) { onResult(result, message, extraData) }
      }
      Log.i(tag, "getPlaceListByMapName() calling... reqId=$reqId mapName=$mapName")
      RobotApi.getInstance().getPlaceListByMapName(reqId, mapName, listener)
    } catch (t: Throwable) {
      Log.e(tag, "getPlaceListByMapName failed", t)
      onResult(-1, t.message, null)
    }
  }

  // ===== Cross-floor (Elevator) Navigation =====

  /**
   * Fetch multi-floor map config and current pose.
   * Result message is a JSON array of MultiFloorInfo objects.
   */
  fun getMultiFloorConfig(
    reqId: Int,
    onResult: (result: Int, message: String?, extraData: String?) -> Unit
  ) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }
      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "getMultiFloorConfig onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "getMultiFloorConfig onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }
        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "getMultiFloorConfig onResult result=$result msgLen=${message?.length ?: 0}")
          onResult(result, message, null)
        }
        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "getMultiFloorConfig onResult result=$result msgLen=${message?.length ?: 0} extra=$extraData")
          onResult(result, message, extraData)
        }
      }
      Log.i(tag, "getMultiFloorConfig() calling... reqId=$reqId")
      RobotApi.getInstance().getMultiFloorConfigAndPose(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "getMultiFloorConfig failed", t)
      onResult(-1, t.message, null)
    }
  }

  /**
   * Start cross-floor elevator navigation.
   *
   * @param destName  Destination point name on the target floor's map
   * @param floorIndex  FloorIndex of the target floor (must be != 0, from getMultiFloorConfig)
   */
  fun startElevatorNavigationToPlace(
    reqId: Int,
    destName: String,
    floorIndex: Int,
    onStatus: (status: Int, data: String?, extraData: String?) -> Unit,
    onResult: (status: Int, response: String?, extraData: String?) -> Unit,
    onError: (errorCode: Int, errorString: String?, extraData: String?) -> Unit,
  ) {
    try {
      if (!connected) {
        onError(-2, "RobotApi not connected", null)
        return
      }
      val listener = object : ActionListener() {
        override fun onStatusUpdate(status: Int, data: String?, extraData: String?) {
          Log.d(tag, "startElevatorNavigation onStatusUpdate status=$status data=$data extra=$extraData")
          onStatus(status, data, extraData)
        }
        override fun onResult(status: Int, response: String?, extraData: String?) {
          Log.i(tag, "startElevatorNavigation onResult status=$status resp=$response extra=$extraData")
          onResult(status, response, extraData)
        }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "startElevatorNavigation onError code=$errorCode err=$errorString extra=$extraData")
          onError(errorCode, errorString, extraData)
        }
      }
      Log.i(tag, "startElevatorNavigation() calling... reqId=$reqId dest=$destName floor=$floorIndex")
      RobotApi.getInstance().startElevatorNavigation(reqId, destName, floorIndex, listener)
    } catch (t: Throwable) {
      Log.e(tag, "startElevatorNavigation failed", t)
      onError(-1, t.message, null)
    }
  }

  /**
   * Stop an in-progress elevator navigation (use only for nav started by startElevatorNavigation).
   */
  fun stopElevatorNavigation(reqId: Int) {
    try {
      if (!connected) return
      RobotApi.getInstance().stopAdvanceNavigation(reqId)
    } catch (t: Throwable) {
      Log.e(tag, "stopElevatorNavigation failed", t)
    }
  }

  fun moveHead(
    reqId: Int,
    hMode: String,
    vMode: String,
    hAngle: Int,
    vAngle: Int,
    onResult: (result: Int, message: String?, extraData: String?) -> Unit
  ) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "moveHead onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "moveHead onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }

        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "moveHead onResult result=$result msg=$message")
          onResult(result, message, null)
        }

        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "moveHead onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "moveHead() calling... reqId=$reqId hMode=$hMode vMode=$vMode hAngle=$hAngle vAngle=$vAngle")
      RobotApi.getInstance().moveHead(reqId, hMode, vMode, hAngle, vAngle, listener)
    } catch (t: Throwable) {
      Log.e(tag, "moveHead failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun resetHead(reqId: Int, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "resetHead onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "resetHead onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }

        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "resetHead onResult result=$result msg=$message")
          onResult(result, message, null)
        }

        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "resetHead onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "resetHead() calling... reqId=$reqId")
      RobotApi.getInstance().resetHead(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "resetHead failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun getPlaceList(reqId: Int, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        try {
          if (RobotApi.getInstance().isApiConnectedService()) {
            connected = true
            Log.i(tag, "getPlaceList: recovered connected via isApiConnectedService")
          }
        } catch (t: Throwable) {
          Log.w(tag, "getPlaceList: isApiConnectedService check failed", t)
        }
      }
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      val listener = object : CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "getPlaceList onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "getPlaceList onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }

        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "getPlaceList onResult result=$result msgLen=${message?.length ?: 0}")
          onResult(result, message, null)
        }

        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "getPlaceList onResult result=$result msgLen=${message?.length ?: 0} extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "getPlaceList() calling... reqId=$reqId")
      RobotApi.getInstance().getPlaceList(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "getPlaceList failed", t)
      onResult(-1, t.message, null)
    }
  }

  /**
   * Force-set the robot's pose estimate (manual relocalization).
   * Use when the robot's internal position has drifted from reality.
   * @param x  X coordinate on the current map
   * @param y  Y coordinate on the current map
   * @param theta  Heading angle in radians
   */
  fun setPoseEstimate(
    reqId: Int,
    x: Double,
    y: Double,
    theta: Double,
    onResult: (result: Int, message: String?, extraData: String?) -> Unit
  ) {
    try {
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }
      val params = org.json.JSONObject().apply {
        put("navi_position_x", x)
        put("navi_position_y", y)
        put("navi_position_theta", theta)
      }
      val listener = object : com.ainirobot.coreservice.client.listener.CommandListener() {
        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "setPoseEstimate onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }
        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "setPoseEstimate onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }
        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "setPoseEstimate onResult result=$result msg=$message")
          onResult(result, message, null)
        }
        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "setPoseEstimate onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }
      Log.i(tag, "setPoseEstimate() calling... reqId=$reqId x=$x y=$y theta=$theta")
      RobotApi.getInstance().setPoseEstimate(reqId, params.toString(), listener)
    } catch (t: Throwable) {
      Log.e(tag, "setPoseEstimate failed", t)
      onResult(-1, t.message, null)
    }
  }

  fun getPosition(reqId: Int, onResult: (result: Int, message: String?, extraData: String?) -> Unit) {
    try {
      if (!connected) {
        // Last chance: check actual service connection
        try {
          if (RobotApi.getInstance().isApiConnectedService()) {
            connected = true
            Log.i(tag, "getPosition: recovered connected via isApiConnectedService")
          }
        } catch (t: Throwable) {
          Log.w(tag, "getPosition: isApiConnectedService check failed", t)
        }
      }
      if (!connected) {
        onResult(-2, "RobotApi not connected", null)
        return
      }

      // Readiness signals (best-effort)
      try {
        val active = RobotApi.getInstance().isActive()
        Log.i(tag, "getPosition: isActive=$active")
      } catch (t: Throwable) {
        Log.w(tag, "getPosition: isActive check failed", t)
      }
      try {
        val ready = RobotApi.getInstance().isNavigationReady()
        Log.i(tag, "getPosition: isNavigationReady=$ready")
      } catch (t: Throwable) {
        Log.w(tag, "getPosition: isNavigationReady check failed", t)
      }

      val listener = object : CommandListener() {
        override fun onStatusUpdate(status: Int, data: String?) {
          Log.d(tag, "getPosition status=$status data=$data")
        }

        override fun onStatusUpdate(status: Int, data: String?, extraData: String?) {
          Log.d(tag, "getPosition status=$status data=$data extra=$extraData")
        }

        override fun onError(errorCode: Int, errorString: String?) {
          Log.e(tag, "getPosition onError code=$errorCode err=$errorString")
          onResult(errorCode, errorString, null)
        }

        override fun onError(errorCode: Int, errorString: String?, extraData: String?) {
          Log.e(tag, "getPosition onError code=$errorCode err=$errorString extra=$extraData")
          onResult(errorCode, errorString, extraData)
        }

        override fun onResult(result: Int, message: String?) {
          Log.i(tag, "getPosition onResult result=$result msg=$message")
          onResult(result, message, null)
        }

        override fun onResult(result: Int, message: String?, extraData: String?) {
          Log.i(tag, "getPosition onResult result=$result msg=$message extra=$extraData")
          onResult(result, message, extraData)
        }
      }

      Log.i(tag, "getPosition() calling... reqId=$reqId")
      RobotApi.getInstance().getPosition(reqId, listener)
    } catch (t: Throwable) {
      Log.e(tag, "getPosition failed", t)
      onResult(-1, t.message, null)
    }
  }
}
