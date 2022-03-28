package demo.AnnotationSystem.Identifier

import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityProperties {
    var accessibilityNodeInfoToString = ""

    var isAccessibilityFocused = false
    var isCheckable = false
    var isChecked = false
    var isClickable = false
    var isContentInvalid = false
    var isContextClickable = false
    var isDismissable = false
    var isEditable = false
    var isEnabled = false
    var isFocusable = false
    var isFocused = false
    var isImportantForAccessibility = false
    var isLongClickable = false
    var isMultiLine = false
    var isPassword = false
    var isScrollable = false
    var isSelected = false
    //var isShowingHintText = false // API 26
    var isVisibleToUser = true

    constructor() { }

    constructor(node: AccessibilityNodeInfo) {
        accessibilityNodeInfoToString = node.toString()

        isAccessibilityFocused = node.isAccessibilityFocused
        isCheckable = node.isCheckable
        isChecked = node.isChecked
        isClickable = node.isClickable
        isContentInvalid = node.isContentInvalid
        isContextClickable = node.isContextClickable
        isDismissable = node.isDismissable
        isEditable = node.isEditable
        isEnabled = node.isEnabled
        isFocusable = node.isFocusable
        isFocused = node.isFocused
        isImportantForAccessibility = node.isImportantForAccessibility
        isLongClickable = node.isLongClickable
        isMultiLine = node.isMultiLine
        isPassword = node.isPassword
        isScrollable = node.isScrollable
        isSelected = node.isSelected
        isVisibleToUser = node.isVisibleToUser
    }
}
