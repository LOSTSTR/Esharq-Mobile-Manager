package app.revenge.manager.esharq

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Proving, once, that the person installing Esharq Mobile is in the Esharq server.
 *
 * The installer cannot decide this — anyone can type a user id, and a phone is the last place to
 * trust with the answer. So it sends the user to Discord through esharq.org, and Discord tells the
 * server who they are. The server asks Discord whether that account is in the Esharq server, and
 * only then hands back a receipt. Everything the finished app does later carries that receipt.
 *
 * Nothing here ever sees a Discord password or an account token: the scope asked for is identity
 * alone, and the short-lived access token is revoked server-side the moment it has been used.
 */
class EsharqAuth(context: Context) {

    private val prefs = context.getSharedPreferences("esharq", Context.MODE_PRIVATE)

    /**
     * Compose state, not just a preference read.
     *
     * The home screen decides whether Install is enabled from this. Reading the preference alone
     * left the screen showing "sign in" after a successful sign-in — the value had changed but
     * nothing told Compose to draw again, so the user came back from Discord to a button that still
     * refused them until they closed and reopened the app.
     */
    var installToken: String? by mutableStateOf(prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() })
        private set

    /**
     * Stable per-install identifier. Not a secret and not an identity — it ties the receipt to this
     * install so that copying the finished APK onto another phone does not silently copy access
     * with it. Generated once and kept.
     */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: newRandomId().also {
            prefs.edit { putString(KEY_DEVICE, it) }
        }

    /**
     * A fresh nonce for one sign-in attempt, so a link someone else sends cannot be mistaken for
     * the attempt this installer started.
     */
    fun beginSignIn(): Uri {
        val nonce = newRandomId()

        // PKCE, and the reason for it.
        //
        // The receipt used to come back inside the redirect. On Android a custom scheme belongs to
        // nobody — any app may declare esharq://mobile-auth and nothing verifies the claim — so any
        // app on the phone could catch it. What comes back now is a code that is useless on its
        // own: spending it requires this verifier, which is written to this app's private
        // preferences and never appears in a URL, a browser, or an intent.
        //
        // The server only ever sees its SHA-256, so even the traffic that starts the flow carries
        // nothing that can finish it.
        val verifier = newVerifier()
        prefs.edit {
            putString(KEY_NONCE, nonce)
            putString(KEY_VERIFIER, verifier)
        }

        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("device", deviceId)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("challenge", challengeFor(verifier))
            .build()
    }

    /** What came back from the browser. */
    sealed interface Result {
        /** Signed in, and in the server. */
        data class Success(val token: String) : Result

        /** Signed in, but not in the Esharq server — the one outcome the user can fix. */
        data class NotMember(val invite: String?) : Result

        /** Cancelled, or something went wrong. [reason] is for the log, not for the user. */
        data class Failed(val reason: String) : Result
    }

    /**
     * Reads the callback the browser handed back. Rejects anything whose nonce is not the one this
     * installer just issued, which is what stops a crafted link from planting a receipt.
     */
    suspend fun completeSignIn(uri: Uri): Result {
        val expected = prefs.getString(KEY_NONCE, null)
        val verifier = prefs.getString(KEY_VERIFIER, null)
        prefs.edit { remove(KEY_NONCE); remove(KEY_VERIFIER) }

        if (expected == null || uri.getQueryParameter("state") != expected) {
            return Result.Failed("state_mismatch")
        }

        uri.getQueryParameter("error")?.let { error ->
            return if (error == "not_member") Result.NotMember(uri.getQueryParameter("invite"))
            else Result.Failed(error)
        }

        val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
            ?: return Result.Failed("no_code")
        if (verifier == null) return Result.Failed("no_verifier")

        // The one request that turns the code into the receipt. Everything before this travelled
        // through a browser; this does not.
        val token = exchange(code, verifier) ?: return Result.Failed("exchange_failed")

        persist(token)
        return Result.Success(token)
    }

    /**
     * Spend the code. POST, so the verifier is in a body rather than a URL — a query string ends up
     * in browser history, in server logs, and in whatever else reads a Location header.
     */
    private suspend fun exchange(code: String, verifier: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(EXCHANGE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            connection.outputStream.use { it.write(JSONObject().apply {
                put("code", code)
                put("verifier", verifier)
            }.toString().toByteArray()) }

            if (connection.responseCode != 200) {
                connection.disconnect()
                return@runCatching null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            JSONObject(body).optString("token").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Forgets the receipt. Used when the user signs out or wants to install for another account. */
    fun signOut() {
        persist(null)
    }

    /** Keeps the stored value and the drawn value in step; they are two things now. */
    private fun persist(token: String?) {
        installToken = token
        prefs.edit { if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token) }
    }

    fun signInIntent(): Intent = Intent(Intent.ACTION_VIEW, beginSignIn())

    /**
     * A code verifier, per RFC 7636: 43 unreserved characters from 32 random bytes.
     *
     * base64url without padding lands exactly in the allowed alphabet, so nothing has to be
     * rewritten before it is compared on the server.
     */
    private fun newVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** The S256 challenge: the digest of the verifier, which is all the server is ever told. */
    private fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun newRandomId(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val AUTH_URL = "https://esharq.org/api/mobile/auth"
        const val EXCHANGE_URL = "https://esharq.org/api/mobile/exchange"
        const val KEY_TOKEN = "install_token"
        const val KEY_DEVICE = "device_id"
        const val KEY_NONCE = "sign_in_nonce"
        const val KEY_VERIFIER = "sign_in_verifier"
    }
}
