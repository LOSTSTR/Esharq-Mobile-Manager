package app.revenge.manager.installer.step.download

import androidx.compose.runtime.Stable
import app.revenge.manager.BuildConfig
import app.revenge.manager.R
import app.revenge.manager.installer.step.download.base.DownloadStep
import java.io.File

/**
 * Downloads the Esharq loader, the Xposed module that gets embedded into Discord.
 *
 * It has to be ours: the loader is the piece that reads the receipt this installer bakes in and
 * authenticates every request with it. Upstream's would ignore the receipt entirely and still
 * carry a copy of the mod inside itself, which would run for anyone at all.
 */
@Stable
class DownloadModStep(
    workingDir: File
): DownloadStep() {

    override val nameRes = R.string.step_dl_mod

    override val downloadFullUrl: String = BuildConfig.LOADER_URL
    override val destination = preferenceManager.moduleLocation
    override val workingCopy = workingDir.resolve("xposed.apk")

}
