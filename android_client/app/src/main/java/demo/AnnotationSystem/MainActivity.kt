package demo.AnnotationSystem

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.net.Uri
import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.util.DisplayMetrics
import kotlin.collections.ArrayList

import demo.AnnotationSystem.Utilities.*
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Environment
import android.view.View
import android.widget.Button


class MainActivity : Activity() {

    var ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 5469

    private fun testOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName))
            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                println("WowICan")
            }
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)

            gImageReader = ImageReader.newInstance(gScreenW, gScreenH, PixelFormat.RGBA_8888, 2)
            gVirtualDisplay = gMediaProjectionManager!!.getMediaProjection(Activity.RESULT_OK, data).createVirtualDisplay("capture", gScreenW, gScreenH, displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, gImageReader!!.surface, null, null)
        }
    }

    fun openSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun requestPermissions() {
        requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE), 1)
    }

    fun startScreenCapture() {
        if (!ScreenCaptureImageActivity.active) {
            val intent = Intent(this, ScreenCaptureImageActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (isExternalStorageWritable()) {
            startScreenCapture()
            testOverlayPermission()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUtils(this)

        this.findViewById<Button>(R.id.activateAccessibilityButton).setOnClickListener {
            openSettings()
        }

        this.findViewById<Button>(R.id.openAppButton).setOnClickListener {
            requestPermissions()
        }

        requestPermissions()

//        testOverlayPermission()
//
//        requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE), 1)
    }

}