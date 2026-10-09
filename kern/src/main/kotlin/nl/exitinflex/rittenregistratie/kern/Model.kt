package nl.exitinflex.rittenregistratie.kern

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Het karakter van de rit, zoals artikel 3.13 lid 1 onderdeel c sub 5
 * Uitvoeringsregeling loonbelasting 2011 dat vraagt.
 *
 * Woon-werkverkeer staat er apart in omdat dezelfde kilometer fiscaal twee
 * kanten op valt: voor de loonheffing (de 500-kilometergrens bij een
 * Verklaring geen privégebruik auto) telt woon-werk als zakelijk, voor de
 * btw-correctie op het privégebruik telt hij als privé.
 */
@Serializable
enum class Ritsoort(val label: String) {
    ZAKELIJK("Zakelijk"),
    WOON_WERK("Woon-werk"),
    PRIVE("Privé");

    /** Telt deze rit mee voor de 500 privékilometers per kalenderjaar? */
    val priveVoorLoonheffing: Boolean get() = this == PRIVE

    /** Telt deze rit mee voor de btw-correctie privégebruik auto? */
    val priveVoorBtw: Boolean get() = this == PRIVE || this == WOON_WERK
}

/** Waar de gegevens vandaan komen; relevant voor de geloofwaardigheid van de administratie. */
@Serializable
enum class Ritbron {
    HANDMATIG,
    GPS,
    /** Stand rechtstreeks uit de auto overgenomen. */
    AUTO,
    IMPORT,
    /** Sluitpost na het ijken op de werkelijke tellerstand. */
    CORRECTIE,
}

@Serializable
data class Voertuig(
    val id: String,
    val merk: String,
    val type: String,
    val kenteken: String,
    @Serializable(with = LocalDateSerializer::class)
    val terBeschikkingVanaf: LocalDate,
    @Serializable(with = LocalDateNullableSerializer::class)
    val terBeschikkingTot: LocalDate? = null,
    /** Kilometerstand op het moment dat de auto ter beschikking kwam. */
    val beginstandKm: Int,
) {
    val omschrijving: String get() = "$merk $type ($kenteken)"

    fun terBeschikkingOp(datum: LocalDate): Boolean =
        !datum.isBefore(terBeschikkingVanaf) && (terBeschikkingTot == null || !datum.isAfter(terBeschikkingTot))
}

/**
 * Eén rit. De velden dekken één op één de gegevens die de rittenregistratie
 * volgens artikel 3.13 URLB 2011 moet bevatten, aangevuld met velden die een
 * controle van de Belastingdienst overleefbaar maken (doel, tijdstippen,
 * herkomst van de gegevens, vergrendeling).
 */
@Serializable
data class Rit(
    val id: String,
    val voertuigId: String,
    @Serializable(with = LocalDateSerializer::class)
    val datum: LocalDate,
    val beginstandKm: Int,
    val eindstandKm: Int,
    val beginadres: String,
    val eindadres: String,
    val soort: Ritsoort,
    /** Zakelijk doel of de bezochte relatie. Niet wettelijk verplicht, wel het eerste dat een inspecteur vraagt. */
    val doel: String = "",
    /** Verplicht in te vullen zodra er is afgeweken van de gebruikelijke route. */
    val afwijkendeRoute: String? = null,
    /** Privé-omrijkilometers binnen een zakelijke rit. Tellen mee voor de 500-kilometergrens. */
    val priveOmrijkilometers: Int = 0,
    @Serializable(with = LocalTimeNullableSerializer::class)
    val vertrektijd: LocalTime? = null,
    @Serializable(with = LocalTimeNullableSerializer::class)
    val aankomsttijd: LocalTime? = null,
    val opmerking: String? = null,
    val bron: Ritbron = Ritbron.HANDMATIG,
    /** Door GPS gemeten afstand, ter controle van de ingevoerde tellerstanden. */
    val gemetenKm: Double? = null,
    /**
     * Automatisch vastgelegde ritten worden meteen bewaard — een rit kwijtraken
     * maakt de reeks onsluitend — maar gelden pas als af zodra jij het karakter
     * en de standen hebt bevestigd.
     */
    val bevestigd: Boolean = true,
    @Serializable(with = InstantSerializer::class)
    val aangemaaktOp: Instant = Instant.EPOCH,
    @Serializable(with = InstantSerializer::class)
    val gewijzigdOp: Instant = Instant.EPOCH,
    /** Zodra een periode is afgesloten wordt de rit vergrendeld en alleen nog met een correctierit gewijzigd. */
    @Serializable(with = InstantNullableSerializer::class)
    val vergrendeldOp: Instant? = null,
) {
    val afstandKm: Int get() = eindstandKm - beginstandKm

    val vergrendeld: Boolean get() = vergrendeldOp != null

    /** Kilometers die meetellen voor de 500-kilometergrens van de loonheffing. */
    val priveKmLoonheffing: Int
        get() = if (soort.priveVoorLoonheffing) afstandKm else priveOmrijkilometers.coerceAtMost(afstandKm)

    /** Kilometers die als privé gelden voor de btw-correctie (inclusief woon-werkverkeer). */
    val priveKmBtw: Int
        get() = if (soort.priveVoorBtw) afstandKm else priveOmrijkilometers.coerceAtMost(afstandKm)

    val zakelijkeKmLoonheffing: Int get() = afstandKm - priveKmLoonheffing

    /** Verschil tussen de GPS-meting en de ingevoerde tellerstanden, in kilometers. */
    val meetverschilKm: Double? get() = gemetenKm?.let { afstandKm - it }
}
