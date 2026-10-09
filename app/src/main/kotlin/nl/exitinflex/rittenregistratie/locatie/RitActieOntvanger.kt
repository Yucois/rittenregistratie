package nl.exitinflex.rittenregistratie.locatie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.exitinflex.rittenregistratie.RitApp
import nl.exitinflex.rittenregistratie.kern.Ritsoort

/** Verwerkt de keuze uit de melding: één tik bevestigt de rit met het gekozen karakter. */
class RitActieOntvanger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTIE_BEVESTIG) return
        val ritId = intent.getStringExtra(EXTRA_RIT) ?: return
        val soort = intent.getStringExtra(EXTRA_SOORT)
            ?.let { naam -> runCatching { Ritsoort.valueOf(naam) }.getOrNull() }
            ?: return

        val afronden = goAsync()
        val app = context.applicationContext as? RitApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val register = app?.bak?.register ?: return@launch
                val rit = register.rit(ritId) ?: return@launch
                register.bewaar(rit.copy(soort = soort, bevestigd = true))
                Ritmelding.verberg(context, ritId)
            } finally {
                afronden.finish()
            }
        }
    }

    companion object {
        const val ACTIE_BEVESTIG = "nl.exitinflex.rittenregistratie.BEVESTIG"
        const val EXTRA_RIT = "rit"
        const val EXTRA_SOORT = "soort"
    }
}
