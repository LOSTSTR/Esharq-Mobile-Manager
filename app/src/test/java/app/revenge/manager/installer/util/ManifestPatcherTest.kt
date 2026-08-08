package app.revenge.manager.installer.util

import pxb.android.axml.NodeVisitor.TYPE_INT_BOOLEAN
import pxb.android.axml.NodeVisitor.TYPE_STRING
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A binary manifest keeps booleans as integers. Handing the writer a Kotlin Boolean produced an
 * attribute that read as false no matter what was asked for, which is how the Debuggable setting
 * came to be a switch that did nothing: it was on, the patch ran without error, and the installed
 * app was not debuggable.
 */
class ManifestPatcherTest {

    @Test
    fun `booleans are written as the integers a manifest stores`() {
        assertEquals(1, ManifestPatcher.axmlValue(true))
        assertEquals(0, ManifestPatcher.axmlValue(false))
    }

    @Test
    fun `a boolean carries the boolean type, not whatever the old value had`() {
        // The attribute being replaced is android:debuggable="false", which arrives as an int.
        assertEquals(TYPE_INT_BOOLEAN, ManifestPatcher.axmlType(true, fallback = 0x10))
    }

    @Test
    fun `strings still declare themselves strings`() {
        assertEquals(TYPE_STRING, ManifestPatcher.axmlType("Esharq", fallback = TYPE_INT_BOOLEAN))
        assertEquals("Esharq", ManifestPatcher.axmlValue("Esharq"))
    }

    @Test
    fun `anything else is passed through with the type it arrived with`() {
        assertEquals(0x10, ManifestPatcher.axmlType(23, fallback = 0x10))
        assertEquals(23, ManifestPatcher.axmlValue(23))
        assertEquals(null, ManifestPatcher.axmlValue(null))
    }
}
