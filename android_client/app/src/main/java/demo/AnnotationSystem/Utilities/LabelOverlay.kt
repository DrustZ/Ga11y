package demo.AnnotationSystem.Utilities

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import demo.AnnotationSystem.Solution.LabelSolution


class LabelOverlay {
    var anchorNode: AccessibilityNodeInfo? = null
    var accessibilityNode: AccessibilityNodeInfo? = null
    var overlay: LinearLayout
    var correctLabelButton: Button
    var bounds = Rect()

    constructor(labelHelper: LabelHelper,
                context: Context,
                label: String,
                accessibilityNode: AccessibilityNodeInfo? = null,
                anchorNode: AccessibilityNodeInfo? = null,
                customBounds: Rect? = null) {
        overlay = LinearLayout(context)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.setBackgroundColor(Color.argb(255, 0, 0, 100))
        overlay.alpha = 0.02.toFloat()

        if (anchorNode != null) {
            this.anchorNode = anchorNode
        }
        if (accessibilityNode != null) {
            this.accessibilityNode = accessibilityNode
            accessibilityNode.getBoundsInScreen(bounds)
        }
        if (customBounds != null) {
            bounds = customBounds
        }

        val params = WindowManager.LayoutParams(
                10, //bounds.width(),
                10, // bounds.height()
                bounds.left,
                bounds.top - gStatusBarHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.LEFT
        gWindowManager!!.addView(overlay, params)

        val prev = LinearLayout(context)
        prev.isFocusable = true
        prev.layoutParams = LinearLayout.LayoutParams(10, 1)
        prev.setBackgroundColor(Color.argb(150, 0, 0, 180))
        prev.setPadding(0, 0, 0, 0)

        val next = LinearLayout(context)
        next.isFocusable = true
        next.layoutParams = LinearLayout.LayoutParams(10, 2)
        next.setBackgroundColor(Color.argb(150, 180, 0, 0))
        next.setPadding(0, 0, 0, 0)

        correctLabelButton = Button(context)
        correctLabelButton.setPadding(0, 0, 0, 0)
        correctLabelButton.text = label
        correctLabelButton.setBackgroundColor(Color.TRANSPARENT)
        correctLabelButton.layoutParams = LinearLayout.LayoutParams(bounds.width(), 10 - 3)

        correctLabelButton.setOnClickListener { view ->
            val labelSolution = context as LabelSolution
            labelSolution.handleBuffer()
//            labelHelper.reset()
//            performTwoFingerClick(context, bounds.centerX(), bounds.centerY())
        }

        overlay.addView(prev)
        overlay.addView(correctLabelButton)
        overlay.addView(next)
    }
}