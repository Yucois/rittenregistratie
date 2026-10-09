package nl.exitinflex.rittenregistratie.kern

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RitclassificatieTest {

    private val thuis = "Dorpsstraat 12, Bussum"
    private val werk = "Keizersgracht 100, Amsterdam"
    private val klant = "Industrieweg 7, Zwolle"

    private fun eerdereRit(eindadres: String, soort: Ritsoort, doel: String, dag: Int, stand: Int) =
        rit(
            id = "h$dag",
            datum = LocalDate.of(2026, 3, dag),
            begin = stand,
            eind = stand + 50,
            soort = soort,
            eindadres = eindadres,
            doel = doel,
        )

    @Test
    fun `tussen thuis en kantoor is het woon-werk en is de app er zeker van`() {
        val voorstel = Ritclassificatie.voorstel(thuis, werk, thuis, werk)
        assertEquals(Ritsoort.WOON_WERK, voorstel.soort)
        assertTrue(voorstel.zelfAfhandelen)
    }

    @Test
    fun `een bestemming die twee keer zakelijk was, wordt zelf afgehandeld`() {
        val geschiedenis = listOf(
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Bespreking Van Dijk", 3, 10_000),
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Bespreking Van Dijk", 10, 10_100),
        )
        val voorstel = Ritclassificatie.voorstel(thuis, klant, thuis, werk, geschiedenis)
        assertEquals(Ritsoort.ZAKELIJK, voorstel.soort)
        assertEquals("Bespreking Van Dijk", voorstel.doel)
        assertTrue(voorstel.zelfAfhandelen)
    }

    @Test
    fun `na een enkele eerdere rit is het een voorstel, geen zekerheid`() {
        val geschiedenis = listOf(eerdereRit(klant, Ritsoort.ZAKELIJK, "Kennismaking", 3, 10_000))
        val voorstel = Ritclassificatie.voorstel(thuis, klant, thuis, werk, geschiedenis)
        assertEquals(Ritclassificatie.Zekerheid.MIDDEL, voorstel.zekerheid)
        assertFalse(voorstel.zelfAfhandelen)
    }

    @Test
    fun `het meest recente doel wordt overgenomen`() {
        val geschiedenis = listOf(
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Oude afspraak", 3, 10_000),
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Kwartaaloverleg", 18, 10_200),
        )
        assertEquals("Kwartaaloverleg", Ritclassificatie.voorstel(thuis, klant, thuis, werk, geschiedenis).doel)
    }

    @Test
    fun `een bestemming die eerder verschillend is vastgelegd vraagt om een keuze`() {
        val geschiedenis = listOf(
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Bespreking", 3, 10_000),
            eerdereRit(klant, Ritsoort.PRIVE, "", 10, 10_100),
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Bespreking", 12, 10_200),
        )
        val voorstel = Ritclassificatie.voorstel(thuis, klant, thuis, werk, geschiedenis)
        assertEquals(Ritsoort.ZAKELIJK, voorstel.soort)
        assertEquals(Ritclassificatie.Zekerheid.LAAG, voorstel.zekerheid)
        assertFalse(voorstel.zelfAfhandelen)
    }

    @Test
    fun `onbevestigde ritten tellen niet mee als leermateriaal`() {
        val geschiedenis = listOf(
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Bespreking", 3, 10_000).copy(bevestigd = false),
            eerdereRit(klant, Ritsoort.ZAKELIJK, "Bespreking", 10, 10_100).copy(bevestigd = false),
        )
        val voorstel = Ritclassificatie.voorstel(thuis, klant, thuis, werk, geschiedenis, Ritsoort.PRIVE)
        assertEquals(Ritsoort.PRIVE, voorstel.soort)
        assertEquals(Ritclassificatie.Zekerheid.LAAG, voorstel.zekerheid)
    }

    @Test
    fun `een onbekende bestemming valt terug op de standaardkeuze`() {
        val voorstel = Ritclassificatie.voorstel(thuis, "Strandweg 1, Zandvoort", thuis, werk, emptyList(), Ritsoort.PRIVE)
        assertEquals(Ritsoort.PRIVE, voorstel.soort)
        assertFalse(voorstel.zelfAfhandelen)
    }

    @Test
    fun `kleine verschillen in schrijfwijze staan een match niet in de weg`() {
        assertTrue(Ritclassificatie.komtOvereen("Dorpsstraat 12", "dorpsstraat 12, Bussum"))
        assertFalse(Ritclassificatie.komtOvereen("Dorpsstraat 12", "Dorpsweg 12"))
        assertFalse(Ritclassificatie.komtOvereen("", "Dorpsstraat 12"))
    }
}
