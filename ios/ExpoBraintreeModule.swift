import ExpoModulesCore
import Braintree
import Contacts
import PassKit

public class ExpoBraintreeModule: Module {
  private var authorization: String?

  public func definition() -> ModuleDefinition {
    Name("ExpoBraintree")

    // MARK: - Initialization

    AsyncFunction("initialize") { (auth: String) in
      guard !auth.isEmpty else {
        throw BraintreeError.initializationFailed
      }
      self.authorization = auth
    }

    // MARK: - Card Tokenization

    AsyncFunction("tokenizeCard") { (cardData: CardData) -> [String: Any?] in
      let auth = try self.requireAuthorization()
      let cardClient = BTCardClient(authorization: auth)

      let card = BTCard(
        number: cardData.number,
        expirationMonth: cardData.expirationMonth,
        expirationYear: cardData.expirationYear,
        cvv: cardData.cvv ?? "",
        postalCode: cardData.postalCode,
        cardholderName: cardData.cardholderName
      )

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

    AsyncFunction("tokenizeApplePay") { (request: ApplePayRequestData) -> [String: Any?] in
      let auth = try self.requireAuthorization()
      let applePayClient = BTApplePayClient(authorization: auth)

      let paymentRequest = try await applePayClient.makePaymentRequest()
      paymentRequest.merchantIdentifier = request.merchantIdentifier
      paymentRequest.countryCode = request.countryCode
      paymentRequest.currencyCode = request.currencyCode
      paymentRequest.merchantCapabilities = .capability3DS
      paymentRequest.paymentSummaryItems = request.paymentSummaryItems.map { item in
        PKPaymentSummaryItem(
          label: item.label,
          amount: NSDecimalNumber(string: item.amount),
          type: item.type == "pending" ? .pending : .final
        )
      }

      if let shippingFields = request.requiredShippingContactFields {
        paymentRequest.requiredShippingContactFields = Set(shippingFields.compactMap { field in
          switch field {
          case "postalAddress": return .postalAddress
          case "name": return .name
          case "emailAddress": return .emailAddress
          case "phoneNumber": return .phoneNumber
          default: return nil
          }
        })
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

      return try await withCheckedThrowingContinuation { continuation in
        let controller = PKPaymentAuthorizationController(paymentRequest: paymentRequest)
        let delegate = ApplePayDelegate(applePayClient: applePayClient, continuation: continuation)

        // Prevent delegate from being deallocated
        objc_setAssociatedObject(controller, "delegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)

        controller.delegate = delegate
        Task {
          let presented = await controller.present()
          if !presented {
            continuation.resume(throwing: BraintreeError.applePayPresentationFailed)
          }
        }
      }
    }

    // MARK: - PayPal Checkout

    AsyncFunction("tokenizePayPalCheckout") { (request: PayPalCheckoutRequestData) -> [String: Any?] in
      let auth = try self.requireAuthorization()
      let paypalClient = await MainActor.run { BTPayPalClient(authorization: auth) }

      let intent: BTPayPalRequestIntent
      switch request.intent {
      case "sale": intent = .sale
      case "order": intent = .order
      default: intent = .authorize
      }

      let checkoutRequest = BTPayPalCheckoutRequest(
        amount: request.amount,
        intent: intent,
        userAction: request.userAction == "commit" ? .payNow : .none,
        currencyCode: request.currencyCode,
        displayName: request.displayName,
        isShippingAddressEditable: request.shippingAddressEditable ?? false,
        isShippingAddressRequired: request.shippingAddressRequired ?? false
      )

      let nonce = try await paypalClient.tokenize(checkoutRequest)
      return Self.serializePayPalNonce(nonce)
    }

    // MARK: - PayPal Vault

    AsyncFunction("tokenizePayPalVault") { (request: PayPalVaultRequestData) -> [String: Any?] in
      let auth = try self.requireAuthorization()
      let paypalClient = await MainActor.run { BTPayPalClient(authorization: auth) }

      let vaultRequest = BTPayPalVaultRequest(
        billingAgreementDescription: request.billingAgreementDescription,
        displayName: request.displayName,
        isShippingAddressEditable: request.shippingAddressEditable ?? false,
        isShippingAddressRequired: request.shippingAddressRequired ?? false
      )

      let nonce = try await paypalClient.tokenize(vaultRequest)
      return Self.serializePayPalNonce(nonce)
    }

    // MARK: - Venmo

    AsyncFunction("tokenizeVenmo") { (request: VenmoRequestData) -> [String: Any?] in
      let auth = try self.requireAuthorization()

      guard let universalLink = URL(string: request.universalLink) else {
        throw BraintreeError.invalidUniversalLink
      }

      let venmoClient = BTVenmoClient(authorization: auth, universalLink: universalLink)

      let venmoRequest = BTVenmoRequest(
        paymentMethodUsage: request.paymentMethodUsage == "multiUse" ? .multiUse : .singleUse,
        profileID: request.profileId,
        displayName: request.displayName,
        collectCustomerBillingAddress: request.collectCustomerBillingAddress ?? false,
        collectCustomerShippingAddress: request.collectCustomerShippingAddress ?? false
      )

      let nonce = try await venmoClient.tokenize(venmoRequest)

      return [
        "nonce": nonce.nonce,
        "type": "venmo",
        "isDefault": nonce.isDefault,
        "description": nonce.description,
        "username": nonce.username,
        "billingAddress": Self.serializePostalAddress(nonce.billingAddress),
        "shippingAddress": Self.serializePostalAddress(nonce.shippingAddress),
      ]
    }
  }

  // MARK: - Helpers

  private func requireAuthorization() throws -> String {
    guard let auth = authorization else {
      throw BraintreeError.notInitialized
    }
    return auth
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
      "billingAddress": serializePostalAddress(nonce.billingAddress),
      "shippingAddress": serializePostalAddress(nonce.shippingAddress),
    ]
  }

  private static func serializePostalAddress(_ address: BTPostalAddress?) -> [String: Any?]? {
    guard let address = address else { return nil }
    let components = address.addressComponents()
    return [
      "recipientName": components["recipientName"],
      "streetAddress": components["streetAddress"],
      "extendedAddress": components["extendedAddress"],
      "locality": components["locality"],
      "region": components["region"],
      "postalCode": components["postalCode"],
      "countryCodeAlpha2": components["countryCodeAlpha2"],
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
  @Field var requiredShippingContactFields: [String]?
}

struct PayPalCheckoutRequestData: Record {
  @Field var amount: String
  @Field var currencyCode: String?
  @Field var intent: String?
  @Field var userAction: String?
  @Field var displayName: String?
  @Field var shippingAddressRequired: Bool?
  @Field var shippingAddressEditable: Bool?
}

struct PayPalVaultRequestData: Record {
  @Field var billingAgreementDescription: String?
  @Field var displayName: String?
  @Field var userAuthenticationEmail: String?
  @Field var shippingAddressRequired: Bool?
  @Field var shippingAddressEditable: Bool?
}

struct VenmoRequestData: Record {
  @Field var paymentMethodUsage: String
  @Field var universalLink: String
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
  case applePayCancelled
  case invalidUniversalLink

  var errorDescription: String? {
    switch self {
    case .notInitialized:
      return "Braintree client not initialized. Call initialize() first."
    case .initializationFailed:
      return "Failed to initialize Braintree client. Check your authorization token."
    case .applePayPresentationFailed:
      return "Failed to present Apple Pay payment sheet."
    case .applePayCancelled:
      return "User cancelled Apple Pay."
    case .invalidUniversalLink:
      return "Invalid universal link URL provided for Venmo."
    }
  }
}

// MARK: - Apple Pay Delegate

private class ApplePayDelegate: NSObject, PKPaymentAuthorizationControllerDelegate {
  private let applePayClient: BTApplePayClient
  private var continuation: CheckedContinuation<[String: Any?], Error>?
  private var didAuthorize = false

  init(applePayClient: BTApplePayClient, continuation: CheckedContinuation<[String: Any?], Error>) {
    self.applePayClient = applePayClient
    self.continuation = continuation
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

        var result: [String: Any?] = [
          "nonce": nonce.nonce,
          "type": "applePay",
          "isDefault": nonce.isDefault,
          "description": nonce.description,
        ]

        if let contact = payment.shippingContact {
          if let postalAddress = contact.postalAddress {
            result["shippingAddress"] = [
              "recipientName": contact.name.map { PersonNameComponentsFormatter().string(from: $0) },
              "streetAddress": postalAddress.street,
              "extendedAddress": postalAddress.subLocality.isEmpty ? nil : postalAddress.subLocality,
              "locality": postalAddress.city,
              "region": postalAddress.state,
              "postalCode": postalAddress.postalCode,
              "countryCodeAlpha2": postalAddress.isoCountryCode,
            ] as [String: Any?]
          }
          result["email"] = contact.emailAddress
          result["phoneNumber"] = contact.phoneNumber?.stringValue
        }

        continuation?.resume(returning: result)
        continuation = nil
      } catch {
        completion(PKPaymentAuthorizationResult(status: .failure, errors: [error]))
        continuation?.resume(throwing: error)
        continuation = nil
      }
    }
  }

  func paymentAuthorizationControllerDidFinish(_ controller: PKPaymentAuthorizationController) {
    controller.dismiss {
      if !self.didAuthorize {
        self.continuation?.resume(throwing: BraintreeError.applePayCancelled)
        self.continuation = nil
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
    case .masterCard: return "MasterCard"
    case .visa: return "Visa"
    case .JCB: return "JCB"
    case .laser: return "Laser"
    case .maestro: return "Maestro"
    case .unionPay: return "UnionPay"
    case .hiper: return "Hiper"
    case .hipercard: return "Hipercard"
    case .solo: return "Solo"
    case .`switch`: return "Switch"
    case .ukMaestro: return "UKMaestro"
    @unknown default: return "Unknown"
    }
  }
}
