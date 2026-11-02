export * from './Huawei.types';

import { getHuaweiModule } from './HuaweiModule';

export function checkAppUpdate() {
  return getHuaweiModule().checkAppUpdate();
}

export function showUpdateDialog(forceUpdate: boolean = false) {
  return getHuaweiModule().showUpdateDialog(forceUpdate);
}

export function isIapEnvironmentReady() {
  return getHuaweiModule().isIapEnvironmentReady();
}

export function getOwnedSubscriptions() {
  return getHuaweiModule().getOwnedSubscriptions();
}

export function getProductInfo(skuIds: string[]) {
  return getHuaweiModule().getProductInfo(skuIds);
}

export function createPurchaseIntent(productId: string) {
  return getHuaweiModule().createPurchaseIntent(productId);
}

export function startManageSubscriptionActivity(productId?: string) {
  return getHuaweiModule().startManageSubscriptionActivity(productId);
}

/**
 * HMS Safety Detect SysIntegrity attestation. `nonce` must be a server-issued opaque
 * string of at least 16 bytes; the resolved JWS is forwarded verbatim to your server,
 * which verifies it. Rejects on any device without a usable HMS Core (see SysIntegrity.kt).
 */
export function getSysIntegrityToken(nonce: string) {
  return getHuaweiModule().getSysIntegrityToken(nonce);
}

/**
 * HMS Push Kit token, or null when HMS has not issued one yet (EMUI < 10 delivers it later,
 * through `addPushTokenListener`). Rejects on any device without a usable HMS Core (see Push.kt).
 */
export function getPushToken() {
  return getHuaweiModule().getPushToken();
}

export function addPushTokenListener(listener: (token: string) => void) {
  return getHuaweiModule().addListener('onPushToken', ({ token }) => listener(token));
}

/** The payload of the notification that cold-started the app, consumed on first read. */
export function getInitialPushData() {
  return getHuaweiModule().getInitialPushData();
}

/** A notification tapped while the app was already running. */
export function addPushOpenedListener(listener: (data: Record<string, string>) => void) {
  return getHuaweiModule().addListener('onPushOpened', listener);
}
