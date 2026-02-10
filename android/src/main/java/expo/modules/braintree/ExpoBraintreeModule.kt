package expo.modules.braintree

import android.app.Activity
import android.content.Intent
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import com.braintreepayments.api.BraintreeClient
import com.braintreepayments.api.Card
import com.braintreepayments.api.CardClient
import com.braintreepayments.api.CardNonce
import com.braintreepayments.api.CardResult
import com.braintreepayments.api.GooglePayClient
import com.braintreepayments.api.GooglePayRequest
import com.braintreepayments.api.GooglePayCardNonce
import com.braintreepayments.api.GooglePayResult
import com.braintreepayments.api.PayPalClient
import com.braintreepayments.api.PayPalCheckoutRequest
import com.braintreepayments.api.PayPalVaultRequest
import com.braintreepayments.api.PayPalAccountNonce
import com.braintreepayments.api.PayPalResult
import com.braintreepayments.api.VenmoClient
import com.braintreepayments.api.VenmoRequest
import com.braintreepayments.api.VenmoPaymentMethodUsage
import com.braintreepayments.api.VenmoAccountNonce
import com.braintreepayments.api.VenmoResult
import com.braintreepayments.api.PostalAddress
import com.google.android.gms.wallet.TransactionInfo
import com.google.android.gms.wallet.WalletConstants

class ExpoBraintreeModule : Module() {
  private var braintreeClient: BraintreeClient? = null

  private val activity: Activity
    get() = appContext.activityProvider?.currentActivity
      ?: throw BraintreeNotInitializedException()

  override fun definition() = ModuleDefinition {
    Name("ExpoBraintree")

    // ── Initialization ────────────────────────────────────────────────────

    AsyncFunction("initialize") { authorization: String ->
      braintreeClient = BraintreeClient(activity, authorization)
    }

    // ── Card Tokenization ─────────────────────────────────────────────────

    AsyncFunction("tokenizeCard") { cardData: CardDataRecord, promise: Promise ->
      val client = requireClient()
      val cardClient = CardClient(client)

      val card = Card().apply {
        number = cardData.number
        expirationMonth = cardData.expirationMonth
        expirationYear = cardData.expirationYear
        cvv = cardData.cvv
        postalCode = cardData.postalCode
        cardholderName = cardData.cardholderName
      }

      cardClient.tokenize(card) { result ->
        when (result) {
          is CardResult.Success -> {
            val nonce = result.nonce
            promise.resolve(mapOf(
              "nonce" to nonce.string,
              "type" to "card",
              "isDefault" to nonce.isDefault,
              "description" to nonce.string,
              "cardNetwork" to nonce.cardNetwork,
              "lastFour" to nonce.lastFour,
              "expirationMonth" to nonce.expirationMonth,
              "expirationYear" to nonce.expirationYear,
              "bin" to nonce.bin,
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
      val client = requireClient()
      val googlePayClient = GooglePayClient(activity, client)

      val readyRequest = GooglePayRequest().apply {
        isEmailRequired = false
      }

      googlePayClient.isReadyToPay(activity, readyRequest) { isReady, error ->
        if (error != null) {
          promise.reject(CodedException("GOOGLE_PAY_READY_ERROR", error.message, error))
        } else {
          promise.resolve(isReady)
        }
      }
    }

    AsyncFunction("tokenizeGooglePay") { request: GooglePayRequestRecord, promise: Promise ->
      val client = requireClient()
      val googlePayClient = GooglePayClient(activity, client)

      val googlePayRequest = GooglePayRequest().apply {
        transactionInfo = TransactionInfo.newBuilder()
          .setTotalPrice(request.totalPrice)
          .setTotalPriceStatus(
            when (request.totalPriceStatus) {
              "ESTIMATED" -> WalletConstants.TOTAL_PRICE_STATUS_ESTIMATED
              "NOT_CURRENTLY_KNOWN" -> WalletConstants.TOTAL_PRICE_STATUS_NOT_CURRENTLY_KNOWN
              else -> WalletConstants.TOTAL_PRICE_STATUS_FINAL
            }
          )
          .setCurrencyCode(request.currencyCode)
          .build()
        isBillingAddressRequired = request.billingAddressRequired ?: false
        isEmailRequired = request.emailRequired ?: false
        isShippingAddressRequired = request.shippingAddressRequired ?: false
        googleMerchantId = request.googleMerchantId
        googleMerchantName = request.merchantName
      }

      googlePayClient.setListener { result ->
        when (result) {
          is GooglePayResult.Success -> {
            val nonce = result.nonce
            val gpNonce = nonce as? GooglePayCardNonce
            promise.resolve(mapOf(
              "nonce" to nonce.string,
              "type" to "googlePay",
              "isDefault" to nonce.isDefault,
              "description" to nonce.string,
              "email" to gpNonce?.email,
              "billingAddress" to gpNonce?.billingAddress?.let { serializePostalAddress(it) },
              "shippingAddress" to gpNonce?.shippingAddress?.let { serializePostalAddress(it) },
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

      googlePayClient.requestPayment(activity, googlePayRequest) { error ->
        if (error != null) {
          promise.reject(CodedException("GOOGLE_PAY_REQUEST_ERROR", error.message, error))
        }
      }
    }

    // ── PayPal Checkout ───────────────────────────────────────────────────

    AsyncFunction("tokenizePayPalCheckout") { request: PayPalCheckoutRequestRecord, promise: Promise ->
      val client = requireClient()
      val payPalClient = PayPalClient(activity, client)

      val checkoutRequest = PayPalCheckoutRequest(request.amount).apply {
        currencyCode = request.currencyCode ?: "USD"
        intent = when (request.intent) {
          "sale" -> com.braintreepayments.api.PayPalPaymentIntent.SALE
          "order" -> com.braintreepayments.api.PayPalPaymentIntent.ORDER
          else -> com.braintreepayments.api.PayPalPaymentIntent.AUTHORIZE
        }
        if (request.userAction == "commit") {
          userAction = com.braintreepayments.api.PayPalPaymentUserAction.PAY_NOW
        }
        isShippingAddressRequired = request.shippingAddressRequired ?: false
        isShippingAddressEditable = request.shippingAddressEditable ?: false
      }

      payPalClient.setListener { result ->
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

      payPalClient.tokenizePayPalAccount(activity, checkoutRequest) { error ->
        if (error != null) {
          promise.reject(CodedException("PAYPAL_LAUNCH_ERROR", error.message, error))
        }
      }
    }

    // ── PayPal Vault ──────────────────────────────────────────────────────

    AsyncFunction("tokenizePayPalVault") { request: PayPalVaultRequestRecord, promise: Promise ->
      val client = requireClient()
      val payPalClient = PayPalClient(activity, client)

      val vaultRequest = PayPalVaultRequest().apply {
        billingAgreementDescription = request.billingAgreementDescription
        userAuthenticationEmail = request.userAuthenticationEmail
        isShippingAddressRequired = request.shippingAddressRequired ?: false
        isShippingAddressEditable = request.shippingAddressEditable ?: false
      }

      payPalClient.setListener { result ->
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

      payPalClient.tokenizePayPalAccount(activity, vaultRequest) { error ->
        if (error != null) {
          promise.reject(CodedException("PAYPAL_LAUNCH_ERROR", error.message, error))
        }
      }
    }

    // ── Venmo ─────────────────────────────────────────────────────────────

    AsyncFunction("tokenizeVenmo") { request: VenmoRequestRecord, promise: Promise ->
      val client = requireClient()
      val venmoClient = VenmoClient(activity, client)

      val venmoRequest = VenmoRequest(
        when (request.paymentMethodUsage) {
          "multiUse" -> VenmoPaymentMethodUsage.MULTI_USE
          else -> VenmoPaymentMethodUsage.SINGLE_USE
        }
      ).apply {
        profileId = request.profileId
        collectCustomerBillingAddress = request.collectCustomerBillingAddress ?: false
        collectCustomerShippingAddress = request.collectCustomerShippingAddress ?: false
      }

      venmoClient.setListener { result ->
        when (result) {
          is VenmoResult.Success -> {
            val nonce = result.nonce
            promise.resolve(mapOf(
              "nonce" to nonce.string,
              "type" to "venmo",
              "isDefault" to nonce.isDefault,
              "description" to nonce.string,
              "username" to nonce.username,
              "billingAddress" to serializePostalAddress(nonce.billingAddress),
              "shippingAddress" to serializePostalAddress(nonce.shippingAddress),
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

      venmoClient.tokenizeVenmoAccount(activity, venmoRequest) { error ->
        if (error != null) {
          promise.reject(CodedException("VENMO_LAUNCH_ERROR", error.message, error))
        }
      }
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  private fun requireClient(): BraintreeClient {
    return braintreeClient ?: throw BraintreeNotInitializedException()
  }

  private fun serializePayPalNonce(nonce: PayPalAccountNonce): Map<String, Any?> {
    return mapOf(
      "nonce" to nonce.string,
      "type" to "paypal",
      "isDefault" to nonce.isDefault,
      "description" to nonce.string,
      "email" to nonce.email,
      "firstName" to nonce.firstName,
      "lastName" to nonce.lastName,
      "billingAddress" to serializePostalAddress(nonce.billingAddress),
      "shippingAddress" to serializePostalAddress(nonce.shippingAddress),
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
      "countryCodeAlpha2" to address.countryCodeAlpha2,
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
  @Field val googleMerchantId: String? = null
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
  @Field val universalLink: String = ""
  @Field val profileId: String? = null
  @Field val displayName: String? = null
  @Field val collectCustomerBillingAddress: Boolean? = false
  @Field val collectCustomerShippingAddress: Boolean? = false
}

// ── Exceptions ────────────────────────────────────────────────────────────

class BraintreeNotInitializedException :
  CodedException("NOT_INITIALIZED", "Braintree client not initialized. Call initialize() first.", null)
