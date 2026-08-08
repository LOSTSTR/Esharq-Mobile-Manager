package app.revenge.manager.esharq

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

/**
 * Proving that the APK we are about to patch is really Discord's.
 *
 * The installer does not get Discord from Discord. It downloads the split APKs from community
 * mirrors, and the only thing checked about them was that the file existed and was not zero bytes.
 * Whoever controls a mirror could therefore hand over a modified Discord, and this installer would
 * patch it, sign it with its own key, and install it wearing the Esharq name — with the user having
 * done nothing wrong and nothing to notice.
 *
 * Google Play will not distribute an APK signed by anyone but the developer, so Discord's signing
 * certificate is the one thing a mirror cannot forge. Pinning it costs a hash per download and
 * removes the mirrors from the set of parties that have to be trusted — they can still serve the
 * files, they just cannot change them.
 */
object DiscordSignature {

    /**
     * SHA-256 of Discord's signing certificate, as read from a Play-distributed Discord APK:
     * CN=Jason Citron, O=Hammer and Chisel, L=Burlingame, ST=CA, C=US
     *
     * If Discord ever rotates this key, every download will be refused until this value is updated
     * — a loud failure that stops installs, rather than a quiet one that lets anything through.
     */
    const val CERTIFICATE_SHA256 = "3c39d23cf9367849a5c699395647fe0e5bfea5a1f1f40d8c717ddc70f8bfa113"

    /** What the check concluded, kept apart from the file reading so it can be tested. */
    sealed interface Result {
        data object Genuine : Result

        /** Signed, but by somebody else. [found] is for the log — nobody needs it on screen. */
        data class WrongSigner(val found: List<String>) : Result

        /** Not verifiable at all: corrupt, truncated, or not an APK. */
        data class Unverifiable(val reason: String) : Result
    }

    /**
     * Reads every signing certificate [apk] carries and decides.
     *
     * Every certificate has to be Discord's, not merely one of them — an APK signed by Discord *and*
     * somebody else is not an APK Discord shipped.
     */
    fun check(apk: File): Result {
        val result = try {
            ApkVerifier.Builder(apk).build().verify()
        } catch (t: Throwable) {
            return Result.Unverifiable(t.message ?: t::class.java.simpleName)
        }

        if (!result.isVerified) {
            return Result.Unverifiable("signature does not verify")
        }

        val found = result.signerCertificates.map { sha256(it.encoded) }
        return judge(found)
    }

    /** The decision itself, over digests that have already been read. */
    fun judge(certificates: List<String>): Result = when {
        certificates.isEmpty() -> Result.Unverifiable("no signing certificate")
        certificates.all { it.equals(CERTIFICATE_SHA256, ignoreCase = true) } -> Result.Genuine
        else -> Result.WrongSigner(certificates)
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
