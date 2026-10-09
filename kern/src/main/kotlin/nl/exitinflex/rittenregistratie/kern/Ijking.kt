package nl.exitinflex.rittenregistratie.kern

import java.time.Instant
import java.time.LocalDate

/**
 * Het ijkmoment: de registratie naast de werkelijke kilometerstand leggen.
 *
 * Zolang de standen uit een gps-meting komen loopt er onvermijdelijk verschil
 * op — een paar procent per rit is normaal. Dat verschil wegwerken door de
 * standen stilletjes bij te stellen zou de administratie onbetrouwbaar maken.
 * In plaats daarvan komt het verschil er als aparte correctierit in: zichtbaar,
 * verklaarbaar, en de reeks blijft sluitend.
 *
 * De correctie geldt als privé. Dat is streng, en met opzet: kilometers die
 * niet zijn vastgelegd, merkt de Belastingdienst ook als privé aan. Wie ze
 * zakelijk wil verantwoorden, moet erbij zetten waardoor ze niet geregistreerd
 * zijn — en dan kan de soort met de hand worden gewijzigd.
 */
object Ijking {

    sealed interface Uitkomst {
        /** De registratie loopt gelijk met de teller; er valt niets te corrigeren. */
        data object Gelijk : Uitkomst

        /** Er zijn kilometers niet vastgelegd; deze rit dekt ze af. */
        data class Correctie(val rit: Rit, val verschilKm: Int) : Uitkomst

        /** De opgegeven stand kan niet kloppen. */
        data class Onmogelijk(val melding: String) : Uitkomst
    }

    fun ijk(
        voertuig: Voertuig,
        laatsteStand: Int,
        werkelijkeStand: Int,
        datum: LocalDate,
        ritId: String,
        nu: Instant = Instant.now(),
    ): Uitkomst {
        val verschil = werkelijkeStand - laatsteStand
        return when {
            verschil == 0 -> Uitkomst.Gelijk
            verschil < 0 -> Uitkomst.Onmogelijk(
                "De teller staat op $werkelijkeStand km, lager dan de laatst geregistreerde stand " +
                    "($laatsteStand km). Controleer of je de stand goed hebt overgenomen.",
            )
            else -> Uitkomst.Correctie(
                rit = Rit(
                    id = ritId,
                    voertuigId = voertuig.id,
                    datum = datum,
                    beginstandKm = laatsteStand,
                    eindstandKm = werkelijkeStand,
                    beginadres = "",
                    eindadres = "",
                    soort = Ritsoort.PRIVE,
                    doel = "",
                    opmerking = "Sluitpost na ijking op de werkelijke kilometerstand van $werkelijkeStand km.",
                    bron = Ritbron.CORRECTIE,
                    bevestigd = false,
                    aangemaaktOp = nu,
                    gewijzigdOp = nu,
                ),
                verschilKm = verschil,
            )
        }
    }

    /** Boven deze afwijking of dit aantal dagen is ijken op zijn plaats. */
    private const val MAX_KM_TUSSEN_IJKINGEN = 1_000
    private const val MAX_DAGEN_TUSSEN_IJKINGEN = 30L

    /**
     * Of het tijd is om de teller na te lopen. Komen de standen uit de auto zelf,
     * dan is ijken overbodig en geeft deze functie altijd false.
     */
    fun ijkenNodig(
        standenKomenUitDeAuto: Boolean,
        laatsteIjking: LocalDate?,
        kmSindsIjking: Int,
        vandaag: LocalDate,
    ): Boolean {
        if (standenKomenUitDeAuto) return false
        if (kmSindsIjking <= 0) return false
        if (laatsteIjking == null) return kmSindsIjking >= 100
        val dagen = java.time.temporal.ChronoUnit.DAYS.between(laatsteIjking, vandaag)
        return kmSindsIjking >= MAX_KM_TUSSEN_IJKINGEN || dagen >= MAX_DAGEN_TUSSEN_IJKINGEN
    }
}
