package com.novaplay.tv.data.epg

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.GregorianCalendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.SAXParserFactory

/** One programme parsed from an XMLTV document; channelId is already normalized. */
data class XmltvProgramme(
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?,
)

/**
 * XMLTV timestamps like "20260712183000 +0200": local date-time digits plus an
 * optional UTC offset. Parsed by hand because java.time is unavailable below
 * API 26 and SimpleDateFormat cannot express the optional parts safely.
 */
object XmltvTimestamp {

    // yyyyMMdd[HH[mm[ss]]] — the spec allows right-truncation.
    private val OFFSET = Regex("""^([+-])(\d{2})(\d{2})$""")

    /** Epoch milliseconds in UTC, or null when the value is unusable. */
    fun parseToEpochMs(raw: String?): Long? {
        val trimmed = raw?.trim() ?: return null
        val space = trimmed.indexOf(' ')
        val digits = if (space >= 0) trimmed.substring(0, space) else trimmed
        val offsetText = if (space >= 0) trimmed.substring(space + 1).trim() else ""

        if (digits.length !in setOf(8, 10, 12, 14) || digits.any { !it.isDigit() }) return null
        val padded = digits.padEnd(14, '0')

        val year = padded.substring(0, 4).toInt()
        val month = padded.substring(4, 6).toInt()
        val day = padded.substring(6, 8).toInt()
        val hour = padded.substring(8, 10).toInt()
        val minute = padded.substring(10, 12).toInt()
        val second = padded.substring(12, 14).toInt()
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 59) return null

        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        return calendar.timeInMillis - parseOffsetMs(offsetText)
    }

    // Missing or unrecognized offsets fall back to UTC — the practical default,
    // since real-world XMLTV feeds carry explicit offsets almost universally.
    private fun parseOffsetMs(text: String): Long {
        val match = OFFSET.find(text) ?: return 0L
        val sign = if (match.groupValues[1] == "-") -1L else 1L
        val hours = match.groupValues[2].toLong()
        val minutes = match.groupValues[3].toLong()
        return sign * (hours * 60 + minutes) * 60_000L
    }
}

/**
 * Streaming SAX parser for XMLTV guide documents. Programmes are emitted to a
 * callback one at a time so multi-hundred-megabyte guides never materialize in
 * memory; individual dirty programmes are skipped, never fatal. SAX is used
 * (not XmlPullParser) so the same code runs on device and in JVM unit tests.
 */
@Singleton
class XmltvParser @Inject constructor() {

    /**
     * Parses [input] to completion, invoking [onProgramme] per valid programme.
     * Blocking — callers pick the dispatcher. Throws on unrecoverably malformed
     * XML; the caller treats that as a failed refresh and keeps existing data.
     */
    fun parse(input: InputStream, onProgramme: (XmltvProgramme) -> Unit) {
        val factory = SAXParserFactory.newInstance()
        // Guide documents never need DTDs or entities; disable everything
        // external so a hostile feed cannot trigger XXE-style fetches. Each
        // feature is best-effort because platform parsers support different sets.
        for (feature in HARDENING_FEATURES) {
            runCatching { factory.setFeature(feature.first, feature.second) }
        }
        val parser = factory.newSAXParser()
        parser.parse(InputSource(input), ProgrammeHandler(onProgramme))
    }

    // Collects direct <title>/<desc> text of each <programme> element and emits
    // on the closing tag when the channel, times and title are all usable.
    private class ProgrammeHandler(
        private val onProgramme: (XmltvProgramme) -> Unit,
    ) : DefaultHandler() {
        private var channel: String? = null
        private var startMs: Long? = null
        private var endMs: Long? = null
        private var title: String? = null
        private var description: String? = null
        private var insideProgramme = false
        private var currentText: StringBuilder? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (elementName(localName, qName)) {
                "programme" -> {
                    insideProgramme = true
                    channel = EpgChannelKey.normalize(attributes?.getValue("channel"))
                    startMs = XmltvTimestamp.parseToEpochMs(attributes?.getValue("start"))
                    endMs = XmltvTimestamp.parseToEpochMs(attributes?.getValue("stop"))
                    title = null
                    description = null
                }
                // First title/desc wins; multi-language feeds repeat the element.
                "title" -> if (insideProgramme && title == null) currentText = StringBuilder()
                "desc" -> if (insideProgramme && description == null) currentText = StringBuilder()
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            if (ch != null) currentText?.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (elementName(localName, qName)) {
                "title" -> currentText?.let { text ->
                    if (title == null) title = text.toString().trim().takeIf { it.isNotEmpty() }
                    currentText = null
                }
                "desc" -> currentText?.let { text ->
                    if (description == null) description = text.toString().trim().takeIf { it.isNotEmpty() }
                    currentText = null
                }
                "programme" -> {
                    val id = channel
                    val start = startMs
                    val end = endMs
                    val name = title
                    if (id != null && start != null && end != null && end > start && name != null) {
                        onProgramme(XmltvProgramme(id, start, end, name, description))
                    }
                    insideProgramme = false
                    currentText = null
                }
            }
        }

        // Namespace-aware parsers fill localName, plain ones only qName.
        private fun elementName(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()
    }

    private companion object {
        val HARDENING_FEATURES = listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )
    }
}
