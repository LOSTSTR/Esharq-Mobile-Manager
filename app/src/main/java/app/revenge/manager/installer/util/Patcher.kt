package app.revenge.manager.installer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.File

object Patcher {

    /**
     * @param debuggable whether the finished app should be debuggable.
     *
     * LSPatch rewrites the manifest after we have patched it, and writes this flag from its own
     * configuration — so the manager setting it and never telling LSPatch produced an app that was
     * not debuggable no matter what the user chose. The setting looked like it worked: it saved, the
     * patch ran, and nothing complained.
     */
    suspend fun patch(
        logger: Logger,
        outputDir: File,
        apkPaths: List<String>,
        embeddedModules: List<String>,
        debuggable: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            LSPatch(
                logger,
                *apkPaths.toTypedArray(),
                *(if (debuggable) arrayOf("--debuggable") else emptyArray()),
                "-o",
                outputDir.absolutePath,
                "-l",
                "0",
                "-v",
                "-m",
                *embeddedModules.toTypedArray(),
                "-k",
                app.revenge.manager.installer.util.Signer.keyStore.absolutePath,
                "password",
                "alias",
                "password"
            ).doCommandLine()
        }
    }

}