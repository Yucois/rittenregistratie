package nl.exitinflex.rittenregistratie.kern

/**
 * Bepaalt zelf wat een rit is, op basis van je vaste adressen en van wat je
 * eerder bij dezelfde bestemming hebt vastgelegd.
 *
 * Het doel is dat je op den duur niets meer hoeft te kiezen: een bestemming
 * die je twee keer als zakelijk hebt bevestigd, wordt de derde keer vanzelf
 * zakelijk, met hetzelfde doel erbij. Alleen een onbekende bestemming vraagt
 * nog om een tik.
 *
 * De zekerheid bepaalt of de app het zelf afhandelt. Dat is bewust streng:
 * automatisch vastleggen mag nooit betekenen dat er iets in de administratie
 * komt wat je niet zou hebben ingevuld.
 */
object Ritclassificatie {

    enum class Zekerheid { HOOG, MIDDEL, LAAG }

    data class Voorstel(
        val soort: Ritsoort,
        val doel: String,
        val zekerheid: Zekerheid,
        val reden: String,
    ) {
        /** Bij deze zekerheid handelt de app de rit zelf af; je krijgt hem alleen te zien. */
        val zelfAfhandelen: Boolean get() = zekerheid == Zekerheid.HOOG
    }

    /** Vanaf dit aantal eerdere, bevestigde ritten naar dezelfde plek is de app er zeker van. */
    private const val ZEKER_VANAF = 2

    fun voorstel(
        beginadres: String,
        eindadres: String,
        thuisadres: String,
        werkadres: String,
        geschiedenis: List<Rit> = emptyList(),
        standaard: Ritsoort = Ritsoort.ZAKELIJK,
    ): Voorstel {
        val vanThuis = komtOvereen(beginadres, thuisadres)
        val vanWerk = komtOvereen(beginadres, werkadres)
        val naarThuis = komtOvereen(eindadres, thuisadres)
        val naarWerk = komtOvereen(eindadres, werkadres)
        if ((vanThuis && naarWerk) || (vanWerk && naarThuis)) {
            return Voorstel(
                soort = Ritsoort.WOON_WERK,
                doel = "",
                zekerheid = Zekerheid.HOOG,
                reden = "Tussen je thuisadres en je kantooradres.",
            )
        }

        // Alleen bevestigde ritten zijn leermateriaal; van een onbevestigde rit
        // staat immers nog niet vast dat hij klopt.
        val eerder = geschiedenis
            .filter { it.bevestigd && komtOvereen(it.eindadres, eindadres) }
            .sortedWith(compareByDescending<Rit> { it.datum }.thenByDescending { it.beginstandKm })

        if (eerder.isNotEmpty()) {
            val soorten = eerder.map { it.soort }.distinct()
            if (soorten.size == 1) {
                val soort = soorten.first()
                return Voorstel(
                    soort = soort,
                    doel = eerder.firstOrNull { it.doel.isNotBlank() }?.doel.orEmpty(),
                    zekerheid = if (eerder.size >= ZEKER_VANAF) Zekerheid.HOOG else Zekerheid.MIDDEL,
                    reden = "Deze bestemming heb je ${eerder.size}× als ${soort.label.lowercase()} vastgelegd.",
                )
            }
            val vaakste = eerder.groupingBy { it.soort }.eachCount().maxByOrNull { it.value }!!.key
            return Voorstel(
                soort = vaakste,
                doel = "",
                zekerheid = Zekerheid.LAAG,
                reden = "Deze bestemming heb je eerder verschillend vastgelegd.",
            )
        }

        return Voorstel(
            soort = standaard,
            doel = "",
            zekerheid = Zekerheid.LAAG,
            reden = "Onbekende bestemming; de standaardkeuze is ingevuld.",
        )
    }

    /**
     * Adressen uit een geocoder en handmatig ingetypte adressen schrijven zelden
     * precies hetzelfde. Vergelijken gebeurt daarom op de eerste woorden —
     * straat en huisnummer — zonder hoofdletters en leestekens.
     */
    fun komtOvereen(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val links = sleutel(a)
        val rechts = sleutel(b)
        if (links.isEmpty() || rechts.isEmpty()) return false
        return links == rechts || links.startsWith(rechts) || rechts.startsWith(links)
    }

    private fun sleutel(adres: String): String = adres
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(' ')
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString(" ")
}
