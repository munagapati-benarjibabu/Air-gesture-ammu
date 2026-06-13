package com.example.projectammu

import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

class AmmuAccessibilityService : AccessibilityService() {

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(EXTRA_COMMAND) ?: return
            Log.d(TAG, "Received command by broadcast: $command")
            performAmmuCommand(command)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        val filter = IntentFilter(ACTION_ACCESSIBILITY_COMMAND)

        ContextCompat.registerReceiver(
            this,
            commandReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }

        runCatching {
            unregisterReceiver(commandReceiver)
        }
        super.onDestroy()
    }

    fun performAmmuCommand(command: String): Boolean {
        Log.d(TAG, "Performing command: $command")

        return when (command) {
            COMMAND_SCROLL_DOWN -> {
                scrollDown()
                true
            }

            COMMAND_SCROLL_UP -> {
                scrollUp()
                true
            }

            COMMAND_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            COMMAND_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            COMMAND_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            else -> false
        }
    }

    private fun scrollDown() {
        if (!scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            Log.d(TAG, "Node scroll down failed, using gesture swipe")
            swipeVertical(fromYRatio = 0.82f, toYRatio = 0.18f)
        } else {
            Log.d(TAG, "Node scroll down performed")
        }
    }

    private fun scrollUp() {
        if (!scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            Log.d(TAG, "Node scroll up failed, using gesture swipe")
            swipeVertical(fromYRatio = 0.18f, toYRatio = 0.82f)
        } else {
            Log.d(TAG, "Node scroll up performed")
        }
    }

    private fun scroll(action: Int): Boolean {
        val scrollableNode = rootInActiveWindow?.let(::findScrollableNode)
        return scrollableNode?.performAction(action) == true
    }

    private fun swipeVertical(fromYRatio: Float, toYRatio: Float) {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val fromY = metrics.heightPixels * fromYRatio
        val toY = metrics.heightPixels * toYRatio

        val path = Path().apply {
            moveTo(centerX, fromY)
            lineTo(centerX, toY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450))
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Fallback swipe completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Fallback swipe cancelled")
                }
            },
            null
        )

        Log.d(TAG, "Fallback swipe dispatched=$dispatched")
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val scrollableChild = findScrollableNode(child)
            if (scrollableChild != null) {
                return scrollableChild
            }
        }

        return null
    }

    companion object {
        const val ACTION_ACCESSIBILITY_COMMAND =
            "com.example.projectammu.action.ACCESSIBILITY_COMMAND"
        const val EXTRA_COMMAND = "extra_command"

        const val COMMAND_SCROLL_DOWN = "scroll_down"
        const val COMMAND_SCROLL_UP = "scroll_up"
        const val COMMAND_HOME = "home"
        const val COMMAND_BACK = "back"
        const val COMMAND_RECENTS = "recents"
        private const val TAG = "AmmuAccessibility"

        private var activeService: AmmuAccessibilityService? = null

        fun performIfConnected(command: String): Boolean {
            return activeService?.performAmmuCommand(command) == true
        }
    }
}
