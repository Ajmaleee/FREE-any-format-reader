package com.example.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

class StructuredTextHandler(private val context: Context) {

    suspend fun openJson(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val raw = readStream(uri, isAsset, assetPath)
                ?: return@withContext DocumentContent.Error("Unable to open file. Permission may have expired.", isAccessRevoked = true)

            // Try formatting JSON or JSONL
            val trimmed = raw.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    val tokener = JSONTokener(trimmed)
                    val value = tokener.nextValue()
                    val formatted = when (value) {
                        is JSONObject -> value.toString(2)
                        is JSONArray -> value.toString(2)
                        else -> trimmed
                    }
                    DocumentContent.StructuredText(
                        formattedText = formatted,
                        rawText = raw,
                        isJson = true,
                        isValid = true
                    )
                } catch (e: Exception) {
                    DocumentContent.StructuredText(
                        formattedText = raw,
                        rawText = raw,
                        isJson = true,
                        isValid = false,
                        errorHint = "JSON syntax warning: ${e.localizedMessage}"
                    )
                }
            } else {
                // Potential JSONL
                val formattedLines = mutableListOf<String>()
                var anyValid = false
                trimmed.lines().forEach { line ->
                    val lineTrim = line.trim()
                    if (lineTrim.startsWith("{")) {
                        try {
                            formattedLines.add(JSONObject(lineTrim).toString(2))
                            anyValid = true
                        } catch (e: Exception) {
                            formattedLines.add(lineTrim)
                        }
                    } else {
                        formattedLines.add(lineTrim)
                    }
                }
                DocumentContent.StructuredText(
                    formattedText = formattedLines.joinToString("\n---\n"),
                    rawText = raw,
                    isJson = true,
                    isValid = anyValid,
                    errorHint = if (!anyValid) "Plain text or non-standard JSON" else null
                )
            }
        } catch (e: Exception) {
            DocumentContent.Error("Error parsing JSON: ${e.localizedMessage}")
        }
    }

    suspend fun openXml(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val raw = readStream(uri, isAsset, assetPath)
                ?: return@withContext DocumentContent.Error("Unable to open XML file. Permission may have expired.", isAccessRevoked = true)

            try {
                val transformer = TransformerFactory.newInstance().newTransformer().apply {
                    setOutputProperty(OutputKeys.INDENT, "yes")
                    setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                    setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                }
                val result = StreamResult(StringWriter())
                val source = StreamSource(StringReader(raw))
                transformer.transform(source, result)
                val formatted = result.writer.toString()
                DocumentContent.StructuredText(
                    formattedText = formatted,
                    rawText = raw,
                    isJson = false,
                    isValid = true
                )
            } catch (e: Exception) {
                DocumentContent.StructuredText(
                    formattedText = raw,
                    rawText = raw,
                    isJson = false,
                    isValid = false,
                    errorHint = "XML formatting note: ${e.localizedMessage}"
                )
            }
        } catch (e: Exception) {
            DocumentContent.Error("Error reading XML: ${e.localizedMessage}")
        }
    }

    suspend fun openPlainText(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val raw = readStream(uri, isAsset, assetPath)
                ?: return@withContext DocumentContent.Error("Unable to open text file. Permission may have expired.", isAccessRevoked = true)

            val lines = raw.lines()
            DocumentContent.Text(
                lines = lines,
                totalLines = lines.size,
                rawText = raw
            )
        } catch (e: Exception) {
            DocumentContent.Error("Error reading text: ${e.localizedMessage}")
        }
    }

    private fun readStream(uri: Uri, isAsset: Boolean, assetPath: String?): String? {
        val stream = if (isAsset && assetPath != null) {
            context.assets.open(assetPath)
        } else {
            context.contentResolver.openInputStream(uri) ?: return null
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}
