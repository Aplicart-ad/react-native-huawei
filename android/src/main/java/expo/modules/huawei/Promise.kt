package expo.modules.huawei

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise as ReactPromise

/** Converts Kotlin collections to bridge-compatible values. */
class Promise(private val promise: ReactPromise) {
  fun resolve(value: Any?) {
    val result = when (value) {
      is Map<*, *> -> Arguments.makeNativeMap(value as Map<String, Any?>)
      is List<*> -> Arguments.makeNativeArray(value)
      else -> value
    }
    promise.resolve(result)
  }
  fun reject(code: String, message: String?, cause: Throwable?) {
    promise.reject(code, message, cause)
  }
}
