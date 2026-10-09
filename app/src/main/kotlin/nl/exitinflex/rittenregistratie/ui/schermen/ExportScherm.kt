@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui.schermen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.exitinflex.rittenregistratie.deel.Deelhelper
import nl.exitinflex.rittenregistratie.ui.RitViewModel
import java.time.LocalDate

@Composable
fun ExportScherm(viewModel: RitViewModel) {
    val stand by viewModel.stand.collectAsState()
    val context = LocalContext.current
    var jaar by remember { mutableIntStateOf(LocalDate.now().year) }
    val jaren = stand.ritten.map { it.datum.year }.distinct().sortedDescending().ifEmpty { listOf(jaar) }

    val kiezer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { Deelhelper.lees(context, uri) }
            .onSuccess { viewModel.herstelBackup(it) }
            .onFailure { viewModel.meld("Het bestand kon niet worden gelezen.") }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Export") }) }) { ruimte ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(ruimte)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                jaren.forEach { optie ->
                    FilterChip(selected = optie == jaar, onClick = { jaar = optie }, label = { Text(optie.toString()) })
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Voor de boekhouder of de Belastingdienst", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Het rapport bevat de auto, de jaartotalen, de controlepunten en alle ritten. " +
                            "Open het op de telefoon en kies Afdrukken om er een pdf van te maken.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { viewModel.exporteerRapport(jaar) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Rapport $jaar delen")
                    }
                    OutlinedButton(onClick = { viewModel.exporteerCsv(jaar) }, modifier = Modifier.fillMaxWidth()) {
                        Text("CSV $jaar delen")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Back-up", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "De administratie moet zeven jaar bewaard blijven; een telefoon gaat zelden zo lang mee. " +
                            "Zet de back-up buiten het toestel, bijvoorbeeld in de cloudmap van het kantoor.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { viewModel.exporteerBackup() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back-up maken en delen")
                    }
                    OutlinedButton(
                        onClick = { kiezer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back-up terugzetten") }
                }
            }

            Text(
                "Tip: exporteer aan het eind van elk kwartaal en bewaar dat bestand. Een registratie die " +
                    "gaandeweg is bijgehouden weegt zwaarder dan een die achteraf is gereconstrueerd.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
