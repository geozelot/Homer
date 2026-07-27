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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val canAuth = remember(enabled) {
        enabled && activity != null &&
            BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var unlocked by rememberSaveable { mutableStateOf(false) }
    val locked = canAuth && !unlocked

    // Re-lock when the app is backgrounded, so the next resume prompts again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && enabled) unlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun prompt() {
        val host = activity ?: return
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Homer")
            .setSubtitle("Confirm it's you to continue")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            },
        ).authenticate(info)
    }

    // Prompt automatically as soon as we're in a locked state.
    LaunchedEffect(locked) { if (locked) prompt() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (locked) LockScreen(icon = Icons.Filled.Lock, onUnlock = ::prompt) else content()
    }
}

@Composable
private fun LockScreen(icon: ImageVector, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Ground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(56.dp))
        Text("Homer", style = SerifDisplay, color = Parchment, modifier = Modifier.padding(top = 16.dp))
        Text("Locked", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
        ) { Text("Unlock") }
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
