package demo.AnnotationSystem.Solution

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.Range
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import demo.AnnotationSystem.Identifier.AppId
import demo.AnnotationSystem.Identifier.Identifier
import demo.AnnotationSystem.Identifier.Repairs
import demo.AnnotationSystem.R
import demo.AnnotationSystem.Utilities.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


class LabelSolution: AccessibilityService(), TextToSpeech.OnInitListener {

    private var labelHelper = LabelHelper()
    private var appId: AppId? = null
    private var repairs: Repairs? = null
    private var reset = false
    private val handler = Handler()
    private val hasher = ImagePHash(16, 8)
    private var focusedBounds = Rect()
    private val focusedRects = ArrayList< Array<Int> >()
    private var shouldFindContour = false
    private var borderWidth = 14
    private var mLongPressHandler: Handler? = null
    private val DELAY_VOLUME_LONG_PRESS = 600L
    private var vibrator: Vibrator? = null
    private var shouldInsertGifBtn = false
    private var shouldFocusOverlayBtn = true
    private var beepThread = Thread()
    private var tts: TextToSpeech? = null
    private val annotationCache = HashMap<String, Int>()
    private val annotations = ArrayList<String>()


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!")
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }

    override fun onCreate() {
        super.onCreate()

        setupUtils(this)

        loadAllData()

        repairs = Repairs(this)

        val metrics = resources.displayMetrics
        borderWidth = borderWidth / 440 * metrics.densityDpi
        Log.e(PROXIES_PACKAGE_NAME, "border width: " + borderWidth)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        tts = TextToSpeech(this, this)
    }

    private fun speak(message: String) {
        BeepHelper().beep()
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);

        tts!!.speak(message, TextToSpeech.QUEUE_FLUSH, params, "")
    }

    private fun updateAppId(event: AccessibilityEvent, context: Context) {
        val packageName = event.packageName.toString()
        val versionCode = getVersionCode(context, packageName)

        if (appId == null || appId!!.packageName != packageName || appId!!.versionCode != versionCode) {
            appId = Identifier.getAppId(packageName, versionCode)
        }
    }

    private fun checkSwipeNavigationMode(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> {
                labelHelper.inSwipeNavigationMode = false
//                Log.i("DemoLog", "TYPE_TOUCH_EXPLORATION_GESTURE_START")
            }
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> {
                labelHelper.inSwipeNavigationMode = true
//                Log.i("DemoLog", "TYPE_TOUCH_EXPLORATION_GESTURE_END");
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
//                Log.i("DemoLog", "TYPE_TOUCH_INTERACTION_START");
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
//                Log.i("DemoLog", "TYPE_TOUCH_INTERACTION_END");
            }
        }
    }


    private fun addGifButton(event: AccessibilityEvent, context: Context) {
        updateAppId(event, context)

        if (reset) {
            labelHelper.reset()
            reset = false
        } else {
            reset = true
        }

        if (rootInActiveWindow != null && appId != null && event.source != null) {
            labelHelper.reset()
            labelHelper = LabelHelper(context, event.source, resources.getString(R.string.get_gif_desc_prompt))
        }

        labelHelper.anchorNode = event.source
    }

    private fun focusGifButton() {
        val overlay = labelHelper.overlayList[0]
        val success = overlay.correctLabelButton.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        Log.e(PROXIES_PACKAGE_NAME, "addGifButton: " + success + ", " + overlay.correctLabelButton.text)
    }

    private fun onProxiesFocused(event: AccessibilityEvent) {
        val bounds = Rect()
        if (event.source == null) return
        event.source.getBoundsInScreen(bounds)

        // Focus on correctLabelButton
        if (bounds.height() > 2) {
            for (overlay in labelHelper.overlayList) {
                // The source text might be all upper case.
                if (overlay.correctLabelButton.text.toString().equals(event.source.text.toString(), ignoreCase = true)) {
                    labelHelper.focusedNode = overlay.accessibilityNode
                    if (overlay.anchorNode != null) {
                        labelHelper.focusedNode = overlay.anchorNode
                    }

                    Handler().postDelayed({
                        overlay.correctLabelButton.setBackgroundColor(Color.RED)
                    }, 100) // hopefully trigger at least one frame of capture
                }
            }
            return
        }

        // Focus on prev/next helper button
        if (labelHelper.focusedNode != null) {
            labelHelper.focusedNode!!.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
            labelHelper.reset()
            resetImageBuffer()
            shouldFocusOverlayBtn = false
            if (bounds.height() == 1) {
                performSwipe("Left", this)
            } else if (bounds.height() == 2) {
                performSwipe("Right", this)
            }
        }
    }

    private fun getAllDescendants(source: AccessibilityNodeInfo): ArrayList<AccessibilityNodeInfo> {
        val queue = LinkedList<AccessibilityNodeInfo>()
        val descendants = ArrayList<AccessibilityNodeInfo>()
        queue.offer(source)
        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            if (curr != null) {
                descendants.add(curr)
                val childCount = curr.childCount
                for (index in 0 until childCount) {
                    val child = curr.getChild(index)
                    queue.offer(child)
                }
            }
        }
        return descendants
    }


    private fun getAllNodesMatchingBounds(source: AccessibilityNodeInfo, bounds: Rect): ArrayList<AccessibilityNodeInfo> {
        val queue = LinkedList<AccessibilityNodeInfo>()
        val descendants = ArrayList<AccessibilityNodeInfo>()
        queue.offer(source)
        while (queue.isNotEmpty()) {
            val curr = queue.poll()
            if (curr != null) {
                val rect = Rect()
                curr.getBoundsInScreen(rect)
                val contain = bounds.contains(rect)
                if (contain) {
                    descendants.add(curr)
//                    Log.e(PROXIES_PACKAGE_NAME, "$rect text: ${curr.text}, cd: ${curr.contentDescription}")
                }

                val childCount = curr.childCount
                for (index in 0 until childCount) {
                    val child = curr.getChild(index)
                    queue.offer(child)
                }
                if (!contain)
                    curr.recycle()
            }
        }
        return descendants
    }


    private fun isBoundLegal(bounds: Rect, bitmap: Bitmap): Boolean {
        return (bounds.top + bounds.height() <= bitmap.height && bounds.left + bounds.width() <= bitmap.width
                && bounds.left >= 0 && bounds.top >= 0)
    }

//    private fun getViewBuffer(bounds: Rect): LinkedList<Pair<Long, Bitmap>> {
//        val buffer = LinkedList<Pair<Long, Bitmap>>()
//        synchronized(BUFFER_LOCK) {
//            for (elem in gImageBuffer) {
//                val ts = elem.first
//                val bmp = elem.second
//                if (isBoundLegal(bounds, bmp)) {
//                    val temp = Bitmap.createBitmap(bmp, bounds.left, bounds.top, bounds.width(), bounds.height())
//                    val cropped = temp.copy(temp.config, false)
//                    buffer.add(Pair(ts, cropped))
//                }
//            }
//            return buffer
//        }
//    }


    private fun saveImage(finalBitmap: Bitmap, fname: String, saveDir: File) {
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

    private fun writeStringAsFile(fileContents: String, fileName: String, saveDir: File) {
        val context = applicationContext
        val file = File(saveDir, fileName)
        try {
            val out = FileWriter(file)
            out.write(fileContents)
            out.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun isRectTooSmall(bounds: Rect): Boolean {
        return bounds.width() <= 28 || bounds.height() <= 28
    }

    private fun getInnerRect(rect: Rect, outer: Rect): Rect {
        if (!isRectTooSmall(outer)) {
            val x1 = max(rect.left, outer.left + borderWidth)
            val x2 = min(rect.right, outer.right - borderWidth)
            val y1 = max(rect.top, outer.top + borderWidth)
            val y2 = min(rect.bottom, outer.bottom - borderWidth)
            return Rect(x1, y1, x2, y2)
        }
        return rect
    }

    private fun scaleRect(rect: Rect): Rect {
        val x1 = (rect.left.toDouble() / SCALE_FACTOR).roundToInt()
        val x2 = (rect.right.toDouble() / SCALE_FACTOR).roundToInt()
        val y1 = (rect.top.toDouble() / SCALE_FACTOR).roundToInt()
        val y2 = (rect.bottom.toDouble() / SCALE_FACTOR).roundToInt()
        return Rect(x1, y1, x2, y2)
    }

    private fun findOccurance(hashes: ArrayList<LongArray>): HashMap<Int, ArrayList<Int>> {
        val occurrenceMap = HashMap<Int, ArrayList<Int>>()
        val firstSeen = HashMap<LongArray, Int>()
        for ((i, hash) in hashes.withIndex()) {
            var firstIndex: Int? = null
            for ((j, hash2) in hashes.withIndex()) {
                if (j == i) break
                if (hash2 contentEquals hash) {
                    firstIndex = j
                    break
                }
            }
            if (firstIndex != null) {
                occurrenceMap[firstIndex]!!.add(i)
            } else {
                firstSeen[hash] = i
                occurrenceMap[i] = arrayListOf(i)
            }
        }
        return occurrenceMap
    }

    private fun findEndIndexOfCluster(indices: ArrayList<Int>, index: Int): Int {
        var idx = index
        while (idx < indices.size - 1) {
            if (indices[idx+1] - indices[idx] > 1)
                return indices[idx]
            idx += 1
        }
        return indices[idx]
    }

    private fun findLoopRange(occurrenceMap: HashMap<Int, ArrayList<Int>>): Range<Int>? {
        if (occurrenceMap.isEmpty())
            return null
        val numOccurrence = HashMap<Int, Int>()
        var startIndex = 0
        var endIndex = 0
        for ((key, value) in occurrenceMap) {
            numOccurrence[key] = 1
            if (key > endIndex) endIndex = key
            for (i in 1 until value.size) {
                if (value[i] - value[i-1] > 1)
                    numOccurrence.computeIfPresent(key) {_, v -> v + 1}
                if (value[i] > endIndex)
                    endIndex = value[i]
            }
        }
        val occurrenceCount = numOccurrence.values
        Log.e(PROXIES_PACKAGE_NAME, "$occurrenceCount")
        val counts = occurrenceCount.groupingBy { it }.eachCount()

        var maxCount = 0
        var maxRep = 0
        var secondMaxCount = 0
        var secondMaxRep = 0
        for ((rep, count) in counts) {
            if (count > maxCount) {
                secondMaxCount = maxCount
                secondMaxRep = maxRep
                maxCount = count
                maxRep = rep
            } else if (count > secondMaxCount) {
                secondMaxCount = count
                secondMaxRep = rep
            }
        }

        var mode = maxRep
        if (maxRep == 1 && secondMaxRep != 0 && secondMaxCount >= 5)
            mode = secondMaxRep

        Log.e(PROXIES_PACKAGE_NAME, "$counts $mode")

        if (mode >= 2) {     // TODO: Fix this
            var minIndex = Int.MAX_VALUE
            for ((k, v) in numOccurrence) {
                if (v == mode && k < minIndex) {
                    minIndex = k
                }
            }
            val indices = occurrenceMap[minIndex]!!
            startIndex = findEndIndexOfCluster(indices, 0) + 1
            var cluster = 0
            for (idx in 1 until indices.size) {
                if (indices[idx] - indices[idx-1] > 1) {
                    cluster += 1
                }
                if (mode == 2) {
                    if (cluster == 1)
                        endIndex = findEndIndexOfCluster(indices, idx)
                } else {
                    if (cluster == 1) startIndex = indices[idx]
                    else if (cluster == 2) endIndex = indices[idx] - 1
                }
            }
        }
        return Range(startIndex, endIndex)
    }

    fun handleBuffer() {
        val bounds = focusedBounds

        val innerRect = bounds//getInnerRect(bounds, bounds)

//        Log.e(PROXIES_PACKAGE_NAME, "current image buffer size: " + gImageBuffer.size)

        var delayTime = BUFFER_LENGTH
        var delayTimeShort = 1500L
        val currTime = System.currentTimeMillis()

        synchronized(BUFFER_LOCK) {
            if (gImageBuffer.size > 0) {
                delayTime = max(BUFFER_LENGTH - (currTime - gImageBuffer[0].timestamp), 0)
                delayTimeShort = max(1500 - (currTime - gImageBuffer[0].timestamp), 0)
//                Log.e(
//                    PROXIES_PACKAGE_NAME,
//                    "current image buffer duration: " + (currTime - gImageBuffer[0].timestamp)
//                )
            }
        }

        startBeeps()

        val cacheHandler = Handler()
        cacheHandler.postDelayed({
            val thread = Thread(Runnable {
                if (gImageBuffer.isEmpty()) {
                    beepThread.interrupt()
                    BeepHelper().endBeep()
                    speak(resources.getString(R.string.record_fail))
                    handler.removeCallbacksAndMessages(null)
                    gShouldStartBuffer = false
                } else {
                    synchronized(BUFFER_LOCK) {
                        for (pair in gImageBuffer) {
                            val hash = hasher.culcPHash(pair.image)    // LongArray
                            val hashStr = hash.joinToString()

                            val index = annotationCache[hashStr]
                            if (index != null) {    // found in cache
                                val label = annotations[index]
                                beepThread.interrupt()
                                speak("Last Gif: $label")
                                handler.removeCallbacksAndMessages(null)
                                gShouldStartBuffer = false
                                return@Runnable
                            }
                        }
                    }
                }
            })
            thread.start()
        }, delayTimeShort)

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            val thread = Thread(Runnable {
                try {
                    gShouldStartBuffer = false

                    synchronized(BUFFER_LOCK) {
                        val viewBuffer = gImageBuffer   //getViewBuffer(scaleRect(innerRect))
                        if (viewBuffer.isEmpty() && gOldBufferPair != null) {
                            viewBuffer.add(gOldBufferPair!!)
                        }
                        if (viewBuffer.isEmpty()) {
                            return@Runnable
                        }
                        val hashes = ArrayList<String>()
                        for (pair in viewBuffer) {
                            val hash = hasher.culcPHash(pair.image)    // LongArray
                            val hashStr = hash.joinToString()
                            hashes.add(hashStr)

                            val index = annotationCache[hashStr]
                            if (index != null) {    // found in cache
                                val label = annotations[index]
                                speak("Last Gif: $label")
                                return@Runnable
                            }
                        }

                        Log.d(PROXIES_PACKAGE_NAME, "cache size = " + annotationCache.size)

//                        val occurrenceMap = findOccurance(hashes)
//                        for ((k, v) in occurrenceMap) {
//                            Log.e(PROXIES_PACKAGE_NAME, "Index: ${k}, Occurances ${v}, ${v.size}")
//                        }
//                val range = findLoopRange(occurrenceMap)
//                if (range != null) {
//                    Log.e(PROXIES_PACKAGE_NAME, "Range: ${range.lower} ${range.upper}")
//                }
                        val saveDir = getSaveDir()

                        val metadata = HashMap<String, Any>()
                        val images = ArrayList<HashMap<Long, String>>()
                        val filesSet = HashSet<String>()
                        for (frameData in viewBuffer) {
                            if (frameData.filename != null) {
                                images.add(hashMapOf(frameData.timestamp - viewBuffer[0].timestamp to frameData.filename!!))
                                filesSet.add(frameData.filename!!)
                            }
                        }

                        metadata["images"] = images
                        metadata["rects"] = focusedRects
                        metadata["findContour"] = shouldFindContour
                        val lang = Locale.getDefault().language
                        metadata["chinese"] = (lang == "zh")
                        val gson = Gson()
                        val json = gson.toJson(metadata)
                        val jsonName = "metadata.json"
                        writeStringAsFile(json, jsonName, saveDir)
                        filesSet.add(jsonName)

                        // zip required files
                        var waitCount = 0
                        while (!gImageBuffer.fold(true) { acc, frameData -> acc && frameData.saved } && waitCount < 6) {
                            Thread.sleep(50) // wait for file saving to finish
                            waitCount++
                        }
                        val zipName = "out-${viewBuffer[0].timestamp}.zip"
                        val compressor = Compress(saveDir, filesSet.toTypedArray(), zipName)
                        compressor.zip()
                        val params = hashMapOf("filetype" to "imgs")
                        val result = multipartRequest(
                            "http://is-mingrui.ischool.uw.edu:8080/upload",
                            params,
                            File(saveDir, zipName).canonicalPath,
                            "zipfile",
                            "application/zip"
                        )
                        Log.i(PROXIES_PACKAGE_NAME, "Response: $result")

                        // now that we have the response, we can stop beeping
                        beepThread.interrupt()
                        BeepHelper().endBeep()

                        val jsonLastIndex = result.lastIndexOf('}')
                        if (jsonLastIndex == -1) {
                            speak(resources.getString(R.string.fetch_error))
                            return@Runnable
                        }
                        val resultJsonString = result.substring(0, jsonLastIndex + 1)
                        val resultObj = JSONObject(resultJsonString)
                        val label: String = resultObj.getString("label")

                        speak(resources.getString(R.string.last_gif) + label)

                        // save the label in cache
                        for (hashStr in hashes) {
                            annotationCache[hashStr] = annotations.size
                        }
                        annotations.add(label)

                        // clean up
                        if (saveDir.exists() && saveDir.isDirectory) {
                            for (tempFile in saveDir.listFiles()) {
                                tempFile.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                }
            })

            thread.start()
        }, delayTime)
    }

    private fun startBeeps() {
        beepThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {     // && gShouldStartBuffer
                    BeepHelper().beep()
                    Thread.sleep(800)
                }
            } catch (e: InterruptedException) {
                Log.i(PROXIES_PACKAGE_NAME, "Beep ended")
            }
        }
        beepThread.start()
    }


    private fun resetImageBuffer() {
        Log.i(PROXIES_PACKAGE_NAME, "reset image buffer")
        handler.removeCallbacksAndMessages(null)
        val temp = gImageBuffer.lastOrNull()
        for (i in gImageBuffer.indices) {
            if (i < gImageBuffer.size - 1) {
                gImageBuffer[i].image?.recycle()
            }
        }
        gImageBuffer.clear()
        gOldBufferPair = temp
    }

    private fun checkChildNodes(template: String, nodes: ArrayList<AccessibilityNodeInfo>): Boolean {
        for (node in nodes) {
            val nodeStr = "" + node.className + "_" + node.viewIdResourceName + "_" + node.contentDescription
            if (nodeStr.contains(template)) return true
        }
        return false
    }

    private fun checkChildNodesNoViewId(template: String, nodes: ArrayList<AccessibilityNodeInfo>): Boolean {
        for (node in nodes) {
            val nodeStr = "" + node.className + "_" + node.contentDescription
            if (nodeStr.contains(template)) return true
        }
        return false
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        checkSwipeNavigationMode(event)

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED && event.source != null) {
            val bounds = Rect()
            event.source.getBoundsInScreen(bounds)

            var node = event.source
//            Log.e(PROXIES_PACKAGE_NAME, "" + node.className + "_" + node.viewIdResourceName + "_" + node.contentDescription + ": " + bounds)

            if (shouldInsertGifBtn && labelHelper.inSwipeNavigationMode) {
                Log.e(PROXIES_PACKAGE_NAME, "add gif button: ${node.contentDescription}")
                resetImageBuffer()
                gShouldStartBuffer = true
                focusGifButton()
                shouldInsertGifBtn = false
                return
            }

            if (gShouldStartBuffer && event.packageName.toString() != PROXIES_PACKAGE_NAME) {
                gShouldStartBuffer = false
                handler.removeCallbacksAndMessages(null)
            }

            if (rootInActiveWindow != null) {
                node = rootInActiveWindow
            }
            val nodes = getAllNodesMatchingBounds(node, bounds)

            if (event.packageName != null && shouldFocusOverlayBtn) {   // shouldFocusOverlayBtn prevents inserting overlays when user just exited overlay
                if (
                    (event.packageName.contains("org.telegram.messenger")
                            && (checkChildNodes("android.view.ViewGroup_null_GIF", nodes)
                                || (event.source.contentDescription?.contains("Sticker\n") == true && event.source.className?.toString() == "android.view.ViewGroup")))
                    || (event.packageName.contains("com.facebook.orca")
                            && checkChildNodes("android.view.ViewGroup_null_Sent photo message", nodes)
                            && checkChildNodes("android.widget.ImageView_null_Forward button", nodes)) // this distinguishes it from a plain image
                    || (event.packageName.contains("com.tencent.mm")
                            && (checkChildNodesNoViewId("android.widget.FrameLayout_Sticker Gallery", nodes)
                                || checkChildNodesNoViewId("android.widget.FrameLayout_表情", nodes))
                            && checkChildNodes("android.widget.ImageView_null_null", nodes)
                            && !checkChildNodes("android.widget.LinearLayout_null_null", nodes))
                    || (event.packageName.contains("com.twitter.android")
                            && checkChildNodes("android.widget.FrameLayout_com.twitter.android:id/video_player_view_GIF", nodes))
                    || (event.packageName.contains("com.discord")
                            && checkChildNodes("android.widget.FrameLayout_com.discord:id/embed_inline_media_null", nodes)
                            && checkChildNodes("android.widget.FrameLayout_com.discord:id/inline_media_player_view_null", nodes))
                ) {
                    shouldInsertGifBtn = true
                    focusedBounds = bounds
                    gCropBounds = focusedBounds
                    shouldFindContour = event.packageName.contains("org.telegram.messenger")
                    addGifButton(event, this)
//                    Log.e(PROXIES_PACKAGE_NAME, "should add gif button: ${node.contentDescription}")
                }
            }
            shouldFocusOverlayBtn = true

            focusedRects.clear()
            for (node in nodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val ir = rect //getInnerRect(rect, bounds)
                focusedRects.add(arrayOf(ir.left, ir.top, ir.right, ir.bottom))
                Log.e(PROXIES_PACKAGE_NAME, "" + rect + " " + node.childCount + " " + node.className + "_" + node.viewIdResourceName + "_" + node.contentDescription)
                node.recycle()
            }

        } else if (
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            Log.i(PROXIES_PACKAGE_NAME, "" + System.currentTimeMillis() + ": " + AccessibilityEvent.eventTypeToString(event.eventType))
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ) {
            Log.i(PROXIES_PACKAGE_NAME, "TYPE_WINDOW_STATE_CHANGED")
        }
        // Log.d("ACCESSIBILITY EVENT", "" + AccessibilityEvent.eventTypeToString(event.eventType))


        // ** Interaction proxy below **

//        Log.e(PROXIES_PACKAGE_NAME, "event source: " + getEventSource(event) + event.packageName)
        when (getEventSource(event)) {
            EventSource.EMPTY -> return
            EventSource.PROXIES -> {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                        Log.e(PROXIES_PACKAGE_NAME, "onProxiesFocused")
                        onProxiesFocused(event)
                        return
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
//                        onWindowChange(event, this)
                        Log.i("DemoLog", "Clicked")
                        return
                    }
                }
            }
            EventSource.APP -> {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
//                        onAppFocused(event)
                    }
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
//                        onWindowChange(event, this)
                        Log.i("DemoLog", "Clicked")
                    }
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
//                        onWindowChange(event, this)
                    }
                }
            }
            else -> {
                when (event.eventType) {
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        //onWindowChange(event, this)
                    }
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        //labelHelper.reset()
                    }
                }
            }
        }
    }


    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
    }

    override fun onInterrupt() {}
}