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
} from "./ExpoBraintree.types";

export * from "./ExpoBraintree.types";

// ── Initialization ──────────────────────────────────────────────────────────

export async function initialize(authorization: string): Promise<void> {
  return await ExpoBraintreeModule.initialize(authorization);
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
