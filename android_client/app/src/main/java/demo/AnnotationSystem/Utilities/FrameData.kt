package demo.AnnotationSystem.Utilities

import android.graphics.Bitmap

class FrameData(val image: Bitmap?, val timestamp: Long) {
    var filename: String? = null
    var saved = false
}
