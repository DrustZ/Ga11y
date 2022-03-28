package demo.AnnotationSystem.Utilities

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import demo.AnnotationSystem.Identifier.Identifier
import demo.AnnotationSystem.TalkbackUtilities.AccessibilityNodeInfoUtils
import demo.AnnotationSystem.TalkbackUtilities.NodeSpeechRuleProcessor
import demo.AnnotationSystem.TalkbackUtilities.OrderedTraversalController
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*


val PROXIES_PACKAGE_NAME = "demo.AnnotationSystem"
val APPID_DB = "AppIdDB_20181105James"
val SCREEN_DB = "ScreenDB_20181105James"
val ELEMENT_DB = "ElementDB_20181105James"

var gStatusBarHeight = 0
var gNavBarHeight = 0
var gScreenW = 0
var gScreenH = 0

var gWindowManager: WindowManager? = null
var gMediaProjectionManager: MediaProjectionManager? = null
var gImageReader: ImageReader? = null
var gVirtualDisplay: VirtualDisplay? = null

var gImageBuffer: LinkedList<FrameData> = LinkedList()
var gCropBounds = Rect()    // set by LabelSolution, used by ScreenCaptureImageActivity
var gFullSizeImage: Bitmap? = null
var gOldBufferPair: FrameData? = null
var gImageByteStream: LinkedList<Pair<Long, ByteArrayOutputStream>> = LinkedList()
var gShouldStartBuffer = false
const val SCALE_FACTOR = 2.0
const val BUFFER_LENGTH = 6000L
const val BUFFER_LOCK = "BufferLock"


fun getStatusBarHeight(context: Context): Int {
    var result = 0
    val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resourceId > 0) {
        result = context.resources.getDimensionPixelSize(resourceId)
    }
    return result
}

fun getNavBarHeight(context: Context): Int {
    var result = 0
    val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    if (resourceId > 0) {
        result = context.resources.getDimensionPixelSize(resourceId)
    }
    return result
}

fun setupUtils(context: Context) {
    gWindowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
    gMediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    gStatusBarHeight = getStatusBarHeight(context)
    gNavBarHeight = getNavBarHeight(context)

    val size = Point()
    val display = gWindowManager!!.defaultDisplay
    display.getRealSize(size)
    gScreenW = size.x
    gScreenH = size.y
}

fun shouldIgnoreEvent(event: AccessibilityEvent): Boolean {
    if (event.packageName == null) {
        return true
    }
    if (event.packageName.toString().contains("com.google.android")
            || event.packageName.toString().contains("com.android")
            || event.packageName.toString() == "android") {
        return true
    }
    return if (event.packageName == PROXIES_PACKAGE_NAME) {
        true
    } else false
}

fun getScreenShot(): ByteArray? {
    if (gImageReader == null) return null
    val image = gImageReader!!.acquireLatestImage()
    if (image == null || image.getPlanes() == null) return null

    val plane = image.getPlanes()[0] ?: return null

    val buffer = plane.getBuffer()
    val pixelStride = plane.getPixelStride()
    val rowPadding = plane.getRowStride() - pixelStride * gScreenW

    val bmp = Bitmap.createBitmap(gScreenW + rowPadding / pixelStride, gScreenH, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buffer)
    image.close()

    val croppedBmp = Bitmap.createBitmap(bmp, 0, 0, gScreenW, gScreenH)

    val stream = ByteArrayOutputStream()
    croppedBmp.compress(Bitmap.CompressFormat.JPEG, 50, stream)
    return stream.toByteArray()
}



fun getVersionCode(context: Context, packageName: String): Int {
    val pm = context.packageManager
    try {
        val info = pm.getPackageInfo(packageName, 0)
        //Log.i("DemoLog", "VersionName: "+info.versionName);
        return info.versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }

    return -1
}

fun getVersionName(context: Context, packageName: String): String {
    val pm = context.packageManager
    try {
        val info = pm.getPackageInfo(packageName, 0)
        //Log.i("DemoLog", "VersionName: "+info.versionName);
        return info.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }

    return ""
}

fun getActivityName(context: Context, event: AccessibilityEvent): String? {
    if (event.packageName != null && event.className != null) {
        val componentName = ComponentName(
                event.packageName.toString(),
                event.className.toString()
        )
        try {
            // If it is an activity, className is activityName
            if (context.packageManager.getActivityInfo(componentName, 0) != null) {
                return componentName.className
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }

    }
    return null
}

fun formatForFirebase(packageName: String): String {
    return packageName.replace('.', '-')
}


fun sendEmptyAnnouncement(context: Context) {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    if (manager.isEnabled) {
        val e = AccessibilityEvent.obtain()
        e.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
        e.text.add("")
        manager.sendAccessibilityEvent(e)
    }

}

/**
 * Obtain a list of nodes in the order TalkBack would traverse them
 *
 * @param root The root of the tree to traverse
 * @return The nodes on screen in the order TalkBack would traverse them.
 */
fun getNodesInTalkBackOrder(root: AccessibilityNodeInfo, removeEmpty: Boolean = false, context: Context? = null): ArrayList<AccessibilityNodeInfoCompat> {
    val outList = ArrayList<AccessibilityNodeInfoCompat>()
    val traversalController = OrderedTraversalController()
    traversalController.initOrder(AccessibilityNodeInfoCompat.wrap(root), true)
    var node = traversalController.findFirst()
    while (node != null) {
        // When it is focusable(speakable) by Talkback
        if (AccessibilityNodeInfoUtils.shouldFocusNode(node)) {
            if (removeEmpty) {
                val contentDescription = NodeSpeechRuleProcessor.getDescriptionForTree(context, node, null, null)
                if (contentDescription != null && contentDescription.isNotEmpty()) {
                    outList.add(node)
                }
            } else {
                outList.add(node)
            }
        }
        node = traversalController.findNext(node)
    }
    traversalController.recycle()

    return outList
}

fun getAllNodes(info: AccessibilityNodeInfoCompat): ArrayList<AccessibilityNodeInfoCompat> {
    val allNodeList = ArrayList<AccessibilityNodeInfoCompat>()
    getAllNodesHelper(allNodeList, info)
    return allNodeList
}

fun getAllNodesHelper(unlabeledNodeList: ArrayList<AccessibilityNodeInfoCompat>,
                      info: AccessibilityNodeInfoCompat?) {
    if (info == null) return
    if (info.childCount == 0) {
        unlabeledNodeList.add(info)
    } else {
        unlabeledNodeList.add(info)
        for (i in 0..info.childCount - 1) {
            getAllNodesHelper(unlabeledNodeList, info.getChild(i))
        }
    }
}

fun isVisible(node: AccessibilityNodeInfo): Boolean {
    val boundsInScreen = Rect()
    node.getBoundsInScreen(boundsInScreen)
    if (boundsInScreen.left >= boundsInScreen.right || boundsInScreen.top >= boundsInScreen.bottom) {
        return false
    }
    if (boundsInScreen.right > gScreenW || boundsInScreen.bottom > gScreenH) {
        return false
    }

    return true
}

fun getViewIdResourceNameListHelper(viewIdResourceNameList: ArrayList<String>,
                                   info: AccessibilityNodeInfo?) {
    if (info == null) return
    //if (info.viewIdResourceName != null && isVisible(info)) {
        viewIdResourceNameList.add(info.viewIdResourceName)
    //}
    if (info.childCount != 0) {
        for (i in 0 until info.childCount) {
            getViewIdResourceNameListHelper(viewIdResourceNameList, info.getChild(i))
        }
    }
}

fun getViewIdResourceNameList(info: AccessibilityNodeInfo): ArrayList<String> {
    val viewIdResourceNameList = ArrayList<String>()
    getViewIdResourceNameListHelper(viewIdResourceNameList, info)
    return viewIdResourceNameList
}

enum class EventSource {
    EMPTY,
    SETTINGS,
    LAUNCHER,
    KEYBOARD,
    TASKSTACK,
    PROXIES,
    APP
}

fun getEventSource(event: AccessibilityEvent): EventSource {
    when (event.packageName) {
        null -> return EventSource.EMPTY
        "com.android.settings" -> return EventSource.SETTINGS
        "com.google.android.googlequicksearchbox" -> return EventSource.LAUNCHER
        "com.google.android.inputmethod.latin" -> return EventSource.KEYBOARD
        "com.android.systemui" -> return EventSource.TASKSTACK
        PROXIES_PACKAGE_NAME -> return EventSource.PROXIES
    }
    return EventSource.APP
}


fun performSwipe(direction: String, service: AccessibilityService) {
    val swipe = Path()

    when (direction) {
        "Left" -> {
            swipe.moveTo(400f, 300f)
            swipe.lineTo(100f, 300f)
        }
        "Right" -> {
            swipe.moveTo(100f, 300f)
            swipe.lineTo(400f, 300f)
        }
    }

    val builder = GestureDescription.Builder()
    builder.addStroke(GestureDescription.StrokeDescription(swipe, 0, 1))

    service.dispatchGesture(builder.build(), null, null)
}

fun performOneFingerClick(centerX: Int, centerY: Int): GestureDescription {
    val scroll = Path()
    scroll.moveTo(centerX.toFloat(), centerY.toFloat())
    scroll.lineTo(centerX.toFloat(), centerY.toFloat())

    val stroke = GestureDescription.StrokeDescription(scroll, 100, 10)
    val gesture_builder = GestureDescription.Builder()
    gesture_builder.addStroke(stroke)

    return gesture_builder.build()
}

fun performTwoFingerClick(context: Context, centerX: Int, centerY: Int) {
    val scroll = Path()
    scroll.moveTo((centerX - 3).toFloat(), centerY.toFloat())
    scroll.lineTo((centerX - 3).toFloat(), (centerY + 5).toFloat())

    val scroll2 = Path()
    scroll2.moveTo((centerX + 3).toFloat(), (centerY - 3).toFloat())

    val stroke = GestureDescription.StrokeDescription(scroll, 100, 300)
    val stroke2 = GestureDescription.StrokeDescription(scroll2, 100, 300)
    val gesture_builder = GestureDescription.Builder()
    gesture_builder.addStroke(stroke)
    gesture_builder.addStroke(stroke2)

    (context as AccessibilityService).dispatchGesture(gesture_builder.build(), null, null)
}


fun intersectionsCount(set1: Set<*>, set2: Set<*>): Int {
    if (set2.size < set1.size) return intersectionsCount(set2, set1)
    var count = 0
    for (o in set1) {
        if (set2.contains(o)) {
            count++
        }
    }
    return count
}


fun getSetSimilarity(set0: Set<*>, set1: Set<*>): Double {
    val common = intersectionsCount(set0, set1)
    val union = set0.size + set1.size - common
    val similarity = common.toDouble() / union
    return similarity
}

fun loadAllData() {
    FirebaseDatabase.getInstance().reference.child(APPID_DB).addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            for (child in dataSnapshot.children) {
                val packageName = child.key

                FirebaseDatabase.getInstance().reference.child(APPID_DB).child(packageName).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        val versionCode = dataSnapshot.children.last().key.toInt()
                        Log.i("DemoLog", "PackageName: " + packageName + "; version: " + versionCode)
                        Identifier.getAppId(packageName, versionCode)
                    }

                    override fun onCancelled(firebaseError: DatabaseError) {}
                })
            }
        }

        override fun onCancelled(firebaseError: DatabaseError) { }
    })
}

fun isExternalStorageWritable(): Boolean {
    val state = Environment.getExternalStorageState()
    return state == Environment.MEDIA_MOUNTED
}

fun getSaveDir(): File {
    if (!isExternalStorageWritable()) {
        Log.e(PROXIES_PACKAGE_NAME, "Unable to write image to storage.")
    }

    val root = Environment.getExternalStorageDirectory().toString()
    val saveDir = File(root + "/saved_images")
    saveDir.mkdirs()
    return saveDir
}

private fun saveImageFile(finalBitmap: Bitmap, fname: String, saveDir: File) {
    val file = File(saveDir, fname)
    if (file.exists()) file.delete()
    try {
        val out = FileOutputStream(file)
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        out.flush()
        out.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun saveImg(frameData: FrameData, index: Int) {
    val thread = Thread {
        val saveDir = getSaveDir()
        val name = "img" + index.toString().padStart(3, '0') + ".jpg"
        if (frameData.image != null) {
            saveImageFile(frameData.image, name, saveDir)
            Log.d(
                PROXIES_PACKAGE_NAME,
                "image $name saved. " + frameData.image.width + "x" + frameData.image.height
            )
            synchronized(BUFFER_LOCK) {
                frameData.filename = name
                frameData.saved = true
            }
        }
    }
    thread.start()

}