package expo.modules.huawei

import android.app.Activity
import android.content.Intent
import com.huawei.hms.jos.JosApps
import com.huawei.updatesdk.service.appmgr.bean.ApkUpgradeInfo
import com.huawei.updatesdk.service.otaupdate.CheckUpdateCallBack
import com.huawei.updatesdk.service.otaupdate.UpdateKey


object AppUpdate {

  fun checkAppUpdate(activity: Activity, promise: Promise) {
    val client = JosApps.getAppUpdateClient(activity)
    client.checkAppUpdate(activity, object : CheckUpdateCallBack {
      override fun onUpdateInfo(intent: Intent?) {
        @Suppress("DEPRECATION")
        val info = intent?.getParcelableExtra<ApkUpgradeInfo>(UpdateKey.INFO)
        if (info == null) {
          promise.resolve(mapOf("updateAvailable" to false))
          return
        }
        val mustUpdate = intent.getBooleanExtra(UpdateKey.MUST_UPDATE, false)
        promise.resolve(mapOf(
          "updateAvailable" to true,
          "immediateUpdateAllowed" to mustUpdate,
          "flexibleUpdateAllowed" to !mustUpdate,
        ))
      }

      override fun onMarketInstallInfo(intent: Intent?) {
        promise.resolve(mapOf("updateAvailable" to false))
      }

      override fun onMarketStoreError(responseCode: Int) {
        promise.resolve(mapOf("updateAvailable" to false))
      }

      override fun onUpdateStoreError(responseCode: Int) {
        promise.resolve(mapOf("updateAvailable" to false))
      }
    })
  }

  fun showUpdateDialog(activity: Activity, forceUpdate: Boolean, promise: Promise) {
    val client = JosApps.getAppUpdateClient(activity)
    client.checkAppUpdate(activity, object : CheckUpdateCallBack {
      override fun onUpdateInfo(intent: Intent?) {
        @Suppress("DEPRECATION")
        val info = intent?.getParcelableExtra<ApkUpgradeInfo>(UpdateKey.INFO)
        if (info != null) {
          client.showUpdateDialog(activity, info, forceUpdate)
        }
        promise.resolve(null)
      }

      override fun onMarketInstallInfo(intent: Intent?) {
        promise.resolve(null)
      }

      override fun onMarketStoreError(responseCode: Int) {
        promise.resolve(null)
      }

      override fun onUpdateStoreError(responseCode: Int) {
        promise.resolve(null)
      }
    })
  }
}
