package nl.exitinflex.rittenregistratie.locatie

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Stand van de rit die op dit moment wordt gemeten.
 *
 * De gps-meting is hulp bij het invullen, geen vervanging van de
 * kilometerteller: de fiscale registratie draait om de tellerstanden, en een
 * gps-spoor wijkt daar altijd een paar procent van af. Het verschil tussen
 * beide is juist bruikbaar als controle op een vergissing.
 */
data class Ritmeting(
    val actief: Boolean = false,
    val gestartOp: Instant? = null,
    val afstandMeter: Double = 0.0,
    val startAdres: String = "",
    val laatsteAdres: String = "",
    val startBreedte: Double? = null,
    val startLengte: Double? = null,
    val laatsteBreedte: Double? = null,
    val laatsteLengte: Double? = null,
    val laatsteMomentMs: Long? = null,
    val aantalPunten: Int = 0,
    /** Tellerstand bij vertrek, als de auto die kon leveren. */
    val startStandKm: Int? = null,
    /**
     * Met de hand gestart, en dus niet gebonden aan de verbinding met de auto.
     * Een automatische rit stopt als de auto weg is; een handmatige pas als jij
     * op afronden drukt.
     */
    val handmatig: Boolean = false,
) {
    val afstandKm: Double get() = afstandMeter / 1000.0
}

/**
 * Gedeelde stand tussen de dienst die meet en de schermen die meekijken.
 * Bewaart zichzelf in de voorkeuren, zodat een rit een herstart van het
 * proces overleeft.
 */
object Ritmeter {

    private const val OPSLAG = "lopende_rit"

    private val _meting = MutableStateFlow(Ritmeting())
    val meting: StateFlow<Ritmeting> = _meting.asStateFlow()

    /** Fixes die slechter zijn dan dit aantal meter worden niet meegeteld. */
    private const val MAX_ONNAUWKEURIGHEID_M = 50f

    /** Sprong die een hogere snelheid dan 250 km/u impliceert: een uitschieter, niet gereden. */
    private const val MAX_SNELHEID_MS = 70.0

    fun herstel(context: Context) {
        val opslag = context.getSharedPreferences(OPSLAG, Context.MODE_PRIVATE)
        if (!opslag.getBoolean("actief", false)) return
        _meting.value = Ritmeting(
            actief = true,
            gestartOp = opslag.getString("gestartOp", null)?.let(Instant::parse),
            afstandMeter = opslag.getFloat("afstand", 0f).toDouble(),
            startAdres = opslag.getString("startAdres", "").orEmpty(),
            laatsteAdres = opslag.getString("laatsteAdres", "").orEmpty(),
            startBreedte = opslag.getString("startBreedte", null)?.toDoubleOrNull(),
            startLengte = opslag.getString("startLengte", null)?.toDoubleOrNull(),
            laatsteBreedte = opslag.getString("laatsteBreedte", null)?.toDoubleOrNull(),
            laatsteLengte = opslag.getString("laatsteLengte", null)?.toDoubleOrNull(),
            aantalPunten = opslag.getInt("punten", 0),
            startStandKm = opslag.getInt("startStand", 0).takeIf { it > 0 },
            handmatig = opslag.getBoolean("handmatig", false),
        )
    }

    fun start(context: Context, adres: String, locatie: Location?, handmatig: Boolean = false) {
        _meting.value = Ritmeting(
            actief = true,
            handmatig = handmatig,
            gestartOp = Instant.now(),
            startAdres = adres,
            laatsteAdres = adres,
            startBreedte = locatie?.latitude,
            startLengte = locatie?.longitude,
            laatsteBreedte = locatie?.latitude,
            laatsteLengte = locatie?.longitude,
            laatsteMomentMs = System.currentTimeMillis(),
        )
        bewaar(context)
    }

    /** Verwerkt een nieuwe positie; geeft terug of de meting is bijgewerkt. */
    fun verwerk(context: Context, locatie: Location): Boolean {
        val huidig = _meting.value
        if (!huidig.actief) return false
        if (locatie.hasAccuracy() && locatie.accuracy > MAX_ONNAUWKEURIGHEID_M) return false

        val vorigeBreedte = huidig.laatsteBreedte
        val vorigeLengte = huidig.laatsteLengte
        val nu = System.currentTimeMillis()
        var afstand = huidig.afstandMeter
        if (vorigeBreedte != null && vorigeLengte != null) {
            val resultaat = FloatArray(1)
            Location.distanceBetween(vorigeBreedte, vorigeLengte, locatie.latitude, locatie.longitude, resultaat)
            val sprong = resultaat[0].toDouble()
            val seconden = (((nu - (huidig.laatsteMomentMs ?: nu)) / 1000.0)).coerceAtLeast(1.0)
            // Een sprong die een onmogelijke snelheid vraagt komt van een slechte fix, niet van de auto.
            if (sprong < MAX_SNELHEID_MS * seconden) afstand += sprong
        }

        _meting.value = huidig.copy(
            afstandMeter = afstand,
            laatsteBreedte = locatie.latitude,
            laatsteLengte = locatie.longitude,
            laatsteMomentMs = nu,
            aantalPunten = huidig.aantalPunten + 1,
        )
        bewaar(context)
        return true
    }

    /** De tellerstand bij vertrek, opgehaald bij de auto. */
    fun zetStartStand(context: Context, stand: Int) {
        if (stand <= 0 || !_meting.value.actief || _meting.value.startStandKm != null) return
        _meting.value = _meting.value.copy(startStandKm = stand)
        bewaar(context)
    }

    fun zetLaatsteAdres(context: Context, adres: String) {
        if (adres.isBlank()) return
        _meting.value = _meting.value.copy(laatsteAdres = adres)
        if (_meting.value.startAdres.isBlank()) {
            _meting.value = _meting.value.copy(startAdres = adres)
        }
        bewaar(context)
    }

    fun stop(context: Context): Ritmeting {
        val eind = _meting.value.copy(actief = false)
        _meting.value = eind
        context.getSharedPreferences(OPSLAG, Context.MODE_PRIVATE).edit().clear().apply()
        return eind
    }

    fun wis(context: Context) {
        _meting.value = Ritmeting()
        context.getSharedPreferences(OPSLAG, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun bewaar(context: Context) {
        val m = _meting.value
        context.getSharedPreferences(OPSLAG, Context.MODE_PRIVATE).edit()
            .putBoolean("actief", m.actief)
            .putString("gestartOp", m.gestartOp?.toString())
            .putFloat("afstand", m.afstandMeter.toFloat())
            .putString("startAdres", m.startAdres)
            .putString("laatsteAdres", m.laatsteAdres)
            .putString("startBreedte", m.startBreedte?.toString())
            .putString("startLengte", m.startLengte?.toString())
            .putString("laatsteBreedte", m.laatsteBreedte?.toString())
            .putString("laatsteLengte", m.laatsteLengte?.toString())
            .putInt("punten", m.aantalPunten)
            .putInt("startStand", m.startStandKm ?: 0)
            .putBoolean("handmatig", m.handmatig)
            .apply()
    }
}
