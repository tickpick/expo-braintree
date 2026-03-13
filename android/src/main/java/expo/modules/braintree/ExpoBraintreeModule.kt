package expo.modules.braintree

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record

// Card
import com.braintreepayments.api.card.CardClient
import com.braintreepayments.api.card.Card
import com.braintreepayments.api.card.CardResult

// Google Pay
import com.braintreepayments.api.googlepay.GooglePayClient
import com.braintreepayments.api.googlepay.GooglePayRequest
import com.braintreepayments.api.googlepay.GooglePayResult
import com.braintreepayments.api.googlepay.GooglePayCardNonce
import com.braintreepayments.api.googlepay.GooglePayLauncher
import com.braintreepayments.api.googlepay.GooglePayPaymentAuthRequest
import com.braintreepayments.api.googlepay.GooglePayPaymentAuthResult
import com.braintreepayments.api.googlepay.GooglePayReadinessResult
import com.braintreepayments.api.googlepay.GooglePayTotalPriceStatus

// PayPal
import com.braintreepayments.api.paypal.PayPalClient
import com.braintreepayments.api.paypal.PayPalCheckoutRequest
import com.braintreepayments.api.paypal.PayPalVaultRequest
import com.braintreepayments.api.paypal.PayPalAccountNonce
import com.braintreepayments.api.paypal.PayPalResult
import com.braintreepayments.api.paypal.PayPalPaymentIntent
import com.braintreepayments.api.paypal.PayPalPaymentUserAction
import com.braintreepayments.api.paypal.PayPalLauncher
import com.braintreepayments.api.paypal.PayPalPaymentAuthRequest
import com.braintreepayments.api.paypal.PayPalPaymentAuthResult
import com.braintreepayments.api.paypal.PayPalPendingRequest

// Venmo
import com.braintreepayments.api.venmo.VenmoClient
import com.braintreepayments.api.venmo.VenmoRequest
import com.braintreepayments.api.venmo.VenmoResult
import com.braintreepayments.api.venmo.VenmoAccountNonce
import com.braintreepayments.api.venmo.VenmoPaymentMethodUsage
import com.braintreepayments.api.venmo.VenmoLauncher
import com.braintreepayments.api.venmo.VenmoPaymentAuthRequest
import com.braintreepayments.api.venmo.VenmoPaymentAuthResult
import com.braintreepayments.api.venmo.VenmoPendingRequest

// Core
import com.braintreepayments.api.core.PostalAddress

class ExpoBraintreeModule : Module() {
  private var authorization: String? = null
  private var appLinkReturnUrl: Uri? = null

  // Google Pay state
  private var googlePayLauncher: GooglePayLauncher? = null
  private var googlePayClient: GooglePayClient? = null
  private var googlePayPromise: Promise? = null

  // PayPal state
  private val payPalLauncher = PayPalLauncher()
  private var payPalClient: PayPalClient? = null
  private var payPalPendingRequestString: String? = null
  private var payPalPromise: Promise? = null

  // Venmo state
  private val venmoLauncher = VenmoLauncher()
  private var venmoClient: VenmoClient? = null
  private var venmoPendingRequestString: String? = null
  private var venmoPromise: Promise? = null

  private val currentActivity
    get() = appContext.activityProvider?.currentActivity
      ?: throw BraintreeNotInitializedException()

  private val currentContext: Context
    get() = appContext.reactContext
      ?: throw BraintreeNotInitializedException()

  override fun definition() = ModuleDefinition {
    Name("ExpoBraintree")

    // Handle PayPal/Venmo return from browser/app
    OnNewIntent { intent ->
      handlePayPalReturn(intent)
      handleVenmoReturn(intent)
    }

    OnActivityEntersForeground {
      currentActivity.intent?.let { intent ->
        handlePayPalReturn(intent)
        handleVenmoReturn(intent)
      }
    }

    // ── Initialization ────────────────────────────────────────────────────

    AsyncFunction("initialize") { auth: String ->
      authorization = auth

      // Try to initialize Google Pay launcher (needs ComponentActivity)
      try {
        val activity = currentActivity
        if (activity is ComponentActivity && googlePayLauncher == null) {
          googlePayLauncher = GooglePayLauncher(activity) { paymentAuthResult ->
            handleGooglePayReturn(paymentAuthResult)
          }
        }
      } catch (_: Exception) {
        // Google Pay launcher creation may fail if called after onStart
      }
    }

    AsyncFunction("setReturnUrl") { url: String ->
      appLinkReturnUrl = Uri.parse(url)
    }

    // ── Card Tokenization ─────────────────────────────────────────────────

    AsyncFunction("tokenizeCard") { cardData: CardDataRecord, promise: Promise ->
      val auth = requireAuth()
      val cardClient = CardClient(currentContext, auth)

      val card = Card(
        number = cardData.number,
        expirationMonth = cardData.expirationMonth,
        expirationYear = cardData.expirationYear,
        cvv = cardData.cvv,
        postalCode = cardData.postalCode,
        cardholderName = cardData.cardholderName
      )

      cardClient.tokenize(card) { result ->
        when (result) {
          is CardResult.Success -> {
            val nonce = result.nonce
            promise.resolve(mapOf(
              "nonce" to nonce.string,
              "type" to "card",
              "isDefault" to nonce.isDefault,
              "cardNetwork" to nonce.cardType,
              "lastFour" to nonce.lastFour,
              "expirationMonth" to nonce.expirationMonth,
              "expirationYear" to nonce.expirationYear,
              "bin" to nonce.bin
            ))
          }
          is CardResult.Failure -> {
            promise.reject(CodedException("CARD_TOKENIZE_ERROR", result.error.message, result.error))
          }
        }
      }
    }

    // ── Google Pay ─────────────────────────────────────────────────────────

    AsyncFunction("isGooglePayReady") { _: Map<String, Any>, promise: Promise ->
      val auth = requireAuth()
      val client = GooglePayClient(currentContext, auth)
      googlePayClient = client

      client.isReadyToPay(currentContext) { readinessResult ->
        when (readinessResult) {
          is GooglePayReadinessResult.ReadyToPay -> promise.resolve(true)
          else -> promise.resolve(false)
        }
      }
    }

    AsyncFunction("tokenizeGooglePay") { request: GooglePayRequestRecord, promise: Promise ->
      val auth = requireAuth()
      val client = googlePayClient ?: GooglePayClient(currentContext, auth)
      googlePayClient = client

      val launcher = googlePayLauncher
        ?: throw CodedException(
          "GOOGLE_PAY_NOT_READY",
          "GooglePay launcher not initialized. Call initialize() early in your app lifecycle.",
          null
        )

      googlePayPromise = promise

      val totalPriceStatus = when (request.totalPriceStatus) {
        "ESTIMATED" -> GooglePayTotalPriceStatus.TOTAL_PRICE_STATUS_ESTIMATED
        else -> GooglePayTotalPriceStatus.TOTAL_PRICE_STATUS_FINAL
      }

      val googlePayRequest = GooglePayRequest(
        currencyCode = request.currencyCode,
        totalPrice = request.totalPrice,
        totalPriceStatus = totalPriceStatus,
        isBillingAddressRequired = request.billingAddressRequired ?: false,
        isEmailRequired = request.emailRequired ?: false,
        isShippingAddressRequired = request.shippingAddressRequired ?: false,
        allowPrepaidCards = request.allowPrepaidCards ?: true,
        googleMerchantName = request.merchantName,
        countryCode = request.countryCode
      )

      client.createPaymentAuthRequest(googlePayRequest) { paymentAuthRequest ->
        when (paymentAuthRequest) {
          is GooglePayPaymentAuthRequest.ReadyToLaunch -> {
            launcher.launch(paymentAuthRequest)
          }
          is GooglePayPaymentAuthRequest.Failure -> {
            googlePayPromise?.reject(
              CodedException("GOOGLE_PAY_ERROR", paymentAuthRequest.error.message, paymentAuthRequest.error)
            )
            googlePayPromise = null
          }
        }
      }
    }

    // ── PayPal Checkout ───────────────────────────────────────────────────

    AsyncFunction("tokenizePayPalCheckout") { request: PayPalCheckoutRequestRecord, promise: Promise ->
      val auth = requireAuth()
      val returnUrl = appLinkReturnUrl
        ?: throw CodedException("PAYPAL_NOT_CONFIGURED", "Return URL not set. Call setReturnUrl() first.", null)

      val client = PayPalClient(currentContext, auth, returnUrl)
      payPalClient = client
      payPalPromise = promise

      val checkoutRequest = PayPalCheckoutRequest(
        amount = request.amount,
        hasUserLocationConsent = true,
        intent = when (request.intent) {
          "sale" -> PayPalPaymentIntent.SALE
          "order" -> PayPalPaymentIntent.ORDER
          else -> PayPalPaymentIntent.AUTHORIZE
        },
        currencyCode = request.currencyCode ?: "USD",
        isShippingAddressRequired = request.shippingAddressRequired ?: false,
        isShippingAddressEditable = request.shippingAddressEditable ?: false,
        userAction = if (request.userAction == "commit")
          PayPalPaymentUserAction.USER_ACTION_COMMIT
        else
          PayPalPaymentUserAction.USER_ACTION_DEFAULT,
        displayName = request.displayName
      )

      client.createPaymentAuthRequest(currentContext, checkoutRequest) { paymentAuthRequest ->
        when (paymentAuthRequest) {
          is PayPalPaymentAuthRequest.ReadyToLaunch -> {
            val pendingRequest = payPalLauncher.launch(currentActivity, paymentAuthRequest)
            when (pendingRequest) {
              is PayPalPendingRequest.Started -> {
                payPalPendingRequestString = pendingRequest.pendingRequestString
              }
              is PayPalPendingRequest.Failure -> {
                payPalPromise?.reject(
                  CodedException("PAYPAL_LAUNCH_ERROR", pendingRequest.error.message, pendingRequest.error)
                )
                payPalPromise = null
              }
            }
          }
          is PayPalPaymentAuthRequest.Failure -> {
            payPalPromise?.reject(
              CodedException("PAYPAL_ERROR", paymentAuthRequest.error.message, paymentAuthRequest.error)
            )
            payPalPromise = null
          }
        }
      }
    }

    // ── PayPal Vault ──────────────────────────────────────────────────────

    AsyncFunction("tokenizePayPalVault") { request: PayPalVaultRequestRecord, promise: Promise ->
      val auth = requireAuth()
      val returnUrl = appLinkReturnUrl
        ?: throw CodedException("PAYPAL_NOT_CONFIGURED", "Return URL not set. Call setReturnUrl() first.", null)

      val client = PayPalClient(currentContext, auth, returnUrl)
      payPalClient = client
      payPalPromise = promise

      val vaultRequest = PayPalVaultRequest(
        hasUserLocationConsent = true,
        billingAgreementDescription = request.billingAgreementDescription,
        userAuthenticationEmail = request.userAuthenticationEmail,
        isShippingAddressRequired = request.shippingAddressRequired ?: false,
        isShippingAddressEditable = request.shippingAddressEditable ?: false,
        displayName = request.displayName
      )

      client.createPaymentAuthRequest(currentContext, vaultRequest) { paymentAuthRequest ->
        when (paymentAuthRequest) {
          is PayPalPaymentAuthRequest.ReadyToLaunch -> {
            val pendingRequest = payPalLauncher.launch(currentActivity, paymentAuthRequest)
            when (pendingRequest) {
              is PayPalPendingRequest.Started -> {
                payPalPendingRequestString = pendingRequest.pendingRequestString
              }
              is PayPalPendingRequest.Failure -> {
                payPalPromise?.reject(
                  CodedException("PAYPAL_LAUNCH_ERROR", pendingRequest.error.message, pendingRequest.error)
                )
                payPalPromise = null
              }
            }
          }
          is PayPalPaymentAuthRequest.Failure -> {
            payPalPromise?.reject(
              CodedException("PAYPAL_ERROR", paymentAuthRequest.error.message, paymentAuthRequest.error)
            )
            payPalPromise = null
          }
        }
      }
    }

    // ── Venmo ─────────────────────────────────────────────────────────────

    AsyncFunction("tokenizeVenmo") { request: VenmoRequestRecord, promise: Promise ->
      val auth = requireAuth()
      val returnUrl = appLinkReturnUrl
        ?: throw CodedException("VENMO_NOT_CONFIGURED", "Return URL not set. Call setReturnUrl() first.", null)

      val client = VenmoClient(currentContext, auth, returnUrl)
      venmoClient = client
      venmoPromise = promise

      val venmoRequest = VenmoRequest(
        paymentMethodUsage = when (request.paymentMethodUsage) {
          "multiUse" -> VenmoPaymentMethodUsage.MULTI_USE
          else -> VenmoPaymentMethodUsage.SINGLE_USE
        },
        profileId = request.profileId,
        collectCustomerBillingAddress = request.collectCustomerBillingAddress ?: false,
        collectCustomerShippingAddress = request.collectCustomerShippingAddress ?: false
      )

      client.createPaymentAuthRequest(currentContext, venmoRequest) { paymentAuthRequest ->
        when (paymentAuthRequest) {
          is VenmoPaymentAuthRequest.ReadyToLaunch -> {
            val pendingRequest = venmoLauncher.launch(currentActivity, paymentAuthRequest)
            when (pendingRequest) {
              is VenmoPendingRequest.Started -> {
                venmoPendingRequestString = pendingRequest.pendingRequestString
              }
              is VenmoPendingRequest.Failure -> {
                venmoPromise?.reject(
                  CodedException("VENMO_LAUNCH_ERROR", pendingRequest.error.message, pendingRequest.error)
                )
                venmoPromise = null
              }
            }
          }
          is VenmoPaymentAuthRequest.Failure -> {
            venmoPromise?.reject(
              CodedException("VENMO_ERROR", paymentAuthRequest.error.message, paymentAuthRequest.error)
            )
            venmoPromise = null
          }
        }
      }
    }
  }

  // ── Return Handlers ──────────────────────────────────────────────────

  private fun handleGooglePayReturn(paymentAuthResult: GooglePayPaymentAuthResult) {
    val client = googlePayClient ?: return
    val promise = googlePayPromise ?: return
    googlePayPromise = null

    client.tokenize(paymentAuthResult) { result ->
      when (result) {
        is GooglePayResult.Success -> {
          val nonce = result.nonce
          promise.resolve(mapOf(
            "nonce" to nonce.string,
            "type" to "googlePay",
            "isDefault" to nonce.isDefault,
            "email" to nonce.email,
            "cardNetwork" to nonce.cardNetwork,
            "lastFour" to nonce.lastFour,
            "billingAddress" to serializePostalAddress(nonce.billingAddress),
            "shippingAddress" to serializePostalAddress(nonce.shippingAddress)
          ))
        }
        is GooglePayResult.Failure -> {
          promise.reject(CodedException("GOOGLE_PAY_ERROR", result.error.message, result.error))
        }
        is GooglePayResult.Cancel -> {
          promise.reject(CodedException("GOOGLE_PAY_CANCELLED", "User cancelled Google Pay", null))
        }
      }
    }
  }

  private fun handlePayPalReturn(intent: Intent) {
    val pendingStr = payPalPendingRequestString ?: return
    val client = payPalClient ?: return
    val promise = payPalPromise ?: return

    val paymentAuthResult = payPalLauncher.handleReturnToApp(
      PayPalPendingRequest.Started(pendingStr), intent
    )

    when (paymentAuthResult) {
      is PayPalPaymentAuthResult.Success -> {
        payPalPendingRequestString = null
        payPalPromise = null

        client.tokenize(paymentAuthResult) { result ->
          when (result) {
            is PayPalResult.Success -> {
              promise.resolve(serializePayPalNonce(result.nonce))
            }
            is PayPalResult.Failure -> {
              promise.reject(CodedException("PAYPAL_ERROR", result.error.message, result.error))
            }
            is PayPalResult.Cancel -> {
              promise.reject(CodedException("PAYPAL_CANCELLED", "User cancelled PayPal", null))
            }
          }
        }
      }
      is PayPalPaymentAuthResult.Failure -> {
        payPalPendingRequestString = null
        payPalPromise = null
        promise.reject(CodedException("PAYPAL_ERROR", paymentAuthResult.error.message, paymentAuthResult.error))
      }
      is PayPalPaymentAuthResult.NoResult -> {
        // User returned without completing flow, keep waiting
      }
    }
  }

  private fun handleVenmoReturn(intent: Intent) {
    val pendingStr = venmoPendingRequestString ?: return
    val client = venmoClient ?: return
    val promise = venmoPromise ?: return

    val paymentAuthResult = venmoLauncher.handleReturnToApp(
      VenmoPendingRequest.Started(pendingStr), intent
    )

    when (paymentAuthResult) {
      is VenmoPaymentAuthResult.Success -> {
        venmoPendingRequestString = null
        venmoPromise = null

        client.tokenize(paymentAuthResult) { result ->
          when (result) {
            is VenmoResult.Success -> {
              val nonce = result.nonce
              promise.resolve(mapOf(
                "nonce" to nonce.string,
                "type" to "venmo",
                "isDefault" to nonce.isDefault,
                "username" to nonce.username,
                "billingAddress" to serializePostalAddress(nonce.billingAddress),
                "shippingAddress" to serializePostalAddress(nonce.shippingAddress)
              ))
            }
            is VenmoResult.Failure -> {
              promise.reject(CodedException("VENMO_ERROR", result.error.message, result.error))
            }
            is VenmoResult.Cancel -> {
              promise.reject(CodedException("VENMO_CANCELLED", "User cancelled Venmo", null))
            }
          }
        }
      }
      is VenmoPaymentAuthResult.Failure -> {
        venmoPendingRequestString = null
        venmoPromise = null
        promise.reject(CodedException("VENMO_ERROR", paymentAuthResult.error.message, paymentAuthResult.error))
      }
      is VenmoPaymentAuthResult.NoResult -> {
        // User returned without completing flow, keep waiting
      }
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private fun requireAuth(): String {
    return authorization ?: throw BraintreeNotInitializedException()
  }

  private fun serializePayPalNonce(nonce: PayPalAccountNonce): Map<String, Any?> {
    return mapOf(
      "nonce" to nonce.string,
      "type" to "paypal",
      "isDefault" to nonce.isDefault,
      "email" to nonce.email,
      "firstName" to nonce.firstName,
      "lastName" to nonce.lastName,
      "billingAddress" to serializePostalAddress(nonce.billingAddress),
      "shippingAddress" to serializePostalAddress(nonce.shippingAddress)
    )
  }

  private fun serializePostalAddress(address: PostalAddress?): Map<String, Any?>? {
    if (address == null) return null
    return mapOf(
      "recipientName" to address.recipientName,
      "streetAddress" to address.streetAddress,
      "extendedAddress" to address.extendedAddress,
      "locality" to address.locality,
      "region" to address.region,
      "postalCode" to address.postalCode,
      "countryCodeAlpha2" to address.countryCodeAlpha2
    )
  }
}

// ── Record Types ──────────────────────────────────────────────────────────

class CardDataRecord : Record {
  @Field val number: String = ""
  @Field val expirationMonth: String = ""
  @Field val expirationYear: String = ""
  @Field val cvv: String? = null
  @Field val postalCode: String? = null
  @Field val cardholderName: String? = null
}

class GooglePayRequestRecord : Record {
  @Field val merchantName: String? = null
  @Field val countryCode: String = "US"
  @Field val currencyCode: String = "USD"
  @Field val totalPrice: String = ""
  @Field val totalPriceStatus: String? = "FINAL"
  @Field val allowPrepaidCards: Boolean? = true
  @Field val billingAddressRequired: Boolean? = false
  @Field val emailRequired: Boolean? = false
  @Field val shippingAddressRequired: Boolean? = false
}

class PayPalCheckoutRequestRecord : Record {
  @Field val amount: String = ""
  @Field val currencyCode: String? = "USD"
  @Field val intent: String? = "authorize"
  @Field val userAction: String? = null
  @Field val displayName: String? = null
  @Field val shippingAddressRequired: Boolean? = false
  @Field val shippingAddressEditable: Boolean? = false
}

class PayPalVaultRequestRecord : Record {
  @Field val billingAgreementDescription: String? = null
  @Field val displayName: String? = null
  @Field val userAuthenticationEmail: String? = null
  @Field val shippingAddressRequired: Boolean? = false
  @Field val shippingAddressEditable: Boolean? = false
}

class VenmoRequestRecord : Record {
  @Field val paymentMethodUsage: String = "singleUse"
  @Field val profileId: String? = null
  @Field val displayName: String? = null
  @Field val collectCustomerBillingAddress: Boolean? = false
  @Field val collectCustomerShippingAddress: Boolean? = false
}

// ── Exceptions ────────────────────────────────────────────────────────────

class BraintreeNotInitializedException :
  CodedException("NOT_INITIALIZED", "Braintree client not initialized. Call initialize() first.", null)
