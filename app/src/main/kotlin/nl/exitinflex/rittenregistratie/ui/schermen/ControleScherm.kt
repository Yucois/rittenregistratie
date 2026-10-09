@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui.schermen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import nl.exitinflex.rittenregistratie.kern.Ijking
import nl.exitinflex.rittenregistratie.ui.Bevindingregel
import nl.exitinflex.rittenregistratie.ui.KmVeld
import nl.exitinflex.rittenregistratie.ui.DatumVeld
import nl.exitinflex.rittenregistratie.ui.Privemeter
import nl.exitinflex.rittenregistratie.ui.RitViewModel
import java.time.LocalDate

@Composable
fun ControleScherm(viewModel: RitViewModel, opRit: (String) -> Unit) {
    val stand by viewModel.stand.collectAsState()
    val controle = stand.controle
    val context = LocalContext.current
    val standbron by viewModel.standbron.collectAsState()
    var werkelijkeStand by remember(controle?.laatsteStand) { mutableIntStateOf(controle?.laatsteStand ?: 0) }

    LaunchedEffect(Unit) {
        viewModel.laadStandbron(context)
        viewModel.controleerTegenAuto(context)
    }

    val nietVastgelegd by viewModel.nietVastgelegdeKm.collectAsState()

    val laatsteIjking = stand.voorkeuren.laatsteIjking
    val kmSindsIjking = stand.ritten
        .filter { laatsteIjking == null || !it.datum.isBefore(laatsteIjking) }
        .sumOf { it.afstandKm }
    val ijkenNodig = Ijking.ijkenNodig(
        standenKomenUitDeAuto = standbron.bruikbaar,
        laatsteIjking = laatsteIjking,
        kmSindsIjking = kmSindsIjking,
        vandaag = LocalDate.now(),
    )
    var jaar by remember { mutableIntStateOf(LocalDate.now().year) }
    var afsluitdatum by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).minusDays(1)) }
    val jaren = (controle?.jaaroverzichten?.map { it.jaar } ?: emptyList()).ifEmpty { listOf(jaar) }
    val overzicht = controle?.jaar(jaar)
    val bevindingen = controle?.bevindingen.orEmpty()

    Scaffold(topBar = { TopAppBar(title = { Text("Controle") }) }) { ruimte ->
        LazyColumn(
            Modifier.fillMaxSize().padding(ruimte).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    jaren.sortedDescending().forEach { optie ->
                        FilterChip(
                            selected = optie == jaar,
                            onClick = { jaar = optie },
                            label = { Text(optie.toString()) },
                        )
                    }
                }
            }
            item { Privemeter(overzicht) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sluitendheid", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (controle?.sluitend == true) {
                                "De reeks sluit aan: elke kilometer tussen de eerste en de laatste stand is verantwoord."
                            } else {
                                "Er zitten gaten of fouten in de reeks. Zolang die er zijn is de registratie " +
                                    "niet sluitend, en dan houdt hij bij een controle geen stand."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (controle?.sluitend == true) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        overzicht?.let {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Regel("Totaal", "${it.totaalKm} km")
                            Regel("Zakelijk", "${it.zakelijkKm} km")
                            Regel("Woon-werk", "${it.woonWerkKm} km")
                            Regel("Privé", "${it.priveKm} km")
                            if (it.priveOmrijKm > 0) Regel("Privé-omrijkilometers", "${it.priveOmrijKm} km")
                            if (it.nietVerantwoordeKm > 0) Regel("Niet verantwoord", "${it.nietVerantwoordeKm} km")
                            Regel("Laatste tellerstand", "${controle?.laatsteStand ?: 0} km")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tellerstand ijken", style = MaterialTheme.typography.labelLarge)
                        if (nietVastgelegd > DREMPEL_NIET_VASTGELEGD) {
                            Text(
                                "De auto staat $nietVastgelegd km verder dan de registratie. " +
                                    "Er is dus een rit niet vastgelegd — leg hem alsnog vast, of ijk hier " +
                                    "zodat het verschil als privé wordt verantwoord.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            when {
                                standbron.bruikbaar ->
                                    "De standen komen rechtstreeks uit de auto. IJken is niet nodig; " +
                                        "je kunt het hieronder wel nalopen."
                                ijkenNodig ->
                                    "Tijd om na te lopen: sinds de laatste ijking staat er $kmSindsIjking km " +
                                        "in de registratie. Neem de stand van het dashboard over."
                                else ->
                                    "Neem af en toe de stand van het dashboard over. Een verschil komt er als " +
                                        "aparte correctierit in, zodat zichtbaar blijft waar het vandaan komt."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ijkenNodig) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            "Laatst geregistreerd: ${controle?.laatsteStand ?: 0} km" +
                                (laatsteIjking?.let { " · laatste ijking $it" } ?: " · nog niet geijkt"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        KmVeld("Werkelijke kilometerstand", werkelijkeStand, { werkelijkeStand = it })
                        Button(
                            onClick = { viewModel.ijk(werkelijkeStand) },
                            enabled = werkelijkeStand > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("IJken") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Periode afsluiten", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Vergrendelt alle ritten tot en met de gekozen datum. Daarna zijn ze niet meer stil " +
                                "te wijzigen; een correctie komt er als aparte rit bij. Dat maakt de administratie " +
                                "geloofwaardiger dan een bestand dat tot het laatste moment aanpasbaar is.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        DatumVeld("Tot en met", afsluitdatum) { afsluitdatum = it }
                        OutlinedButton(
                            onClick = { viewModel.vergrendelTot(afsluitdatum) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Vergrendelen") }
                    }
                }
            }
            item {
                Text(
                    if (bevindingen.isEmpty()) "Geen opmerkingen." else "Bevindingen (${bevindingen.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(bevindingen, key = { it.code + (it.ritId ?: "") + it.melding.hashCode() }) { bevinding ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = bevinding.ritId != null) {
                                bevinding.ritId?.let(opRit)
                            }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Bevindingregel(bevinding)
                    }
                }
            }
            item { Column(Modifier.padding(bottom = 24.dp)) {} }
        }
    }
}

/** Onder dit verschil is het meetruis en geen vergeten rit. */
private const val DREMPEL_NIET_VASTGELEGD = 3

@Composable
private fun Regel(kop: String, waarde: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(kop, style = MaterialTheme.typography.bodyMedium)
        Text(waarde, style = MaterialTheme.typography.bodyMedium)
    }
}
