package com.geozelot.homer.ui.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geozelot.homer.R
import com.geozelot.homer.ui.components.HomerTextButton
import com.geozelot.homer.ui.components.LeadingIconInset
import com.geozelot.homer.ui.components.ScreenInset

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    syncMode: Boolean = false,
    /**
     * Fixes which half of this screen is shown and hides the chips that switch between them.
     *
     * For the setup flow, whose first screen asks the same question — an account or a link — as its
     * own full page. Leaving the chips visible there would let the answer be changed twice, in two
     * places, one of which had already moved the flow on.
     */
    forcedMode: LoginViewModel.Mode? = null,
    /** Back out, when this screen sits inside a flow that has somewhere to go back to. */
    onBack: (() -> Unit)? = null,
    onLinked: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(syncMode) { viewModel.setSyncMode(syncMode) }
    LaunchedEffect(forcedMode) { forcedMode?.let(viewModel::setMode) }

    // Open the Nextcloud login page in a Custom Tab when the flow is initiated.
    LaunchedEffect(Unit) {
        viewModel.openBrowser.collect { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    // Sync mode: navigate back once the sync account is linked (the library is unchanged).
    LaunchedEffect(Unit) { viewModel.linked.collect { onLinked() } }

    // Edge-to-edge is on and the window doesn't fit system windows, so without safeDrawingPadding
    // the share-link form's password field and submit button end up behind the keyboard with no way
    // to reach them, and content can sit under the status/nav bars.
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        // The same header the rest of the flow has, in the same place, at the same inset. This is
        // one of eight setup screens and it was the only one whose way back was a word at the
        // bottom of the page — below the fold on a short screen, and nowhere near where the arrow
        // had been on the screen before it. Reserved whether or not there is a button, so the
        // wordmark does not jump between the steps that have one and the steps that do not.
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = LeadingIconInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                IconButton(onClick = it) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenInset)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                text = stringResource(if (syncMode) R.string.login_sync_subtitle else R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
            )

            when (state.status) {
                LoginViewModel.Status.WaitingForBrowser -> {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.login_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(R.string.login_waiting_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HomerTextButton(
                        onClick = viewModel::cancelLogin,
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(stringResource(R.string.action_cancel)) }
                }

                else -> {
                    // Two entry points: sign in to an account, or open a public share link. Hidden in
                    // sync mode — linking a progress account is always an account login.
                    if (!syncMode && forcedMode == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.mode == LoginViewModel.Mode.ACCOUNT,
                                onClick = { viewModel.setMode(LoginViewModel.Mode.ACCOUNT) },
                                label = { Text(stringResource(R.string.login_mode_account)) },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = state.mode == LoginViewModel.Mode.SHARE,
                                onClick = { viewModel.setMode(LoginViewModel.Mode.SHARE) },
                                label = { Text(stringResource(R.string.login_mode_share)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (state.mode == LoginViewModel.Mode.ACCOUNT) {
                        OutlinedTextField(
                            value = state.serverUrl,
                            onValueChange = viewModel::onServerUrlChange,
                            label = { Text(stringResource(R.string.login_server_label)) },
                            placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
                            singleLine = true,
                            enabled = state.status == LoginViewModel.Status.Idle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                        )
                        Button(
                            onClick = viewModel::startLogin,
                            enabled = state.canSubmit,
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().widthIn(max = 420.dp),
                        ) {
                            if (state.status == LoginViewModel.Status.Connecting) {
                                // Sized down: the default indicator is ~40dp and roughly doubled the
                                // button's height while shoving the label sideways.
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.login_button))
                        }
                    } else {
                        OutlinedTextField(
                            value = state.shareUrl,
                            onValueChange = viewModel::onShareUrlChange,
                            label = { Text(stringResource(R.string.login_share_label)) },
                            placeholder = { Text(stringResource(R.string.login_share_placeholder)) },
                            singleLine = true,
                            enabled = state.status == LoginViewModel.Status.Idle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                        )
                        OutlinedTextField(
                            value = state.sharePassword,
                            onValueChange = viewModel::onSharePasswordChange,
                            label = { Text(stringResource(R.string.login_share_password_label)) },
                            singleLine = true,
                            enabled = state.status == LoginViewModel.Status.Idle,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().widthIn(max = 420.dp),
                        )
                        Text(
                            text = stringResource(R.string.login_share_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().widthIn(max = 420.dp),
                        )
                        Button(
                            onClick = viewModel::openShare,
                            enabled = state.canSubmitShare,
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().widthIn(max = 420.dp),
                        ) {
                            if (state.status == LoginViewModel.Status.Connecting) {
                                // Sized down: the default indicator is ~40dp and roughly doubled the
                                // button's height while shoving the label sideways.
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(stringResource(R.string.login_share_button))
                        }
                    }
                }
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
