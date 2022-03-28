package demo.AnnotationSystem.Solution

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import demo.AnnotationSystem.Identifier.AppId
import demo.AnnotationSystem.Identifier.Identifier

import demo.AnnotationSystem.Utilities.*


class NavigationSolution : AccessibilityService() {
    private var navigationHelper = NavigationHelper()
    private var appId: AppId? = null

    override fun onCreate() {
        super.onCreate()

        setupUtils(this)

        loadAllData()
    }

    private fun updateAppId(event: AccessibilityEvent, context: Context) {
        val packageName = event.packageName.toString()
        val versionCode = getVersionCode(context, packageName)

        if (appId == null || appId!!.packageName != packageName || appId!!.versionCode != versionCode) {
            appId = Identifier.getAppId(packageName, versionCode)
        }
    }

    private fun onWindowChange(event: AccessibilityEvent, context: Context) {
        updateAppId(event, context)

        //navigationHelper.reset()

        var delay: Long = 500

        android.os.Handler().postDelayed({
            if (rootInActiveWindow != null && appId != null && navigationHelper.navigationOverlay == null) {
                val screenPair = appId!!.getTemplateScreen(rootInActiveWindow, event, context)
                var remoteScreen = screenPair.second
                if (remoteScreen != null && remoteScreen.navigationOrderList.size != 0) {
                    navigationHelper = NavigationHelper(context, screenPair.first, screenPair.second!!)
                }
            }
        }, delay)
    }

    private fun onProxiesFocused(event: AccessibilityEvent) {
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (getEventSource(event)) {
            EventSource.EMPTY -> return
            EventSource.PROXIES -> {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    onProxiesFocused(event)
                }
            }
            EventSource.APP -> {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindowChange(event, this)
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> onWindowChange(event, this)
                }
            }
            else -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    //navigationHelper.reset()
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean { return true }
    override fun onInterrupt() {}
}