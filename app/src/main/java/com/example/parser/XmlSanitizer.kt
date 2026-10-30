package com.example.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object XmlSanitizer {

    // Matches any '&' that is NOT immediately followed by a standard XML entity or numeric character reference
    private val BARE_AMPERSAND_REGEX = Regex("&(?!(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)", RegexOption.IGNORE_CASE)

    fun sanitize(xml: String): String {
        if (!xml.contains('&')) return xml
        return BARE_AMPERSAND_REGEX.replace(xml, "&amp;")
    }

    fun sanitize(bytes: ByteArray): String {
        val raw = String(bytes, Charsets.UTF_8)
        return sanitize(raw)
    }

    fun createParser(xml: String): XmlPullParser {
        val sanitized = sanitize(xml)
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(sanitized))
        return parser
    }

    fun createParser(bytes: ByteArray): XmlPullParser {
        val sanitized = sanitize(bytes)
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(sanitized))
        return parser
    }

    fun unescapeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }
}
