export type AppUpdateInfo = {
  updateAvailable: boolean;
  immediateUpdateAllowed?: boolean;
  flexibleUpdateAllowed?: boolean;
};

export type OwnedSubscription = {
  productId: string;
  subIsValid: boolean;
};

export type ProductInfo = {
  productId: string;
  price: string;
};

export type PurchaseResult = {
  returnCode: number;
  cancelled: boolean;
};
