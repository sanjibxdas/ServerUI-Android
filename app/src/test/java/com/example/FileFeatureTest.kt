package com.example

import com.example.ui.screens.ChmodUtils
import com.example.ui.screens.detectLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFeatureTest {

    @Test
    fun testDetectLanguage() {
        assertEquals("json", detectLanguage("config.json"))
        assertEquals("yaml", detectLanguage("docker-compose.yml"))
        assertEquals("yaml", detectLanguage("service.yaml"))
        assertEquals("python", detectLanguage("script.py"))
        assertEquals("shell", detectLanguage("deploy.sh"))
        assertEquals("env", detectLanguage(".env"))
        assertEquals("env", detectLanguage(".env.production"))
        assertEquals("ini", detectLanguage("php.ini"))
        assertEquals("ini", detectLanguage("service.conf"))
        assertEquals("text", detectLanguage("notes.txt"))
    }

    @Test
    fun testRwxToOctalConversion() {
        // 0755: rwxr-xr-x
        val flags755 = booleanArrayOf(
            true, true, true,   // rwx
            true, false, true,  // r-x
            true, false, true   // r-x
        )
        val octal755 = ChmodUtils.booleansToOctal(flags755)
        assertEquals(493, octal755)
        assertEquals("0755", ChmodUtils.formatOctalString(octal755))

        // 0644: rw-r--r--
        val flags644 = booleanArrayOf(
            true, true, false,  // rw-
            true, false, false, // r--
            true, false, false  // r--
        )
        val octal644 = ChmodUtils.booleansToOctal(flags644)
        assertEquals(420, octal644)
        assertEquals("0644", ChmodUtils.formatOctalString(octal644))

        // 0700: rwx------
        val flags700 = booleanArrayOf(
            true, true, true,
            false, false, false,
            false, false, false
        )
        val octal700 = ChmodUtils.booleansToOctal(flags700)
        assertEquals("0700", ChmodUtils.formatOctalString(octal700))
    }

    @Test
    fun testParsePermissionsString() {
        val flags755 = ChmodUtils.parsePermissionsString("-rwxr-xr-x")
        assertTrue(flags755[0] && flags755[1] && flags755[2])
        assertTrue(flags755[3] && !flags755[4] && flags755[5])
        assertTrue(flags755[6] && !flags755[7] && flags755[8])
        assertEquals("0755", ChmodUtils.formatOctalString(ChmodUtils.booleansToOctal(flags755)))

        val flags644 = ChmodUtils.parsePermissionsString("-rw-r--r--")
        assertEquals("0644", ChmodUtils.formatOctalString(ChmodUtils.booleansToOctal(flags644)))
        assertTrue(flags644[0] && flags644[1] && !flags644[2])
        assertTrue(flags644[3] && !flags644[4] && !flags644[5])
        assertTrue(flags644[6] && !flags644[7] && !flags644[8])

        val directOctal = ChmodUtils.permissionsStringToOctal("-rw-r--r--")
        assertEquals(420, directOctal)
    }
}
