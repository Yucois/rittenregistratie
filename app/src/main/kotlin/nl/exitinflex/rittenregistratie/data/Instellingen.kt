package nl.exitinflex.rittenregistratie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import java.time.LocalDate

private val Context.voorkeurenOpslag: DataStore<Preferences> by preferencesDataStore(name = "instellingen")

data class Voorkeuren(
    val thuisadres: String = "",
    val werkadres: String = "",
    val standaardSoort: Ritsoort = Ritsoort.ZAKELIJK,
    /** Ritten starten en stoppen met gps-meting als hulp bij het invullen. */
    val gpsMeting: Boolean = true,
    /**
     * Ritten die de app met zekerheid kan plaatsen — woon-werk, of een
     * bestemming die je al twee keer hetzelfde hebt vastgelegd — meteen als
     * bevestigd opnemen. Je krijgt ze nog wel te zien en kunt ze wijzigen.
     */
    val automatischBevestigen: Boolean = true,
    /** Wanneer de registratie voor het laatst naast de werkelijke tellerstand is gelegd. */
    val laatsteIjking: LocalDate? = null,
)

class Instellingen(private val context: Context) {

    private object Sleutels {
        val thuisadres = stringPreferencesKey("thuisadres")
        val werkadres = stringPreferencesKey("werkadres")
        val standaardSoort = stringPreferencesKey("standaard_soort")
        val gpsMeting = booleanPreferencesKey("gps_meting")
        val automatischBevestigen = booleanPreferencesKey("automatisch_bevestigen")
        val laatsteIjking = stringPreferencesKey("laatste_ijking")
    }

    val voorkeuren: Flow<Voorkeuren> = context.voorkeurenOpslag.data.map { opgeslagen ->
        Voorkeuren(
            thuisadres = opgeslagen[Sleutels.thuisadres].orEmpty(),
            werkadres = opgeslagen[Sleutels.werkadres].orEmpty(),
            standaardSoort = opgeslagen[Sleutels.standaardSoort]
                ?.let { runCatching { Ritsoort.valueOf(it) }.getOrNull() }
                ?: Ritsoort.ZAKELIJK,
            gpsMeting = opgeslagen[Sleutels.gpsMeting] ?: true,
            automatischBevestigen = opgeslagen[Sleutels.automatischBevestigen] ?: true,
            laatsteIjking = opgeslagen[Sleutels.laatsteIjking]
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }

    suspend fun bewaar(voorkeuren: Voorkeuren) {
        context.voorkeurenOpslag.edit { opslag ->
            opslag[Sleutels.thuisadres] = voorkeuren.thuisadres
            opslag[Sleutels.werkadres] = voorkeuren.werkadres
            opslag[Sleutels.standaardSoort] = voorkeuren.standaardSoort.name
            opslag[Sleutels.gpsMeting] = voorkeuren.gpsMeting
            opslag[Sleutels.automatischBevestigen] = voorkeuren.automatischBevestigen
            voorkeuren.laatsteIjking?.let { opslag[Sleutels.laatsteIjking] = it.toString() }
        }
    }
}
