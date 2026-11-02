package expo.modules.huawei

import android.content.Context
import android.content.pm.PackageManager

/**
 * The AppGallery Connect app id, shared by every HMS API in this module that needs it
 * (SysIntegrity, Push Kit).
 */
object HmsAppId {

  // Injected into the merged manifest by the react-native-huawei config plugin from the
  // appId passed in app.json's plugin config, in the "appid=<id>" form HMS expects. Reading
  // it back here avoids either hardcoding the id a second time or pulling agconnect-core
  // into this module just for AGConnectServicesConfig (its version is managed by the agcp
  // gradle plugin at app level, so declaring it here risks a version skew).
  const val META_DATA = "com.huawei.hms.client.appid"

  fun read(context: Context): String? {
    return try {
      val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
      // Stored as "appid=<your-app-id>"; the HMS APIs want the bare id.
      info.metaData?.getString(META_DATA)?.substringAfter("appid=")?.takeIf { it.isNotEmpty() }
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }
  }
}
