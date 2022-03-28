package demo.AnnotationSystem.Utilities


import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
import demo.AnnotationSystem.Identifier.Screen

class NavigationHelper {
    var context: Context? = null
    var clientScreen: Screen? = null
    var remoteScreen: Screen? = null
    var navigationOverlay: NavigationOverlay? = null

    constructor() { }

    constructor(context: Context, clientScreen: Screen, remoteScreen: Screen, forSwitch: Boolean = false) {
        this.context = context
        this.clientScreen = clientScreen
        this.remoteScreen = remoteScreen

        navigationOverlay = NavigationOverlay(context, clientScreen, remoteScreen, forSwitch)

        // Focus 1st element for Talkback
        android.os.Handler().postDelayed({
            if (!forSwitch && navigationOverlay!!.navigationElementList.size != 0) {
                navigationOverlay!!.navigationElementList[0].performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS, null)
            }
        }, 500)
    }

    fun reset() {
        try {
            if (this.navigationOverlay == null) { return }
            gWindowManager!!.removeView(this.navigationOverlay!!.fullOverlay)
            this.navigationOverlay = null
        } catch (e: IllegalArgumentException) {
            // When overlay is removed after click
            if (this.navigationOverlay != null) { this.navigationOverlay = null }
        }
    }
}
