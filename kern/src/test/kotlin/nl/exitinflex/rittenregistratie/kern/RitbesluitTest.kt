package nl.exitinflex.rittenregistratie.kern

import nl.exitinflex.rittenregistratie.kern.Ritbesluit.Actie
import nl.exitinflex.rittenregistratie.kern.Ritbesluit.Toestand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * De situaties uit de praktijk, één voor één. Elke test hier is een fout die
 * eerder in de app zat of een situatie waarin hij niet mag gebeuren.
 */
class RitbesluitTest {

    private val audi = "AA:BB:CC:DD:EE:01"
    private val hoortoestel = "11:22:33:44:55:66"
    private val andereAuto = "99:88:77:66:55:44"

    private fun toestand(
        verbonden: Boolean?,
        ritLoopt: Boolean = false,
        handmatig: Boolean = false,
        automatisch: Boolean = true,
        adres: String? = audi,
    ) = Toestand(
        automatischAan = automatisch,
        autoAdres = adres,
        autoVerbonden = verbonden,
        ritLoopt = ritLoopt,
        ritHandmatig = handmatig,
    )

    // --- De klachten van de gebruiker ---------------------------------------

    @Test
    fun `lopen zonder de Audi start geen rit`() {
        // De app wordt geopend of Android herstart hem; de Audi is niet verbonden.
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = false)))
    }

    @Test
    fun `een andere auto zegt niets over de Audi en start dus geen rit`() {
        val uitBericht = Ritbesluit.verbondenVolgensBericht(andereAuto, audi, verbonden = true)
        assertNull(uitBericht)
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = uitBericht)))
    }

    @Test
    fun `het hoortoestel dat verbindt start geen rit`() {
        val uitBericht = Ritbesluit.verbondenVolgensBericht(hoortoestel, audi, verbonden = true)
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = uitBericht)))
    }

    @Test
    fun `een herstart door Android zonder zekerheid over de auto doet niets`() {
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = null)))
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = null, ritLoopt = true)))
    }

    // --- Het normale verloop ---------------------------------------------------

    @Test
    fun `de Audi verbindt en er begint een rit`() {
        val uitBericht = Ritbesluit.verbondenVolgensBericht(audi, audi, verbonden = true)
        assertEquals(true, uitBericht)
        assertEquals(Actie.BEGIN, Ritbesluit.beslis(toestand(verbonden = uitBericht)))
    }

    @Test
    fun `hoofdletters in het adres maken niet uit`() {
        assertEquals(true, Ritbesluit.verbondenVolgensBericht(audi.lowercase(), audi, verbonden = true))
    }

    @Test
    fun `de Audi verbreekt en de rit wordt afgesloten`() {
        val uitBericht = Ritbesluit.verbondenVolgensBericht(audi, audi, verbonden = false)
        assertEquals(Actie.AFRONDEN, Ritbesluit.beslis(toestand(verbonden = uitBericht, ritLoopt = true)))
    }

    @Test
    fun `een ander apparaat dat verbreekt sluit de rit niet af`() {
        val uitBericht = Ritbesluit.verbondenVolgensBericht(hoortoestel, audi, verbonden = false)
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = uitBericht, ritLoopt = true)))
    }

    @Test
    fun `de dienst werd afgeschoten maar de Audi is er nog`() {
        assertEquals(Actie.GA_DOOR, Ritbesluit.beslis(toestand(verbonden = true, ritLoopt = true)))
    }

    @Test
    fun `de dienst werd afgeschoten en de Audi is inmiddels weg`() {
        assertEquals(Actie.AFRONDEN, Ritbesluit.beslis(toestand(verbonden = false, ritLoopt = true)))
    }

    // --- Handmatig en uitgeschakeld -------------------------------------------

    @Test
    fun `een handmatige rit wordt nooit door de bluetooth beeindigd`() {
        assertEquals(
            Actie.NIETS,
            Ritbesluit.beslis(toestand(verbonden = false, ritLoopt = true, handmatig = true)),
        )
    }

    @Test
    fun `met automatisch loggen uit begint er niets`() {
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = true, automatisch = false)))
    }

    @Test
    fun `automatisch loggen uitzetten tijdens een rit sluit die rit af`() {
        assertEquals(
            Actie.AFRONDEN,
            Ritbesluit.beslis(toestand(verbonden = true, ritLoopt = true, automatisch = false)),
        )
    }

    @Test
    fun `zonder gekozen auto begint er niets`() {
        assertEquals(Actie.NIETS, Ritbesluit.beslis(toestand(verbonden = true, adres = null)))
        assertNull(Ritbesluit.verbondenVolgensBericht(audi, null, verbonden = true))
    }
}
