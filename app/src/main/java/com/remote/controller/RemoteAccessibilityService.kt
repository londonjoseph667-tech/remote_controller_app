package com.remote.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RemoteAccessibilityService : AccessibilityService() {

    private var server: RemoteServer? = null
    private var nsdHelper: NsdHelper? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.d("RemoteAccessibility", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("RemoteAccessibility", "Service Connected")

        val port = 8888
        server = RemoteServer(port) { command ->
            handleCommand(command)
        }
        server?.start()

        nsdHelper = NsdHelper(this)
        nsdHelper?.registerService(port, "${Build.MODEL} (Android Remote)")
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        nsdHelper?.unregisterService()
    }

    private fun handleCommand(command: String) {
        Log.d("RemoteAccessibility", "Received command: $command")
        when {
            command == "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            command == "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            command == "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            command.startsWith("CLICK:") -> {
                val coords = command.removePrefix("CLICK:").split(",")
                if (coords.size == 2) {
                    val x = coords[0].toFloatOrNull() ?: 0f
                    val y = coords[1].toFloatOrNull() ?: 0f
                    clickAt(x, y)
                }
            }
        }
    }

    private fun clickAt(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        dispatchGesture(gestureBuilder.build(), null, null)
    }
}
