package nl.exitinflex.rittenregistratie.kern

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Ernst { FOUT, WAARSCHUWING, INFO }

/**
 * Eén constatering over de administratie. [code] is stabiel zodat de app er
 * teksten en acties aan kan hangen; [melding] is de uitleg voor de gebruiker.
 */
data class Bevinding(
    val ernst: Ernst,
    val code: String,
    val melding: String,
    val ritId: String? = null,
)

/** De grens uit artikel 13bis Wet LB: maximaal 500 privékilometers per kalenderjaar. */
const val PRIVE_GRENS_KM = 500

data class Jaaroverzicht(
    val jaar: Int,
    val aantalRitten: Int,
    val totaalKm: Int,
    val zakelijkKm: Int,
    val woonWerkKm: Int,
    val priveKm: Int,
    val priveOmrijKm: Int,
    /** Kilometers tussen twee ritten die nergens verantwoord zijn. */
    val nietVerantwoordeKm: Int,
    /** Automatisch vastgelegde ritten die nog op bevestiging wachten. */
    val onbevestigdeRitten: Int = 0,
) {
    /** Privékilometers voor de 500-kilometergrens (woon-werk telt hier als zakelijk). */
    val priveKmLoonheffing: Int get() = priveKm + priveOmrijKm

    /**
     * Wat de inspecteur telt als de gaten niet alsnog worden verklaard: onverantwoorde
     * kilometers worden bij een controle als privé aangemerkt.
     */
    val priveKmLoonheffingWorstCase: Int get() = priveKmLoonheffing + nietVerantwoordeKm

    /** Privékilometers voor de btw-correctie: woon-werkverkeer telt hier wél als privé. */
    val priveKmBtw: Int get() = priveKm + woonWerkKm + priveOmrijKm

    val restantTot500: Int get() = PRIVE_GRENS_KM - priveKmLoonheffingWorstCase

    val grensOverschreden: Boolean get() = priveKmLoonheffingWorstCase > PRIVE_GRENS_KM

    val sluitend: Boolean get() = nietVerantwoordeKm == 0

    /** Aandeel privé voor de btw-correctie, bijvoorbeeld 0.12 voor 12%. */
    val btwPriveAandeel: Double get() = if (totaalKm == 0) 0.0 else priveKmBtw.toDouble() / totaalKm

    /**
     * Lineaire prognose van de privékilometers over het hele jaar, op basis van
     * het deel van het jaar dat op [peildatum] verstreken is.
     */
    fun prognosePriveKm(peildatum: LocalDate): Int {
        if (peildatum.year != jaar) return priveKmLoonheffingWorstCase
        val verstreken = peildatum.dayOfYear
        val lengte = LocalDate.of(jaar, 12, 31).dayOfYear
        if (verstreken <= 0) return priveKmLoonheffingWorstCase
        return (priveKmLoonheffingWorstCase.toDouble() * lengte / verstreken).roundToInt()
    }
}

data class Controleresultaat(
    val bevindingen: List<Bevinding>,
    val jaaroverzichten: List<Jaaroverzicht>,
    /** Laatst geregistreerde tellerstand; het vertrekpunt voor de volgende rit. */
    val laatsteStand: Int,
) {
    val fouten: List<Bevinding> get() = bevindingen.filter { it.ernst == Ernst.FOUT }
    val waarschuwingen: List<Bevinding> get() = bevindingen.filter { it.ernst == Ernst.WAARSCHUWING }
    val sluitend: Boolean get() = fouten.isEmpty() && jaaroverzichten.all { it.sluitend }

    fun jaar(jaar: Int): Jaaroverzicht? = jaaroverzichten.firstOrNull { it.jaar == jaar }
}

/**
 * Controleert of de registratie voldoet aan de eisen en of hij sluitend is:
 * de eindstand van elke rit moet de beginstand van de volgende zijn.
 */
object Rittencontrole {

    /** Boven dit verschil tussen GPS-meting en tellerstanden volgt een waarschuwing. */
    private const val MEETVERSCHIL_MARGE_KM = 3.0
    private const val MEETVERSCHIL_MARGE_DEEL = 0.10
    private const val ONWAARSCHIJNLIJKE_RITLENGTE_KM = 1500

    fun controleerRit(rit: Rit, voertuig: Voertuig?): List<Bevinding> {
        val b = mutableListOf<Bevinding>()
        fun fout(code: String, melding: String) = b.add(Bevinding(Ernst.FOUT, code, melding, rit.id))
        fun waarschuwing(code: String, melding: String) =
            b.add(Bevinding(Ernst.WAARSCHUWING, code, melding, rit.id))

        if (rit.eindstandKm <= rit.beginstandKm) {
            fout("stand.oplopend", "De eindstand (${rit.eindstandKm}) moet hoger zijn dan de beginstand (${rit.beginstandKm}).")
        }
        if (rit.bron == Ritbron.CORRECTIE) {
            // Een correctierit heeft per definitie geen adressen: hij dekt kilometers
            // die niet zijn vastgelegd. Wat hij wél moet hebben is een toelichting.
            if (rit.opmerking.isNullOrBlank()) {
                fout("correctie.zondertoelichting", "Leg vast waardoor deze kilometers niet zijn geregistreerd.")
            }
        } else {
            if (rit.beginadres.isBlank()) fout("adres.begin", "Het beginadres ontbreekt.")
            if (rit.eindadres.isBlank()) fout("adres.eind", "Het eindadres ontbreekt.")
        }

        if (rit.priveOmrijkilometers < 0) {
            fout("omrij.negatief", "Privé-omrijkilometers kunnen niet negatief zijn.")
        } else if (rit.priveOmrijkilometers > 0) {
            if (rit.afwijkendeRoute.isNullOrBlank()) {
                fout(
                    "route.afwijkend",
                    "Er zijn privé-omrijkilometers ingevuld; beschrijf dan ook de gereden route, want die wijkt af van de gebruikelijke.",
                )
            }
            if (rit.priveOmrijkilometers >= rit.afstandKm && rit.afstandKm > 0) {
                fout(
                    "omrij.tegroot",
                    "De privé-omrijkilometers (${rit.priveOmrijkilometers}) passen niet binnen de rit van ${rit.afstandKm} km.",
                )
            }
            if (rit.soort == Ritsoort.PRIVE) {
                waarschuwing(
                    "omrij.dubbel",
                    "Bij een privérit telt de hele afstand al als privé; losse omrijkilometers zijn dan dubbelop.",
                )
            }
        }

        if (!rit.bevestigd) {
            waarschuwing(
                "rit.onbevestigd",
                "Automatisch vastgelegd en nog niet bevestigd: controleer het karakter en de tellerstanden.",
            )
        }

        if (rit.soort == Ritsoort.ZAKELIJK && rit.doel.isBlank()) {
            waarschuwing(
                "doel.leeg",
                "Geen zakelijk doel of relatie vermeld. Dat is het eerste waar een controle naar vraagt.",
            )
        }

        if (voertuig != null && !voertuig.terBeschikkingOp(rit.datum)) {
            fout(
                "datum.buitenperiode",
                "De rit valt buiten de periode waarin ${voertuig.kenteken} ter beschikking stond.",
            )
        }

        if (rit.afstandKm > ONWAARSCHIJNLIJKE_RITLENGTE_KM) {
            waarschuwing("afstand.groot", "Een rit van ${rit.afstandKm} km; controleer de tellerstanden op een typefout.")
        }

        val gemeten = rit.gemetenKm
        if (gemeten != null && gemeten > 0) {
            val verschil = abs(rit.afstandKm - gemeten)
            val marge = maxOf(MEETVERSCHIL_MARGE_KM, gemeten * MEETVERSCHIL_MARGE_DEEL)
            if (verschil > marge) {
                waarschuwing(
                    "meting.afwijking",
                    "De tellerstanden geven ${rit.afstandKm} km, de gps-meting ${"%.1f".format(gemeten)} km.",
                )
            }
        }

        return b
    }

    /**
     * Controleert de hele reeks van één voertuig: aansluiting van de tellerstanden,
     * dubbelingen en de jaartotalen.
     *
     * @param ritten alle ritten van het voertuig, in willekeurige volgorde.
     * @param peildatum vandaag; ritten met een latere datum leveren een waarschuwing op.
     */
    fun controleer(
        voertuig: Voertuig,
        ritten: List<Rit>,
        peildatum: LocalDate = LocalDate.now(),
    ): Controleresultaat {
        val eigen = ritten.filter { it.voertuigId == voertuig.id }
        val bevindingen = mutableListOf<Bevinding>()
        eigen.forEach { bevindingen += controleerRit(it, voertuig) }
        eigen.filter { it.datum.isAfter(peildatum) }.forEach {
            bevindingen += Bevinding(Ernst.WAARSCHUWING, "datum.toekomst", "De rit ligt in de toekomst.", it.id)
        }

        val gesorteerd = eigen.sortedWith(compareBy({ it.beginstandKm }, { it.datum }, { it.id }))

        // Gaten per jaar bijhouden: een gat valt in het jaar van de rit die erna komt.
        val gatenPerJaar = mutableMapOf<Int, Int>()

        if (gesorteerd.isNotEmpty()) {
            val eerste = gesorteerd.first()
            val startgat = eerste.beginstandKm - voertuig.beginstandKm
            when {
                startgat > 0 -> {
                    gatenPerJaar.merge(eerste.datum.year, startgat, Int::plus)
                    bevindingen += Bevinding(
                        Ernst.FOUT,
                        "hiaat.start",
                        "Tussen de beginstand van de auto (${voertuig.beginstandKm} km) en de eerste rit " +
                            "(${eerste.beginstandKm} km) zitten $startgat onverantwoorde kilometers.",
                        eerste.id,
                    )
                }
                startgat < 0 -> bevindingen += Bevinding(
                    Ernst.FOUT,
                    "stand.voorbeginstand",
                    "De eerste rit begint onder de opgegeven beginstand van de auto (${voertuig.beginstandKm} km).",
                    eerste.id,
                )
            }
        }

        for (i in 1 until gesorteerd.size) {
            val vorige = gesorteerd[i - 1]
            val huidige = gesorteerd[i]
            val gat = huidige.beginstandKm - vorige.eindstandKm
            when {
                gat > 0 -> {
                    gatenPerJaar.merge(huidige.datum.year, gat, Int::plus)
                    bevindingen += Bevinding(
                        Ernst.FOUT,
                        "hiaat",
                        "$gat km tussen ${vorige.eindstandKm} en ${huidige.beginstandKm} zijn niet verantwoord. " +
                            "Onverklaarde kilometers worden bij een controle als privé aangemerkt.",
                        huidige.id,
                    )
                }
                gat < 0 -> bevindingen += Bevinding(
                    Ernst.FOUT,
                    "overlap",
                    "De rit begint bij ${huidige.beginstandKm} km terwijl de vorige rit al tot " +
                        "${vorige.eindstandKm} km liep; de standen overlappen.",
                    huidige.id,
                )
            }
            if (huidige.datum.isBefore(vorige.datum)) {
                bevindingen += Bevinding(
                    Ernst.WAARSCHUWING,
                    "volgorde.datum",
                    "De datum (${huidige.datum}) ligt vóór die van de rit met een lagere tellerstand (${vorige.datum}).",
                    huidige.id,
                )
            }
        }

        val jaren = (eigen.map { it.datum.year } + gatenPerJaar.keys).distinct().sorted()
        val overzichten = jaren.map { jaar ->
            val vanJaar = eigen.filter { it.datum.year == jaar }
            Jaaroverzicht(
                jaar = jaar,
                aantalRitten = vanJaar.size,
                totaalKm = vanJaar.sumOf { it.afstandKm },
                zakelijkKm = vanJaar.filter { it.soort == Ritsoort.ZAKELIJK }.sumOf { it.afstandKm },
                woonWerkKm = vanJaar.filter { it.soort == Ritsoort.WOON_WERK }.sumOf { it.afstandKm },
                priveKm = vanJaar.filter { it.soort == Ritsoort.PRIVE }.sumOf { it.afstandKm },
                priveOmrijKm = vanJaar.filter { it.soort != Ritsoort.PRIVE }
                    .sumOf { it.priveOmrijkilometers.coerceAtMost(it.afstandKm) },
                nietVerantwoordeKm = gatenPerJaar[jaar] ?: 0,
                onbevestigdeRitten = vanJaar.count { !it.bevestigd },
            )
        }

        overzichten.filter { it.grensOverschreden }.forEach {
            bevindingen += Bevinding(
                Ernst.FOUT,
                "grens.overschreden",
                "In ${it.jaar} staan ${it.priveKmLoonheffingWorstCase} privékilometers geregistreerd; " +
                    "boven de 500 vervalt de Verklaring geen privégebruik auto en volgt bijtelling.",
            )
        }

        return Controleresultaat(
            bevindingen = bevindingen,
            jaaroverzichten = overzichten,
            laatsteStand = gesorteerd.lastOrNull()?.eindstandKm ?: voertuig.beginstandKm,
        )
    }
}
