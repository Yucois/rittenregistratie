package nl.exitinflex.rittenregistratie.locatie

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.exitinflex.rittenregistratie.data.Autoinstellingen
import nl.exitinflex.rittenregistratie.kern.Ritbesluit

/**
 * Wordt door Android gewekt bij elke bluetooth-verbinding en -verbreking, en na
 * het opstarten van de telefoon of een update van de app. Hetzelfde patroon
 * als de Home Assistant-app: geen dienst die de hele dag moet blijven draaien,
 * maar Android dat de app wakker maakt als er iets verandert.
 */
class AutoOntvanger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val auto = Autoinstellingen.auto(context)
        val berichtAdres = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        )?.let { runCatching { it.address }.getOrNull() }

        val verbondenUitBericht = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                Ritbesluit.verbondenVolgensBericht(berichtAdres, auto?.adres, verbonden = true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                Ritbesluit.verbondenVolgensBericht(berichtAdres, auto?.adres, verbonden = false)
            else -> null
        }

        // Een bericht over een ander apparaat (hoortoestel, andere auto) is geen
        // aanleiding om iets te doen; alleen opstarten en updates wel.
        val overDeAuto = verbondenUitBericht != null
        val opstarten = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!overDeAuto && !opstarten) return

        val aanleiding = when {
            verbondenUitBericht == true -> "${auto?.naam} verbonden"
            verbondenUitBericht == false -> "${auto?.naam} verbroken"
            else -> "telefoon opgestart"
        }

        // Na een update: een automatische rit die nog openstaat terwijl de auto niet
        // verbonden is, komt uit de vorige versie — die startte ten onrechte ritten
        // tijdens het lopen. Zo'n rit niet bewaren, maar wel melden.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val lopend = Ritmeter.meting.value
            if (lopend.actief && !lopend.handmatig &&
                Autoverbinding.isVerbonden(context, auto?.adres) != true
            ) {
                Ritmeter.wis(context)
                Dienststand.meld("Onafgemaakte rit uit de vorige versie opgeruimd")
                Ritmelding.probleem(
                    context,
                    "Onafgemaakte rit opgeruimd",
                    "Bij de update stond er nog een rit open uit de vorige versie, zonder dat de auto " +
                        "verbonden was. Die is niet bewaard. Kijk in de lijst of er ritten staan die je " +
                        "niet gereden hebt, en verwijder die.",
                )
            }
        }

        val klaar = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                Ritregelaar.evalueer(context.applicationContext, verbondenUitBericht, aanleiding)
            } finally {
                klaar.finish()
            }
        }
    }
}
