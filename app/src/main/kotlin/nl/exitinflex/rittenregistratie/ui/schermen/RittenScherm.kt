@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui.schermen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.Card
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.exitinflex.rittenregistratie.kern.Ernst
import nl.exitinflex.rittenregistratie.data.Autoinstellingen
import nl.exitinflex.rittenregistratie.locatie.Autoverbinding
import nl.exitinflex.rittenregistratie.locatie.Dienststand
import nl.exitinflex.rittenregistratie.ui.Privemeter
import nl.exitinflex.rittenregistratie.ui.RitRegel
import nl.exitinflex.rittenregistratie.ui.RitViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val maandFormaat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("nl"))

@Composable
fun RittenScherm(
    viewModel: RitViewModel,
    opNieuweRit: () -> Unit,
    opRit: (String) -> Unit,
    opLive: () -> Unit,
    opAuto: () -> Unit,
) {
    val stand by viewModel.stand.collectAsState()
    val meting by viewModel.meting.collectAsState()
    val context = LocalContext.current
    val meet by Dienststand.meet.collectAsState()
    val laatsteGebeurtenis by Dienststand.laatsteGebeurtenis.collectAsState()
    // Rechten en instellingen worden buiten de app gewijzigd; bij terugkeer opnieuw kijken.
    val levenscyclus = LocalLifecycleOwner.current
    var ververs by remember { mutableIntStateOf(0) }
    DisposableEffect(levenscyclus) {
        val waarnemer = LifecycleEventObserver { _, gebeurtenis ->
            if (gebeurtenis == Lifecycle.Event.ON_RESUME) ververs++
        }
        levenscyclus.lifecycle.addObserver(waarnemer)
        onDispose { levenscyclus.lifecycle.removeObserver(waarnemer) }
    }
    val stappen = remember(ververs, stand.voertuig, stand.voorkeuren, meet) {
        instelstappen(context, stand.voertuig, stand.voorkeuren, opAuto)
    }
    val automatisch = Autoinstellingen.automatisch(context)
    val gekoppeldeAuto = Autoinstellingen.auto(context)?.naam
    val autoNuVerbonden = remember(ververs, meet) {
        Autoverbinding.isVerbonden(context, Autoinstellingen.auto(context)?.adres)
    }
    val vandaag = LocalDate.now()
    val overzicht = stand.controle?.jaar(vandaag.year)
    val foutenPerRit = stand.controle?.bevindingen
        ?.filter { it.ernst == Ernst.FOUT && it.ritId != null }
        ?.groupBy { it.ritId }
        .orEmpty()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stand.voertuig?.omschrijving ?: "Rittenregistratie") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = opNieuweRit,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Rit") },
            )
        },
    ) { ruimte ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(ruimte).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Privemeter(overzicht, vandaag, Modifier.padding(top = 12.dp))
            }
            if (stappen.any { !it.klaar }) {
                item { AanDeSlagKaart(stappen) }
            }
            item {
                // Zonder dit is niet te zien of de app op de auto let of stilletjes
                // is gestopt — en dat merk je anders pas aan een ontbrekende rit.
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            when {
                                meet -> "Rit wordt nu bijgehouden"
                                gekoppeldeAuto == null -> "Nog geen auto gekoppeld — zie Auto."
                                !automatisch -> "Automatisch loggen staat uit."
                                else -> "Wacht op $gekoppeldeAuto" + when (autoNuVerbonden) {
                                    true -> " — nu verbonden"
                                    false -> " — nu niet verbonden"
                                    null -> ""
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (laatsteGebeurtenis.isNotBlank()) {
                            Text(
                                "Laatst gezien: $laatsteGebeurtenis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (meting.actief) {
                        Button(onClick = opLive, modifier = Modifier.weight(1f)) {
                            Text("Rit loopt — ${"%.1f".format(meting.afstandKm)} km")
                        }
                    } else {
                        OutlinedButton(onClick = opLive, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("  Rit meten met gps")
                        }
                    }
                }
            }
            if (stand.ritten.isEmpty()) {
                item {
                    Text(
                        "Nog geen ritten. Elke rit die je rijdt hoort hier te staan — ook de privéritten, " +
                            "want een registratie met gaten telt in het nadeel.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            stand.ritten
                .sortedWith(compareByDescending<nl.exitinflex.rittenregistratie.kern.Rit> { it.datum }.thenByDescending { it.beginstandKm })
                .groupBy { it.datum.withDayOfMonth(1) }
                .forEach { (maand, ritten) ->
                    item(key = "kop-$maand") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                maand.format(maandFormaat).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "${ritten.sumOf { it.afstandKm }} km",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                    items(ritten, key = { it.id }) { rit ->
                        RitRegel(
                            rit = rit,
                            foutmelding = foutenPerRit[rit.id]?.firstOrNull()?.melding,
                            onKlik = { opRit(rit.id) },
                        )
                    }
                }

            item { Column(Modifier.padding(bottom = 72.dp)) {} }
        }
    }
}
