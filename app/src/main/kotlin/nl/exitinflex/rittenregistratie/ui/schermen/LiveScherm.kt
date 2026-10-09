@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui.schermen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import nl.exitinflex.rittenregistratie.ui.RitViewModel
import java.time.Duration
import java.time.Instant

@Composable
fun LiveScherm(viewModel: RitViewModel, opAfronden: () -> Unit, opTerug: () -> Unit) {
    val context = LocalContext.current
    val meting by viewModel.meting.collectAsState()
    var tik by remember { mutableLongStateOf(0L) }

    val rechten = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val vrager = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { gegeven ->
        if (gegeven[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            gegeven[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.startMeting(context)
        } else {
            viewModel.meld("Zonder locatietoegang kan de app niet meten. Vul de rit dan handmatig in.")
        }
    }

    LaunchedEffect(meting.actief) {
        while (meting.actief) {
            delay(1_000)
            tik++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rit meten") },
                navigationIcon = {
                    IconButton(onClick = opTerug) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
            )
        },
    ) { ruimte ->
        Column(
            Modifier.fillMaxSize().padding(ruimte).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "%.1f km".format(meting.afstandKm),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    // tik laat deze regel elke seconde opnieuw uitrekenen.
                    val minuten = remember(tik, meting.gestartOp) {
                        meting.gestartOp?.let { Duration.between(it, Instant.now()).toMinutes() }
                    }
                    Text(
                        if (meting.actief) {
                            val uren = (minuten ?: 0L) / 60
                            val rest = (minuten ?: 0L) % 60
                            "Onderweg: ${if (uren > 0) "${uren} uur en " else ""}${rest} minuten"
                        } else {
                            "Nog geen meting"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (meting.startAdres.isNotBlank()) {
                        Text("Vertrek: ${meting.startAdres}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${meting.aantalPunten} meetpunten", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                "De meting is hulp bij het invullen. De kilometerstand op het dashboard blijft leidend: " +
                    "die staat in de registratie en die kan de Belastingdienst nalopen.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (!meting.actief) {
                Button(
                    onClick = {
                        val heeft = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (heeft) viewModel.startMeting(context) else vrager.launch(rechten)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Rit starten") }
            } else {
                Button(
                    onClick = { viewModel.stopMeting(context, opAfronden) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Rit afronden en invullen") }
                OutlinedButton(
                    onClick = {
                        viewModel.annuleerMeting(context)
                        opTerug()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Meting weggooien") }
            }
        }
    }
}
