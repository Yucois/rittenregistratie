package nl.exitinflex.rittenregistratie.locatie

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Zet coördinaten om in een adres. Het begin- en eindadres zijn verplicht in
 * de registratie; met een leesbaar adres is die eis met één druk op de knop
 * vervuld in plaats van met typewerk onderweg.
 */
class Adreszoeker(context: Context) {

    private val context = context.applicationContext
    private val beschikbaar = Geocoder.isPresent()

    @Suppress("DEPRECATION")
    suspend fun adresVan(breedte: Double, lengte: Double): String = withContext(Dispatchers.IO) {
        if (!beschikbaar) return@withContext coordinaten(breedte, lengte)
        runCatching {
            val geocoder = Geocoder(context, Locale("nl", "NL"))
            val adressen = geocoder.getFromLocation(breedte, lengte, 1)
            val adres = adressen?.firstOrNull() ?: return@runCatching coordinaten(breedte, lengte)
            val straat = listOfNotNull(adres.thoroughfare, adres.subThoroughfare)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val plaats = adres.locality ?: adres.subAdminArea ?: adres.adminArea
            listOf(straat, plaats.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { coordinaten(breedte, lengte) }
        }.getOrElse { coordinaten(breedte, lengte) }
    }

    /** Terugval als er geen adres te vinden is; beter dan een leeg verplicht veld. */
    private fun coordinaten(breedte: Double, lengte: Double): String =
        String.format(Locale.US, "%.5f, %.5f", breedte, lengte)
}
