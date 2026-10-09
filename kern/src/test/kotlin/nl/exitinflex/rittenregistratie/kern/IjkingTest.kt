package nl.exitinflex.rittenregistratie.kern

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IjkingTest {

    private val vandaag = LocalDate.of(2026, 6, 30)
    private val nu = Instant.parse("2026-06-30T12:00:00Z")

    @Test
    fun `een gelijke stand levert geen correctie op`() {
        assertEquals(Ijking.Uitkomst.Gelijk, Ijking.ijk(audi, 12_000, 12_000, vandaag, "c1", nu))
    }

    @Test
    fun `het verschil komt er als aparte correctierit in`() {
        val uitkomst = Ijking.ijk(audi, 12_000, 12_085, vandaag, "c1", nu)
        val correctie = assertIs<Ijking.Uitkomst.Correctie>(uitkomst)
        assertEquals(85, correctie.verschilKm)
        assertEquals(12_000, correctie.rit.beginstandKm)
        assertEquals(12_085, correctie.rit.eindstandKm)
        assertEquals(Ritsoort.PRIVE, correctie.rit.soort)
        assertEquals(Ritbron.CORRECTIE, correctie.rit.bron)
        assertFalse(correctie.rit.bevestigd)
    }

    @Test
    fun `een correctierit maakt de reeks weer sluitend`() {
        val ritten = listOf(rit("1", begin = 10_000, eind = 10_500))
        val correctie = assertIs<Ijking.Uitkomst.Correctie>(
            Ijking.ijk(audi, 10_500, 10_560, vandaag, "c1", nu),
        )
        val resultaat = Rittencontrole.controleer(audi, ritten + correctie.rit, vandaag)
        assertTrue(resultaat.fouten.isEmpty(), resultaat.fouten.joinToString { it.melding })
        assertEquals(0, resultaat.jaar(2026)!!.nietVerantwoordeKm)
        // De niet-vastgelegde kilometers tellen nu als privé, zoals een controle ze ook telt.
        assertEquals(60, resultaat.jaar(2026)!!.priveKm)
    }

    @Test
    fun `een correctierit zonder toelichting is een fout`() {
        val correctie = assertIs<Ijking.Uitkomst.Correctie>(
            Ijking.ijk(audi, 10_500, 10_560, vandaag, "c1", nu),
        )
        assertTrue(Rittencontrole.controleerRit(correctie.rit.copy(opmerking = null), audi)
            .any { it.code == "correctie.zondertoelichting" })
        // Met toelichting blijft alleen de bevestiging over.
        assertEquals(
            listOf("rit.onbevestigd"),
            Rittencontrole.controleerRit(correctie.rit, audi).map { it.code },
        )
    }

    @Test
    fun `een lagere tellerstand dan de registratie kan niet`() {
        assertIs<Ijking.Uitkomst.Onmogelijk>(Ijking.ijk(audi, 12_000, 11_900, vandaag, "c1", nu))
    }

    @Test
    fun `ijken is overbodig zodra de standen uit de auto komen`() {
        assertFalse(Ijking.ijkenNodig(true, null, 5_000, vandaag))
    }

    @Test
    fun `zonder eerdere ijking is honderd kilometer genoeg aanleiding`() {
        assertFalse(Ijking.ijkenNodig(false, null, 40, vandaag))
        assertTrue(Ijking.ijkenNodig(false, null, 120, vandaag))
    }

    @Test
    fun `na een maand of duizend kilometer is het weer tijd`() {
        val vorige = LocalDate.of(2026, 6, 20)
        assertFalse(Ijking.ijkenNodig(false, vorige, 300, vandaag))
        assertTrue(Ijking.ijkenNodig(false, vorige, 1_200, vandaag))
        assertTrue(Ijking.ijkenNodig(false, LocalDate.of(2026, 5, 1), 300, vandaag))
    }
}
