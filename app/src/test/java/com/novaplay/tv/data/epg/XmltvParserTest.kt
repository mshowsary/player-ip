package com.novaplay.tv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class XmltvParserTest {

    // ---- XmltvTimestamp ----

    @Test
    fun parsesUtcTimestampWithExplicitZeroOffset() {
        // 2026-07-12 18:30:00 UTC = 20,646 days + 66,600 s after the epoch.
        assertEquals(1_783_881_000_000L, XmltvTimestamp.parseToEpochMs("20260712183000 +0000"))
    }

    @Test
    fun appliesPositiveAndNegativeOffsets() {
        val utc = XmltvTimestamp.parseToEpochMs("20260712183000 +0000")!!
        // 20:30 at +0200 is the same instant as 18:30 UTC.
        assertEquals(utc, XmltvTimestamp.parseToEpochMs("20260712203000 +0200"))
        // 13:00 at -0530 is 18:30 UTC.
        assertEquals(utc, XmltvTimestamp.parseToEpochMs("20260712130000 -0530"))
    }

    @Test
    fun missingOffsetFallsBackToUtc() {
        assertEquals(
            XmltvTimestamp.parseToEpochMs("20260712183000 +0000"),
            XmltvTimestamp.parseToEpochMs("20260712183000"),
        )
    }

    @Test
    fun acceptsTruncatedTimestamps() {
        val midnight = XmltvTimestamp.parseToEpochMs("20260712000000 +0000")!!
        assertEquals(midnight, XmltvTimestamp.parseToEpochMs("20260712"))
        assertEquals(midnight + 18L * 60 * 60 * 1000, XmltvTimestamp.parseToEpochMs("2026071218"))
        assertEquals(midnight + (18L * 60 + 30) * 60 * 1000, XmltvTimestamp.parseToEpochMs("202607121830"))
    }

    @Test
    fun rejectsGarbageTimestamps() {
        assertNull(XmltvTimestamp.parseToEpochMs(null))
        assertNull(XmltvTimestamp.parseToEpochMs(""))
        assertNull(XmltvTimestamp.parseToEpochMs("not-a-time"))
        assertNull(XmltvTimestamp.parseToEpochMs("2026"))
        assertNull(XmltvTimestamp.parseToEpochMs("20261399000000")) // month 13, day 99
        assertNull(XmltvTimestamp.parseToEpochMs("20260712250000")) // hour 25
    }

    // ---- XmltvParser ----

    @Test
    fun parsesProgrammesWithNormalizedChannelIds() {
        val programmes = parseAll(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="France24.EN"><display-name>France 24</display-name></channel>
              <programme start="20260712180000 +0000" stop="20260712190000 +0000" channel="France24.EN">
                <title lang="en">Evening News</title>
                <desc lang="en">Headlines &amp; analysis.</desc>
              </programme>
              <programme start="20260712190000 +0000" stop="20260712200000 +0000" channel="France24.EN">
                <title>The Debate</title>
              </programme>
            </tv>
            """,
        )

        assertEquals(2, programmes.size)
        val first = programmes[0]
        assertEquals("france24.en", first.channelId)
        assertEquals("Evening News", first.title)
        assertEquals("Headlines & analysis.", first.description)
        assertTrue(first.endMs > first.startMs)
        assertNull(programmes[1].description)
    }

    @Test
    fun takesFirstTitleOfMultiLanguageProgrammes() {
        val programmes = parseAll(
            """
            <tv>
              <programme start="20260712180000 +0000" stop="20260712190000 +0000" channel="c1">
                <title lang="fr">Le Journal</title>
                <title lang="en">The News</title>
              </programme>
            </tv>
            """,
        )

        assertEquals(1, programmes.size)
        assertEquals("Le Journal", programmes[0].title)
    }

    @Test
    fun skipsDirtyProgrammesWithoutAbortingTheDocument() {
        val programmes = parseAll(
            """
            <tv>
              <programme start="garbage" stop="20260712190000 +0000" channel="c1">
                <title>Bad start</title>
              </programme>
              <programme start="20260712190000 +0000" stop="20260712180000 +0000" channel="c1">
                <title>Ends before it starts</title>
              </programme>
              <programme start="20260712180000 +0000" stop="20260712190000 +0000" channel="  ">
                <title>Blank channel</title>
              </programme>
              <programme start="20260712180000 +0000" stop="20260712190000 +0000" channel="c1">
                <title>   </title>
              </programme>
              <programme start="20260712180000 +0000" stop="20260712190000 +0000" channel="c1">
                <title>Survivor</title>
              </programme>
            </tv>
            """,
        )

        assertEquals(1, programmes.size)
        assertEquals("Survivor", programmes[0].title)
    }

    @Test
    fun ignoresUnrelatedElementsInsideProgrammes() {
        val programmes = parseAll(
            """
            <tv>
              <programme start="20260712180000 +0000" stop="20260712190000 +0000" channel="c1">
                <title>Film Night</title>
                <category lang="en">Movie</category>
                <sub-title>Part one</sub-title>
                <credits><actor>Someone</actor></credits>
              </programme>
            </tv>
            """,
        )

        assertEquals(1, programmes.size)
        assertEquals("Film Night", programmes[0].title)
    }

    private fun parseAll(document: String): List<XmltvProgramme> {
        val collected = mutableListOf<XmltvProgramme>()
        val bytes = document.trimIndent().trim().toByteArray(Charsets.UTF_8)
        XmltvParser().parse(ByteArrayInputStream(bytes)) { collected += it }
        return collected
    }
}
