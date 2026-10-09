package nl.exitinflex.rittenregistratie.locatie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import nl.exitinflex.rittenregistratie.data.Autoinstellingen
import nl.exitinflex.rittenregistratie.kern.Ritbesluit

/**
 * Voert uit wat [Ritbesluit] beslist. Wordt aangeroepen bij elk bluetooth-bericht,
 * bij het opstarten van de telefoon en bij het openen van de app.
 */
object Ritregelaar {

    /**
     * @param verbondenUitBericht wat een bluetooth-bericht over de gekozen auto
     *   zegt; null als er geen bericht is of het over een ander apparaat ging.
     *   Dan vraagt de app zelf na of de auto verbonden is.
     * @param vanafVoorgrond true als de app in beeld is. Dan is locatie "tijdens
     *   gebruik" genoeg; vanaf de achtergrond is "altijd toestaan" nodig.
     */
    suspend fun evalueer(
        context: Context,
        verbondenUitBericht: Boolean?,
        aanleiding: String,
        vanafVoorgrond: Boolean = false,
    ) {
        val auto = Autoinstellingen.auto(context)
        val meting = Ritmeter.meting.value
        val verbonden = verbondenUitBericht ?: Autoverbinding.isVerbonden(context, auto?.adres)

        val actie = Ritbesluit.beslis(
            Ritbesluit.Toestand(
                automatischAan = Autoinstellingen.automatisch(context),
                autoAdres = auto?.adres,
                autoVerbonden = verbonden,
                ritLoopt = meting.actief,
                ritHandmatig = meting.handmatig,
            ),
        )
        if (actie != Ritbesluit.Actie.NIETS) Dienststand.meld("$aanleiding → ${actie.name.lowercase()}")

        when (actie) {
            Ritbesluit.Actie.NIETS -> Unit
            Ritbesluit.Actie.BEGIN, Ritbesluit.Actie.GA_DOOR ->
                if (!RitDienst.draait) {
                    val ontbreekt = ontbrekendRecht(context, vanafVoorgrond)
                    if (ontbreekt == null) {
                        RitDienst.startAutomatisch(context)
                    } else {
                        // Niet starten zonder het recht: dan breekt Android de dienst
                        // hard af. Liever een duidelijke melding.
                        Dienststand.meld("Niet gestart: $ontbreekt")
                        Ritmelding.probleem(context, "Rit niet gestart", ontbreekt)
                    }
                }
            Ritbesluit.Actie.AFRONDEN ->
                if (RitDienst.draait) {
                    // Eerst even wachten: een autoradio verbreekt soms kort de verbinding.
                    RitDienst.autoWeg(context)
                } else {
                    // De dienst draait niet meer (Android heeft hem afgeschoten),
                    // maar de rit staat nog open: hier afsluiten.
                    val gemeten = Ritmeter.stop(context)
                    runCatching { Ritafronder.rondAf(context, gemeten) }.getOrNull()
                        ?.let { Ritmelding.toon(context, it) }
                }
        }
    }

    /** Wat er ontbreekt om te mogen meten, of null als alles in orde is. */
    fun ontbrekendRecht(context: Context, vanafVoorgrond: Boolean): String? {
        fun heeft(recht: String) =
            ContextCompat.checkSelfPermission(context, recht) == PackageManager.PERMISSION_GRANTED

        if (!heeft(Manifest.permission.ACCESS_FINE_LOCATION) && !heeft(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            return "De app heeft geen toegang tot je locatie. Open de app en zet onder Auto " +
                "automatisch loggen opnieuw aan."
        }
        if (!vanafVoorgrond && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !heeft(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            return "Locatie staat niet op \"Altijd toestaan\". Instellingen → Apps → " +
                "Rittenregistratie → Rechten → Locatie → Altijd toestaan."
        }
        return null
    }
}
