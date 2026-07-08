package com.geozelot.homer.data.auth

import android.util.Log
import com.geozelot.homer.di.Bootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/** Handshake payload returned when a Login Flow v2 session is initiated. */
@Serializable
data class LoginV2Init(
    val poll: Poll,
    val login: String,
) {
    @Serializable
    data class Poll(val token: String, val endpoint: String)
}

/** Successful poll result: the scoped app password plus its resolved server/user. */
@Serializable
data class LoginV2Poll(
    val server: String,
    val loginName: String,
    @SerialName("appPassword") val appPassword: String,
)

/**
 * Client for Nextcloud [Login Flow v2](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2).
 *
 * 1. [initiate] POSTs to `/index.php/login/v2` → returns a browser [LoginV2Init.login]
 *    URL (opened in a Custom Tab) and a poll token.
 * 2. [poll] is called repeatedly against the poll endpoint until the user finishes
 *    authenticating in the browser, at which point it yields a scoped app password.
 *
 * Uses the unauthenticated [Bootstrap] OkHttp client — there are no credentials yet.
 */
class LoginFlowClient @Inject constructor(
    @Bootstrap private val client: OkHttpClient,
    private val json: Json,
) {
    /** Begins a login flow against [rawServerUrl] (scheme optional; normalized to https). */
    suspend fun initiate(rawServerUrl: String): LoginV2Init = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawServerUrl)
        val initUrl = "$base/index.php/login/v2"
        Log.i(TAG, "initiate: POST $initUrl")
        val request = Request.Builder()
            .url(initUrl)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Login init failed: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty login init response")
            val init = json.decodeFromString(LoginV2Init.serializer(), body)
            Log.i(TAG, "initiate OK: login=${init.login} pollEndpoint=${init.poll.endpoint}")
            init
        }
    }

    /**
     * Polls once. Returns the credentials when the browser flow has completed, or
     * `null` while still pending (Nextcloud replies 404 until the user finishes).
     */
    suspend fun poll(init: LoginV2Init): LoginV2Poll? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(init.poll.endpoint)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("token", init.poll.token).build())
            .build()
        client.newCall(request).execute().use { response ->
            Log.i(TAG, "poll: HTTP ${response.code} @ ${init.poll.endpoint}")
            when {
                response.code == 404 -> null // still waiting for the user
                response.isSuccessful -> {
                    val body = response.body?.string()
                        ?: throw IOException("Empty login poll response")
                    Log.i(TAG, "poll OK: credentials received")
                    json.decodeFromString(LoginV2Poll.serializer(), body)
                }
                else -> throw IOException("Login poll failed: HTTP ${response.code}")
            }
        }
    }

    companion object {
        private const val TAG = "HomerAuth"
        const val USER_AGENT = "Homer"

        /** Ensures an https scheme and strips any trailing slash. */
        fun normalizeServerUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            val withScheme = when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> "https://$trimmed"
            }
            return withScheme
        }
    }
}
