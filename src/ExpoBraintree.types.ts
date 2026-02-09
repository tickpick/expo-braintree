// ── Initialization ──────────────────────────────────────────────────────────

export type Authorization = string; // client token or tokenization key

// ── Card ────────────────────────────────────────────────────────────────────

export interface CardData {
  number: string;
  expirationMonth: string;
  expirationYear: string;
  cvv?: string;
  postalCode?: string;
  cardholderName?: string;
}

// ── Apple Pay (iOS) ─────────────────────────────────────────────────────────

export interface ApplePayRequest {
  merchantIdentifier: string;
  countryCode: string;
  currencyCode: string;
  paymentSummaryItems: ApplePaySummaryItem[];
  supportedNetworks?: ApplePayNetwork[];
}

export interface ApplePaySummaryItem {
  label: string;
  amount: string; // decimal string, e.g. "9.99"
  type?: "final" | "pending";
}

export type ApplePayNetwork =
  | "visa"
  | "masterCard"
  | "amex"
  | "discover"
  | "maestro"
  | "jcb";

// ── Google Pay (Android) ────────────────────────────────────────────────────

export interface GooglePayRequest {
  merchantName?: string;
  countryCode: string;
  currencyCode: string;
  totalPrice: string;
  totalPriceStatus?: "FINAL" | "ESTIMATED" | "NOT_CURRENTLY_KNOWN";
  allowPrepaidCards?: boolean;
  billingAddressRequired?: boolean;
  emailRequired?: boolean;
  shippingAddressRequired?: boolean;
  googleMerchantId?: string;
}

// ── PayPal ──────────────────────────────────────────────────────────────────

export interface PayPalCheckoutRequest {
  amount: string;
  currencyCode?: string;
  intent?: "authorize" | "sale" | "order";
  userAction?: "default" | "commit";
  displayName?: string;
}

export interface PayPalVaultRequest {
  billingAgreementDescription?: string;
  displayName?: string;
  userAuthenticationEmail?: string;
}

// ── Venmo ───────────────────────────────────────────────────────────────────

export interface VenmoRequest {
  paymentMethodUsage: "singleUse" | "multiUse";
  universalLink: string;
  profileId?: string;
  displayName?: string;
  collectCustomerBillingAddress?: boolean;
  collectCustomerShippingAddress?: boolean;
}

// ── Results ─────────────────────────────────────────────────────────────────

export interface PaymentMethodNonce {
  nonce: string;
  type: "card" | "applePay" | "googlePay" | "paypal" | "venmo";
  isDefault: boolean;
  description?: string;
}

export interface CardNonce extends PaymentMethodNonce {
  type: "card";
  cardNetwork?: string;
  lastFour?: string;
  expirationMonth?: string;
  expirationYear?: string;
  bin?: string;
}

export interface ApplePayNonce extends PaymentMethodNonce {
  type: "applePay";
}

export interface GooglePayNonce extends PaymentMethodNonce {
  type: "googlePay";
  cardNetwork?: string;
  lastFour?: string;
  email?: string;
  billingAddress?: Address;
  shippingAddress?: Address;
}

export interface PayPalNonce extends PaymentMethodNonce {
  type: "paypal";
  email?: string;
  firstName?: string;
  lastName?: string;
  billingAddress?: Address;
}

export interface VenmoNonce extends PaymentMethodNonce {
  type: "venmo";
  username?: string;
}

export interface Address {
  recipientName?: string;
  streetAddress?: string;
  extendedAddress?: string;
  locality?: string;
  region?: string;
  postalCode?: string;
  countryCodeAlpha2?: string;
}
