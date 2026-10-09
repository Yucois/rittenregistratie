package nl.exitinflex.rittenregistratie.ui.schermen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nl.exitinflex.rittenregistratie.data.Autoinstellingen
import nl.exitinflex.rittenregistratie.data.Voorkeuren
import nl.exitinflex.rittenregistratie.kern.Voertuig

/** Eén stap van het instellen, met wat er nog moet gebeuren en een knop ernaartoe. */
data class Instelstap(
    val titel: String,
    val klaar: Boolean,
    val uitleg: String,
    val knop: String,
    val actie: () -> Unit,
)

/**
 * Wat er nodig is voordat automatisch loggen werkt. Bedoeld voor wie de app voor
 * het eerst opent: elke stap staat op klaar zodra hij gedaan is, en de knop gaat
 * rechtstreeks naar de plek waar je hem regelt.
 */
fun instelstappen(
    context: Context,
    voertuig: Voertuig?,
    voorkeuren: Voorkeuren,
    naarAuto: () -> Unit,
): List<Instelstap> {
    fun heeft(recht: String) =
        ContextCompat.checkSelfPermission(context, recht) == PackageManager.PERMISSION_GRANTED

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val pakket = Uri.parse("package:${context.packageName}")
    val naarRechten = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pakket)) }

    return listOf(
        Instelstap(
            titel = "Meldingen toestaan",
            klaar = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                heeft(Manifest.permission.POST_NOTIFICATIONS),
            uitleg = "Na elke rit komt daar de vraag zakelijk, woon-werk of privé.",
            knop = "Meldingen aanzetten",
            actie = {
                open(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        ),
        Instelstap(
            titel = "Auto vastleggen",
            klaar = voertuig != null,
            uitleg = "Merk, type, kenteken en de kilometerstand van het dashboard.",
            knop = "Naar Auto",
            actie = naarAuto,
        ),
        Instelstap(
            titel = "Thuis- en kantooradres",
            klaar = voorkeuren.thuisadres.isNotBlank() && voorkeuren.werkadres.isNotBlank(),
            uitleg = "Daarmee herkent de app woon-werkritten zelf.",
            knop = "Naar Auto",
            actie = naarAuto,
        ),
        Instelstap(
            titel = "Bluetooth van je auto kiezen",
            klaar = Autoinstellingen.auto(context) != null,
            uitleg = "De naam waarmee je telefoon met de auto verbindt. Niet je koptelefoon of hoortoestel.",
            knop = "Auto kiezen",
            actie = naarAuto,
        ),
        Instelstap(
            titel = "Automatisch loggen aan",
            klaar = Autoinstellingen.automatisch(context),
            uitleg = "De schakelaar Ritten vanzelf starten en stoppen.",
            knop = "Naar Auto",
            actie = naarAuto,
        ),
        Instelstap(
            titel = "Locatie: Altijd toestaan",
            klaar = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                heeft(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            uitleg = "Anders meet de app niets zolang je telefoon in je zak zit. Rechten → Locatie → Altijd toestaan.",
            knop = "Naar de rechten",
            actie = naarRechten,
        ),
        Instelstap(
            titel = "Batterijbeperking uit",
            klaar = context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true,
            uitleg = "Anders mag Android de app niet starten als je instapt.",
            knop = "Uitzetten",
            actie = {
                open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pakket))
            },
        ),
    )
}

@Composable
fun AanDeSlagKaart(stappen: List<Instelstap>, modifier: Modifier = Modifier) {
    val klaar = stappen.count { it.klaar }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Aan de slag", style = MaterialTheme.typography.titleSmall)
            Text(
                "$klaar van ${stappen.size} klaar. Pas als alles klaar is, gaan ritten vanzelf.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stappen.forEach { stap ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (stap.klaar) "✓  " else "○  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (stap.klaar) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(stap.titel, style = MaterialTheme.typography.bodyMedium)
                }
                if (!stap.klaar) {
                    Text(
                        stap.uitleg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                    TextButton(onClick = stap.actie, modifier = Modifier.padding(start = 12.dp)) {
                        Text(stap.knop)
                    }
                }
            }
        }
    }
}
