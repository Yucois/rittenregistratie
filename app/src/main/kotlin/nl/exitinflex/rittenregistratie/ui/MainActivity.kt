package nl.exitinflex.rittenregistratie.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import nl.exitinflex.rittenregistratie.locatie.Ritregelaar
import nl.exitinflex.rittenregistratie.ui.thema.RittenTheme

class MainActivity : ComponentActivity() {

    private val teOpenenRit = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        teOpenenRit.value = intent?.getStringExtra(EXTRA_RIT)
        setContent {
            RittenTheme {
                Rittenapp(
                    teOpenenRit = teOpenenRit.value,
                    opRitGeopend = { teOpenenRit.value = null },
                )
            }
        }
    }

    /**
     * Bij het openen van de app nagaan of er een rit hoort te lopen. Zit je in de
     * auto en is het bluetooth-bericht gemist, dan begint hij hier alsnog — en
     * vanaf de voorgrond mag dat altijd.
     */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            Ritregelaar.evalueer(applicationContext, null, "app geopend", vanafVoorgrond = true)
        }
    }

    /** De app draait al als je op de melding tikt; dan komt de rit hierlangs binnen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        teOpenenRit.value = intent.getStringExtra(EXTRA_RIT)
    }

    companion object {
        const val EXTRA_RIT = "rit_id"
    }
}
