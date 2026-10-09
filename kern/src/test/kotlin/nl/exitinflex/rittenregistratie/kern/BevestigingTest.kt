package nl.exitinflex.rittenregistratie.kern

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BevestigingTest {

    @Test
    fun `een onbevestigde rit levert een waarschuwing op, geen fout`() {
        val automatisch = rit("1", begin = 10_000, eind = 10_100).copy(bevestigd = false)
        val bevindingen = Rittencontrole.controleerRit(automatisch, audi)
        assertEquals(listOf("rit.onbevestigd"), bevindingen.map { it.code })
        assertTrue(bevindingen.all { it.ernst == Ernst.WAARSCHUWING })
    }

    @Test
    fun `onbevestigde ritten worden per jaar geteld maar tellen wel mee in de kilometers`() {
        val resultaat = Rittencontrole.controleer(
            audi,
            listOf(
                rit("1", begin = 10_000, eind = 10_100),
                rit("2", begin = 10_100, eind = 10_150).copy(bevestigd = false),
            ),
            LocalDate.of(2026, 6, 30),
        )
        val jaar = resultaat.jaar(2026)!!
        assertEquals(1, jaar.onbevestigdeRitten)
        assertEquals(150, jaar.totaalKm)
        // Een rit die nog bevestigd moet worden mag de reeks niet onsluitend maken.
        assertTrue(resultaat.sluitend)
    }
}
