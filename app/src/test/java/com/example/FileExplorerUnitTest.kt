package com.example

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import com.example.ui.screens.ChmodUtils
import com.example.ui.screens.SyntaxVisualTransformation
import org.junit.Assert.*
import org.junit.Test

class FileExplorerUnitTest {

    @Test
    fun testChmodUtils_booleansToOctal() {
        // 0755: rwxr-xr-x
        // Owner: r(4)+w(2)+x(1)=7, Group: r(4)+x(1)=5, Other: r(4)+x(1)=5
        val flags755 = booleanArrayOf(
            true, true, true,   // Owner
            true, false, true,  // Group
            true, false, true   // Other
        )
        val octal755 = ChmodUtils.booleansToOctal(flags755)
        assertEquals(0x1ED, octal755) // In octal: 0755 is 493 decimal, or (7*64 + 5*8 + 5)
        assertEquals("0755", ChmodUtils.formatOctalString(octal755))

        // 0644: rw-r--r--
        val flags644 = booleanArrayOf(
            true, true, false,
            true, false, false,
            true, false, false
        )
        val octal644 = ChmodUtils.booleansToOctal(flags644)
        assertEquals("0644", ChmodUtils.formatOctalString(octal644))

        // 0600: rw-------
        val flags600 = booleanArrayOf(
            true, true, false,
            false, false, false,
            false, false, false
        )
        val octal600 = ChmodUtils.booleansToOctal(flags600)
        assertEquals("0600", ChmodUtils.formatOctalString(octal600))
    }

    @Test
    fun testChmodUtils_parsePermissionsString() {
        val parsed755 = ChmodUtils.parsePermissionsString("-rwxr-xr-x")
        assertEquals("0755", ChmodUtils.formatOctalString(ChmodUtils.booleansToOctal(parsed755)))

        val parsed644 = ChmodUtils.parsePermissionsString("drw-r--r--")
        assertEquals("0644", ChmodUtils.formatOctalString(ChmodUtils.booleansToOctal(parsed644)))

        val octalDirect = ChmodUtils.permissionsStringToOctal("-rwx------")
        assertEquals("0700", ChmodUtils.formatOctalString(octalDirect))
    }

    @Test
    fun testSyntaxVisualTransformation_preservesTextAndOffsetMapping() {
        val testJson = """{"server": "test", "port": 22, "enabled": true}"""
        val transform = SyntaxVisualTransformation("json")
        val transformed = transform.filter(AnnotatedString(testJson))

        // VisualTransformation must preserve exact text length and characters for 1:1 cursor mapping
        assertEquals(testJson, transformed.text.text)
        assertEquals(OffsetMapping.Identity, transformed.offsetMapping)
        assertTrue("Expected styled spans in highlighted json", transformed.text.spanStyles.isNotEmpty())

        val testYaml = """
            server_name: production
            port: 8080
            debug: false
        """.trimIndent()
        val transformYaml = SyntaxVisualTransformation("yaml")
        val transformedYaml = transformYaml.filter(AnnotatedString(testYaml))
        assertEquals(testYaml, transformedYaml.text.text)
        assertTrue(transformedYaml.text.spanStyles.isNotEmpty())

        val testShell = "if [ \$? -eq 0 ]; then echo \"Success\"; fi"
        val transformSh = SyntaxVisualTransformation("sh")
        val transformedSh = transformSh.filter(AnnotatedString(testShell))
        assertEquals(testShell, transformedSh.text.text)
        assertTrue(transformedSh.text.spanStyles.isNotEmpty())

        val testEnv = "DATABASE_PORT=5432\nDEBUG=true\nAPI_KEY=\"secret123\""
        val transformEnv = SyntaxVisualTransformation("env")
        val transformedEnv = transformEnv.filter(AnnotatedString(testEnv))
        assertEquals(testEnv, transformedEnv.text.text)
        assertTrue(transformedEnv.text.spanStyles.isNotEmpty())
    }

    @Test
    fun testDirectoryBookmarksLogic() {
        val bookmarks = mutableListOf("~", "/var/log", "/etc")

        // Add bookmark
        val newBookmark = "/var/www/html"
        if (!bookmarks.contains(newBookmark)) {
            bookmarks.add(newBookmark)
        }
        assertTrue(bookmarks.contains(newBookmark))
        assertEquals(4, bookmarks.size)

        // Deduplication
        if (!bookmarks.contains(newBookmark)) {
            bookmarks.add(newBookmark)
        }
        assertEquals(4, bookmarks.size)

        // Removal
        bookmarks.remove("/etc")
        assertFalse(bookmarks.contains("/etc"))
        assertEquals(3, bookmarks.size)
    }
}
