package demo.AnnotationSystem.Identifier

import android.content.Context
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityInfoCheckResult
import com.google.android.apps.common.testing.accessibility.framework.SpeakableTextPresentInfoCheck

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import demo.AnnotationSystem.TalkbackUtilities.NodeSpeechRuleProcessor

import java.util.ArrayList
import java.util.Date
import java.util.LinkedList

import demo.AnnotationSystem.Utilities.*


class Screen {
    var id = ""
    var packageName = ""
    var activityName = ""

    var elementList = ArrayList<Element>()
    var elementIdList = ArrayList<String>()
    var viewIdResourceNameList = ArrayList<String>()
    var unlabeledElementIdList = ArrayList<String>()
    var talkbackElementIdList = ArrayList<String>()

    // Download Only
    var navigationOrderList = ArrayList<String>()
    var addedLabelList = ArrayList<Pair<String, String>>()
    var customWidget: Widget? = null

    // Download from Firebase
    constructor(id: String) {
        this.id = id

        val self = this
        val ref = FirebaseDatabase.getInstance().reference.child(SCREEN_DB).child(id)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.child("packageName").value == null) return
                self.packageName = dataSnapshot.child("packageName").value as String
                self.activityName = dataSnapshot.child("activityName").value as String

                if (dataSnapshot.hasChild("elementIdList")) {
                    self.elementIdList = dataSnapshot.child("elementIdList").getValue(object : GenericTypeIndicator<ArrayList<String>>() {})!!

                    for (elementId in dataSnapshot.child("elementIdList").getValue(object : GenericTypeIndicator<ArrayList<String>>() {})!!) {
                        self.elementList.add(Element(self.id, elementId))
                    }
                }

                if (dataSnapshot.hasChild("viewIdResourceNameList")) {
                    self.viewIdResourceNameList = dataSnapshot.child("viewIdResourceNameList").getValue(object : GenericTypeIndicator<ArrayList<String>>() {})!!
                }
                if (dataSnapshot.hasChild("unlabeledElementIdList")) {
                    self.unlabeledElementIdList = dataSnapshot.child("unlabeledElementIdList").getValue(object : GenericTypeIndicator<ArrayList<String>>() {})!!
                }
                if (dataSnapshot.hasChild("talkbackElementIdList")) {
                    self.talkbackElementIdList = dataSnapshot.child("talkbackElementIdList").getValue(object : GenericTypeIndicator<ArrayList<String>>() {})!!
                }
                if (dataSnapshot.hasChild("navigationOrderList")) {
                    self.navigationOrderList = dataSnapshot.child("navigationOrderList").getValue(object : GenericTypeIndicator<ArrayList<String>>() {})!!
                }

                if (dataSnapshot.hasChild("addedLabelList")) {
                    ref.child("addedLabelList").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            for (addedLabel in dataSnapshot.children) {
                                self.addedLabelList.add(Pair(addedLabel.key, addedLabel.child("newAltText").value as String))
                            }
                        }

                        override fun onCancelled(p0: DatabaseError?) { }
                    })
                }

                if (dataSnapshot.hasChild("customWidget")) {
                    ref.child("customWidget").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            Log.i("DemoLog", "customWidget")

                            val children = ArrayList<WidgetChild>()
                            for (widgetChild in dataSnapshot.child("children").children) {
                                val child = widgetChild.getValue(object : GenericTypeIndicator<WidgetChild>() {})!!
                                children.add(child)
                            }

                            self.customWidget = Widget(
                                    id,
                                    dataSnapshot.child("elementId").value as String,
                                    dataSnapshot.child("altText").value as String,
                                    children
                            )
                        }

                        override fun onCancelled(p0: DatabaseError?) { }
                    })
                }
            }

            override fun onCancelled(firebaseError: DatabaseError) { }
        })
    }

    // Create from client
    constructor(rootAccessibilityNode: AccessibilityNodeInfo,
                activity: String?,
                context: Context) {
        id = Date().time.toString()

        if (rootAccessibilityNode.packageName != null) {
            packageName = rootAccessibilityNode.packageName.toString()
        }
        if (activity != null) {
            activityName = activity
        }

        viewIdResourceNameList = getViewIdResourceNameList(rootAccessibilityNode)
        Log.i("James", viewIdResourceNameList.toString())

        createElementList(elementList, rootAccessibilityNode, 0, -1)
        createUnlabeledElementList(rootAccessibilityNode, context)
        createTalkbackElementIdList(rootAccessibilityNode, context)

        updateAnchorNode()
    }

    private fun updateAnchorNode() {
        for (node in elementList) {
            if (node.className == "android.widget.TextView" && node.text == null && node.contentDescription == null && node.viewIdResourceName == "com.yelp.android:id/war_stars_view") {
                node.anchorNodeId = "?"
            }
        }
    }

    private fun createElementList(elementList: ArrayList<Element>,
                                  accessibilityNode: AccessibilityNodeInfo?,
                                  depth: Int,
                                  parentIndex: Int): Int {
        if (accessibilityNode == null) return -1
        if (!isVisible(accessibilityNode)) return -1

        val element = Element(accessibilityNode, depth)
        elementList.add(element)
        val index = elementList.size - 1
        val childrenIndexList = ArrayList<Int>()
        for (i in 0..accessibilityNode.childCount - 1) {
            childrenIndexList.add(createElementList(elementList, accessibilityNode.getChild(i), depth + 1, index))
        }
        element.parentIndex = parentIndex
        element.childrenIndexList = childrenIndexList
        return index
    }

    private fun createUnlabeledElementList(rootAccessibilityNode: AccessibilityNodeInfo, context: Context) {
        val checks = AccessibilityCheckPreset.getInfoChecksForPreset(AccessibilityCheckPreset.LATEST)

        val results = LinkedList<AccessibilityInfoCheckResult>()
        for (check in checks) {
            if (check is SpeakableTextPresentInfoCheck) {
                results.addAll(check.runCheckOnInfoHierarchy(rootAccessibilityNode, context))
            }
        }

        val errors = AccessibilityCheckResultUtils.getResultsForType(results, AccessibilityCheckResult.AccessibilityCheckResultType.ERROR)

        for (error in errors) {
            val errorNode = error.info
            for (element in elementList) {
                if (errorNode == element.accessibilityNode) {
                    unlabeledElementIdList.add(element.id)
                }
            }
        }
    }

    private fun createTalkbackElementIdList(rootAccessibilityNode: AccessibilityNodeInfo, context: Context) {
        val talkbackOrderNodeList = getNodesInTalkBackOrder(rootAccessibilityNode)
        for (node in talkbackOrderNodeList) {
            for (element in elementList) {
                if (node == AccessibilityNodeInfoCompat.wrap(element.accessibilityNode!!)) {
                    talkbackElementIdList.add(element.id)

                    var talkbackText = NodeSpeechRuleProcessor.getDescriptionForTree(context, node,null, node)
                    element.talkbackText = talkbackText.toString()
                }
            }
        }
    }

    fun writeToFirebase(packageName: String) {
        val ref = FirebaseDatabase.getInstance().reference.child(SCREEN_DB).child(this.id)
        ref.child("packageName").setValue(packageName)
        ref.child("activityName").setValue(activityName)

        ref.child("viewIdResourceNameList").setValue(viewIdResourceNameList)
        ref.child("unlabeledElementIdList").setValue(unlabeledElementIdList)
        ref.child("talkbackElementIdList").setValue(talkbackElementIdList)

        val list = ArrayList<String>()
        for (node in elementList) {
            list.add(node.id)
            node.writeToFirebase(this.id)
        }
        ref.child("elementIdList").setValue(list)

        val screenshot = getScreenShot()
        if (screenshot != null) {
            val storageRef = FirebaseStorage.getInstance().getReference(id + ".png")
            val uploadTask = storageRef.putBytes(screenshot)
            uploadTask.addOnSuccessListener { taskSnapshot ->
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                val downloadUrl = taskSnapshot.downloadUrl
                Log.i("DemoLog", "" + downloadUrl!!)
            }
            uploadTask.addOnFailureListener {
                // Handle unsuccessful uploads
            }
        }
    }

    fun getElement(elementId: String): Element? {
        for (element in elementList) {
            if (element.id == elementId) {
                return element
            }
        }
        return null
    }

    private fun printScreen() {
        for (node in elementList) {
            node.printData()
        }
    }
}
