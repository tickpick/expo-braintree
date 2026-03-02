"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __exportStar = (this && this.__exportStar) || function(m, exports) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports, p)) __createBinding(exports, m, p);
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.initialize = initialize;
exports.tokenizeCard = tokenizeCard;
exports.isApplePaySupported = isApplePaySupported;
exports.tokenizeApplePay = tokenizeApplePay;
exports.isGooglePayReady = isGooglePayReady;
exports.tokenizeGooglePay = tokenizeGooglePay;
exports.tokenizePayPalCheckout = tokenizePayPalCheckout;
exports.tokenizePayPalVault = tokenizePayPalVault;
exports.tokenizeVenmo = tokenizeVenmo;
const react_native_1 = require("react-native");
const ExpoBraintreeModule_1 = __importDefault(require("./ExpoBraintreeModule"));
__exportStar(require("./ExpoBraintree.types"), exports);
// ── Initialization ──────────────────────────────────────────────────────────
async function initialize(authorization) {
    return await ExpoBraintreeModule_1.default.initialize(authorization);
}
// ── Card ────────────────────────────────────────────────────────────────────
async function tokenizeCard(card) {
    return await ExpoBraintreeModule_1.default.tokenizeCard(card);
}
// ── Apple Pay (iOS) ─────────────────────────────────────────────────────────
async function isApplePaySupported() {
    if (react_native_1.Platform.OS !== "ios")
        return false;
    return await ExpoBraintreeModule_1.default.isApplePaySupported();
}
async function tokenizeApplePay(request) {
    if (react_native_1.Platform.OS !== "ios") {
        throw new Error("Apple Pay is only available on iOS");
    }
    return await ExpoBraintreeModule_1.default.tokenizeApplePay(request);
}
// ── Google Pay (Android) ────────────────────────────────────────────────────
async function isGooglePayReady(request) {
    if (react_native_1.Platform.OS !== "android")
        return false;
    return await ExpoBraintreeModule_1.default.isGooglePayReady(request ?? {});
}
async function tokenizeGooglePay(request) {
    if (react_native_1.Platform.OS !== "android") {
        throw new Error("Google Pay is only available on Android");
    }
    return await ExpoBraintreeModule_1.default.tokenizeGooglePay(request);
}
// ── PayPal ──────────────────────────────────────────────────────────────────
async function tokenizePayPalCheckout(request) {
    return await ExpoBraintreeModule_1.default.tokenizePayPalCheckout(request);
}
async function tokenizePayPalVault(request) {
    return await ExpoBraintreeModule_1.default.tokenizePayPalVault(request);
}
// ── Venmo ───────────────────────────────────────────────────────────────────
async function tokenizeVenmo(request) {
    return await ExpoBraintreeModule_1.default.tokenizeVenmo(request);
}
