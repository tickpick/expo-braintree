package expo.modules.braintree

import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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

  // A result that arrived while no module instance was listening. This
  // happens when the process is killed with the Google Pay sheet on top:
  // androidx redelivers the result right after register(), before any Expo
  // module has been created.
  private var pendingResult: GooglePayPaymentAuthResult? = null

  /**
   * Forwards a completed Google Pay result to whichever module instance is
   * currently listening. Setting a non-null handler flushes any result that
   * arrived while no one was listening.
   */
  var onResult: ((GooglePayPaymentAuthResult) -> Unit)? = null
    set(value) {
      field = value
      val pending = pendingResult
      if (value != null && pending != null) {
        pendingResult = null
        value(pending)
      }
    }

  fun register(activity: ComponentActivity) {
    if (launcher != null && registeredActivity === activity) return
    registeredActivity = activity
    launcher = GooglePayLauncher(activity) { result ->
      val handler = onResult
      if (handler != null) handler(result) else pendingResult = result
    }
    // Drop our references when this Activity is destroyed so the singleton
    // doesn't pin a dead Activity (and its view hierarchy) in memory. Only
    // clear if this Activity is still the registered one — on recreation the
    // new instance's register() runs before the old instance's onDestroy.
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        if (registeredActivity === activity) {
          registeredActivity = null
          launcher = null
        }
      }
    })
  }
}
