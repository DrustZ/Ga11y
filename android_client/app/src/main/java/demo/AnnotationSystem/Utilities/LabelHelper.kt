package demo.AnnotationSystem.Utilities


import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonObject
import demo.AnnotationSystem.Identifier.*

import java.util.ArrayList

class LabelHelper {
    var inSwipeNavigationMode = true
    var shouldReturnToAnchor = false

    var anchorNode: AccessibilityNodeInfo? = null
    var focusedNode: AccessibilityNodeInfo? = null

    var context: Context? = null
    var clientScreen: Screen? = null
    var remoteScreen: Screen? = null
    var overlayList = ArrayList<LabelOverlay>()

    constructor() { }

    constructor(context: Context, clientScreen: Screen, remoteScreen: Screen) {
        this.context = context
        this.clientScreen = clientScreen
        this.remoteScreen = remoteScreen

        for (label in remoteScreen.addedLabelList) {
            val remoteNode = remoteScreen.elementList.find { it.id == label.first }
            if (remoteNode == null) return

            val clientNode = getElementByRemoteElement(clientScreen, remoteScreen, remoteNode)  // TODO: change to JSON
            if (clientNode == null || clientNode.accessibilityNode == null) return

            overlayList.add(LabelOverlay(this, context, label.second, clientNode.accessibilityNode))
        }
        // Add extra element on screen

    }

    constructor(context: Context, root: AccessibilityNodeInfo, templates: ArrayList<JsonObject>) {
        this.context = context

        for (template in templates) {
            val changesObj = template.getAsJsonObject("changes")
            var contentDesc = ""
            if (changesObj.has("contentDesc")) {
                contentDesc = changesObj.getAsJsonPrimitive("contentDesc").asString
            }

            val nodeInfo = getElementByJson(root, template)
            if (nodeInfo == null) return

            var bounds = Rect()
            nodeInfo.getBoundsInScreen(bounds)
            Log.e("NodeInfo", "" + bounds + nodeInfo.contentDescription + "," + nodeInfo.viewIdResourceName)

            overlayList.add(LabelOverlay(this, context, contentDesc, nodeInfo))
        }
    }

    constructor(context: Context, node: AccessibilityNodeInfo, newInfo: String) {
        this.context = context

        var contentDesc = newInfo

        var bounds = Rect()
        node.getBoundsInScreen(bounds)
        Log.e("NodeInfo", "" + bounds + node.contentDescription + "," + node.viewIdResourceName)

        overlayList.add(LabelOverlay(this, context, contentDesc, node))
    }

    fun reset() {
        Log.i("DemoLog", "Reset, hasContext: " + (this.context == null))
        for (overlay in overlayList) {
            try {
                gWindowManager!!.removeView(overlay.overlay)
            } catch (e: IllegalArgumentException) { }
        }
        overlayList = arrayListOf()
//        if (this.context != null) {
//            this.overlayList.add(LabelOverlay(this, this.context!!, "ZXY", null, null, Rect(200, 300, 300, 500)))
//        }
    }
}

