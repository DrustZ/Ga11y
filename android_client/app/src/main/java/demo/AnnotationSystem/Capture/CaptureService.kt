package demo.AnnotationSystem.Capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityButtonController
import android.media.Ringtone
import android.media.RingtoneManager

import demo.AnnotationSystem.Identifier.Identifier
import demo.AnnotationSystem.Identifier.AppId
import demo.AnnotationSystem.Utilities.*


class CaptureService : AccessibilityService() {
    private var appId: AppId? = null
    private var activityName: String? = null

    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        setupUtils(this)

        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        if (appId != null) {
            appId!!.initNewTrace();
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val context = this
        val mAccessibilityButtonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                captureScreen(context)
                /*val n = windows
                val n = getAllNodes(AccessibilityNodeInfoCompat.wrap(rootInActiveWindow))
                Log.i("XXX", "#Window: " + n.size.toString())
                for (nn in n) {
                    Log.i("XXX", nn.toString())
                }*/
            }
        }
        accessibilityButtonController.registerAccessibilityButtonCallback(mAccessibilityButtonCallback)
    }


    private fun onScreenChanged(context: Context, event: AccessibilityEvent) {
        val packageName = event.packageName.toString()
        val versionCode = getVersionCode(context, packageName)
        appId = Identifier.getAppId(packageName, versionCode)

        updateActivityName(context, event)
    }

    private fun onContentChanged(context: Context, event: AccessibilityEvent) {
        updateActivityName(context, event)
    }

    private fun updateActivityName(context: Context, event: AccessibilityEvent) {
        activityName = getActivityName(context, event)
        if (activityName != null) {
            appId?.mostRecentActivityName = activityName!!
        }
    }

    private fun captureScreen(context: Context) {
        Log.i("DemoLog", "captureScreen")
        appId?.addWindow(rootInActiveWindow, activityName, context)
        if (ringtone != null) {
            ringtone!!.play()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (getEventSource(event)) {
            EventSource.EMPTY -> return
            EventSource.PROXIES -> return
            EventSource.APP -> {
                val activityName = getActivityName(this, event)
                if (activityName == null) {
                    Log.i("DemoLog", "Null")
                } else {
                    Log.i("DemoLog", activityName)
                }
                when (event.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onScreenChanged(this, event)
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onContentChanged(this, event)
                }
            }
            else -> return
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            //captureScreen(this)
            return true
        }
        return true
    }


    override fun onInterrupt() {}
}
