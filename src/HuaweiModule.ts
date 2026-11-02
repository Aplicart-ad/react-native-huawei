import { NativeEventEmitter, NativeModules, Platform, type EmitterSubscription } from 'react-native';

import { AppUpdateInfo, OwnedSubscription, ProductInfo, PurchaseResult } from './Huawei.types';

type HuaweiModuleEvents = {
  onPushToken(event: { token: string }): void;
  onPushOpened(data: Record<string, string>): void;
};

interface HuaweiModule {
  addListener<K extends keyof HuaweiModuleEvents>(event: K, listener: HuaweiModuleEvents[K]): EmitterSubscription;
  checkAppUpdate(): Promise<AppUpdateInfo>;
  showUpdateDialog(forceUpdate: boolean): Promise<void>;
  isIapEnvironmentReady(): Promise<boolean>;
  getOwnedSubscriptions(): Promise<OwnedSubscription[]>;
  getProductInfo(skuIds: string[]): Promise<ProductInfo[]>;
  createPurchaseIntent(productId: string): Promise<PurchaseResult>;
  startManageSubscriptionActivity(productId?: string): Promise<void>;
  getSysIntegrityToken(nonce: string): Promise<string>;
  getPushToken(): Promise<string | null>;
  getInitialPushData(): Record<string, string> | null;
}

let nativeModule: HuaweiModule | null = null;

export function getHuaweiModule(): HuaweiModule {
  if (Platform.OS !== 'android') {
    throw new Error('Huawei native module is only available on Android');
  }

  if (!nativeModule) {
    const bridge = NativeModules.Huawei;
    if (!bridge) throw new Error('Huawei native module is not linked. Install the package and rebuild Android.');
    const emitter = new NativeEventEmitter(bridge);
    nativeModule = {
      checkAppUpdate: () => bridge.checkAppUpdate(),
      showUpdateDialog: (forceUpdate) => bridge.showUpdateDialog(forceUpdate),
      isIapEnvironmentReady: () => bridge.isIapEnvironmentReady(),
      getOwnedSubscriptions: () => bridge.getOwnedSubscriptions(),
      getProductInfo: (ids) => bridge.getProductInfo(ids),
      createPurchaseIntent: (id) => bridge.createPurchaseIntent(id),
      startManageSubscriptionActivity: (id) => bridge.startManageSubscriptionActivity(id ?? null),
      getSysIntegrityToken: (nonce) => bridge.getSysIntegrityToken(nonce),
      getPushToken: () => bridge.getPushToken(),
      getInitialPushData: () => bridge.getInitialPushData(),
      addListener: (event, listener) => emitter.addListener(event, (...args) => {
        (listener as (payload: unknown) => void)(args[0]);
      }),
    };
  }
  return nativeModule;
}
