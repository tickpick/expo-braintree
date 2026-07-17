import ExpoModulesCore
import Braintree

/// Forwards universal-link return URLs to the Braintree SDK so app-switch flows
/// (Venmo, and PayPal if app switch is enabled) can complete when the user
/// returns to the app. All Braintree app-switch returns arrive as an
/// `NSUserActivity` universal link; the SDK has no custom-scheme return path
/// unless `BTAppContextSwitcher.returnURLScheme` is set, which this module
/// never does.
public class ExpoBraintreeAppDelegateSubscriber: ExpoAppDelegateSubscriber {
  public func application(
    _ application: UIApplication,
    continue userActivity: NSUserActivity,
    restorationHandler: @escaping ([any UIUserActivityRestoring]?) -> Void
  ) -> Bool {
    // Expo aggregates state restoration across subscribers; every implementer
    // must invoke the handler or the countdown stalls for all of them.
    restorationHandler(nil)
    guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
          let url = userActivity.webpageURL else {
      return false
    }
    return BTAppContextSwitcher.sharedInstance.handleOpen(url)
  }
}
