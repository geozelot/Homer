package com.geozelot.homer.ui.login

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.LaunchedEffect
import com.geozelot.homer.R

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Open the Nextcloud login page in a Custom Tab when the flow is initiated.
    LaunchedEffect(Unit) {
        viewModel.openBrowser.collect { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(
            text = stringResource(R.string.login_subtitle),
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
                TextButton(
                    onClick = viewModel::cancelLogin,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.action_cancel)) }
            }

            else -> {
                // Two entry points: sign in to an account, or open a public share link.
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
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
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
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(stringResource(R.string.login_share_button))
                    }
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
