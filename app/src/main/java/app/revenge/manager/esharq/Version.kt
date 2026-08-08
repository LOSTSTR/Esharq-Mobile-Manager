package app.revenge.manager.esharq

/**
 * Deciding whether a published release is actually newer than the installed build.
 *
 * This used to be `remoteTag != VERSION_NAME`, which answers a different question. Anything that is
 * not identical counts as an update under that rule, including a release older than what is already
 * installed — so once this fork's version passed the number it was comparing against, the update
 * prompt appeared on every launch and offered to move the user backwards.
 */
object Version {

    /**
     * True when [releaseTag] names a version above [installed].
     *
     * Both sides are read as dot-separated numbers, ignoring a leading "v". A part either side is
     * missing counts as zero, so 1.4 is newer than 1.3.9 and 1.3.1 is newer than 1.3.
     *
     * Anything that does not parse as numbers answers false. That is deliberate: the cost of a
     * missed prompt is a user staying on a working version for a while, and the cost of a wrong one
     * is every user being told to install something that is not an update.
     */
    fun isNewer(releaseTag: String, installed: String): Boolean {
        val remote = parse(releaseTag) ?: return false
        val current = parse(installed) ?: return false

        for (i in 0 until maxOf(remote.size, current.size)) {
            val a = remote.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parse(version: String): List<Int>? {
        val trimmed = version.trim().removePrefix("v").trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(".")
        return parts.map { part -> part.toIntOrNull() ?: return null }
    }
}
