package nl.exitinflex.rittenregistratie.locatie

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context

/**
 * Is de gekozen auto nu via bluetooth verbonden?
 *
 * Werkwijze overgenomen van de Home Assistant-app, die dit al jaren bij een
 * groot aantal gebruikers doet: het gekoppelde apparaat zelf vragen of het
 * verbonden is. Die vraag zit niet in de openbare Android-interface, maar is
 * wel toegankelijk; lukt hij niet, dan is het antwoord "onbekend" en doet de
 * app niets — liever geen rit dan een verzonnen rit.
 */
object Autoverbinding {

    @SuppressLint("MissingPermission")
    fun isVerbonden(context: Context, adres: String?): Boolean? {
        if (adres.isNullOrBlank()) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return false
        val apparaat = runCatching {
            adapter.bondedDevices.firstOrNull { it.address.equals(adres, ignoreCase = true) }
        }.getOrNull() ?: return null
        return runCatching {
            apparaat.javaClass.getMethod("isConnected").invoke(apparaat) as Boolean
        }.getOrNull()
    }
}
