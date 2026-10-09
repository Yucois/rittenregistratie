package nl.exitinflex.rittenregistratie.kern

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControleTest {

    private val peildatum = LocalDate.of(2026, 6, 30)

    @Test
    fun `een aansluitende reeks is sluitend`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(
                rit("1", begin = 10_000, eind = 10_120),
                rit("2", begin = 10_120, eind = 10_200, soort = Ritsoort.WOON_WERK, doel = ""),
            ),
            peildatum,
        )
        assertTrue(resultaat.sluitend, resultaat.bevindingen.joinToString { it.melding })
        assertEquals(10_200, resultaat.laatsteStand)
    }

    @Test
    fun `een gat tussen twee ritten is een fout en telt als prive`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(
                rit("1", begin = 10_000, eind = 10_120),
                rit("2", begin = 10_150, eind = 10_200),
            ),
            peildatum,
        )
        assertFalse(resultaat.sluitend)
        assertEquals(listOf("hiaat"), resultaat.fouten.map { it.code })
        val jaar = resultaat.jaar(2026)!!
        assertEquals(30, jaar.nietVerantwoordeKm)
        assertEquals(0, jaar.priveKmLoonheffing)
        assertEquals(30, jaar.priveKmLoonheffingWorstCase)
    }

    @Test
    fun `een gat tussen de beginstand van de auto en de eerste rit valt op`() {
        val resultaat = Rittencontrole.controleer(audi, listOf(rit("1", begin = 10_040, eind = 10_100)), peildatum)
        assertEquals(listOf("hiaat.start"), resultaat.fouten.map { it.code })
        assertEquals(40, resultaat.jaar(2026)!!.nietVerantwoordeKm)
    }

    @Test
    fun `overlappende tellerstanden worden gemeld`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(
                rit("1", begin = 10_000, eind = 10_120),
                rit("2", begin = 10_100, eind = 10_200),
            ),
            peildatum,
        )
        assertEquals(listOf("overlap"), resultaat.fouten.map { it.code })
    }

    @Test
    fun `jaartotalen scheiden loonheffing en btw`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(
                rit("1", begin = 10_000, eind = 10_100),
                rit("2", begin = 10_100, eind = 10_140, soort = Ritsoort.WOON_WERK),
                rit("3", begin = 10_140, eind = 10_200, soort = Ritsoort.PRIVE, doel = ""),
                rit("4", begin = 10_200, eind = 10_300, omrij = 10, route = "Via Zeist"),
            ),
            peildatum,
        )
        val jaar = resultaat.jaar(2026)!!
        assertEquals(300, jaar.totaalKm)
        assertEquals(200, jaar.zakelijkKm)
        assertEquals(40, jaar.woonWerkKm)
        assertEquals(60, jaar.priveKm)
        assertEquals(10, jaar.priveOmrijKm)
        assertEquals(70, jaar.priveKmLoonheffing)
        assertEquals(110, jaar.priveKmBtw)
        assertEquals(430, jaar.restantTot500)
        assertFalse(jaar.grensOverschreden)
        assertEquals(0.3667, jaar.btwPriveAandeel, 0.001)
    }

    @Test
    fun `boven de 500 privekilometers volgt een fout`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(rit("1", begin = 10_000, eind = 10_600, soort = Ritsoort.PRIVE, doel = "")),
            peildatum,
        )
        assertTrue(resultaat.fouten.any { it.code == "grens.overschreden" })
        assertEquals(-100, resultaat.jaar(2026)!!.restantTot500)
    }

    @Test
    fun `de prognose rekent het jaar uit op basis van het verstreken deel`() {
        val jaar = Jaaroverzicht(
            jaar = 2026,
            aantalRitten = 1,
            totaalKm = 200,
            zakelijkKm = 0,
            woonWerkKm = 0,
            priveKm = 200,
            priveOmrijKm = 0,
            nietVerantwoordeKm = 0,
        )
        // 1 juli: 182 van de 365 dagen verstreken, dus ruim het dubbele.
        assertEquals(401, jaar.prognosePriveKm(LocalDate.of(2026, 7, 1)))
        assertEquals(200, jaar.prognosePriveKm(LocalDate.of(2027, 2, 1)))
    }

    @Test
    fun `ritten van een ander voertuig tellen niet mee`() {
        val vreemd = rit("x", begin = 90_000, eind = 90_100).copy(voertuigId = "auto-2")
        val resultaat = Rittencontrole.controleer(audi, listOf(rit("1", begin = 10_000, eind = 10_100), vreemd), peildatum)
        assertTrue(resultaat.sluitend)
        assertEquals(100, resultaat.jaar(2026)!!.totaalKm)
    }

    @Test
    fun `gaten worden per jaar toegerekend aan de rit erna`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(
                rit("1", datum = LocalDate.of(2026, 12, 30), begin = 10_000, eind = 10_100),
                rit("2", datum = LocalDate.of(2027, 1, 3), begin = 10_180, eind = 10_200),
            ),
            LocalDate.of(2027, 2, 1),
        )
        assertEquals(0, resultaat.jaar(2026)!!.nietVerantwoordeKm)
        assertEquals(80, resultaat.jaar(2027)!!.nietVerantwoordeKm)
    }
}
