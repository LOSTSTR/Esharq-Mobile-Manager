package app.revenge.manager.installer.step.patching

import app.revenge.manager.R
import app.revenge.manager.esharq.EsharqAuth
import app.revenge.manager.installer.step.Step
import app.revenge.manager.installer.step.StepGroup
import app.revenge.manager.installer.step.StepRunner
import app.revenge.manager.installer.step.download.DownloadBaseStep
import com.github.diamondminer88.zip.ZipWriter
import org.koin.core.component.inject

/**
 * Writes the server's receipt into the APK being built.
 *
 * This is what makes the finished app the user's own: the loader reads this on every launch and
 * sends it with every request, so the mod is served to this account and no other. An app built
 * without it has nothing to authenticate as — and since no copy of the mod ships inside the APK,
 * that leaves plain, unmodified Discord rather than a broken one.
 *
 * Runs before signing, so the receipt is covered by the signature like everything else.
 */
class WriteInstallTokenStep : Step() {

    private val auth: EsharqAuth by inject()

    override val group = StepGroup.PATCHING
    override val nameRes = R.string.step_write_install_token

    override suspend fun run(runner: StepRunner) {
        val token = auth.installToken
            ?: throw IllegalStateException("Not signed in — Esharq Mobile is only built for members of the Esharq server")

        // The receipt is signed base64url in two parts. Checking the shape before writing it into
        // a JSON literal is what keeps a malformed value from producing an APK that is silently
        // unreadable to the loader, rather than an install that fails here where it can be seen.
        if (!TOKEN.matches(token)) {
            throw IllegalStateException("Esharq install receipt is malformed — sign in again")
        }

        val baseApk = runner.getCompletedStep<DownloadBaseStep>().workingCopy

        runner.logger.i("Writing Esharq install receipt into ${baseApk.name}")
        ZipWriter(baseApk, /* append = */ true).use {
            it.writeEntry(INSTALL_ASSET, """{"token":"$token"}""".toByteArray())
        }
    }

    private companion object {
        /** Must match RevengeConstants.INSTALL_ASSET in the Esharq loader. */
        const val INSTALL_ASSET = "assets/esharq-install.json"

        val TOKEN = Regex("""^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$""")
    }
}
