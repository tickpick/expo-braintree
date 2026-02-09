"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_plugins_1 = require("expo/config-plugins");
const withBraintree = (config, props) => {
    const { merchantIdentifier, urlScheme, enableVenmo = true, enablePayPal = true, enableGooglePay = true, } = props ?? {};
    // iOS: Apple Pay entitlement
    if (merchantIdentifier) {
        config = withApplePayEntitlement(config, merchantIdentifier);
    }
    // iOS: URL scheme for PayPal/Venmo return
    if (urlScheme) {
        config = withBraintreeURLScheme(config, urlScheme);
    }
    // Android: manifest queries + metadata
    config = withBraintreeAndroidManifest(config, {
        enableVenmo,
        enablePayPal,
        enableGooglePay,
    });
    return config;
};
// ── iOS: Apple Pay entitlement ──────────────────────────────────────────────
const withApplePayEntitlement = (config, merchantIdentifier) => {
    return (0, config_plugins_1.withEntitlementsPlist)(config, (mod) => {
        mod.modResults["com.apple.developer.in-app-payments"] = [
            merchantIdentifier,
        ];
        return mod;
    });
};
// ── iOS: URL scheme for PayPal/Venmo browser return ─────────────────────────
const withBraintreeURLScheme = (config, urlScheme) => {
    return (0, config_plugins_1.withInfoPlist)(config, (mod) => {
        const schemes = mod.modResults.CFBundleURLTypes ?? [];
        const existing = schemes.find((s) => s.CFBundleURLSchemes?.includes(urlScheme));
        if (!existing) {
            schemes.push({
                CFBundleURLSchemes: [urlScheme],
            });
        }
        mod.modResults.CFBundleURLTypes = schemes;
        return mod;
    });
};
// ── Android: Manifest queries + Google Pay metadata ─────────────────────────
const withBraintreeAndroidManifest = (config, { enableVenmo, enablePayPal, enableGooglePay }) => {
    return (0, config_plugins_1.withAndroidManifest)(config, (mod) => {
        const manifest = mod.modResults.manifest;
        // Add <queries> for Venmo and PayPal app detection
        if (!manifest.queries) {
            manifest.queries = [];
        }
        const packages = [];
        if (enableVenmo) {
            packages.push("com.venmo");
        }
        if (enablePayPal) {
            packages.push("com.paypal.android.p2pmobile");
        }
        if (packages.length > 0) {
            const queryEntry = { package: [] };
            for (const pkg of packages) {
                queryEntry.package.push({
                    $: { "android:name": pkg },
                });
            }
            manifest.queries.push(queryEntry);
        }
        // Add Google Pay metadata
        if (enableGooglePay) {
            const application = manifest.application?.[0];
            if (application) {
                if (!application["meta-data"]) {
                    application["meta-data"] = [];
                }
                const gpayMeta = application["meta-data"].find((m) => m.$?.["android:name"] === "com.google.android.gms.wallet.api.enabled");
                if (!gpayMeta) {
                    application["meta-data"].push({
                        $: {
                            "android:name": "com.google.android.gms.wallet.api.enabled",
                            "android:value": "true",
                        },
                    });
                }
            }
        }
        return mod;
    });
};
exports.default = withBraintree;
