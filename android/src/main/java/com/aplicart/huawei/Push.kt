package com.aplicart.huawei

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.common.ApiException
import com.huawei.hms.push.HmsMessageService


/** HMS Push Kit tokens and notification tap payloads. */
object HuaweiPush {
  private const val TAG = "HuaweiPush"
  private const val PREFS = "huawei_push"
  private const val PREF_TOKEN = "token"

  /**
   * Set by the sending server on each notification click intent, so an
   * ordinary launch is never mistaken for a notification tap.
   */
  private const val DEFAULT_PUSH_MARKER = "huawei_push"

  private fun pushMarker(context: Context): String {
    val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    return info.metaData?.getString("com.aplicart.huawei.PUSH_MARKER") ?: DEFAULT_PUSH_MARKER
  }

  /** Set while the module is alive, so a token arriving through the service reaches JS. */
  @Volatile
  var tokenListener: ((String) -> Unit)? = null

  fun getToken(context: Context, promise: Promise) {
    val appId = HmsAppId.read(context)
    if (appId == null) {
      promise.reject("HMS_PUSH_NO_APP_ID", "Missing ${HmsAppId.META_DATA} manifest meta-data", null)
      return
    }

    // getToken() is a blocking network call and throws if run on the main thread.
    Thread {
      try {
        val token = HmsInstanceId.getInstance(context).getToken(appId, "HCM")
        // On EMUI below 10 getToken() returns empty and the token is delivered later through
        // HuaweiPushService.onNewToken instead; fall back to the last one that arrived that way.
        promise.resolve(token?.takeIf { it.isNotEmpty() } ?: storedToken(context))
      } catch (e: ApiException) {
        promise.reject("HMS_PUSH_TOKEN_ERROR", "Push Kit getToken failed (${e.statusCode}): ${e.message}", e)
      } catch (e: Exception) {
        promise.reject("HMS_PUSH_TOKEN_ERROR", e.message, e)
      }
    }.start()
  }

  fun onNewToken(context: Context, token: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_TOKEN, token).apply()
    tokenListener?.invoke(token)
  }

  private fun storedToken(context: Context): String? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_TOKEN, null)

  /**
   * The notification payload carried by a launch intent, or null when the intent did not come
   * from one of our notifications. Consumes the marker, so a configuration change or a later
   * read of the same activity intent does not report the tap twice.
   */
  fun takePushData(context: Context, intent: Intent?): Map<String, String>? {
    val PUSH_MARKER_EXTRA = pushMarker(context)
    val extras = intent?.extras ?: return null
    if (!extras.containsKey(PUSH_MARKER_EXTRA)) return null
    intent.removeExtra(PUSH_MARKER_EXTRA)

    val data = mutableMapOf<String, String>()
    for (key in extras.keySet()) {
      if (key == PUSH_MARKER_EXTRA) continue
      val value = extras.get(key)
      if (value is String) data[key] = value
    }
    Log.d(TAG, "Opened from notification with ${data.keys}")
    return data
  }
}

// Both overloads exist in Push Kit 6.13 and which one HMS Core invokes has varied by version,
// so both are handled; a repeat is harmless (JS skips an unchanged registration).
class HuaweiPushService : HmsMessageService() {
  override fun onNewToken(token: String?, bundle: Bundle?) {
    if (!token.isNullOrEmpty()) HuaweiPush.onNewToken(applicationContext, token)
  }

  @Deprecated("Superseded by onNewToken(String, Bundle)")
  override fun onNewToken(token: String?) {
    if (!token.isNullOrEmpty()) HuaweiPush.onNewToken(applicationContext, token)
  }
}
