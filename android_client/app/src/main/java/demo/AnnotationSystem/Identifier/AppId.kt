package demo.AnnotationSystem.Identifier

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import java.util.ArrayList

import demo.AnnotationSystem.Utilities.*

class AppId {
    var isLoaded = false

    var traceCreatedAt = "";

    // In case empty string will break firebase (most of the case it will be overwrite by new activity, e.g. the start activity; but just in case app switch etc)
    var mostRecentActivityName = "InitEmpty"

    var packageName = ""
    var versionCode = -1
    var screenW = 0
    var screenH = 0
    var appScreenList = ArrayList<Screen>()

    val firebaseAppIdRef: DatabaseReference
        get() = FirebaseDatabase.getInstance().reference.child(APPID_DB).child(packageName).child(versionCode.toString())

    constructor(packageName: String, versionCode: Int) {
        this.packageName = formatForFirebase(packageName)
        this.versionCode = versionCode
        this.screenW = screenW
        this.screenH = screenH

        val self = this
        val ref = firebaseAppIdRef
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (!dataSnapshot.exists()) {
                    // Create new AppId in Firebase
                    ref.child("screenW").setValue(screenW)
                    ref.child("screenH").setValue(screenH)
                    ref.child("createdAt").setValue((System.currentTimeMillis()/1000).toString())
                    Log.i("DemoLog", "New AppId created.")
                } else {
                    for (activityName in dataSnapshot.child("appActivityList").children) {
                        for (id in activityName.children) {
                            self.appScreenList.add(Screen(id.key))
                        }
                    }
                    android.os.Handler().postDelayed({
                        self.isLoaded = true
                    }, 5000)
                }
                self.initNewTrace();
            }
            override fun onCancelled(firebaseError: DatabaseError) { }
        })
    }

    fun initNewTrace() {
        traceCreatedAt = (System.currentTimeMillis()/1000).toString()
    }

    fun getTemplateScreen(root: AccessibilityNodeInfo, event: AccessibilityEvent, context: Context): Pair<Screen, Screen?> {
        val activityName = getActivityName(context, event)
        val currentScreen = Screen(root, activityName, context)
        for (screen in appScreenList) {
            if (isSameScreen(currentScreen, screen)) {
                return Pair(currentScreen, screen)
            }
        }
        return Pair(currentScreen, null)
    }

    fun addWindow(root: AccessibilityNodeInfo?, activityName: String?, context: Context): Boolean {
        if (root == null) return false

        var activity: String? = activityName
        if (activityName == null) {
            activity = mostRecentActivityName
        }

        val newScreen = Screen(root, activityName, context)

        val ref = firebaseAppIdRef
        val screenCreatedAt = (System.currentTimeMillis()/1000).toString()
        ref.child("traceList").child(traceCreatedAt).child(screenCreatedAt).child("screenId").setValue(newScreen.id)

        for (screen in appScreenList) {
            if (isSameScreen(newScreen, screen)) {
                Log.i("DemoLog", "Template Screen ID: " + screen.id)
                newScreen.writeToFirebase(packageName)
                ref.child("appActivityList").child(formatForFirebase(activity!!)).child(screen.id).child(newScreen.id).setValue(true)
                ref.child("traceList").child(traceCreatedAt).child(screenCreatedAt).child("templateScreenId").setValue(screen.id)
                return false
            }
        }

        // No same tree found
        appScreenList.add(newScreen)
        Log.i("DemoLog", "New Screen ID: " + newScreen.id)
        newScreen.writeToFirebase(packageName)
        ref.child("appActivityList").child(formatForFirebase(activity!!)).child(newScreen.id).setValue(true)
        ref.child("traceList").child(traceCreatedAt).child(screenCreatedAt).child("templateScreenId").setValue("")

        return true
    }
}
