package expo.modules.braintree

import androidx.activity.ComponentActivity
import com.braintreepayments.api.googlepay.GooglePayLauncher
import com.braintreepayments.api.googlepay.GooglePayPaymentAuthResult

/**
 * GooglePayLauncher wraps an androidx ActivityResultLauncher, which Android
 * only allows registering before the host Activity passes STARTED. Expo's
 * own module lifecycle hooks (OnCreate, OnActivityEntersForeground, and the
 * JS-triggered initialize() call) all fire after that point — by the time
 * any of them run, MainActivity has already reached RESUMED. Braintree's own
 * docs require constructing GooglePayLauncher directly in the host Activity's
 * onCreate(), so registration has to happen there instead — see
 * MainActivity.kt's `GooglePayLauncherHolder.register(this)` call, injected
 * by this package's config plugin (app.plugin.js).
 */
object GooglePayLauncherHolder {
  var launcher: GooglePayLauncher? = null
    private set

  // The Activity instance `launcher` is currently registered against. Android
  // unregisters the underlying ActivityResultLauncher when that instance is
  // destroyed (config change, dev-client reload, low-memory recreation,
  // etc.) — comparing by identity lets us detect a new instance and
  // re-register, instead of calling launch() on a launcher whose
  // registration Android already tore down.
  private var registeredActivity: ComponentActivity? = null

  /** Forwards a completed Google Pay result to whichever module instance is currently listening. */
  var onResult: ((GooglePayPaymentAuthResult) -> Unit)? = null

  fun register(activity: ComponentActivity) {
    if (launcher != null && registeredActivity === activity) return
    registeredActivity = activity
    launcher = GooglePayLauncher(activity) { result -> onResult?.invoke(result) }
  }
}
