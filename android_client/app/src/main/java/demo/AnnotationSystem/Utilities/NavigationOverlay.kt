package demo.AnnotationSystem.Utilities

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import demo.AnnotationSystem.Identifier.*
import demo.AnnotationSystem.TalkbackUtilities.NodeSpeechRuleProcessor


class NavigationOverlay {
    var fullOverlay: FrameLayout
    var navigationElementList = ArrayList<View>()
    var overlayParams = WindowManager.LayoutParams(
            gScreenW,
            gScreenH,
            0,
            0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

    constructor(context: Context,
                clientScreen: Screen,
                remoteScreen: Screen,
                forSwitch: Boolean) {
        fullOverlay = FrameLayout(context)
        fullOverlay.setBackgroundColor(Color.argb(0, 0, 180, 0))
        fullOverlay.alpha = 0.2.toFloat()

        navigationElementList = ArrayList<View>()

        overlayParams.gravity = Gravity.TOP or Gravity.LEFT
        gWindowManager!!.addView(fullOverlay, overlayParams)

        if (forSwitch) {
            createOverlaysForSwitch(context, clientScreen, remoteScreen)
        } else {
            createOverlaysForTalkback(context, clientScreen, remoteScreen)
        }
    }

    fun createOverlaysForTalkback(context: Context,
                                  clientScreen: Screen,
                                  remoteScreen: Screen) {
        for (elementId in remoteScreen.navigationOrderList) {
            val remoteElement = remoteScreen.elementList.find { it.id == elementId }
            if (remoteElement != null) {
                val clientNode = getElementByRemoteElement(clientScreen, remoteScreen, remoteElement)
                if (clientNode != null) {
                    val button = Button(context)
                    button.setBackgroundColor(Color.argb(0, 255, 0, 0))
                    button.alpha = 0.3.toFloat()
                    val node = AccessibilityNodeInfoCompat.wrap(clientNode.accessibilityNode!!)
                    button.contentDescription = NodeSpeechRuleProcessor.getDescriptionForTree(context, node,null, node)
                    button.id = elementId.toInt() + 100
                    navigationElementList.add(button)

                    val bounds = clientNode.boundsInScreen
                    val elementParams = FrameLayout.LayoutParams(bounds.width(), bounds.height())
                    elementParams.leftMargin = bounds.left
                    elementParams.topMargin = bounds.top - gStatusBarHeight
                    fullOverlay.addView(button, elementParams)

                    button.setOnClickListener({ _ ->
                        gWindowManager!!.removeView(fullOverlay)
                        performTwoFingerClick(context, bounds.centerX(), bounds.centerY())
                    })
                }
            }
        }

        for (i in 0..navigationElementList.size - 1) {
            if (i == 0) {
                navigationElementList[i].accessibilityTraversalAfter = navigationElementList[navigationElementList.size - 1].id
                navigationElementList[i].accessibilityTraversalBefore = navigationElementList[i+1].id
            } else if (i == navigationElementList.size - 1) {
                navigationElementList[i].accessibilityTraversalAfter = navigationElementList[i-1].id
                navigationElementList[i].accessibilityTraversalBefore = navigationElementList[0].id
            } else {
                navigationElementList[i].accessibilityTraversalAfter = navigationElementList[i-1].id
                navigationElementList[i].accessibilityTraversalBefore = navigationElementList[i+1].id
            }
        }
    }

    fun createOverlaysForSwitch(context: Context,
                                clientScreen: Screen,
                                remoteScreen: Screen) {
        fullOverlay.setOnClickListener({ _ ->
            // Get focus
        })

        for (elementId in remoteScreen.navigationOrderList) {
            val remoteElement = remoteScreen.elementList.find { it.id == elementId }
            if (remoteElement != null) {
                val clientNode = getElementByRemoteElement(clientScreen, remoteScreen, remoteElement)
                if (clientNode != null) {
                    val button = View(context)
                    button.setBackgroundColor(Color.RED)
                    button.alpha = 0.3.toFloat()
                    val node = AccessibilityNodeInfoCompat.wrap(clientNode.accessibilityNode!!)
                    button.contentDescription = NodeSpeechRuleProcessor.getDescriptionForTree(context, node,null, node)
                    button.id = elementId.toInt() + 100
                    navigationElementList.add(button)

                    val bounds = clientNode.boundsInScreen
                    overlayParams.width = bounds.width()
                    overlayParams.height = bounds.height()
                    overlayParams.x = bounds.left
                    overlayParams.y = bounds.top - gStatusBarHeight
                    gWindowManager!!.addView(button, overlayParams)

                    button.setOnClickListener({ _ ->
                        Log.i("DemoLog", "Clicked")
                        for (v in navigationElementList) {
                            gWindowManager!!.removeView(v)
                        }
                        //gWindowManager!!.removeView(fullOverlay)
                        android.os.Handler().postDelayed({
                            performTwoFingerClick(context, bounds.centerX(), bounds.centerY())
                        }, 500)
                    })
                }
            }
        }

        for (i in 0..navigationElementList.size - 1) {
            if (i == 0) {
                navigationElementList[i].accessibilityTraversalAfter = navigationElementList[navigationElementList.size - 1].id
                navigationElementList[i].accessibilityTraversalBefore = navigationElementList[i+1].id
            } else if (i == navigationElementList.size - 1) {
                navigationElementList[i].accessibilityTraversalAfter = navigationElementList[i-1].id
                navigationElementList[i].accessibilityTraversalBefore = navigationElementList[0].id
            } else {
                navigationElementList[i].accessibilityTraversalAfter = navigationElementList[i-1].id
                navigationElementList[i].accessibilityTraversalBefore = navigationElementList[i+1].id
            }
        }
    }
}