# react-native-huawei

Android React Native module for Huawei AppGallery updates, auto-renewing subscriptions,
Safety Detect attestation, and Push Kit. Works in bare React Native with no Expo runtime.
Expo apps use the same React Native module with an optional config plugin.

Android only; requires a native build. Expo Go, iOS and web cannot execute these APIs.
The bridge uses React Native's native module interoperability. Verified on React Native 0.88
(New Architecture, AGP 9.2) with a minified release build on a device without HMS: startup,
promise rejections reaching JS, and warm and cold notification-tap delivery. Flows that need
HMS Core (purchases, account resolution, tokens, update dialog) still require a Huawei device.

## Installation

```sh
npm install react-native-huawei
```

## Expo setup

Add to your app config:

```json
{
  "expo": {
    "plugins": [
      ["react-native-huawei", { "appId": "YOUR_NUMERIC_APP_ID", "pushMarker": "huawei_push" }]
    ]
  }
}
```

Replace `YOUR_NUMERIC_APP_ID` with your AppGallery app ID (digits, passed as a string).
Place your own `agconnect-services.json` alongside app.json in the consuming app.
Configure the app's signing certificate and required HMS services in AppGallery Connect.
The plugin copies that file to android/app, adds Huawei Maven dependencies and the
AGConnect Gradle plugin, and writes the app ID and push marker to the Android manifest.
It also removes REQUEST_INSTALL_PACKAGES and adds the allowBackup manifest merge override.

```sh
npx expo prebuild --platform android
npx expo run:android
```

Commit or back up native changes before running prebuild. Rebuild whenever native
configuration changes.

## Bare React Native setup (no Expo required)

Install the package and let React Native CLI autolink `HuaweiPackage` using the supplied
`react-native.config.js`. Do not install Expo or run Expo prebuild for this setup.
Use an Android React Native project with its standard Android and Kotlin Gradle plugins.

In android/build.gradle add Huawei's repository in both buildscript.repositories and
allprojects.repositories (or dependencyResolutionManagement.repositories in settings.gradle
if your project centralizes repositories):

```groovy
maven { url 'https://developer.huawei.com/repo/' }
```

In buildscript.dependencies add the AGConnect plugin, retaining your existing Android
Gradle Plugin version:

```groovy
classpath 'com.huawei.agconnect:agcp:1.9.6.300'
```

Place your own agconnect-services.json in android/app and add to android/app/build.gradle:

```groovy
apply plugin: 'com.huawei.agconnect'
```

In your app's AndroidManifest.xml, add the tools namespace on the manifest element,
remove the SDK's install-packages permission, and add metadata inside application:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">
  <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"
                   tools:node="remove" />
  <application android:allowBackup="false" tools:replace="android:allowBackup">
    <meta-data android:name="com.huawei.hms.client.appid"
               android:value="appid=YOUR_NUMERIC_APP_ID" />
    <meta-data android:name="com.aplicart.huawei.PUSH_MARKER"
               android:value="huawei_push" />
  </application>
</manifest>
```

Merge these entries into your existing manifest; retain your application's attributes,
activities and any other tools:replace values. For warm notification taps, use singleTask
on the launch activity. Configure signing and HMS services in AppGallery Connect, then run:

```sh
npx react-native run-android
```

The module's manifest registers its push service automatically. No manual MainApplication
registration is needed when CLI autolinking is enabled. All functions use the same imports
and return types shown below in both Expo and bare React Native.

## API

```ts
import { Platform } from 'react-native';
import { getPushToken, addPushTokenListener } from 'react-native-huawei';

if (Platform.OS === 'android') {
  const subscription = addPushTokenListener(token => {
    // Send the token to your authenticated backend.
  });
  const token = await getPushToken();
  subscription.remove();
}
```

| Function | Result / behavior |
| --- | --- |
| `checkAppUpdate()` | Promise of `{ updateAvailable, immediateUpdateAllowed?, flexibleUpdateAllowed? }` |
| `showUpdateDialog(forceUpdate = false)` | Promise of void; asks HMS to display an available update |
| `isIapEnvironmentReady()` | Promise of boolean; may open account resolution UI |
| `getOwnedSubscriptions()` | Promise of `{ productId, subIsValid }[]` |
| `getProductInfo(skuIds)` | Promise of `{ productId, price }[]`; subscriptions only |
| `createPurchaseIntent(productId)` | Promise of `{ returnCode, cancelled }`; opens purchase UI |
| `startManageSubscriptionActivity(productId?)` | Promise of void; opens subscription management |
| `getSysIntegrityToken(nonce)` | Promise of opaque JWS; nonce must contain at least 16 UTF-8 bytes |
| `getPushToken()` | Promise of token or null; older EMUI may deliver a token later |
| `addPushTokenListener(listener)` | Subscription with `remove()` |
| `getInitialPushData()` | Synchronous string-valued payload or null; consumed on first read |
| `addPushOpenedListener(listener)` | Subscription for taps while running, with `remove()` |

Native calls throw on unsupported platforms. HMS availability, account state and network
failures can reject promises. UI APIs require an activity. Serialize IAP purchase and
account resolution operations; the module tracks one pending request of each type.
Owned subscription queries do not paginate.
The API exposes subscription operations, not consumables or purchase receipt verification.
Verify subscription entitlement on your backend before granting paid access.

Generate attestation nonces on your server and verify the returned JWS there, including
signature, nonce, package/certificate identity and integrity claims. The nonce is passed
as UTF-8 bytes; HMS encodes it in the JWS. No backend verification is included here.

For notification taps, the sending server must include an intent extra named by
`pushMarker` (default `huawei_push`). The marker's presence identifies a notification;
remaining string extras become the JS payload. Use the app's launch activity and its
singleTask launch mode so warm taps arrive through onNewIntent. HMS displays notification
messages; this module does not expose foreground/background data-message handlers or
request Android notification permission. Request permission in your app as appropriate.

## Upgrading to 0.2.0

The native package moved from `expo.modules.huawei` to `com.aplicart.huawei`. The JS API is
unchanged. Expo apps only need a clean prebuild. Bare apps must rename the manifest metadata
to `com.aplicart.huawei.PUSH_MARKER`: under the old name the marker silently falls back to
`huawei_push`, so a custom marker would stop matching notification taps.

## Public repository hygiene

This package contains no service configuration, signing keys, access tokens, or server
credentials. App IDs are supplied by consumers. `.gitignore` excludes common credential
files, and the npm `files` allowlist includes only package source and documentation.
Never add real service configurations or credentials to examples or tests.

## Development and validation

```sh
npm install
npm test
npm run typecheck
npm pack --dry-run
```

The plugin tests cover config validation, Gradle edits, manifest updates and repeat runs.
Real-device HMS flows and bare React Native apps still need validation. TypeScript and
config-plugin tests are checked. No React Native or Expo version support matrix has been
established; see the top of this file for what has been verified.
