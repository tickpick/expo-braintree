# expo-braintree

Expo module for Braintree payments — Apple Pay, Google Pay, PayPal, Venmo, and card tokenization.

## Installation

```bash
npx expo install expo-braintree
```

## Configuration

Add the config plugin to your `app.json` / `app.config.ts`:

```json
{
  "plugins": [
    [
      "expo-braintree",
      {
        "merchantIdentifier": "merchant.com.your.app",
        "urlScheme": "com.your.app.payments",
        "enableVenmo": true,
        "enablePayPal": true,
        "enableGooglePay": true
      }
    ]
  ]
}
```

## Usage

```typescript
import {
  initialize,
  tokenizeCard,
  tokenizeApplePay,
  tokenizeGooglePay,
  tokenizePayPalCheckout,
  tokenizePayPalVault,
  tokenizeVenmo,
  isApplePaySupported,
  isGooglePayReady,
} from "expo-braintree";

// Initialize with a client token from your server
await initialize("<CLIENT_TOKEN>");

// Card tokenization
const cardNonce = await tokenizeCard({
  number: "4111111111111111",
  expirationMonth: "12",
  expirationYear: "2028",
  cvv: "123",
});

// Apple Pay (iOS)
if (await isApplePaySupported()) {
  const applePayNonce = await tokenizeApplePay({
    merchantIdentifier: "merchant.com.your.app",
    countryCode: "US",
    currencyCode: "USD",
    paymentSummaryItems: [{ label: "Total", amount: "9.99" }],
  });
}

// Google Pay (Android)
if (await isGooglePayReady()) {
  const googlePayNonce = await tokenizeGooglePay({
    countryCode: "US",
    currencyCode: "USD",
    totalPrice: "9.99",
  });
}

// PayPal Checkout
const paypalNonce = await tokenizePayPalCheckout({
  amount: "9.99",
  currencyCode: "USD",
  userAction: "commit", // shows "Complete Purchase" instead of "Review Order"
});

// PayPal Vault
const paypalVaultNonce = await tokenizePayPalVault({
  billingAgreementDescription: "Your subscription",
});

// Venmo
const venmoNonce = await tokenizeVenmo({
  paymentMethodUsage: "multiUse",
  universalLink: "https://your-app.com/braintree-venmo",
});
```

## Native SDK Versions

| Platform | SDK | Version |
|----------|-----|---------|
| iOS | braintree_ios | ~> 7.3 |
| Android | braintree-android | 5.22.0 |

## Requirements

- iOS 16.0+
- Android API 23+ (Android 6.0)
- Expo SDK 52+
