package expo.modules.huawei

import android.app.Activity
import android.content.Intent
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class HuaweiModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  override fun getName() = "Huawei"

  private fun emit(name: String, data: Map<String, String>) {
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(name, Arguments.makeNativeMap(data))
  }

  private val activityListener = object : BaseActivityEventListener() {
    override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
      HuaweiIap.handleActivityResult(activity, requestCode, resultCode, data)
    }
    override fun onNewIntent(intent: Intent) {
      HuaweiPush.takePushData(context, intent)?.let { emit("onPushOpened", it) }
    }
  }

  override fun initialize() {
    super.initialize()
    context.addActivityEventListener(activityListener)
    HuaweiPush.tokenListener = { emit("onPushToken", mapOf("token" to it)) }
  }

  override fun invalidate() {
    context.removeActivityEventListener(activityListener)
    HuaweiPush.tokenListener = null
    super.invalidate()
  }

  @ReactMethod fun addListener(eventName: String) {}
  @ReactMethod fun removeListeners(count: Double) {}

  private fun withActivity(promise: com.facebook.react.bridge.Promise, action: (Activity, Promise) -> Unit) {
    val activity = context.currentActivity
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No current activity")
      return
    }
    activity.runOnUiThread { action(activity, Promise(promise)) }
  }

  @ReactMethod fun checkAppUpdate(promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> AppUpdate.checkAppUpdate(activity, result) }
  }

  @ReactMethod fun showUpdateDialog(forceUpdate: Boolean, promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> AppUpdate.showUpdateDialog(activity, forceUpdate, result) }
  }

  @ReactMethod fun isIapEnvironmentReady(promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> HuaweiIap.isEnvironmentReady(activity, result) }
  }

  @ReactMethod fun getOwnedSubscriptions(promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> HuaweiIap.getOwnedSubscriptions(activity, result) }
  }

  @ReactMethod fun getProductInfo(skuIds: ReadableArray, promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> HuaweiIap.getProductInfo(activity, (0 until skuIds.size()).map { requireNotNull(skuIds.getString(it)) { "SKU IDs must be strings" } }, result) }
  }

  @ReactMethod fun createPurchaseIntent(productId: String, promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> HuaweiIap.createPurchaseIntent(activity, productId, result) }
  }

  @ReactMethod fun startManageSubscriptionActivity(productId: String?, promise: com.facebook.react.bridge.Promise) {
    withActivity(promise) { activity, result -> HuaweiIap.startManageSubscriptionActivity(activity, productId, result) }
  }

  @ReactMethod fun getSysIntegrityToken(nonce: String, promise: com.facebook.react.bridge.Promise) {
    HuaweiSysIntegrity.getToken(context, nonce, Promise(promise))
  }
  @ReactMethod fun getPushToken(promise: com.facebook.react.bridge.Promise) {
    HuaweiPush.getToken(context, Promise(promise))
  }
  @ReactMethod(isBlockingSynchronousMethod = true)
  fun getInitialPushData(): WritableMap? =
    HuaweiPush.takePushData(context, context.currentActivity?.intent)?.let { Arguments.makeNativeMap(it) }
}
