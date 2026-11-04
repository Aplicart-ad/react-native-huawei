package com.aplicart.huawei

import android.content.Context
import com.huawei.hms.common.ApiException
import com.huawei.hms.support.api.entity.safetydetect.SysIntegrityResp
import com.huawei.hms.support.api.safetydetect.SafetyDetect
import com.huawei.hms.support.api.safetydetect.SafetyDetectStatusCodes


/**
 * Returns an opaque Safety Detect JWS for verification by the consuming app's server.
 * Verify its signature, nonce, package name, certificate digest and integrity claims.
 */
object HuaweiSysIntegrity {

  fun getToken(context: Context, nonce: String, promise: Promise) {
    val appId = HmsAppId.read(context)
    if (appId == null) {
      promise.reject("SYS_INTEGRITY_NO_APP_ID", "Missing ${HmsAppId.META_DATA} manifest meta-data", null)
      return
    }

    // The nonce is treated as an opaque, server-issued string: HMS Base64-encodes whatever
    // bytes it is given into the JWS's `nonce` claim, so server-api compares that claim
    // against Base64(UTF-8(nonce it issued)) — no encoding negotiation between the two ends,
    // and any nonce format (uuid, hex, base64) round-trips unchanged. Huawei requires at
    // least 16 bytes of entropy.
    val nonceBytes = nonce.toByteArray(Charsets.UTF_8)
    if (nonceBytes.size < 16) {
      promise.reject("SYS_INTEGRITY_NONCE_TOO_SHORT", "Nonce must be at least 16 bytes", null)
      return
    }

    SafetyDetect.getClient(context).sysIntegrity(nonceBytes, appId)
      .addOnSuccessListener { resp: SysIntegrityResp ->
        val jws = resp.result
        if (jws.isNullOrEmpty()) {
          promise.reject("SYS_INTEGRITY_EMPTY", "SysIntegrity returned an empty result", null)
        } else {
          promise.resolve(jws)
        }
      }
      .addOnFailureListener { e ->
        // Common, entirely expected failures here are a device with no/outdated HMS Core,
        // an unsupported EMUI version, or no network — the JS layer treats every rejection
        // as "no attestation available" and falls through without a header rather than
        // blocking the request.
        val statusCode = (e as? ApiException)?.statusCode
        val message = if (statusCode != null) {
          "${SafetyDetectStatusCodes.getStatusCodeString(statusCode)} ($statusCode): ${e.message}"
        } else {
          e.message
        }
        promise.reject("SYS_INTEGRITY_ERROR", message, e)
      }
  }
}
