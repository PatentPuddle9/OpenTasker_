package com.opentasker.core.marketplace

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.opentasker.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class GitHubDeviceChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

internal data class GitHubTokenGrant(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Int,
)

/**
 * GitHub OAuth Device Flow for the in-app Marketplace contributor.
 *
 * The OAuth client ID is public application metadata. No client secret is included in the app.
 * User access tokens are encrypted with an Android Keystore AES-GCM key before persistence.
 */
internal object GitHubDeviceAuth {
    // Keep these identifiers stable: Android retains app-private preferences and Keystore keys
    // across an in-place APK update, which lets the same encrypted GitHub session survive updates.
    private const val PREFS = "opentasker_github_auth"
    private const val ENCRYPTED_TOKEN = "encrypted_access_token"
    private const val USER_LOGIN = "github_login"
    private const val ACCESS_EXPIRES_AT = "access_expires_at"
    private const val KEY_ALIAS = "OpenTasker.GitHubOAuth.v1"
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val API_BASE = "https://api.github.com"
    private const val API_VERSION = "2022-11-28"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 25_000

    val isConfigured: Boolean
        get() = BuildConfig.GITHUB_OAUTH_CLIENT_ID.isNotBlank()

    fun savedLogin(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(USER_LOGIN, null)
            ?.takeIf { it.isNotBlank() }

    fun signOut(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(ENCRYPTED_TOKEN)
            .remove(ACCESS_EXPIRES_AT)
            .remove(USER_LOGIN)
            .apply()
    }

    suspend fun beginDeviceFlow(): GitHubDeviceChallenge = withContext(Dispatchers.IO) {
        val clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim()
        require(clientId.isNotBlank()) {
            "GitHub sign-in is not configured in this APK. Set the OPENTASKER_GITHUB_OAUTH_CLIENT_ID repository Actions secret and rebuild."
        }
        val response = postFormBlocking(
            DEVICE_CODE_URL,
            linkedMapOf(
                "client_id" to clientId,
                "scope" to "public_repo read:user offline_access",
            ),
        )
        val error = response.optString("error")
        require(error.isBlank()) {
            response.optString("error_description").ifBlank {
                "GitHub could not start device authorization (${error})."
            }
        }
        val verificationUri = response.optString("verification_uri")
        require(verificationUri == "https://github.com/login/device") {
            "GitHub returned an unexpected verification address."
        }
        GitHubDeviceChallenge(
            deviceCode = response.getString("device_code"),
            userCode = response.getString("user_code"),
            verificationUri = verificationUri,
            expiresInSeconds = response.optInt("expires_in", 900).coerceIn(60, 1800),
            intervalSeconds = response.optInt("interval", 5).coerceIn(5, 60),
        )
    }

    suspend fun waitForAuthorization(challenge: GitHubDeviceChallenge): GitHubTokenGrant {
        var interval = challenge.intervalSeconds
        val expiresAt = System.currentTimeMillis() + challenge.expiresInSeconds * 1_000L
        while (System.currentTimeMillis() < expiresAt) {
            delay(interval * 1_000L)
            val response = withContext(Dispatchers.IO) {
                postFormBlocking(
                    ACCESS_TOKEN_URL,
                    linkedMapOf(
                        "client_id" to BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim(),
                        "device_code" to challenge.deviceCode,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    ),
                )
            }
            val error = response.optString("error")
            when (error) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = maxOf(interval + 5, response.optInt("interval", interval + 5))
                "expired_token" -> throw IOException("The GitHub sign-in code expired. Start sign-in again.")
                "access_denied" -> throw IOException("GitHub sign-in was cancelled.")
                "" -> {
                    val token = response.optString("access_token")
                    if (token.isNotBlank()) {
                        return GitHubTokenGrant(
                            accessToken = token,
                            refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() },
                            expiresInSeconds = response.optInt("expires_in", 0),
                        )
                    }
                    throw IOException("GitHub completed authorization without returning an access token.")
                }
                else -> throw IOException(
                    response.optString("error_description").ifBlank { "GitHub sign-in failed (${error})." },
                )
            }
        }
        throw IOException("The GitHub sign-in code expired. Start sign-in again.")
    }

    suspend fun finishSignIn(context: Context, grant: GitHubTokenGrant): String = withContext(Dispatchers.IO) {
        require(grant.accessToken.isNotBlank()) { "GitHub did not return an access token." }
        val user = apiRequestBlocking(grant.accessToken, "GET", "/user")
        val login = user.optString("login").trim()
        require(login.matches(Regex("^[A-Za-z0-9-]{1,39}$"))) {
            "GitHub returned an invalid account name."
        }
        TokenStore.save(
            context.applicationContext,
            grant.accessToken,
            grant.refreshToken,
            grant.expiresInSeconds,
            login,
        )
        login
    }

    suspend fun verifySavedLogin(context: Context): String? = withContext(Dispatchers.IO) {
        // A null token can mean that refresh failed because the device is temporarily offline.
        // TokenStore removes the saved login itself only when the encrypted session is invalid
        // or GitHub rejects/revokes it, so preserve the account label for transient failures.
        val token = TokenStore.read(context.applicationContext) ?: return@withContext savedLogin(context)
        try {
            val user = apiRequestBlocking(token, "GET", "/user")
            val login = user.optString("login").trim()
            require(login.isNotBlank()) { "GitHub did not return an account name." }
            // The token store refreshes and rotates encrypted credentials before returning a token.
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(USER_LOGIN, login).apply()
            login
        } catch (error: GitHubApiException) {
            if (error.statusCode == 401 || error.statusCode == 403) {
                signOut(context)
                null
            } else {
                savedLogin(context)
            }
        } catch (_: Exception) {
            savedLogin(context)
        }
    }

    internal fun accessToken(context: Context): String =
        TokenStore.read(context.applicationContext)
            ?: throw IOException("Sign in with GitHub before submitting a contribution.")

    internal fun apiRequestBlocking(
        accessToken: String,
        method: String,
        path: String,
        body: JSONObject? = null,
    ): JSONObject {
        require(path.startsWith("/") && !path.startsWith("//")) { "Invalid GitHub API path." }
        val connection = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "OpenTasker-Android-Marketplace")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val message = response.optString("message").ifBlank { "GitHub API request failed." }
                throw GitHubApiException(status, message)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun postFormBlocking(url: String, fields: Map<String, String>): JSONObject {
        val encoded = fields.entries.joinToString("&") { (key, value) ->
            "${formEncode(key)}=${formEncode(value)}"
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("User-Agent", "OpenTasker-Android-Marketplace")
        }
        try {
            connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw IOException(response.optString("error_description").ifBlank {
                    "GitHub authorization request failed (HTTP $status)."
                })
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun formEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private object TokenStore {
        fun save(
            context: Context,
            accessToken: String,
            refreshToken: String?,
            expiresInSeconds: Int,
            login: String,
        ) {
            val payload = accessToken + "\n" + refreshToken.orEmpty()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                "." + Base64.encodeToString(encrypted, Base64.NO_WRAP)
            val expiresAt = if (expiresInSeconds > 0) {
                System.currentTimeMillis() + expiresInSeconds * 1_000L
            } else {
                0L
            }
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ENCRYPTED_TOKEN, encoded)
                .putLong(ACCESS_EXPIRES_AT, expiresAt)
                .putString(USER_LOGIN, login)
                // This runs on Dispatchers.IO. Commit before reporting sign-in success so an
                // immediate app update or process recreation cannot race an async preferences write.
                .commit()
            check(saved) { "Couldn't save the GitHub sign-in session on this device." }
        }

        fun read(context: Context): String? {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = preferences.getString(ENCRYPTED_TOKEN, null) ?: return null
            val payload = try {
                val parts = stored.split('.', limit = 2)
                require(parts.size == 2) { "Stored GitHub token is malformed." }
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (_: Exception) {
                signOut(context)
                return null
            }
            val parts = payload.split("\n", limit = 2)
            val accessToken = parts.firstOrNull().orEmpty()
            val refreshToken = parts.getOrNull(1).orEmpty()
            if (accessToken.isBlank()) {
                signOut(context)
                return null
            }

            val expiresAt = preferences.getLong(ACCESS_EXPIRES_AT, 0L)
            if (expiresAt == 0L || expiresAt > System.currentTimeMillis() + 60_000L) return accessToken
            if (refreshToken.isBlank()) {
                signOut(context)
                return null
            }

            // GitHub rotates refresh tokens; persist the new encrypted pair together.
            val response = try {
                postFormBlocking(
                    ACCESS_TOKEN_URL,
                    linkedMapOf(
                        "client_id" to BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim(),
                        "grant_type" to "refresh_token",
                        "refresh_token" to refreshToken,
                    ),
                )
            } catch (_: Exception) {
                // Keep the encrypted session intact during network failures so the next request can retry.
                return null
            }
            val refreshedAccess = response.optString("access_token")
            if (refreshedAccess.isBlank()) {
                signOut(context)
                return null
            }
            val refreshedRefresh = response.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken
            save(
                context,
                refreshedAccess,
                refreshedRefresh,
                response.optInt("expires_in", 0),
                preferences.getString(USER_LOGIN, "").orEmpty(),
            )
            return refreshedAccess
        }

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

internal class GitHubApiException(
    val statusCode: Int,
    message: String,
) : IOException(message)
