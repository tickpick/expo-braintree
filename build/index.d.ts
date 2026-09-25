import type { CardData, CardNonce, ApplePayRequest, ApplePayNonce, GooglePayRequest, GooglePayNonce, PayPalCheckoutRequest, PayPalVaultRequest, PayPalNonce, VenmoRequest, VenmoNonce, DataCollectorRequest } from "./ExpoBraintree.types";
export * from "./ExpoBraintree.types";
export declare function initialize(authorization: string): Promise<void>;
/**
 * Set the App Link return URL for PayPal/Venmo flows on Android.
 * Must be called before tokenizePayPalCheckout/tokenizePayPalVault/tokenizeVenmo.
 */
export declare function setReturnUrl(url: string): Promise<void>;
export declare function tokenizeCard(card: CardData): Promise<CardNonce>;
export declare function isApplePaySupported(): Promise<boolean>;
export declare function tokenizeApplePay(request: ApplePayRequest): Promise<ApplePayNonce>;
export declare function isGooglePayReady(request?: Partial<GooglePayRequest>): Promise<boolean>;
export declare function tokenizeGooglePay(request: GooglePayRequest): Promise<GooglePayNonce>;
export declare function tokenizePayPalCheckout(request: PayPalCheckoutRequest): Promise<PayPalNonce>;
export declare function tokenizePayPalVault(request: PayPalVaultRequest): Promise<PayPalNonce>;
export declare function tokenizeVenmo(request: VenmoRequest): Promise<VenmoNonce>;
/**
 * Collect the `device_data` string for Braintree's fraud tools. Pass it to your
 * server with the payment nonce (transaction sale / payment method create).
 * Requires initialize() first. Collection can take a moment, so start it when
 * the checkout screen opens rather than at submit.
 */
export declare function collectDeviceData(request?: DataCollectorRequest): Promise<string>;
