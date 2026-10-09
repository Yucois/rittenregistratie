package nl.exitinflex.rittenregistratie.locatie

import android.content.Context
import kotlinx.coroutines.flow.first
import nl.exitinflex.rittenregistratie.RitApp
import nl.exitinflex.rittenregistratie.auto.Standbroninstellingen
import nl.exitinflex.rittenregistratie.kern.Rit
import nl.exitinflex.rittenregistratie.kern.Ritbron
import nl.exitinflex.rittenregistratie.kern.Ritclassificatie
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Maakt van een gps-meting een rit en bewaart die meteen.
 *
 * Meteen bewaren is een bewuste keuze: een rit die pas wordt opgeslagen nadat
 * je hem hebt ingevuld, ben je kwijt zodra je het vergeet — en een gemiste rit
 * is precies het gat dat de registratie onsluitend maakt. De rit komt er dus in
 * als onbevestigd; bevestigen is één tik op de melding.
 */
object Ritafronder {

    /** Onder deze afstand is er niet gereden maar bijvoorbeeld alleen de radio aan geweest. */
    private const val MINIMALE_AFSTAND_KM = 0.3

    /** Waarom de app de laatste rit zo heeft ingedeeld; gaat mee in de melding. */
    @Volatile
    var laatsteReden: String = ""
        private set

    suspend fun rondAf(context: Context, gemeten: Ritmeting): Rit? {
        val app = context.applicationContext as? RitApp ?: return null
        if (gemeten.afstandKm < MINIMALE_AFSTAND_KM) {
            // Een rit van nul kilometer na een echte verbinding met de auto betekent
            // bijna altijd dat de meting niet werkte. Dat moet je te zien krijgen:
            // een stil weggegooide rit is precies het gat dat je niet wilt.
            if (gemeten.aantalPunten == 0) {
                Ritmelding.probleem(
                    context,
                    "Rit niet gemeten",
                    "De auto was verbonden, maar er kwam geen enkele locatie binnen. " +
                        "Vul de rit met de hand in en controleer de locatie-instelling.",
                )
            }
            Dienststand.meld("Rit van ${"%.1f".format(gemeten.afstandKm)} km niet bewaard (te kort)")
            return null
        }

        val register = app.bak.register
        val voertuig = register.voertuigNu()
        if (voertuig == null) {
            Ritmelding.probleem(
                context,
                "Rit niet bewaard",
                "Er is nog geen auto vastgelegd. Open de app, vul onder Auto de gegevens in en " +
                    "druk op Auto opslaan.",
            )
            return null
        }
        val voorkeuren = app.bak.instellingen.voorkeuren.first()

        val eindadres = gemeten.laatsteBreedte?.let { breedte ->
            gemeten.laatsteLengte?.let { lengte -> app.bak.adreszoeker.adresVan(breedte, lengte) }
        } ?: gemeten.laatsteAdres

        val beginadres = gemeten.startAdres.ifBlank {
            register.laatsteRit()?.eindadres.orEmpty()
        }

        val voorstel = Ritclassificatie.voorstel(
            beginadres = beginadres,
            eindadres = eindadres,
            thuisadres = voorkeuren.thuisadres,
            werkadres = voorkeuren.werkadres,
            geschiedenis = register.alleRitten(),
            standaard = voorkeuren.standaardSoort,
        )
        // Tellerstanden uit de auto gaan voor op de gps-meting: dat is wat er in de
        // administratie hoort te staan, en het scheelt achteraf ijken.
        val standUitAuto = runCatching { Standbroninstellingen.bron(context)?.huidigeStand() }.getOrNull()
        // De laatst geregistreerde stand is de bodem: achteruit rijden kan niet, en
        // een beginstand onder de vorige eindstand zou de reeks laten overlappen.
        // Heeft de gps eerder te ruim gemeten, dan loopt dat er zo vanzelf weer uit.
        val laatsteEind = register.volgendeBeginstand()
        val beginstand = maxOf(gemeten.startStandKm ?: laatsteEind, laatsteEind)
        val eindstand = if (standUitAuto != null && standUitAuto > beginstand) {
            standUitAuto
        } else {
            beginstand + gemeten.afstandKm.roundToInt().coerceAtLeast(1)
        }
        val herkomst = if (gemeten.startStandKm != null && standUitAuto != null) {
            Ritbron.AUTO
        } else {
            Ritbron.GPS
        }
        val zone = ZoneId.systemDefault()
        val nu = Instant.now()
        val rit = Rit(
            id = UUID.randomUUID().toString(),
            voertuigId = voertuig.id,
            datum = (gemeten.gestartOp ?: nu).atZone(zone).toLocalDate(),
            beginstandKm = beginstand,
            eindstandKm = eindstand,
            beginadres = beginadres,
            eindadres = eindadres,
            soort = voorstel.soort,
            doel = voorstel.doel,
            vertrektijd = gemeten.gestartOp?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0),
            aankomsttijd = LocalTime.now().withSecond(0).withNano(0),
            bron = herkomst,
            gemetenKm = gemeten.afstandKm,
            // Alleen wat de app met zekerheid kan plaatsen gaat er bevestigd in;
            // de rest wacht op één tik, zodat er niets in de administratie komt
            // wat je zelf niet zou hebben ingevuld.
            bevestigd = voorstel.zelfAfhandelen && voorkeuren.automatischBevestigen,
            aangemaaktOp = nu,
            gewijzigdOp = nu,
        )
        register.bewaar(rit)
        laatsteReden = voorstel.reden
        return rit
    }
}
