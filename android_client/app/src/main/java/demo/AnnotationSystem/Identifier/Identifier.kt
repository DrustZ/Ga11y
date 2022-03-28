package demo.AnnotationSystem.Identifier

import demo.AnnotationSystem.Utilities.*
import java.util.ArrayList

object Identifier {
    private val appIdList = ArrayList<AppId>()

    fun getAppId(packageName: String, versionCode: Int): AppId {
        for (appId in appIdList) {
            if (appId.packageName == formatForFirebase(packageName) && appId.versionCode == versionCode) {
                return appId
            }
        }
        val newAppId = AppId(packageName, versionCode)
        appIdList.add(newAppId)
        return newAppId
    }
}
