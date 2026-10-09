package nl.exitinflex.rittenregistratie.kern

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RitTest {

    @Test
    fun `afstand is het verschil tussen de tellerstanden`() {
        assertEquals(42, rit("a", begin = 10_000, eind = 10_042).afstandKm)
    }

    @Test
    fun `woon-werk telt zakelijk voor de loonheffing en prive voor de btw`() {
        val woonWerk = rit("a", begin = 10_000, eind = 10_030, soort = Ritsoort.WOON_WERK)
        assertEquals(0, woonWerk.priveKmLoonheffing)
        assertEquals(30, woonWerk.priveKmBtw)
    }

    @Test
    fun `een priverit telt beide kanten op als prive`() {
        val prive = rit("a", begin = 10_000, eind = 10_030, soort = Ritsoort.PRIVE)
        assertEquals(30, prive.priveKmLoonheffing)
        assertEquals(30, prive.priveKmBtw)
        assertEquals(0, prive.zakelijkeKmLoonheffing)
    }

    @Test
    fun `omrijkilometers binnen een zakelijke rit tellen als prive`() {
        val zakelijk = rit("a", begin = 10_000, eind = 10_100, omrij = 12, route = "Via Breukelen, kind opgehaald")
        assertEquals(12, zakelijk.priveKmLoonheffing)
        assertEquals(88, zakelijk.zakelijkeKmLoonheffing)
    }

    @Test
    fun `meetverschil vergelijkt tellerstanden met de gps-meting`() {
        assertEquals(1.5, rit("a", begin = 10_000, eind = 10_100, gemeten = 98.5).meetverschilKm!!, 0.001)
    }

    @Test
    fun `een vergrendelde rit is als zodanig herkenbaar`() {
        val open = rit("a", begin = 10_000, eind = 10_010)
        assertFalse(open.vergrendeld)
        assertTrue(open.copy(vergrendeldOp = java.time.Instant.now()).vergrendeld)
    }
}
