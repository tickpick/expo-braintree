import ExpoModulesCore
import Braintree

/// Forwards return URLs to the Braintree SDK so app-switch flows (Venmo, PayPal)
/// can complete when the user returns to the app. Venmo returns via the universal
/// link passed to `BTVenmoClient`, which arrives as an `NSUserActivity`.
public class ExpoBraintreeAppDelegateSubscriber: ExpoAppDelegateSubscriber {
  public func application(
    _ app: UIApplication,
    open url: URL,
    options: [UIApplication.OpenURLOptionsKey: Any] = [:]
  ) -> Bool {
    return BTAppContextSwitcher.sharedInstance.handleOpen(url)
  }

  public func application(
    _ application: UIApplication,
    continue userActivity: NSUserActivity,
    restorationHandler: @escaping ([any UIUserActivityRestoring]?) -> Void
  ) -> Bool {
    guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
          let url = userActivity.webpageURL else {
      return false
    }
    return BTAppContextSwitcher.sharedInstance.handleOpen(url)
  }
}
