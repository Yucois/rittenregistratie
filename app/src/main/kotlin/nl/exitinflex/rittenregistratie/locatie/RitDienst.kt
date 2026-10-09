package nl.exitinflex.rittenregistratie.locatie

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.exitinflex.rittenregistratie.R
import nl.exitinflex.rittenregistratie.auto.Standbroninstellingen
import nl.exitinflex.rittenregistratie.data.Autoinstellingen
import nl.exitinflex.rittenregistratie.kern.Ritbesluit
import nl.exitinflex.rittenregistratie.ui.MainActivity
import kotlin.math.roundToInt

/**
 * Meet een rit. Doet verder niets: wanneer een rit begint en eindigt beslist
 * [Ritbesluit], en het wakker worden bij de auto regelt [AutoOntvanger].
 *
 * Bewust START_NOT_STICKY, net als de Home Assistant-app. Een dienst die Android
 * na het afschieten vanzelf herstart, krijgt geen opdracht mee; een eerdere
 * versie las dat als "begin een rit" en startte ritten tijdens het lopen. Nu
 * blijft een afgeschoten dienst dood, en het volgende bluetooth-bericht of het
 * openen van de app zet de zaak weer recht.
 */
class RitDienst : Service() {

    private val bereik = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: FusedLocationProviderClient
    private lateinit var adreszoeker: Adreszoeker
    private var startAdresGezocht = false
    private var meten = false
    private var heeftLocatieInDienst = false
    private var afrondWacht: Job? = null
    private var bewaking: Job? = null

    private val locatieTerugmelding = object : LocationCallback() {
        override fun onLocationResult(resultaat: LocationResult) {
            val locatie = resultaat.lastLocation ?: return
            val bijgewerkt = Ritmeter.verwerk(this@RitDienst, locatie)
            if (!startAdresGezocht) {
                startAdresGezocht = true
                bereik.launch {
                    val adres = adreszoeker.adresVan(locatie.latitude, locatie.longitude)
                    Ritmeter.zetLaatsteAdres(this@RitDienst, adres)
                }
            }
            if (bijgewerkt) toonMelding()
        }
    }

    override fun onCreate() {
        super.onCreate()
        draait = true
        client = LocationServices.getFusedLocationProviderClient(this)
        adreszoeker = Adreszoeker(this)
        maakKanaal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Altijd eerst naar de voorgrond: Android sluit een dienst af die dat niet
        // binnen enkele seconden doet.
        naarVoorgrond()

        when (intent?.action) {
            ACTIE_START_AUTOMATISCH -> startMeten(handmatig = false)
            ACTIE_START_HANDMATIG -> startMeten(handmatig = true)
            ACTIE_AUTO_WEG -> autoWeg()
            ACTIE_AFRONDEN -> rondAf()
            ACTIE_STOP -> stopAlles()
            // Geen opdracht: niets doen en stoppen. Een rit begint alleen op
            // uitdrukkelijk verzoek.
            else -> if (!meten) stopAlles()
        }
        return START_NOT_STICKY
    }

    private fun startMeten(handmatig: Boolean) {
        afrondWacht?.cancel()
        afrondWacht = null
        if (!heeftLocatierecht() || !heeftLocatieInDienst) {
            Ritmelding.probleem(
                this,
                "Rit kan niet gemeten worden",
                "Android geeft de app geen locatie. Zet locatie voor Rittenregistratie op " +
                    "\"Altijd toestaan\" (Instellingen → Apps → Rittenregistratie → Rechten).",
            )
            if (!meten) stopAlles()
            return
        }
        if (!Ritmeter.meting.value.actief) {
            Ritmeter.start(this, "", null, handmatig)
            startAdresGezocht = false
            haalStartstandOp()
        }
        meten = true
        Dienststand.zetMeten(true)
        vraagLocatieAan()
        if (!Ritmeter.meting.value.handmatig) bewaakVerbinding()
        toonMelding()
    }

    private fun vraagLocatieAan() {
        val verzoek = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(INTERVAL_MS / 2)
            .setMinUpdateDistanceMeters(MIN_AFSTAND_M)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { client.requestLocationUpdates(verzoek, locatieTerugmelding, mainLooper) }
    }

    /**
     * De auto meldt dat hij weg is. Niet meteen afronden: een autoradio verbreekt
     * soms kort de verbinding. Eerst stoppen met meten, zodat het eindpunt klopt,
     * en na een minuut opnieuw kijken.
     */
    private fun autoWeg() {
        if (!meten || Ritmeter.meting.value.handmatig) {
            if (!meten) stopAlles()
            return
        }
        runCatching { client.removeLocationUpdates(locatieTerugmelding) }
        afrondWacht?.cancel()
        afrondWacht = bereik.launch {
            delay(AFROND_WACHTTIJD_MS)
            val auto = Autoinstellingen.auto(this@RitDienst)
            if (Autoverbinding.isVerbonden(this@RitDienst, auto?.adres) == true) {
                Dienststand.meld("${auto?.naam} was even weg; rit loopt door")
                vraagLocatieAan()
            } else {
                rondAf()
            }
        }
    }

    /**
     * Tijdens een automatische rit geregeld nagaan of de auto nog verbonden is,
     * voor het geval het bericht over het verbreken is gemist. Pas na twee keer
     * achter elkaar "niet verbonden" wordt de rit afgesloten; "onbekend" telt
     * niet mee.
     */
    private fun bewaakVerbinding() {
        bewaking?.cancel()
        bewaking = bereik.launch {
            var gemist = 0
            while (meten) {
                delay(BEWAKING_INTERVAL_MS)
                val auto = Autoinstellingen.auto(this@RitDienst)
                val actie = Ritbesluit.beslis(
                    Ritbesluit.Toestand(
                        automatischAan = Autoinstellingen.automatisch(this@RitDienst),
                        autoAdres = auto?.adres,
                        autoVerbonden = Autoverbinding.isVerbonden(this@RitDienst, auto?.adres),
                        ritLoopt = true,
                        ritHandmatig = Ritmeter.meting.value.handmatig,
                    ),
                )
                gemist = if (actie == Ritbesluit.Actie.AFRONDEN) gemist + 1 else 0
                if (gemist >= 2) {
                    Dienststand.meld("Auto niet meer verbonden; rit afgesloten")
                    rondAf()
                }
            }
        }
    }

    /**
     * Vraagt de auto naar de tellerstand bij vertrek, als die koppeling er is.
     */
    private fun haalStartstandOp() {
        val bron = Standbroninstellingen.bron(this) ?: return
        val dienst = this
        bereik.launch {
            val stand = runCatching { bron.huidigeStand() }.getOrNull() ?: return@launch
            Ritmeter.zetStartStand(dienst, stand)
        }
    }

    /** Meting afsluiten, de rit bewaren en om bevestiging vragen. */
    private fun rondAf() {
        if (!Ritmeter.meting.value.actief) {
            // Bijvoorbeeld een tik op een oude melding: er valt niets af te ronden.
            stopAlles()
            return
        }
        afrondWacht?.cancel()
        afrondWacht = null
        bewaking?.cancel()
        bewaking = null
        runCatching { client.removeLocationUpdates(locatieTerugmelding) }
        meten = false
        Dienststand.zetMeten(false)
        val dienst = this
        bereik.launch {
            val gemeten = Ritmeter.stop(dienst)
            val rit = runCatching { Ritafronder.rondAf(dienst, gemeten) }.getOrNull()
            if (rit != null) Ritmelding.toon(dienst, rit)
            stopAlles()
        }
    }

    private fun stopAlles() {
        afrondWacht?.cancel()
        bewaking?.cancel()
        runCatching { client.removeLocationUpdates(locatieTerugmelding) }
        meten = false
        Dienststand.zetMeten(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun naarVoorgrond() {
        val melding = bouwMelding()
        val soort = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        heeftLocatieInDienst = runCatching {
            ServiceCompat.startForeground(this, MELDING_ID, melding, soort)
        }.isSuccess
    }

    private fun heeftLocatierecht(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun toonMelding() {
        runCatching { getSystemService(NotificationManager::class.java).notify(MELDING_ID, bouwMelding()) }
    }

    private fun bouwMelding(): Notification {
        val meting = Ritmeter.meting.value
        val km = (meting.afstandKm * 10).roundToInt() / 10.0
        val openen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val afronden = PendingIntent.getService(
            this,
            1,
            Intent(this, RitDienst::class.java).setAction(ACTIE_AFRONDEN),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, KANAAL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Rit wordt bijgehouden")
            .setContentText("$km km sinds ${meting.startAdres.ifBlank { "vertrek" }}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openen)
            .addAction(0, "Rit afronden", afronden)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun maakKanaal() {
        val kanaal = NotificationChannel(
            KANAAL,
            getString(R.string.kanaal_rit_naam),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.kanaal_rit_uitleg) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(kanaal)
    }

    override fun onDestroy() {
        runCatching { client.removeLocationUpdates(locatieTerugmelding) }
        bereik.cancel()
        draait = false
        Dienststand.zetMeten(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val KANAAL = "lopende_rit"
        private const val MELDING_ID = 1001
        private const val INTERVAL_MS = 5_000L
        private const val MIN_AFSTAND_M = 15f
        private const val AFROND_WACHTTIJD_MS = 60_000L
        private const val BEWAKING_INTERVAL_MS = 3 * 60_000L

        const val ACTIE_START_AUTOMATISCH = "nl.exitinflex.rittenregistratie.START_AUTOMATISCH"
        const val ACTIE_START_HANDMATIG = "nl.exitinflex.rittenregistratie.START_HANDMATIG"
        const val ACTIE_AUTO_WEG = "nl.exitinflex.rittenregistratie.AUTO_WEG"
        const val ACTIE_AFRONDEN = "nl.exitinflex.rittenregistratie.AFRONDEN"
        const val ACTIE_STOP = "nl.exitinflex.rittenregistratie.STOP"

        /** Of de dienst in dit proces draait. Na het afschieten van het proces is dit weer false. */
        @Volatile
        var draait: Boolean = false
            private set

        /**
         * Een automatische rit starten of voortzetten. Kan vanaf de achtergrond
         * worden geweigerd; dan krijg je een melding met wat eraan te doen is.
         */
        fun startAutomatisch(context: Context) {
            val intent = Intent(context, RitDienst::class.java).setAction(ACTIE_START_AUTOMATISCH)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (fout: Exception) {
                val geweigerd = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    fout is ForegroundServiceStartNotAllowedException
                Dienststand.meld("Starten geweigerd door Android")
                Ritmelding.probleem(
                    context,
                    "Rit kon niet starten",
                    if (geweigerd) {
                        "Android liet de app niet op de achtergrond starten. Zet in de app onder Auto " +
                            "de batterijbeperking uit en zet locatie op \"Altijd toestaan\"."
                    } else {
                        "De meting kon niet starten: ${fout.message}"
                    },
                )
            }
        }

        fun startHandmatig(context: Context) {
            val intent = Intent(context, RitDienst::class.java).setAction(ACTIE_START_HANDMATIG)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** De auto is weg: na een wachttijd afronden als hij niet terugkomt. */
        fun autoWeg(context: Context) = stuurAanLopendeDienst(context, ACTIE_AUTO_WEG)

        /** Meting stoppen zonder te bewaren; het scherm handelt de rit dan zelf af. */
        fun stop(context: Context) = stuurAanLopendeDienst(context, ACTIE_STOP)

        /**
         * Een opdracht aan een dienst die al draait. Die staat op de voorgrond, dus
         * een gewone startService volstaat en valt niet onder de beperkingen voor
         * het starten vanaf de achtergrond.
         */
        private fun stuurAanLopendeDienst(context: Context, actie: String) {
            if (!draait) return
            runCatching { context.startService(Intent(context, RitDienst::class.java).setAction(actie)) }
        }
    }
}
