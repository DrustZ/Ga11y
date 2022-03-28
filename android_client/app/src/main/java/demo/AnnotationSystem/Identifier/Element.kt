package demo.AnnotationSystem.Identifier

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener

import demo.AnnotationSystem.Utilities.*

import java.util.ArrayList


class Element {
    var id = ""

    // Initialize as null so we can find difference between "" and null
    // Basic info
    var className = ""
    var text: String? = null
    var contentDescription: String? = null
    var viewIdResourceName: String? = null
    var boundsInScreen = Rect()
    var boundsInParent = Rect()
    var talkbackText: String? = null

    // View hierarchy info
    var depth = -1
    var parentIndex = -1
    var childrenIndexList = ArrayList<Int>()

    // AccessibilityNode info
    var accessibilityNode: AccessibilityNodeInfo? = null
    var accessibilityProperties = AccessibilityProperties()

    // User-generated info, download only
    var anchorNodeId: String? = null

    // Create from client
    constructor(node: AccessibilityNodeInfo, depth: Int) {
        if (id != "") return
        this.id = node.hashCode().toString()

        if (node.className != null)          { this.className = node.className.toString() }
        if (node.text != null)               { this.text = node.text.toString() }
        if (node.contentDescription != null) { this.contentDescription = node.contentDescription.toString() }
        if (node.viewIdResourceName != null) { this.viewIdResourceName = node.viewIdResourceName.toString() }
        node.getBoundsInScreen(this.boundsInScreen)
        node.getBoundsInParent(this.boundsInParent)

        // talkbackText, parentIndex and childrenIndexList will be added from Screen
        this.depth = depth

        accessibilityNode = node
        accessibilityProperties = AccessibilityProperties(node)
    }

    // Download from Firebase
    constructor(treeId: String, nodeId: String) {
        this.id = nodeId

        val self = this
        val ref = FirebaseDatabase.getInstance().reference.child(ELEMENT_DB).child(treeId).child(nodeId)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                self.className          = dataSnapshot.child("className").value as String
                self.text               = dataSnapshot.child("text").value as String?
                self.contentDescription = dataSnapshot.child("contentDescription").value as String?
                self.viewIdResourceName = dataSnapshot.child("viewIdResourceName").value as String?
                if (dataSnapshot.hasChild("boundsInScreen")) {
                    self.boundsInScreen = dataSnapshot.child("boundsInScreen").getValue(object : GenericTypeIndicator<Rect>() {})!!
                }
                if (dataSnapshot.hasChild("boundsInParent")) {
                    self.boundsInParent = dataSnapshot.child("boundsInParent").getValue(object : GenericTypeIndicator<Rect>() {})!!
                }
                if (dataSnapshot.hasChild("talkbackText")) {
                    self.talkbackText = dataSnapshot.child("talkbackText").value as String?
                }

                self.depth = (dataSnapshot.child("depth").value as Long).toInt()
                self.parentIndex = (dataSnapshot.child("parentIndex").value as Long).toInt()
                if (dataSnapshot.hasChild("childrenIndexList")) {
                    self.childrenIndexList = dataSnapshot.child("childrenIndexList").getValue(object : GenericTypeIndicator<ArrayList<Int>>() {})!!
                }

                if (dataSnapshot.hasChild("accessibilityProperties")) {
                    self.accessibilityProperties = dataSnapshot.child("accessibilityProperties").getValue(object : GenericTypeIndicator<AccessibilityProperties>() {})!!
                }
                self.anchorNodeId = dataSnapshot.child("anchorNodeId").value as String?
            }

            override fun onCancelled(firebaseError: DatabaseError) {

            }
        })
    }

    fun writeToFirebase(screenId: String) {
        val ref = FirebaseDatabase.getInstance().reference.child(ELEMENT_DB).child(screenId).child(this.id)

        ref.child("className").setValue(className)
        ref.child("text").setValue(text)
        ref.child("contentDescription").setValue(contentDescription)
        ref.child("viewIdResourceName").setValue(viewIdResourceName)
        ref.child("boundsInScreen").setValue(boundsInScreen)
        ref.child("boundsInParent").setValue(boundsInParent)
        ref.child("talkbackText").setValue(talkbackText)

        ref.child("depth").setValue(depth)
        ref.child("parentIndex").setValue(parentIndex)
        ref.child("childrenIndexList").setValue(childrenIndexList)

        ref.child("accessibilityProperties").setValue(accessibilityProperties)
    }

    fun printData() {
        Log.i("DemoLog", "" + this.className + ", " + this.text + ", " + this.contentDescription + ", " + this.viewIdResourceName)
    }
}
