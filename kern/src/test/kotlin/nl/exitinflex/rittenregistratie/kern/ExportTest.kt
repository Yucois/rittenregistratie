package nl.exitinflex.rittenregistratie.kern

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {

    private val ritten = listOf(
        rit("1", begin = 10_000, eind = 10_100),
        rit("2", begin = 10_100, eind = 10_160, soort = Ritsoort.PRIVE, doel = ""),
    )

    @Test
    fun `csv heeft een kop met alle verplichte velden`() {
        val csv = CsvExport.naarCsv(audi, ritten)
        val kop = csv.removePrefix("﻿").lineSequence().first()
        assertTrue(kop.startsWith("\"Datum\";\"Kenteken\";\"Beginstand (km)\""))
        assertTrue("\"Gereden route (indien afwijkend)\"" in kop)
        assertEquals(CsvExport.kolommen.size, kop.split(";").size)
    }

    @Test
    fun `csv bevat een regel per rit in tellerstandvolgorde`() {
        val regels = CsvExport.naarCsv(audi, ritten.reversed()).trim().split("\r\n")
        assertEquals(3, regels.size)
        assertTrue(regels[1].contains("\"10000\";\"10100\";\"100\""))
        assertTrue(regels[2].contains("\"10100\";\"10160\";\"60\""))
    }

    @Test
    fun `aanhalingstekens en regeleindes breken het csv niet`() {
        assertEquals("\"Klant \"\"De Vries\"\"\"", CsvExport.veld("Klant \"De Vries\""))
        assertEquals("\"regel een regel twee\"", CsvExport.veld("regel een\r\nregel twee"))
    }

    @Test
    fun `bestandsnaam bevat kenteken en jaar`() {
        assertEquals("rittenregistratie-X123YZ-2026.csv", CsvExport.bestandsnaam(audi, 2026))
    }

    @Test
    fun `html-rapport toont samenvatting en ritten`() {
        val controle = Rittencontrole.controleer(audi, ritten, LocalDate.of(2026, 6, 30))
        val html = HtmlRapport.naarHtml(audi, ritten, 2026, controle, LocalDate.of(2026, 6, 30))
        assertTrue("Audi RS e-tron GT (X-123-YZ)" in html)
        assertTrue("440 km over" in html) // 500 - 60 privékilometers
        assertTrue("Klantlaan 2, Utrecht" in html)
        assertTrue(html.trimStart().startsWith("<!doctype html>"))
    }

    @Test
    fun `html ontsnapt aan gevaarlijke tekens`() {
        assertEquals("Jansen &amp; Co &lt;b&gt;", HtmlRapport.esc("Jansen & Co <b>"))
    }

    @Test
    fun `backup gaat heen en weer zonder verlies`() {
        val backup = Backup(gemaaktOp = Instant.parse("2026-06-30T10:00:00Z"), voertuigen = listOf(audi), ritten = ritten)
        val terug = BackupOpslag.lees(BackupOpslag.schrijf(backup))
        assertEquals(backup, terug)
        assertEquals("rittenregistratie-backup-2026-06-30.json", BackupOpslag.bestandsnaam(backup.gemaaktOp))
    }
}
