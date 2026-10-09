package nl.exitinflex.rittenregistratie.auto

/**
 * Een bron die de werkelijke kilometerstand van de auto kan opgeven.
 *
 * De registratie draait om tellerstanden. Komen die uit de auto zelf, dan is
 * er niets meer over te typen en loopt er ook geen verschil op tussen de
 * gps-meting en de teller. Lukt het ophalen niet, dan valt de app terug op de
 * meting plus een ijkmoment — de koppeling is een gemak, nooit een voorwaarde.
 */
interface Kilometerstandbron {

    val naam: String

    /** De stand in hele kilometers, of null als de bron niets kon leveren. */
    suspend fun huidigeStand(): Int?
}

/** Uitkomst van een proefverbinding, voor het instellingenscherm. */
sealed interface Proefuitkomst {
    data class Gelukt(val stand: Int) : Proefuitkomst
    data class Mislukt(val melding: String) : Proefuitkomst
}

/** Een sensor die de kilometerstand zou kunnen zijn, gevonden bij het zoeken. */
data class Sensorkeuze(
    val entiteit: String,
    val naam: String,
    val stand: Int,
)
