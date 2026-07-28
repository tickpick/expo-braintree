import {
  ConfigPlugin,
  withEntitlementsPlist,
  withInfoPlist,
  withAndroidManifest,
  withMainActivity,
} from "expo/config-plugins";
import { addImports } from "@expo/config-plugins/build/android/codeMod";
import { mergeContents } from "@expo/config-plugins/build/utils/generateCode";

interface BraintreePluginProps {
  merchantIdentifier?: string;
  urlScheme?: string;
  enableVenmo?: boolean;
  enablePayPal?: boolean;
  enableGooglePay?: boolean;
}

const withBraintree: ConfigPlugin<BraintreePluginProps | void> = (
  config,
  props
) => {
  const {
    merchantIdentifier,
    urlScheme,
    enableVenmo = true,
    enablePayPal = true,
    enableGooglePay = true,
  } = props ?? {};

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

  // Android: deep-link fallback scheme for PayPal/Venmo browser-switch return
  if (enablePayPal || enableVenmo) {
    config = withBraintreePayPalReturnScheme(config);
  }

  if (enableGooglePay) {
    config = withGooglePayMainActivity(config);
  }

  return config;
};

// ── iOS: Apple Pay entitlement ──────────────────────────────────────────────

const withApplePayEntitlement: ConfigPlugin<string> = (
  config,
  merchantIdentifier
) => {
  return withEntitlementsPlist(config, (mod) => {
    mod.modResults["com.apple.developer.in-app-payments"] = [
      merchantIdentifier,
    ];
    return mod;
  });
};

// ── iOS: URL scheme for PayPal/Venmo browser return ─────────────────────────

const withBraintreeURLScheme: ConfigPlugin<string> = (config, urlScheme) => {
  return withInfoPlist(config, (mod) => {
    const schemes = mod.modResults.CFBundleURLTypes ?? [];
    const existing = schemes.find((s: any) =>
      s.CFBundleURLSchemes?.includes(urlScheme)
    );
    if (!existing) {
      schemes.push({
        CFBundleURLSchemes: [urlScheme],
      });
    }
    mod.modResults.CFBundleURLTypes = schemes;
    return mod;
  });
};

// ── Android: Google Pay launcher registration in MainActivity ───────────────

// Braintree's GooglePayLauncher wraps an androidx ActivityResultLauncher,
// which Android only allows registering before the host Activity passes
// STARTED — every Expo module lifecycle hook (OnCreate, initialize(), etc.)
// fires after that point in a React Native app, so registration has to be
// injected directly into MainActivity instead. See GooglePayLauncherHolder.kt.
const withGooglePayMainActivity: ConfigPlugin = (config) => {
  return withMainActivity(config, (mod) => {
    try {
      const isJava = mod.modResults.language === "java";
      let contents = addImports(
        mod.modResults.contents,
        ["expo.modules.braintree.GooglePayLauncherHolder"],
        isJava
      );

      const registerCall = isJava
        ? "    GooglePayLauncherHolder.INSTANCE.register(this);"
        : "    GooglePayLauncherHolder.register(this)";

      contents = mergeContents({
        src: contents,
        comment: "    //",
        tag: "expo-braintree-googlepay",
        offset: 1,
        anchor: /super\.onCreate\(.*\)/,
        newSrc: registerCall,
      }).contents;

      mod.modResults.contents = contents;
    } catch (error) {
      // A customized MainActivity without a matching `super.onCreate(...)`
      // line should not abort the entire prebuild — Google Pay just won't
      // work until the call is added by hand.
      console.warn(
        "expo-braintree: could not inject GooglePayLauncherHolder.register(this) into " +
          "MainActivity.onCreate() — add it manually or Google Pay results will not be " +
          `delivered. (${error instanceof Error ? error.message : error})`
      );
    }
    return mod;
  });
};

// ── Android: PayPal/Venmo deep-link fallback return scheme ──────────────────

// Braintree SDK 5 returns from the PayPal/Venmo browser switch via either an App
// Link (the HTTPS setReturnUrl) or, when that isn't usable on the device, a custom
// deep-link scheme. The native module passes "<applicationId>.braintree" as that
// fallback (see ExpoBraintreeModule.browserSwitchDeepLinkScheme); this registers the
// matching intent filter on MainActivity so Android routes the return back into the
// app. Without it, PayPal fails with "deeplink fallback return url is null".
const withBraintreePayPalReturnScheme: ConfigPlugin = (config) => {
  return withAndroidManifest(config, (mod) => {
    const manifest = mod.modResults.manifest;
    const application = manifest.application?.[0];
    const activities = application?.activity ?? [];
    const mainActivity =
      activities.find((a: any) => a.$?.["android:name"] === ".MainActivity") ??
      activities.find((a: any) => a.$?.["android:name"]?.endsWith(".MainActivity")) ??
      activities[0];

    if (!mainActivity) {
      return mod;
    }

    if (!mainActivity["intent-filter"]) {
      mainActivity["intent-filter"] = [];
    }

    const scheme = "${applicationId}.braintree";
    const already = mainActivity["intent-filter"].some((f: any) =>
      f?.data?.some((d: any) => d?.$?.["android:scheme"] === scheme)
    );

    if (!already) {
      mainActivity["intent-filter"].push({
        action: [{ $: { "android:name": "android.intent.action.VIEW" } }],
        category: [
          { $: { "android:name": "android.intent.category.DEFAULT" } },
          { $: { "android:name": "android.intent.category.BROWSABLE" } },
        ],
        data: [{ $: { "android:scheme": scheme } }],
      });
    }

    return mod;
  });
};

// ── Android: Manifest queries + Google Pay metadata ─────────────────────────

const withBraintreeAndroidManifest: ConfigPlugin<{
  enableVenmo: boolean;
  enablePayPal: boolean;
  enableGooglePay: boolean;
}> = (config, { enableVenmo, enablePayPal, enableGooglePay }) => {
  return withAndroidManifest(config, (mod) => {
    const manifest = mod.modResults.manifest;

    // Add <queries> for Venmo and PayPal app detection
    if (!manifest.queries) {
      manifest.queries = [];
    }

    const packages: string[] = [];
    if (enableVenmo) {
      packages.push("com.venmo");
    }
    if (enablePayPal) {
      packages.push("com.paypal.android.p2pmobile");
    }

    if (packages.length > 0) {
      const queryEntry: any = { package: [] };
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
        const gpayMeta = application["meta-data"].find(
          (m: any) =>
            m.$?.["android:name"] === "com.google.android.gms.wallet.api.enabled"
        );
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

export default withBraintree;
