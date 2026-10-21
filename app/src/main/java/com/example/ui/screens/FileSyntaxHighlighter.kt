package com.example.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// xAI-inspired dark syntax palette
val SyntaxKeyword = Color(0xFF79C0FF)      // Blue / cyan
val SyntaxString = Color(0xFF7EE787)       // Soft green
val SyntaxNumber = Color(0xFFFFA657)       // Orange / amber
val SyntaxComment = Color(0xFF8B949E)      // Muted gray
val SyntaxProperty = Color(0xFFD2A8FF)     // Purple / lavender
val SyntaxPunctuation = Color(0xFFC9D1D9)  // Light off-white
val SyntaxBoolean = Color(0xFFFF7B72)      // Coral / red

/**
 * Fast, regex-tokenizing VisualTransformation for code/config syntax highlighting in Compose.
 * Preserves 1:1 character mapping so cursor positioning and editing remain unmodified.
 */
class SyntaxVisualTransformation(private val fileExtension: String) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val ext = fileExtension.lowercase().removePrefix(".")
        val annotated = buildAnnotatedString {
            append(raw)
            when (ext) {
                "json" -> highlightJson(raw)
                "yaml", "yml" -> highlightYaml(raw)
                "sh", "bash", "zsh" -> highlightShell(raw)
                "py" -> highlightPython(raw)
                "env" -> highlightEnv(raw)
                "ini", "conf", "cfg" -> highlightIni(raw)
                else -> {
                    // Default generic styling: comments and strings
                    highlightGeneric(raw)
                }
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }

    private fun AnnotatedString.Builder.highlightJson(raw: String) {
        // Strings & Keys
        val stringRegex = Regex("\"(\\\\.|[^\"])*\"")
        stringRegex.findAll(raw).forEach { match ->
            val range = match.range
            val nextNonWhitespace = raw.substring(range.last + 1).trimStart().firstOrNull()
            if (nextNonWhitespace == ':') {
                addStyle(SpanStyle(color = SyntaxProperty, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
            } else {
                addStyle(SpanStyle(color = SyntaxString), range.first, range.last + 1)
            }
        }
        // Numbers
        Regex("\\b-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?\\b").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxNumber), it.range.first, it.range.last + 1)
        }
        // Booleans & null
        Regex("\\b(true|false|null)\\b").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxBoolean, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightYaml(raw: String) {
        // Comments
        Regex("#.*").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxComment), it.range.first, it.range.last + 1)
        }
        // Keys: word followed by colon
        Regex("(?m)^\\s*([\\w.-]+)\\s*:").findAll(raw).forEach { match ->
            val group = match.groups[1]
            if (group != null) {
                addStyle(SpanStyle(color = SyntaxProperty, fontWeight = FontWeight.SemiBold), group.range.first, group.range.last + 1)
            }
        }
        // Strings in quotes
        Regex("\"[^\"]*\"|'[^']*'").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxString), it.range.first, it.range.last + 1)
        }
        // Numbers
        Regex("\\b\\d+(\\.\\d+)?\\b").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxNumber), it.range.first, it.range.last + 1)
        }
        // Booleans
        Regex("(?i)\\b(true|false|yes|no|on|off)\\b").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxBoolean, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightShell(raw: String) {
        // Comments
        Regex("#.*").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxComment), it.range.first, it.range.last + 1)
        }
        // Keywords
        val keywords = "\\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|return|exit|export|source|sudo|local)\\b"
        Regex(keywords).findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
        // Strings
        Regex("\"(\\\\.|[^\"])*\"|'[^']*'").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxString), it.range.first, it.range.last + 1)
        }
        // Variables $VAR or ${VAR}
        Regex("\\$\\{?[A-Za-z0-9_]+\\}?").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxProperty), it.range.first, it.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightPython(raw: String) {
        // Comments
        Regex("#.*").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxComment), it.range.first, it.range.last + 1)
        }
        // Keywords
        val pyKeywords = "\\b(def|class|import|from|return|if|elif|else|for|while|try|except|finally|with|as|pass|break|continue|lambda|yield|raise|async|await|in|is|not|and|or)\\b"
        Regex(pyKeywords).findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
        // Booleans
        Regex("\\b(True|False|None)\\b").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxBoolean, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
        // Strings (triple quotes and standard quotes)
        Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxString), it.range.first, it.range.last + 1)
        }
        // Numbers
        Regex("\\b\\d+(\\.\\d+)?\\b").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxNumber), it.range.first, it.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightEnv(raw: String) {
        // Comments
        Regex("#.*").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxComment), it.range.first, it.range.last + 1)
        }
        // Keys before '='
        Regex("(?m)^\\s*([A-Za-z0-9_]+)\\s*=").findAll(raw).forEach { match ->
            val group = match.groups[1]
            if (group != null) {
                addStyle(SpanStyle(color = SyntaxProperty, fontWeight = FontWeight.Bold), group.range.first, group.range.last + 1)
            }
        }
        // Strings
        Regex("\"[^\"]*\"|'[^']*'").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxString), it.range.first, it.range.last + 1)
        }
    }

    private fun AnnotatedString.Builder.highlightIni(raw: String) {
        // Comments
        Regex("[;#].*").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxComment), it.range.first, it.range.last + 1)
        }
        // Sections [section]
        Regex("(?m)^\\[[^\\]]+\\]").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1)
        }
        // Keys before '='
        Regex("(?m)^\\s*([\\w.-]+)\\s*=").findAll(raw).forEach { match ->
            val group = match.groups[1]
            if (group != null) {
                addStyle(SpanStyle(color = SyntaxProperty, fontWeight = FontWeight.SemiBold), group.range.first, group.range.last + 1)
            }
        }
    }

    private fun AnnotatedString.Builder.highlightGeneric(raw: String) {
        // Hash comments
        Regex("#.*").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxComment), it.range.first, it.range.last + 1)
        }
        // Quoted strings
        Regex("\"[^\"]*\"|'[^']*'").findAll(raw).forEach {
            addStyle(SpanStyle(color = SyntaxString), it.range.first, it.range.last + 1)
        }
    }
}

/**
 * Detect language from filename or extension for syntax highlighting.
 */
fun detectLanguage(filename: String): String {
    val clean = filename.lowercase().substringAfterLast('/')
    if (clean.startsWith(".env")) return "env"
    val ext = clean.substringAfterLast('.', "")
    return when (ext) {
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "sh", "bash", "zsh" -> "shell"
        "py" -> "python"
        "env" -> "env"
        "ini", "conf", "cfg" -> "ini"
        else -> "text"
    }
}

/**
 * Permissions converter utilities for chmod rwx checkboxes and octal display.
 */
object ChmodUtils {
    /**
     * Converts a 9-element boolean array [ownerR, ownerW, ownerX, groupR, groupW, groupX, otherR, otherW, otherX]
     * to standard 3-digit octal permissions (e.g. 0755).
     */
    fun booleansToOctal(flags: BooleanArray): Int {
        if (flags.size < 9) return 0
        var octal = 0
        // Owner (flags 0, 1, 2)
        var owner = 0
        if (flags[0]) owner += 4
        if (flags[1]) owner += 2
        if (flags[2]) owner += 1
        octal += owner * 64 // shift 6 bits

        // Group (flags 3, 4, 5)
        var group = 0
        if (flags[3]) group += 4
        if (flags[4]) group += 2
        if (flags[5]) group += 1
        octal += group * 8 // shift 3 bits

        // Other (flags 6, 7, 8)
        var other = 0
        if (flags[6]) other += 4
        if (flags[7]) other += 2
        if (flags[8]) other += 1
        octal += other

        return octal
    }

    /**
     * Formats an octal integer as a standard 4-digit display string, e.g. "0755".
     */
    fun formatOctalString(octal: Int): String {
        val o = (octal shr 6) and 7
        val g = (octal shr 3) and 7
        val ot = octal and 7
        return "0$o$g$ot"
    }

    /**
     * Parses standard permissions string (e.g., "-rwxr-xr-x" or "rwxr-xr-x") into a 9-element BooleanArray.
     */
    fun parsePermissionsString(perms: String): BooleanArray {
        val flags = BooleanArray(9)
        val clean = if (perms.length == 10) perms.substring(1) else perms
        if (clean.length >= 9) {
            flags[0] = clean[0] == 'r'
            flags[1] = clean[1] == 'w'
            flags[2] = clean[2] == 'x' || clean[2] == 's'
            flags[3] = clean[3] == 'r'
            flags[4] = clean[4] == 'w'
            flags[5] = clean[5] == 'x' || clean[5] == 's'
            flags[6] = clean[6] == 'r'
            flags[7] = clean[7] == 'w'
            flags[8] = clean[8] == 'x' || clean[8] == 't'
        }
        return flags
    }

    /**
     * Converts permissions string (e.g. "-rw-r--r--") directly to octal integer.
     */
    fun permissionsStringToOctal(perms: String): Int {
        return booleansToOctal(parsePermissionsString(perms))
    }
}
