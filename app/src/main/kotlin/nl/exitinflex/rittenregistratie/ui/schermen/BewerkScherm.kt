@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui.schermen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.exitinflex.rittenregistratie.kern.Ernst
import nl.exitinflex.rittenregistratie.kern.Rittencontrole
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import nl.exitinflex.rittenregistratie.ui.Bevindingregel
import nl.exitinflex.rittenregistratie.ui.DatumVeld
import nl.exitinflex.rittenregistratie.ui.KmVeld
import nl.exitinflex.rittenregistratie.ui.RitViewModel
import nl.exitinflex.rittenregistratie.ui.SoortKeuze
import nl.exitinflex.rittenregistratie.ui.TekstVeld

@Composable
fun BewerkScherm(viewModel: RitViewModel, opTerug: () -> Unit) {
    val rit by viewModel.bewerkRit.collectAsState()
    val stand by viewModel.stand.collectAsState()
    val huidige = rit

    if (huidige == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Geen rit geopend.")
            Button(onClick = opTerug) { Text("Terug") }
        }
        return
    }

    var routeAfwijkend by remember(huidige.id) {
        mutableStateOf(!huidige.afwijkendeRoute.isNullOrBlank() || huidige.priveOmrijkilometers > 0)
    }
    val bevindingen = Rittencontrole.controleerRit(huidige, stand.voertuig)
    val fouten = bevindingen.filter { it.ernst == Ernst.FOUT }
    val bestaat = stand.ritten.any { it.id == huidige.id }
    val vergrendeld = huidige.vergrendeld

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (bestaat) "Rit bewerken" else "Nieuwe rit") },
                navigationIcon = {
                    IconButton(onClick = opTerug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
                actions = {
                    if (bestaat && !vergrendeld) {
                        IconButton(onClick = { viewModel.verwijderRit(huidige.id, opTerug) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Verwijderen")
                        }
                    }
                },
            )
        },
    ) { ruimte ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(ruimte)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vergrendeld) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Deze rit is vergrendeld omdat de periode is afgesloten. Corrigeren doe je met een " +
                            "aanvullende rit, zodat de wijziging zichtbaar blijft.",
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            DatumVeld("Datum", huidige.datum) { nieuw -> viewModel.bewerk { it.copy(datum = nieuw) } }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KmVeld(
                    label = "Beginstand",
                    waarde = huidige.beginstandKm,
                    onWaarde = { nieuw -> viewModel.bewerk { it.copy(beginstandKm = nieuw) } },
                    modifier = Modifier.weight(1f),
                    ingeschakeld = !vergrendeld,
                )
                KmVeld(
                    label = "Eindstand",
                    waarde = huidige.eindstandKm,
                    onWaarde = { nieuw -> viewModel.bewerk { it.copy(eindstandKm = nieuw) } },
                    modifier = Modifier.weight(1f),
                    ingeschakeld = !vergrendeld,
                )
            }
            Text(
                buildString {
                    append("Afstand: ${huidige.afstandKm} km")
                    huidige.gemetenKm?.let { append(" · gps mat ${"%.1f".format(it)} km") }
                },
                style = MaterialTheme.typography.bodySmall,
            )

            TekstVeld(
                "Beginadres",
                huidige.beginadres,
                { nieuw -> viewModel.bewerk { it.copy(beginadres = nieuw) } },
                ingeschakeld = !vergrendeld,
            )
            SnelleAdressen(
                thuis = stand.voorkeuren.thuisadres,
                werk = stand.voorkeuren.werkadres,
                opKeuze = { adres -> viewModel.bewerk { it.copy(beginadres = adres) } },
            )

            TekstVeld(
                "Eindadres",
                huidige.eindadres,
                { nieuw -> viewModel.bewerk { it.copy(eindadres = nieuw) } },
                ingeschakeld = !vergrendeld,
            )
            SnelleAdressen(
                thuis = stand.voorkeuren.thuisadres,
                werk = stand.voorkeuren.werkadres,
                opKeuze = { adres -> viewModel.bewerk { it.copy(eindadres = adres) } },
            )

            Text("Karakter van de rit", style = MaterialTheme.typography.labelLarge)
            SoortKeuze(huidige.soort) { nieuw -> viewModel.bewerk { it.copy(soort = nieuw) } }
            Text(
                when (huidige.soort) {
                    Ritsoort.ZAKELIJK -> "Telt niet mee voor de 500 privékilometers."
                    Ritsoort.WOON_WERK -> "Zakelijk voor de bijtelling, privé voor de btw-correctie."
                    Ritsoort.PRIVE -> "Telt volledig mee voor de 500 privékilometers."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            if (huidige.soort != Ritsoort.PRIVE) {
                TekstVeld(
                    "Zakelijk doel of relatie",
                    huidige.doel,
                    { nieuw -> viewModel.bewerk { it.copy(doel = nieuw) } },
                    ingeschakeld = !vergrendeld,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = routeAfwijkend,
                    onCheckedChange = { aan ->
                        routeAfwijkend = aan
                        if (!aan) {
                            viewModel.bewerk { it.copy(afwijkendeRoute = null, priveOmrijkilometers = 0) }
                        }
                    },
                    enabled = !vergrendeld,
                )
                Text("Afgeweken van de gebruikelijke route")
            }
            if (routeAfwijkend) {
                TekstVeld(
                    "Gereden route",
                    huidige.afwijkendeRoute.orEmpty(),
                    { nieuw -> viewModel.bewerk { it.copy(afwijkendeRoute = nieuw.ifBlank { null }) } },
                    enkelRegel = false,
                    uitleg = "Verplicht zodra de route afwijkt van de meest gebruikelijke.",
                    ingeschakeld = !vergrendeld,
                )
                KmVeld(
                    "Privé-omrijkilometers",
                    huidige.priveOmrijkilometers,
                    { nieuw -> viewModel.bewerk { it.copy(priveOmrijkilometers = nieuw) } },
                    uitleg = "Deze kilometers tellen mee voor de 500.",
                    ingeschakeld = !vergrendeld,
                )
            }

            TekstVeld(
                "Opmerking",
                huidige.opmerking.orEmpty(),
                { nieuw -> viewModel.bewerk { it.copy(opmerking = nieuw.ifBlank { null }) } },
                enkelRegel = false,
                ingeschakeld = !vergrendeld,
            )

            if (bevindingen.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        bevindingen.forEach { Bevindingregel(it) }
                    }
                }
            }

            Button(
                onClick = { viewModel.bewaarBewerkteRit(opTerug) },
                enabled = fouten.isEmpty() && !vergrendeld,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (fouten.isEmpty()) "Opslaan" else "Eerst de fouten herstellen")
            }
        }
    }
}

@Composable
private fun SnelleAdressen(thuis: String, werk: String, opKeuze: (String) -> Unit) {
    val opties = listOfNotNull(
        thuis.takeIf { it.isNotBlank() }?.let { "Thuis" to it },
        werk.takeIf { it.isNotBlank() }?.let { "Kantoor" to it },
    )
    if (opties.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opties.forEach { (naam, adres) ->
            AssistChip(onClick = { opKeuze(adres) }, label = { Text(naam) })
        }
    }
}
