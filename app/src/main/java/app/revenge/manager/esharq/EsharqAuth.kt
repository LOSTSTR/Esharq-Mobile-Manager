package app.revenge.manager.esharq

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.security.SecureRandom
import android.util.Base64

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
        prefs.edit { putString(KEY_NONCE, nonce) }

        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("device", deviceId)
            .appendQueryParameter("nonce", nonce)
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
    fun completeSignIn(uri: Uri): Result {
        val expected = prefs.getString(KEY_NONCE, null)
        prefs.edit { remove(KEY_NONCE) }

        if (expected == null || uri.getQueryParameter("state") != expected) {
            return Result.Failed("state_mismatch")
        }

        uri.getQueryParameter("error")?.let { error ->
            return if (error == "not_member") Result.NotMember(uri.getQueryParameter("invite"))
            else Result.Failed(error)
        }

        val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }
            ?: return Result.Failed("no_token")

        persist(token)
        return Result.Success(token)
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

    private fun newRandomId(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val AUTH_URL = "https://esharq.org/api/mobile/auth"
        const val KEY_TOKEN = "install_token"
        const val KEY_DEVICE = "device_id"
        const val KEY_NONCE = "sign_in_nonce"
    }
}
