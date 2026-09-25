import { Platform } from "react-native";
import ExpoBraintreeModule from "./ExpoBraintreeModule";
import type {
  CardData,
  CardNonce,
  ApplePayRequest,
  ApplePayNonce,
  GooglePayRequest,
  GooglePayNonce,
  PayPalCheckoutRequest,
  PayPalVaultRequest,
  PayPalNonce,
  VenmoRequest,
  VenmoNonce,
  DataCollectorRequest,
} from "./ExpoBraintree.types";

export * from "./ExpoBraintree.types";

// ── Initialization ──────────────────────────────────────────────────────────

export async function initialize(authorization: string): Promise<void> {
  return await ExpoBraintreeModule.initialize(authorization);
}

/**
 * Set the App Link return URL for PayPal/Venmo flows on Android.
 * Must be called before tokenizePayPalCheckout/tokenizePayPalVault/tokenizeVenmo.
 */
export async function setReturnUrl(url: string): Promise<void> {
  return await ExpoBraintreeModule.setReturnUrl(url);
}

// ── Card ────────────────────────────────────────────────────────────────────

export async function tokenizeCard(card: CardData): Promise<CardNonce> {
  return await ExpoBraintreeModule.tokenizeCard(card);
}

// ── Apple Pay (iOS) ─────────────────────────────────────────────────────────

export async function isApplePaySupported(): Promise<boolean> {
  if (Platform.OS !== "ios") return false;
  return await ExpoBraintreeModule.isApplePaySupported();
}

export async function tokenizeApplePay(
  request: ApplePayRequest
): Promise<ApplePayNonce> {
  if (Platform.OS !== "ios") {
    throw new Error("Apple Pay is only available on iOS");
  }
  return await ExpoBraintreeModule.tokenizeApplePay(request);
}

// ── Google Pay (Android) ────────────────────────────────────────────────────

export async function isGooglePayReady(
  request?: Partial<GooglePayRequest>
): Promise<boolean> {
  if (Platform.OS !== "android") return false;
  return await ExpoBraintreeModule.isGooglePayReady(request ?? {});
}

export async function tokenizeGooglePay(
  request: GooglePayRequest
): Promise<GooglePayNonce> {
  if (Platform.OS !== "android") {
    throw new Error("Google Pay is only available on Android");
  }
  return await ExpoBraintreeModule.tokenizeGooglePay(request);
}

// ── PayPal ──────────────────────────────────────────────────────────────────

export async function tokenizePayPalCheckout(
  request: PayPalCheckoutRequest
): Promise<PayPalNonce> {
  return await ExpoBraintreeModule.tokenizePayPalCheckout(request);
}

export async function tokenizePayPalVault(
  request: PayPalVaultRequest
): Promise<PayPalNonce> {
  return await ExpoBraintreeModule.tokenizePayPalVault(request);
}

// ── Venmo ───────────────────────────────────────────────────────────────────

export async function tokenizeVenmo(
  request: VenmoRequest
): Promise<VenmoNonce> {
  return await ExpoBraintreeModule.tokenizeVenmo(request);
}

// ── Device Data (fraud) ─────────────────────────────────────────────────────

/**
 * Collect the `device_data` string for Braintree's fraud tools. Pass it to your
 * server with the payment nonce (transaction sale / payment method create).
 * Requires initialize() first. Collection can take a moment, so start it when
 * the checkout screen opens rather than at submit.
 */
export async function collectDeviceData(
  request: DataCollectorRequest = {}
): Promise<string> {
  return await ExpoBraintreeModule.collectDeviceData(request);
}
