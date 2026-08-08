package app.revenge.manager.esharq

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rule this replaced was "different means newer", and it shipped. Once this fork reached 1.3.1
 * while the repository it was reading sat at 1.3.0, every launch offered an update that was in fact
 * a step backwards — into a different application entirely.
 *
 * So both directions are checked here, not only the happy one. A comparison that never offers an
 * update fails just as badly as one that always does; it simply fails quietly, and users sit on a
 * version with known bugs believing it is current.
 */
class VersionTest {

    @Test
    fun `a newer release is offered`() {
        assertTrue(Version.isNewer("v1.3.2", "1.3.1"))
        assertTrue(Version.isNewer("v1.4.0", "1.3.9"))
        assertTrue(Version.isNewer("v2.0.0", "1.9.9"))
        assertTrue(Version.isNewer("v1.3.1", "1.3"), "a longer version is newer than its prefix")
        assertTrue(Version.isNewer("v1.10.0", "1.9.0"), "10 is above 9, not below it as text")
    }

    @Test
    fun `the installed version is not offered to itself`() {
        assertFalse(Version.isNewer("v1.3.2", "1.3.2"))
        assertFalse(Version.isNewer("1.3.2", "1.3.2"), "with or without the v")
        assertFalse(Version.isNewer("v1.3", "1.3.0"), "trailing zeroes are the same version")
    }

    @Test
    fun `an older release is never offered`() {
        assertFalse(Version.isNewer("v1.3.0", "1.3.1"), "the bug this test exists for")
        assertFalse(Version.isNewer("v1.2.9", "1.3.0"))
        assertFalse(Version.isNewer("v0.9.9", "1.0.0"))
    }

    @Test
    fun `anything unreadable is treated as no update`() {
        assertFalse(Version.isNewer("nightly", "1.3.2"))
        assertFalse(Version.isNewer("v1.3.2-beta", "1.3.1"))
        assertFalse(Version.isNewer("", "1.3.2"))
        assertFalse(Version.isNewer("v", "1.3.2"))
        assertFalse(Version.isNewer("v1.3.2", "unknown"))
    }
}
