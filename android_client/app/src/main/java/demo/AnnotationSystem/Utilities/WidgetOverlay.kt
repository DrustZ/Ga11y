package demo.AnnotationSystem.Utilities

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.View
import android.widget.FrameLayout


class WidgetOverlay {
    var accessibilityNode: AccessibilityNodeInfo
    var overlay: FrameLayout

    constructor(context: Context,
                accessibilityNode: AccessibilityNodeInfo,
                customWidget: Widget) {
        overlay = FrameLayout(context)
        overlay.setBackgroundColor(Color.argb(255, 255, 0, 0))
        overlay.alpha = 0.2f
        Log.i("DemoLog", "Overlay")

        this.accessibilityNode = accessibilityNode

        val bounds = Rect()
        this.accessibilityNode.getBoundsInScreen(bounds)

        val params = WindowManager.LayoutParams(
                bounds.width(),
                bounds.height(),
                bounds.left,
                bounds.top - gStatusBarHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.LEFT
        gWindowManager!!.addView(overlay, params)

        overlay.contentDescription = customWidget.altText

        for (widgetChild in customWidget.children) {
            val childView = View(context)
            childView.contentDescription = widgetChild.altText
            childView.setBackgroundColor(Color.argb(255, 0, 255, 0))
            childView.setClickable(true)

            val width = (bounds.width() * widgetChild.width).toInt()
            val height = (bounds.height() * widgetChild.height).toInt()
            val childParams = FrameLayout.LayoutParams(width, height)
            childParams.leftMargin = (bounds.width() * widgetChild.left).toInt()
            childParams.topMargin = (bounds.height() * widgetChild.top).toInt()
            childView.layoutParams = childParams

            overlay.addView(childView, childParams)

            childView.setOnClickListener({ _ ->
                gWindowManager!!.removeView(overlay)
                val centerX = bounds.left + childParams.leftMargin + width / 2
                val centerY = bounds.top + childParams.topMargin + height / 2
                performTwoFingerClick(context, centerX, centerY)
            })

        }
    }
}