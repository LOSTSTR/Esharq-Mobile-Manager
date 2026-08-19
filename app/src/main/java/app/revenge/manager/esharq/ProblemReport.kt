package app.revenge.manager.esharq

import android.os.Build
import app.revenge.manager.BuildConfig
import app.revenge.manager.installer.step.Step
import app.revenge.manager.installer.step.StepStatus
import app.revenge.manager.installer.util.LogEntry

/**
 * The install log, compressed into something a person can actually send.
 *
 * Sharing already existed and produced a file through the system chooser. That is fine for keeping
 * a copy and useless for the thing people actually do, which is describe the problem in the server
 * and be asked for details. A file has to be saved, found and attached; most give up and write "it
 * doesn't work", and then nobody can help them.
 *
 * The whole log is also the wrong thing to send. It runs to hundreds of lines of successful steps,
 * it is longer than a Discord message allows, and the part that matters is at the end.
 *
 * So this keeps what a reader needs to act — which build, which Discord, which device, which step
 * stopped and what it said — and drops the rest.
 */
object ProblemReport {

    /** Discord refuses messages past 2000 characters, and a report nobody can send helps nobody. */
    private const val LIMIT = 1800

    /** Error lines carry the cause; a few lines before them carry what was being attempted. */
    private const val CONTEXT_LINES = 25

    fun build(steps: List<Step>, logs: List<LogEntry>, discordVersion: String?): String {
        val failed = steps.firstOrNull { it.status == StepStatus.UNSUCCESSFUL }

        val header = buildString {
            appendLine("Esharq Mobile ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Discord ${discordVersion ?: "—"}")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine(if (failed != null) "Failed at: ${failed.javaClass.simpleName}" else "No step reported failure")
        }

        // The tail is where the failure is. Errors are kept whichever way, then padded with the
        // lines around them so the reader can see what was being attempted when it went wrong.
        val errors = logs.withIndex().filter { it.value.level == LogEntry.Level.ERROR }
        val body = if (errors.isEmpty()) {
            logs.takeLast(CONTEXT_LINES)
        } else {
            val from = maxOf(0, errors.first().index - CONTEXT_LINES / 2)
            logs.subList(from, logs.size)
        }.joinToString("\n") { it.message }

        val trimmed = if (body.length <= LIMIT) body else "…\n" + body.takeLast(LIMIT)
        return "$header\n$trimmed"
    }
}
