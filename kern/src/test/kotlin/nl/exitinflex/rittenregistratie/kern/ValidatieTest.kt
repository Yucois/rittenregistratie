package nl.exitinflex.rittenregistratie.kern

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatieTest {

    private fun codes(rit: Rit, voertuig: Voertuig? = audi) =
        Rittencontrole.controleerRit(rit, voertuig).map { it.code }

    @Test
    fun `een volledige rit levert geen bevindingen op`() {
        assertEquals(emptyList(), codes(rit("1", begin = 10_000, eind = 10_100)))
    }

    @Test
    fun `de eindstand moet hoger zijn dan de beginstand`() {
        assertTrue("stand.oplopend" in codes(rit("1", begin = 10_100, eind = 10_100)))
    }

    @Test
    fun `adressen zijn verplicht`() {
        val kaal = rit("1", begin = 10_000, eind = 10_010, beginadres = " ", eindadres = "")
        assertEquals(listOf("adres.begin", "adres.eind"), codes(kaal))
    }

    @Test
    fun `omrijkilometers vragen om een routebeschrijving`() {
        assertTrue("route.afwijkend" in codes(rit("1", begin = 10_000, eind = 10_100, omrij = 15)))
        assertTrue("route.afwijkend" !in codes(rit("1", begin = 10_000, eind = 10_100, omrij = 15, route = "Via Vianen")))
    }

    @Test
    fun `omrijkilometers passen binnen de rit`() {
        assertTrue("omrij.tegroot" in codes(rit("1", begin = 10_000, eind = 10_020, omrij = 30, route = "Omweg")))
    }

    @Test
    fun `een zakelijke rit zonder doel geeft een waarschuwing`() {
        assertEquals(listOf("doel.leeg"), codes(rit("1", begin = 10_000, eind = 10_010, doel = "")))
    }

    @Test
    fun `een rit buiten de terbeschikkingstelling is een fout`() {
        val vorigJaar = rit("1", datum = LocalDate.of(2025, 12, 30), begin = 10_000, eind = 10_010)
        assertTrue("datum.buitenperiode" in codes(vorigJaar))
    }

    @Test
    fun `een groot verschil met de gps-meting geeft een waarschuwing`() {
        assertTrue("meting.afwijking" in codes(rit("1", begin = 10_000, eind = 10_100, gemeten = 60.0)))
        assertTrue("meting.afwijking" !in codes(rit("1", begin = 10_000, eind = 10_100, gemeten = 98.0)))
    }

    @Test
    fun `zonder voertuig wordt de periode niet gecontroleerd`() {
        val vorigJaar = rit("1", datum = LocalDate.of(2025, 12, 30), begin = 10_000, eind = 10_010)
        assertEquals(emptyList(), codes(vorigJaar, voertuig = null))
    }
}
