package nl.exitinflex.rittenregistratie.auto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Haalt de kilometerstand op bij Home Assistant.
 *
 * Home Assistant heeft voor veel automerken een integratie die de stand als
 * gewone sensor klaarzet. Het voordeel boven een koppeling in deze app: het
 * wachtwoord van je auto-account blijft in Home Assistant, en de gegevens gaan niet langs
 * een derde partij.
 */
class HomeAssistantBron(private val instellingen: Standbroninstellingen.Config) : Kilometerstandbron {

    override val naam: String get() = "Home Assistant (${instellingen.entiteit})"

    override suspend fun huidigeStand(): Int? = (haal() as? Proefuitkomst.Gelukt)?.stand

    /** Zelfde aanroep, maar met de foutmelding erbij; voor de proefknop in de instellingen. */
    suspend fun haal(): Proefuitkomst = withContext(Dispatchers.IO) {
        if (!instellingen.compleet) {
            return@withContext Proefuitkomst.Mislukt("Vul het adres, het token en de sensor in.")
        }
        val adres = "${instellingen.basisAdres.trimEnd('/')}/api/states/${instellingen.entiteit}"
        val verbinding = runCatching { URL(adres).openConnection() as HttpURLConnection }
            .getOrElse { return@withContext Proefuitkomst.Mislukt("Dit adres klopt niet: $adres") }

        try {
            verbinding.requestMethod = "GET"
            verbinding.setRequestProperty("Authorization", "Bearer ${instellingen.token}")
            verbinding.setRequestProperty("Accept", "application/json")
            verbinding.connectTimeout = TIJDSLIMIET_MS
            verbinding.readTimeout = TIJDSLIMIET_MS

            when (val code = verbinding.responseCode) {
                200 -> Unit
                401, 403 -> return@withContext Proefuitkomst.Mislukt("Home Assistant weigert het token.")
                404 -> return@withContext Proefuitkomst.Mislukt("De sensor ${instellingen.entiteit} bestaat daar niet.")
                else -> return@withContext Proefuitkomst.Mislukt("Home Assistant antwoordde met code $code.")
            }

            val antwoord = verbinding.inputStream.bufferedReader().use { it.readText() }
            val stand = JSONObject(antwoord).optString("state").toDoubleOrNull()
                ?: return@withContext Proefuitkomst.Mislukt(
                    "De sensor geeft geen getal terug; controleer of dit de kilometerstand is.",
                )
            if (stand <= 0) {
                return@withContext Proefuitkomst.Mislukt("De sensor staat op nul; dat kan geen kilometerstand zijn.")
            }
            Proefuitkomst.Gelukt(stand.roundToInt())
        } catch (fout: Exception) {
            Proefuitkomst.Mislukt(fout.message ?: "Home Assistant was niet bereikbaar.")
        } finally {
            runCatching { verbinding.disconnect() }
        }
    }

    /**
     * Zoekt bij Home Assistant naar sensoren die de kilometerstand kunnen zijn,
     * zodat je de naam niet hoeft op te zoeken en over te typen.
     */
    suspend fun zoekSensoren(): List<Sensorkeuze> = withContext(Dispatchers.IO) {
        if (instellingen.basisAdres.isBlank() || instellingen.token.isBlank()) return@withContext emptyList()
        val antwoord = runCatching { haalOp("${instellingen.basisAdres.trimEnd('/')}/api/states") }
            .getOrNull() ?: return@withContext emptyList()
        val alles = runCatching { JSONArray(antwoord) }.getOrNull() ?: return@withContext emptyList()

        val kandidaten = mutableListOf<Pair<Int, Sensorkeuze>>()
        for (i in 0 until alles.length()) {
            val item = alles.optJSONObject(i) ?: continue
            val entiteit = item.optString("entity_id")
            if (!entiteit.startsWith("sensor.")) continue
            val stand = item.optString("state").toDoubleOrNull() ?: continue
            if (stand < MINIMALE_STAND) continue

            val kenmerken = item.optJSONObject("attributes")
            val eenheid = kenmerken?.optString("unit_of_measurement").orEmpty().lowercase()
            val naam = kenmerken?.optString("friendly_name").orEmpty().ifBlank { entiteit }
            val tekst = (entiteit + " " + naam).lowercase()

            val heetNaarStand = WOORDEN.any { it in tekst }
            val meetKilometers = eenheid == "km"
            if (!heetNaarStand && !meetKilometers) continue

            // Een sensor die zo heet én kilometers meet is vrijwel zeker de teller.
            val score = (if (heetNaarStand) 2 else 0) + (if (meetKilometers) 1 else 0)
            kandidaten += score to Sensorkeuze(entiteit, naam, stand.roundToInt())
        }
        kandidaten.sortedWith(compareByDescending<Pair<Int, Sensorkeuze>> { it.first }
            .thenByDescending { it.second.stand })
            .map { it.second }
            .take(MAX_KANDIDATEN)
    }

    private fun haalOp(adres: String): String {
        val verbinding = URL(adres).openConnection() as HttpURLConnection
        return try {
            verbinding.requestMethod = "GET"
            verbinding.setRequestProperty("Authorization", "Bearer ${instellingen.token}")
            verbinding.setRequestProperty("Accept", "application/json")
            verbinding.connectTimeout = TIJDSLIMIET_MS
            verbinding.readTimeout = TIJDSLIMIET_MS
            verbinding.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { verbinding.disconnect() }
        }
    }

    private companion object {
        const val TIJDSLIMIET_MS = 8_000

        /** Onder deze stand is het geen kilometerteller maar iets anders in kilometers. */
        const val MINIMALE_STAND = 100.0
        const val MAX_KANDIDATEN = 8

        val WOORDEN = listOf("mileage", "odometer", "kilometerstand", "km_stand", "tellerstand")
    }
}
