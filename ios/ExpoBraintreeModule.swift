import ExpoModulesCore
import BraintreeCore
import BraintreeCard
import BraintreeApplePay
import BraintreePayPal
import BraintreeVenmo
import PassKit

public class ExpoBraintreeModule: Module {
  private var apiClient: BTAPIClient?

  public func definition() -> ModuleDefinition {
    Name("ExpoBraintree")

    // MARK: - Initialization

    AsyncFunction("initialize") { (authorization: String) in
      guard let client = BTAPIClient(authorization: authorization) else {
        throw BraintreeError.initializationFailed
      }
      self.apiClient = client
    }

    // MARK: - Card Tokenization

    AsyncFunction("tokenizeCard") { (cardData: CardData) -> [String: Any?] in
      let client = try self.requireClient()
      let cardClient = BTCardClient(apiClient: client)

      let card = BTCard()
      card.number = cardData.number
      card.expirationMonth = cardData.expirationMonth
      card.expirationYear = cardData.expirationYear
      card.cvv = cardData.cvv
      card.postalCode = cardData.postalCode
      card.cardholderName = cardData.cardholderName

      let nonce = try await cardClient.tokenize(card)

      return [
        "nonce": nonce.nonce,
        "type": "card",
        "isDefault": nonce.isDefault,
        "description": nonce.description,
        "cardNetwork": nonce.cardNetwork.humanReadable,
        "lastFour": nonce.lastFour,
        "expirationMonth": nonce.expirationMonth,
        "expirationYear": nonce.expirationYear,
        "bin": nonce.bin,
      ]
    }

    // MARK: - Apple Pay

    AsyncFunction("isApplePaySupported") { () -> Bool in
      return PKPaymentAuthorizationController.canMakePayments()
    }

    AsyncFunction("tokenizeApplePay") { (request: ApplePayRequestData, promise: Promise) in
      let client = try self.requireClient()
      let applePayClient = BTApplePayClient(apiClient: client)

      let paymentRequest = try await applePayClient.makePaymentRequest()
      paymentRequest.merchantIdentifier = request.merchantIdentifier
      paymentRequest.countryCode = request.countryCode
      paymentRequest.currencyCode = request.currencyCode
      paymentRequest.paymentSummaryItems = request.paymentSummaryItems.map { item in
        PKPaymentSummaryItem(
          label: item.label,
          amount: NSDecimalNumber(string: item.amount),
          type: item.type == "pending" ? .pending : .final
        )
      }

      if let networks = request.supportedNetworks {
        paymentRequest.supportedNetworks = networks.compactMap { network in
          switch network {
          case "visa": return .visa
          case "masterCard": return .masterCard
          case "amex": return .amex
          case "discover": return .discover
          case "maestro": return .maestro
          case "jcb": return .JCB
          default: return nil
          }
        }
      }

      let controller = PKPaymentAuthorizationController(paymentRequest: paymentRequest)
      let delegate = ApplePayDelegate(applePayClient: applePayClient, promise: promise)

      // Prevent delegate from being deallocated
      objc_setAssociatedObject(controller, "delegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)

      controller.delegate = delegate
      let presented = await controller.present()
      if !presented {
        promise.reject(BraintreeError.applePayPresentationFailed)
      }
    }

    // MARK: - PayPal Checkout

    AsyncFunction("tokenizePayPalCheckout") { (request: PayPalCheckoutRequestData) -> [String: Any?] in
      let client = try self.requireClient()
      let paypalClient = BTPayPalClient(apiClient: client)

      let checkoutRequest = BTPayPalCheckoutRequest(amount: request.amount)
      checkoutRequest.currencyCode = request.currencyCode ?? "USD"
      if let intent = request.intent {
        switch intent {
        case "sale": checkoutRequest.intent = .sale
        case "order": checkoutRequest.intent = .order
        default: checkoutRequest.intent = .authorize
        }
      }

      let nonce = try await paypalClient.tokenize(checkoutRequest)
      return Self.serializePayPalNonce(nonce)
    }

    // MARK: - PayPal Vault

    AsyncFunction("tokenizePayPalVault") { (request: PayPalVaultRequestData) -> [String: Any?] in
      let client = try self.requireClient()
      let paypalClient = BTPayPalClient(apiClient: client)

      let vaultRequest = BTPayPalVaultRequest()
      vaultRequest.billingAgreementDescription = request.billingAgreementDescription
      vaultRequest.userAuthenticationEmail = request.userAuthenticationEmail

      let nonce = try await paypalClient.tokenize(vaultRequest)
      return Self.serializePayPalNonce(nonce)
    }

    // MARK: - Venmo

    AsyncFunction("tokenizeVenmo") { (request: VenmoRequestData) -> [String: Any?] in
      let client = try self.requireClient()
      let venmoClient = BTVenmoClient(apiClient: client)

      let venmoRequest = BTVenmoRequest(
        paymentMethodUsage: request.paymentMethodUsage == "multiUse" ? .multiUse : .singleUse
      )
      venmoRequest.profileID = request.profileId
      venmoRequest.collectCustomerBillingAddress = request.collectCustomerBillingAddress ?? false
      venmoRequest.collectCustomerShippingAddress = request.collectCustomerShippingAddress ?? false

      let nonce = try await venmoClient.tokenize(venmoRequest)

      return [
        "nonce": nonce.nonce,
        "type": "venmo",
        "isDefault": nonce.isDefault,
        "description": nonce.description,
        "username": nonce.username,
      ]
    }
  }

  // MARK: - Helpers

  private func requireClient() throws -> BTAPIClient {
    guard let client = apiClient else {
      throw BraintreeError.notInitialized
    }
    return client
  }

  private static func serializePayPalNonce(_ nonce: BTPayPalAccountNonce) -> [String: Any?] {
    return [
      "nonce": nonce.nonce,
      "type": "paypal",
      "isDefault": nonce.isDefault,
      "description": nonce.description,
      "email": nonce.email,
      "firstName": nonce.firstName,
      "lastName": nonce.lastName,
      "billingAddress": nonce.billingAddress.map { address in
        [
          "recipientName": address.recipientName,
          "streetAddress": address.streetAddress,
          "extendedAddress": address.extendedAddress,
          "locality": address.locality,
          "region": address.region,
          "postalCode": address.postalCode,
          "countryCodeAlpha2": address.countryCodeAlpha2,
        ] as [String: Any?]
      },
    ]
  }
}

// MARK: - Record Types

struct CardData: Record {
  @Field var number: String
  @Field var expirationMonth: String
  @Field var expirationYear: String
  @Field var cvv: String?
  @Field var postalCode: String?
  @Field var cardholderName: String?
}

struct ApplePaySummaryItemData: Record {
  @Field var label: String
  @Field var amount: String
  @Field var type: String?
}

struct ApplePayRequestData: Record {
  @Field var merchantIdentifier: String
  @Field var countryCode: String
  @Field var currencyCode: String
  @Field var paymentSummaryItems: [ApplePaySummaryItemData]
  @Field var supportedNetworks: [String]?
}

struct PayPalCheckoutRequestData: Record {
  @Field var amount: String
  @Field var currencyCode: String?
  @Field var intent: String?
  @Field var userAction: String?
  @Field var displayName: String?
}

struct PayPalVaultRequestData: Record {
  @Field var billingAgreementDescription: String?
  @Field var displayName: String?
  @Field var userAuthenticationEmail: String?
}

struct VenmoRequestData: Record {
  @Field var paymentMethodUsage: String
  @Field var profileId: String?
  @Field var displayName: String?
  @Field var collectCustomerBillingAddress: Bool?
  @Field var collectCustomerShippingAddress: Bool?
}

// MARK: - Errors

enum BraintreeError: Error, LocalizedError {
  case notInitialized
  case initializationFailed
  case applePayPresentationFailed

  var errorDescription: String? {
    switch self {
    case .notInitialized:
      return "Braintree client not initialized. Call initialize() first."
    case .initializationFailed:
      return "Failed to initialize Braintree client. Check your authorization token."
    case .applePayPresentationFailed:
      return "Failed to present Apple Pay payment sheet."
    }
  }
}

// MARK: - Apple Pay Delegate

private class ApplePayDelegate: NSObject, PKPaymentAuthorizationControllerDelegate {
  private let applePayClient: BTApplePayClient
  private let promise: Promise
  private var didAuthorize = false

  init(applePayClient: BTApplePayClient, promise: Promise) {
    self.applePayClient = applePayClient
    self.promise = promise
  }

  func paymentAuthorizationController(
    _ controller: PKPaymentAuthorizationController,
    didAuthorizePayment payment: PKPayment,
    handler completion: @escaping (PKPaymentAuthorizationResult) -> Void
  ) {
    didAuthorize = true
    Task {
      do {
        let nonce = try await applePayClient.tokenize(payment)
        completion(PKPaymentAuthorizationResult(status: .success, errors: nil))
        promise.resolve([
          "nonce": nonce.nonce,
          "type": "applePay",
          "isDefault": nonce.isDefault,
          "description": nonce.description,
        ] as [String: Any?])
      } catch {
        completion(PKPaymentAuthorizationResult(status: .failure, errors: [error]))
        promise.reject(error)
      }
    }
  }

  func paymentAuthorizationControllerDidFinish(_ controller: PKPaymentAuthorizationController) {
    controller.dismiss {
      if !self.didAuthorize {
        self.promise.reject(BraintreeError.applePayPresentationFailed)
      }
    }
  }
}

// MARK: - Card Network Extension

extension BTCardNetwork {
  var humanReadable: String {
    switch self {
    case .unknown: return "Unknown"
    case .AMEX: return "Amex"
    case .dinersClub: return "DinersClub"
    case .discover: return "Discover"
    case .maestro: return "Maestro"
    case .masterCard: return "MasterCard"
    case .JCB: return "JCB"
    case .laser: return "Laser"
    case .solo: return "Solo"
    case .switch_: return "Switch"
    case .unionPay: return "UnionPay"
    case .hiper: return "Hiper"
    case .hipercard: return "Hipercard"
    case .visa: return "Visa"
    @unknown default: return "Unknown"
    }
  }
}
