package app.revenge.manager.esharq

import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Runs the check against real APK files rather than against a list of digests.
 *
 * DiscordSignatureTest covers the decision; this covers the reading — whether apksig, given an
 * actual Discord split and an actual APK signed by somebody else, produces what the decision
 * expects. That is the half that cannot be reasoned about, and shipping it unproven would have
 * risked refusing every download for every user.
 *
 * The files are not committed: Discord's base APK is 110MB. Point the test at a downloaded copy to
 * run it, and it skips otherwise so a normal build stays offline and fast:
 *
 *   ESHARQ_TEST_DISCORD_APK=... ESHARQ_TEST_FOREIGN_APK=... ./gradlew testDebugUnitTest
 */
class DiscordSignatureFilesTest {

    private val discord = System.getenv("ESHARQ_TEST_DISCORD_APK")?.let(::File)?.takeIf { it.isFile }
    private val foreign = System.getenv("ESHARQ_TEST_FOREIGN_APK")?.let(::File)?.takeIf { it.isFile }

    @Test
    fun `a real Discord split is recognised as Discord's`() {
        val apk = discord ?: return
        assertIs<DiscordSignature.Result.Genuine>(
            DiscordSignature.check(apk),
            "the pinned certificate no longer matches what Discord ships"
        )
    }

    @Test
    fun `an APK signed by somebody else is refused`() {
        val apk = foreign ?: return
        val verdict = DiscordSignature.check(apk)
        assertIs<DiscordSignature.Result.WrongSigner>(verdict)
        assertTrue(verdict.found.none { it.equals(DiscordSignature.CERTIFICATE_SHA256, true) })
    }

    @Test
    fun `a file that is not an APK is refused rather than accepted`() {
        val notAnApk = File.createTempFile("esharq", ".apk").apply {
            writeText("this is not a zip")
            deleteOnExit()
        }
        assertIs<DiscordSignature.Result.Unverifiable>(DiscordSignature.check(notAnApk))
    }

    @Test
    fun `a truncated download is refused rather than accepted`() {
        val apk = discord ?: return
        val half = File.createTempFile("esharq-half", ".apk").apply { deleteOnExit() }
        apk.inputStream().use { input ->
            half.outputStream().use { output ->
                val chunk = ByteArray(1 shl 20)
                var copied = 0L
                while (copied < apk.length() / 2) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    output.write(chunk, 0, read)
                    copied += read
                }
            }
        }
        assertIs<DiscordSignature.Result.Unverifiable>(DiscordSignature.check(half))
    }
}
