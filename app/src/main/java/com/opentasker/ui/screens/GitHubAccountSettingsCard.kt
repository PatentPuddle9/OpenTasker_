package com.opentasker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.core.marketplace.GitHubDeviceAuth
import com.opentasker.core.marketplace.GitHubDeviceChallenge
import kotlinx.coroutines.launch

@Composable
internal fun GitHubAccountSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var account by remember(context) { mutableStateOf(GitHubDeviceAuth.savedLogin(context)) }
    var checkingAccount by remember { mutableStateOf(account == null) }
    var signingIn by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf<GitHubDeviceChallenge?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val verified = runCatching { GitHubDeviceAuth.verifySavedLogin(context) }.getOrNull()
        // Keep the locally saved account visible while GitHub is temporarily unreachable
        // and refresh-token rotation is retried on the next authenticated request.
        account = verified ?: GitHubDeviceAuth.savedLogin(context)
        checkingAccount = false
    }

    fun openVerificationPage(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            errorMessage = "Couldn't open GitHub in a browser. Use Open GitHub below to try again."
        }
    }

    fun startSignIn() {
        if (!GitHubDeviceAuth.isConfigured) {
            errorMessage = "GitHub sign-in isn't configured in this build. The repository owner needs to add the OPENTASKER_GITHUB_OAUTH_CLIENT_ID Actions secret and rebuild."
            return
        }
        scope.launch {
            signingIn = true
            errorMessage = null
            challenge = null
            runCatching {
                val device = GitHubDeviceAuth.beginDeviceFlow()
                challenge = device
                openVerificationPage(device.verificationUri)
                val grant = GitHubDeviceAuth.waitForAuthorization(device)
                GitHubDeviceAuth.finishSignIn(context, grant)
            }.onSuccess { login ->
                account = login
                challenge = null
            }.onFailure { error ->
                errorMessage = error.message ?: "GitHub sign-in failed."
                challenge = null
            }
            signingIn = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("GitHub account", style = MaterialTheme.typography.titleMedium)
            Text(
                "Connect GitHub to submit Actions and Shortcut Widgets to the Marketplace. Signing in is optional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                checkingAccount -> CircularProgressIndicator()
                account != null -> {
                    Text(
                        "Signed in as @${account}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = {
                            GitHubDeviceAuth.signOut(context)
                            account = null
                            challenge = null
                            errorMessage = null
                        },
                    ) { Text("Sign out") }
                }
                else -> {
                    Button(onClick = ::startSignIn, enabled = !signingIn) {
                        Text(if (signingIn) "Waiting for GitHub approval…" else "Sign in with GitHub")
                    }
                }
            }

            if (!GitHubDeviceAuth.isConfigured && account == null) {
                Text(
                    "GitHub sign-in is unavailable until the app is rebuilt with its OAuth Client ID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            challenge?.let { device ->
                Text(
                    "GitHub opened in your browser. Enter this one-time code to authorize OpenTasker:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(device.userCode, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub device code", device.userCode))
                        },
                    ) { Text("Copy code") }
                    OutlinedButton(onClick = { openVerificationPage(device.verificationUri) }) {
                        Text("Open GitHub")
                    }
                }
            }

            if (errorMessage != null) {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
