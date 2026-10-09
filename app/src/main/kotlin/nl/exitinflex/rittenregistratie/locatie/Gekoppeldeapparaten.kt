package nl.exitinflex.rittenregistratie.locatie

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import nl.exitinflex.rittenregistratie.data.Gekoppeldeauto

/**
 * De bluetooth-apparaten die al met de telefoon gekoppeld zijn.
 *
 * Dit is met opzet geen zoekactie naar apparaten in de buurt: een autoradio
 * die al verbonden is, maakt zichzelf niet zichtbaar en komt in zo'n zoektocht
 * dus nooit boven water. In de lijst met gekoppelde apparaten staat hij wel.
 */
object Gekoppeldeapparaten {

    private val AUTOWOORDEN = listOf("audi", "mmi", "car", "auto", "vw", "porsche", "bmw", "mercedes", "tesla")

    fun lijktOpAuto(naam: String): Boolean = AUTOWOORDEN.any { it in naam.lowercase() }

    @SuppressLint("MissingPermission")
    fun lijst(context: Context): List<Gekoppeldeauto> {
        val beheer = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter = beheer.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices
                .map { apparaat ->
                    Gekoppeldeauto(
                        adres = apparaat.address.uppercase(),
                        naam = apparaat.name.orEmpty().ifBlank { apparaat.address },
                    )
                }
                // Wat op een auto lijkt bovenaan. Een hoortoestel of koptelefoon die
                // per ongeluk wordt gekozen, start overal ritten waar je hem draagt.
                .sortedWith(
                    compareByDescending<Gekoppeldeauto> { lijktOpAuto(it.naam) }
                        .thenBy { it.naam.lowercase() },
                )
        }.getOrElse { emptyList() }
    }
}
