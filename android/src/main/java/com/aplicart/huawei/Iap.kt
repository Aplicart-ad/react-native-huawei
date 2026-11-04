package com.aplicart.huawei

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapApiException
import com.huawei.hms.iap.IapClient
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.OwnedPurchasesResult
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.ProductInfoResult
import com.huawei.hms.iap.entity.PurchaseIntentReq
import com.huawei.hms.iap.entity.PurchaseIntentResult
import com.huawei.hms.iap.entity.StartIapActivityReq
import com.huawei.hms.iap.entity.StartIapActivityResult


private const val TAG = "HuaweiIap"

/** Subscription-only Huawei IAP operations. */
object HuaweiIap {

  private const val REQUEST_CODE_PURCHASE = 4002
  private const val REQUEST_CODE_ENV_READY = 4003

  // Only one purchase (or env-ready resolution) can be in flight at a time.
  private var pendingPurchasePromise: Promise? = null
  private var pendingEnvReadyPromise: Promise? = null

  fun isEnvironmentReady(activity: Activity, promise: Promise) {
    Iap.getIapClient(activity).isEnvReady()
      .addOnSuccessListener { promise.resolve(true) }
      .addOnFailureListener { e ->
        // A resolvable failure (e.g. "needs Huawei account sign-in") carries a Status
        // that, like createPurchaseIntent's below, can be resolved via
        // startResolutionForResult — do that instead of flatly reporting not-ready.
        val status = (e as? IapApiException)?.status
        if (status != null && status.hasResolution()) {
          try {
            pendingEnvReadyPromise = promise
            status.startResolutionForResult(activity, REQUEST_CODE_ENV_READY)
            return@addOnFailureListener
          } catch (ex: IntentSender.SendIntentException) {
            pendingEnvReadyPromise = null
          }
        }
        // Hard, non-resolvable failure (or resolution launch failed) — the caller races
        // this against a timeout and simply bails if not ready.
        promise.resolve(false)
      }
  }

  fun getOwnedSubscriptions(activity: Activity, promise: Promise) {
    val req = OwnedPurchasesReq().apply {
      priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
    }
    Iap.getIapClient(activity).obtainOwnedPurchases(req)
      .addOnSuccessListener { result: OwnedPurchasesResult ->
        val list = (result.inAppPurchaseDataList ?: emptyList()).mapNotNull { json ->
          try {
            val data = InAppPurchaseData(json)
            mapOf("productId" to data.productId, "subIsValid" to data.isSubValid)
          } catch (e: Exception) {
            Log.w(TAG, "Dropping unparseable owned-purchase entry", e)
            null
          }
        }
        promise.resolve(list)
      }
      .addOnFailureListener { e -> promise.reject("IAP_OWNED_PURCHASES_ERROR", e.message, e) }
  }

  fun getProductInfo(activity: Activity, skuIds: List<String>, promise: Promise) {
    val req = ProductInfoReq().apply {
      priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
      productIds = skuIds.toMutableList()
    }
    Iap.getIapClient(activity).obtainProductInfo(req)
      .addOnSuccessListener { result: ProductInfoResult ->
        val list = (result.productInfoList ?: emptyList()).map { p ->
          mapOf("productId" to p.productId, "price" to p.price)
        }
        promise.resolve(list)
      }
      .addOnFailureListener { e -> promise.reject("IAP_PRODUCT_INFO_ERROR", e.message, e) }
  }

  fun createPurchaseIntent(activity: Activity, productId: String, promise: Promise) {
    val req = PurchaseIntentReq().apply {
      priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
      this.productId = productId
      developerPayload = ""
    }
    Iap.getIapClient(activity).createPurchaseIntent(req)
      .addOnSuccessListener { result: PurchaseIntentResult ->
        val status = result.status
        if (status == null) {
          promise.reject("IAP_NO_STATUS", "No purchase status returned", null)
          return@addOnSuccessListener
        }
        if (!status.hasResolution()) {
          promise.reject("IAP_NO_RESOLUTION", "Purchase intent has no resolution", null)
          return@addOnSuccessListener
        }
        try {
          pendingPurchasePromise = promise
          status.startResolutionForResult(activity, REQUEST_CODE_PURCHASE)
        } catch (e: IntentSender.SendIntentException) {
          pendingPurchasePromise = null
          promise.reject("IAP_RESOLUTION_ERROR", e.message, e)
        }
      }
      .addOnFailureListener { e -> promise.reject("IAP_CREATE_INTENT_ERROR", e.message, e) }
  }

  // AppGallery Review Guidelines rule 6.2: auto-renewable subscriptions must be
  // cancellable directly within the app, not by redirecting to a third-party app (the
  // old implementation opened the AppGallery store listing via Linking.openURL). This
  // opens HMS's own subscription-management screen instead — with a productId it jumps
  // straight to that subscription's edit/cancel page; without one, the general manager
  // listing all subscriptions. StartIapActivityResult.startActivity() is a plain
  // Activity.startActivity() call (confirmed by decompiling the HMS IAP AAR — unlike
  // createPurchaseIntent above, no Status/startResolutionForResult is involved).
  fun startManageSubscriptionActivity(activity: Activity, productId: String?, promise: Promise) {
    val req = StartIapActivityReq().apply {
      type = if (productId != null) {
        subscribeProductId = productId
        StartIapActivityReq.TYPE_SUBSCRIBE_EDIT_ACTIVITY
      } else {
        StartIapActivityReq.TYPE_SUBSCRIBE_MANAGER_ACTIVITY
      }
    }
    Iap.getIapClient(activity).startIapActivity(req)
      .addOnSuccessListener { result: StartIapActivityResult ->
        result.startActivity(activity)
        promise.resolve(null)
      }
      .addOnFailureListener { e -> promise.reject("IAP_START_ACTIVITY_ERROR", e.message, e) }
  }

  fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_PURCHASE -> handlePurchaseResult(activity, data)
      REQUEST_CODE_ENV_READY -> handleEnvReadyResolution(resultCode)
    }
  }

  private fun handlePurchaseResult(activity: Activity, data: Intent?) {
    val promise = pendingPurchasePromise ?: return
    pendingPurchasePromise = null

    if (data == null) {
      // The resolution screen returns with a null Intent when the user backs out before
      // HMS attaches a PurchaseResultInfo extra — the standard shape for a user
      // cancellation, not a hard failure.
      promise.resolve(mapOf("returnCode" to OrderStatusCode.ORDER_STATE_CANCEL, "cancelled" to true))
      return
    }

    val result = Iap.getIapClient(activity).parsePurchaseResultInfoFromIntent(data)
    promise.resolve(mapOf(
      "returnCode" to result.returnCode,
      "cancelled" to (result.returnCode == OrderStatusCode.ORDER_STATE_CANCEL),
    ))
  }

  private fun handleEnvReadyResolution(resultCode: Int) {
    val promise = pendingEnvReadyPromise ?: return
    pendingEnvReadyPromise = null
    // RESULT_OK means the sign-in (or other resolution) step completed; the environment
    // should now be ready; confirm behavior on a device with HMS account resolution.
    promise.resolve(resultCode == Activity.RESULT_OK)
  }
}
