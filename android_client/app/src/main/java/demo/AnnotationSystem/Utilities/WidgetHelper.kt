package demo.AnnotationSystem.Utilities


import android.content.Context
import android.util.Log
import demo.AnnotationSystem.Identifier.*

class WidgetHelper {
    var context: Context? = null
    var clientScreen: Screen? = null
    var remoteScreen: Screen? = null
    var widgetOverlay: WidgetOverlay? = null

    constructor() { }

    constructor(context: Context, clientScreen: Screen, remoteScreen: Screen) {
        this.context = context
        this.clientScreen = clientScreen
        this.remoteScreen = remoteScreen

        val customWidget = remoteScreen.customWidget
        Log.i("DemoLog", "No Widget")
        Log.i("DemoLog", remoteScreen.id)
        if (customWidget == null) return

        val remoteNode = remoteScreen.elementList.find { it.id == customWidget.elementId }
        if (remoteNode == null) return
        Log.i("DemoLog", "Find remote node")

        val anchorNode = getElementByRemoteElement(clientScreen, remoteScreen, remoteNode)
        if (anchorNode == null || anchorNode.accessibilityNode == null) return
        Log.i("DemoLog", "Find anchor")

        this.widgetOverlay = WidgetOverlay(context, anchorNode.accessibilityNode!!, customWidget)
    }

    fun reset() {
        try {
            if (this.widgetOverlay == null) { return }
            gWindowManager!!.removeView(this.widgetOverlay!!.overlay)
            this.widgetOverlay = null
        } catch (e: IllegalArgumentException) {
            // When overlay is removed after click
            if (this.widgetOverlay != null) { this.widgetOverlay = null }
        }
    }
}


class WidgetChild {
    var left = 0.0
    var top = 0.0
    var width = 0.0
    var height = 0.0
    var altText = ""

    constructor() { }

    constructor(left: Double, top: Double, width: Double, height: Double, altText: String) {
        this.left = left
        this.top = top
        this.width = width
        this.height = height
        this.altText = altText
    }
}

class Widget {
    var screenId = ""
    var elementId = ""
    var altText = ""
    var children = ArrayList<WidgetChild>()

    constructor(screenId: String, elementId: String, altText: String, children: ArrayList<WidgetChild>) {
        this.screenId = screenId
        this.elementId = elementId
        this.altText = altText
        this.children = children
    }
}
