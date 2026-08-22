package com.geozelot.homer.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.theme.Amber
import com.geozelot.homer.ui.theme.Ground
import com.geozelot.homer.ui.theme.Muted
import com.geozelot.homer.ui.theme.OnAmber
import com.geozelot.homer.ui.theme.Parchment
import com.geozelot.homer.ui.theme.SerifDisplay

/** Biometric / device-credential unlock, either of which satisfies the app lock. */
private val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Wraps the app UI in an optional lock. When app lock is enabled and the device can authenticate,
 * content is hidden behind an unlock prompt on cold start and after every backgrounding. If the
 * device has no biometrics or screen lock enrolled it fails open (there'd be no way back in
 * otherwise); the settings toggle is where the user opts in.
 */
@Composable
fun BiometricGate(content: @Composable () -> Unit) {
    val viewModel: LockViewModel = hiltViewModel()
    val enabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    var unlocked by rememberSaveable { mutableStateOf(false) }
    // Survives configuration changes on purpose: the prompt itself does too (it's hosted by the
    // activity), so a rotation used to build and show a SECOND BiometricPrompt on top of the first.
    var prompting by rememberSaveable { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    // Re-checked on every resume: capability was remembered once, so enrolling a fingerprint (or
    // removing the screen lock) while Homer sat in the background left it stale.
    var resumeTick by remember { mutableIntStateOf(0) }

    // Re-lock when the app is backgrounded, so the next resume prompts again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (enabled) unlocked = false
                Lifecycle.Event.ON_RESUME -> resumeTick++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canAuth = remember(enabled, activity, resumeTick) {
        enabled && activity != null &&
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val locked = canAuth && !unlocked

    // [force] is for the Unlock button: if the user can tap it, there is demonstrably no prompt on
    // screen, so it doubles as the recovery path should the flag ever be left set.
    fun prompt(force: Boolean) {
        val host = activity ?: return
        if (prompting && !force) return
        prompting = true
        authError = null
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.lock_prompt_title))
            .setSubtitle(context.getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authError = null
                    prompting = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    // A user-dismissed prompt is expected (they can retry with Unlock); anything
                    // else — notably a lockout after too many attempts — needs to be shown, or the
                    // user is stranded on a blank lock screen with no idea why nothing happened.
                    authError = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> null
                        else -> errString.toString()
                    }
                }
            },
        ).authenticate(info)
    }

    // Prompt automatically as soon as we're in a locked state. This effect restarts after a
    // configuration change, which is exactly why the un-forced call bails out while a prompt from
    // before the rotation is still up — it used to stack a second one.
    LaunchedEffect(locked) { if (locked) prompt(force = false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (locked) {
            LockScreen(icon = Icons.Filled.Lock, error = authError, onUnlock = { prompt(force = true) })
        } else {
            content()
        }
    }
}

@Composable
private fun LockScreen(icon: ImageVector, error: String?, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Ground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.app_name), style = SerifDisplay, color = Parchment, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.lock_locked), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
        ) { Text(stringResource(R.string.lock_unlock)) }
        if (error != null) {
            Text(
                error,
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

/** Walks the ContextWrapper chain to the hosting [FragmentActivity] (needed by BiometricPrompt). */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
