package demo.AnnotationSystem.Identifier

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonObject
import java.util.*


fun getElementByJson(root: AccessibilityNodeInfo, template: JsonObject) : AccessibilityNodeInfo? {

    val dynPath = template.getAsJsonArray("dyn_path")
    val targetObj = dynPath[dynPath.size() - 1].asJsonObject

    val queue = LinkedList<AccessibilityNodeInfo>()
    queue.add(root)

    while (!queue.isEmpty()) {
        val node = queue.pop() ?: continue
        var resId = ""
        node.refresh()
        if (node.viewIdResourceName != null) {
            resId = node.viewIdResourceName
        }
        Log.e("Event", node.className.toString() + "_" + node.viewIdResourceName)

        if (node.viewIdResourceName != null
                && node.viewIdResourceName == targetObj.getAsJsonPrimitive("resourceId").asString
                && node.className == targetObj.getAsJsonPrimitive("className").asString) {
            return node
        }

//        if (node.className !== "ListView" && node.className !== "RecyclerView") {
        for (i in 0 until node.childCount) {
            queue.add(node.getChild(i))
        }
//        }
    }

    return null
}

fun getElementByRemoteElement(clientScreen: Screen, remoteScreen: Screen, remoteElement: Element) : Element? {
    var perfectMatch: Element? = null
    val matchingList = ArrayList<Element>()
    val acceptableMatchingList = ArrayList<Element>()

    // Use stable selectors
    for (node in clientScreen.elementList) {
        // Not same element
        if (node.viewIdResourceName != remoteElement.viewIdResourceName || node.className != remoteElement.className) {
            continue
        }

        // viewIdResourceName indicates it is the same element, or reused element?
        if (node.viewIdResourceName != null
         && node.viewIdResourceName == remoteElement.viewIdResourceName) {
            if (node.talkbackText != null && node.talkbackText == remoteElement.talkbackText) {
                if (perfectMatch == null) {
                    perfectMatch = node
                }
            }
            matchingList.add(0, node)
            continue
        }

        // No viewIdResourceName, check text and contentDescription
        if ((node.text != null && node.text == remoteElement.text)
         || (node.contentDescription != null && node.contentDescription == remoteElement.contentDescription)) {
            matchingList.add(node)
            continue
        } else {
            acceptableMatchingList.add(node)
        }
    }

    // Use hierarchy info
    val childIndexList = ArrayList<Int>()
    var element = remoteElement
    while (element.parentIndex != -1) {
        val currentElementIndex = remoteScreen.elementIdList.indexOf(element.id)
        val parentElement = remoteScreen.elementList[element.parentIndex]
        val childIndex = parentElement.childrenIndexList.indexOf(currentElementIndex)
        childIndexList.add(0, childIndex)
        element = parentElement
    }

    var findResult = true
    var resultRemoteElement: Element = remoteScreen.elementList[0]
    var resultClientElement: Element = clientScreen.elementList[0]
    for (childIndex in childIndexList) {
        if (childIndex < resultClientElement.childrenIndexList.size
         && resultClientElement.childrenIndexList[childIndex] < clientScreen.elementList.size) {
            val remoteIndex = resultRemoteElement.childrenIndexList[childIndex]
            val clientIndex = resultClientElement.childrenIndexList[childIndex]
            if (remoteIndex < 0 || clientIndex < 0
                    || remoteIndex >= remoteScreen.elementList.size
                    || clientIndex >= clientScreen.elementList.size) {
                findResult = false
                break
            }
            resultRemoteElement = remoteScreen.elementList[remoteIndex]
            resultClientElement = clientScreen.elementList[clientIndex]

            if (resultClientElement.className != resultRemoteElement.className) {
                findResult = false
                break
            }
        }
    }

    if (findResult && resultClientElement.viewIdResourceName == remoteElement.viewIdResourceName) {
        return resultClientElement
    }

    if (perfectMatch != null) return perfectMatch

    if (matchingList.size > 0) {
        return matchingList[0]
    }

    if (acceptableMatchingList.size > 0) {
        return acceptableMatchingList[0]
    }

    return null
}

// "//android.widget.ScrollView[1]|android.widget.LinearLayout[1]|android.widget.LinearLayout[2]|android.widget.Button[1]"
// "//android.widget.RadioButton[@text='Use Testdroid Cloud']"
// "//android.widget.EditText[@resource-id='com.bitbar.testdroid:id/editText1']"

// Return the client element and the id of remote element.
fun getElementByXPath(clientScreen: Screen, remoteScreen: Screen, XPathStr: String) : Pair<Element?, String?> {
    val xPathMatching: Element? = null

    var clientRootElement = clientScreen.elementList[0]
    var remoteRootElement = remoteScreen.elementList[0]

    val conditionList = XPathStr.split('|')
    Log.i("Xiaoyi", conditionList.toString())

    for (condition in conditionList) {
        val className = condition.split('[')[0]
        val selector = condition.split('[')[1].replace("]", "")
        val selectorInt = selector.toIntOrNull()
        if (selectorInt == null) {
            // Not an Int
            val selectorValue = selector.substring(selector.indexOf('=') + 2, selector.lastIndex - 1)
            if (selector.contains("@text=")) {
                var findMatch = false
                for (childIndex in clientRootElement.childrenIndexList) {
                    val childElement = clientScreen.elementList[childIndex]
                    if (childElement.text == selectorValue) {
                        clientRootElement = childElement
                        findMatch = true
                    }
                }
                if (!findMatch) {
                    return Pair(null, null)
                }
                findMatch = false
                for (childIndex in remoteRootElement.childrenIndexList) {
                    val childElement = remoteScreen.elementList[childIndex]
                    if (childElement.text == selectorValue) {
                        remoteRootElement = childElement
                        findMatch = true
                    }
                }
                if (!findMatch) {
                    return Pair(null, null)
                }
            } else if (selector.contains("@content-desc=")) {
                var findMatch = false
                for (childIndex in clientRootElement.childrenIndexList) {
                    val childElement = clientScreen.elementList[childIndex]
                    if (childElement.contentDescription == selectorValue) {
                        clientRootElement = childElement
                        findMatch = true
                    }
                }
                if (!findMatch) {
                    return Pair(null, null)
                }
                findMatch = false
                for (childIndex in remoteRootElement.childrenIndexList) {
                    val childElement = remoteScreen.elementList[childIndex]
                    if (childElement.contentDescription == selectorValue) {
                        remoteRootElement = childElement
                        findMatch = true
                    }
                }
                if (!findMatch) {
                    return Pair(null, null)
                }
            } else if (selector.contains("@resource-id=")) {
                var findMatch = false
                for (childIndex in clientRootElement.childrenIndexList) {
                    val childElement = clientScreen.elementList[childIndex]
                    if (childElement.viewIdResourceName == selectorValue) {
                        clientRootElement = childElement
                        findMatch = true
                    }
                }
                if (!findMatch) {
                    return Pair(null, null)
                }
                findMatch = false
                for (childIndex in remoteRootElement.childrenIndexList) {
                    val childElement = remoteScreen.elementList[childIndex]
                    if (childElement.viewIdResourceName == selectorValue) {
                        remoteRootElement = childElement
                        findMatch = true
                    }
                }
                if (!findMatch) {
                    return Pair(null, null)
                }
            }
        } else {
            // Is an Int
            if (remoteRootElement.childrenIndexList.size <= selectorInt ||
                clientRootElement.childrenIndexList.size <= selectorInt) {
                return Pair(null, null)
            }
            clientRootElement = clientScreen.elementList[clientRootElement.childrenIndexList[selectorInt]]
            remoteRootElement = remoteScreen.elementList[remoteRootElement.childrenIndexList[selectorInt]]
        }
    }

    return Pair(clientRootElement, remoteRootElement.id)
}