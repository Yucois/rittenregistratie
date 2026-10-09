@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui.schermen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import nl.exitinflex.rittenregistratie.kern.Voertuig
import nl.exitinflex.rittenregistratie.auto.Standbroninstellingen
import nl.exitinflex.rittenregistratie.locatie.Gekoppeldeapparaten
import nl.exitinflex.rittenregistratie.ui.DatumVeld
import nl.exitinflex.rittenregistratie.ui.KmVeld
import nl.exitinflex.rittenregistratie.ui.RitViewModel
import nl.exitinflex.rittenregistratie.ui.SoortKeuze
import nl.exitinflex.rittenregistratie.ui.TekstVeld
import java.time.LocalDate
import java.util.UUID

@Composable
fun InstellingenScherm(viewModel: RitViewModel) {
    val stand by viewModel.stand.collectAsState()
    val bestaand = stand.voertuig
    val context = LocalContext.current
    val koppelstand by viewModel.autokoppeling.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.laadAutokoppeling(context)
        viewModel.laadStandbron(context)
    }

    val bewaardeBron by viewModel.standbron.collectAsState()
    var bronAdres by remember(bewaardeBron) { mutableStateOf(bewaardeBron.basisAdres) }
    var bronToken by remember(bewaardeBron) { mutableStateOf(bewaardeBron.token) }
    var bronEntiteit by remember(bewaardeBron) { mutableStateOf(bewaardeBron.entiteit) }
    val gevondenSensoren by viewModel.sensoren.collectAsState()

    if (gevondenSensoren.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.sensorenGesloten() },
            title = { Text("Welke sensor is de kilometerstand?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    gevondenSensoren.forEach { sensor ->
                        OutlinedButton(
                            onClick = {
                                bronEntiteit = sensor.entiteit
                                viewModel.bewaarStandbron(
                                    context,
                                    Standbroninstellingen.Config(
                                        aan = true,
                                        basisAdres = bronAdres,
                                        token = bronToken,
                                        entiteit = sensor.entiteit,
                                    ),
                                )
                                viewModel.sensorenGesloten()
                                viewModel.meld("Gekoppeld aan ${sensor.naam}: ${sensor.stand} km.")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${sensor.naam} — ${sensor.stand} km") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.sensorenGesloten() }) { Text("Sluiten") }
            },
        )
    }

    val apparaten by viewModel.apparaten.collectAsState()
    // Na het kiezen van de auto meteen de rechten regelen die automatisch loggen nodig
    // heeft; anders staat het wel aan maar mag de app er op de achtergrond niets mee.
    var rechtenNaKoppelen by remember { mutableStateOf(false) }

    val bluetoothVrager = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { gegeven ->
        if (gegeven) {
            viewModel.laadApparaten(context)
        } else {
            viewModel.meld("Zonder bluetoothtoegang kan de app de auto niet herkennen.")
        }
    }

    fun kiesAuto() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothVrager.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.laadApparaten(context)
        }
    }

    if (apparaten.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.apparatenGesloten() },
            title = { Text("Welk apparaat is de auto?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Kies de autoradio waarmee je telefoon verbindt als je instapt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    apparaten.forEach { apparaat ->
                        val isAuto = Gekoppeldeapparaten.lijktOpAuto(apparaat.naam)
                        if (isAuto) {
                            Button(
                                onClick = {
                                viewModel.koppelAuto(context, apparaat)
                                rechtenNaKoppelen = true
                            },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(apparaat.naam) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.koppelAuto(context, apparaat)
                                    rechtenNaKoppelen = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(apparaat.naam) }
                        }
                    }
                    Text(
                        "Kies niet je hoortoestel of koptelefoon: dan begint er overal een rit waar je die draagt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.apparatenGesloten() }) { Text("Sluiten") }
            },
        )
    }

    // Automatisch loggen meet terwijl de telefoon in je zak zit. Daarvoor is
    // locatie "altijd" nodig: met alleen "tijdens gebruik" geeft Android een
    // dienst op de achtergrond geen locatie, en dan meet de app niets.
    val achtergrondVrager = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { gegeven ->
        viewModel.zetAutomatisch(context, true)
        if (!gegeven) {
            viewModel.meld(
                "Kies bij locatie \"Altijd toestaan\", anders meet de app niet als je telefoon in je zak zit.",
            )
        }
    }

    fun vraagAchtergrondlocatie() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            viewModel.zetAutomatisch(context, true)
            return
        }
        val heeft = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (heeft) {
            viewModel.zetAutomatisch(context, true)
        } else {
            viewModel.meld("Kies in het volgende scherm \"Altijd toestaan\".")
            achtergrondVrager.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val locatieVrager = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { gegeven ->
        val heeftLocatie = gegeven[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            gegeven[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (heeftLocatie) {
            // Android vraagt "altijd" pas nadat "tijdens gebruik" is gegeven.
            vraagAchtergrondlocatie()
        } else {
            viewModel.meld("Zonder locatietoegang kan de app een rit niet meten.")
        }
    }

    fun zetAutomatischAan() {
        val heeftLocatie = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (heeftLocatie) {
            vraagAchtergrondlocatie()
        } else {
            locatieVrager.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(rechtenNaKoppelen) {
        if (rechtenNaKoppelen) {
            rechtenNaKoppelen = false
            zetAutomatischAan()
        }
    }

    var merk by remember(bestaand) { mutableStateOf(bestaand?.merk.orEmpty()) }
    var type by remember(bestaand) { mutableStateOf(bestaand?.type.orEmpty()) }
    var kenteken by remember(bestaand) { mutableStateOf(bestaand?.kenteken.orEmpty()) }
    var beginstand by remember(bestaand) { mutableStateOf(bestaand?.beginstandKm ?: 0) }
    var vanaf by remember(bestaand) { mutableStateOf(bestaand?.terBeschikkingVanaf ?: LocalDate.now()) }

    val voorkeuren = stand.voorkeuren
    var thuis by remember(voorkeuren) { mutableStateOf(voorkeuren.thuisadres) }
    var werk by remember(voorkeuren) { mutableStateOf(voorkeuren.werkadres) }

    Scaffold(topBar = { TopAppBar(title = { Text("Auto en instellingen") }) }) { ruimte ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(ruimte)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("De auto", style = MaterialTheme.typography.titleSmall)
            Text(
                "Merk, type, kenteken en de periode waarin de auto ter beschikking staat horen volgens " +
                    "artikel 3.13 van de Uitvoeringsregeling loonbelasting in de registratie.",
                style = MaterialTheme.typography.bodySmall,
            )
            TekstVeld("Merk", merk, { merk = it })
            TekstVeld("Type", type, { type = it })
            TekstVeld("Kenteken", kenteken, { kenteken = it.uppercase() })
            DatumVeld("Ter beschikking vanaf", vanaf) { vanaf = it }
            KmVeld(
                "Kilometerstand bij aanvang",
                beginstand,
                { beginstand = it },
                uitleg = "Het vertrekpunt van de reeks. Hier begint de sluitende registratie.",
            )
            Button(
                onClick = {
                    viewModel.bewaarVoertuig(
                        Voertuig(
                            id = bestaand?.id ?: UUID.randomUUID().toString(),
                            merk = merk.trim(),
                            type = type.trim(),
                            kenteken = kenteken.trim(),
                            terBeschikkingVanaf = vanaf,
                            terBeschikkingTot = bestaand?.terBeschikkingTot,
                            beginstandKm = beginstand,
                        ),
                    )
                },
                enabled = merk.isNotBlank() && kenteken.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Auto opslaan") }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Automatisch loggen", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Koppel de bluetooth van de auto. Vanaf dan begint de rit zodra je instapt en " +
                            "sluit hij zichzelf af als je uitstapt. Je krijgt één melding: zakelijk, " +
                            "woon-werk of privé — één tik en de rit staat erin.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        koppelstand.auto?.let { "Gekoppeld met ${it.naam}" } ?: "Nog geen auto gekoppeld",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = koppelstand.automatisch,
                            onCheckedChange = { aan ->
                                if (aan) zetAutomatischAan() else viewModel.zetAutomatisch(context, false)
                            },
                            enabled = koppelstand.auto != null,
                        )
                        Text("  Ritten vanzelf starten en stoppen", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = voorkeuren.automatischBevestigen,
                            onCheckedChange = {
                                viewModel.bewaarVoorkeuren(voorkeuren.copy(automatischBevestigen = it))
                            },
                        )
                        Text("  Bekende ritten zelf afhandelen", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Woon-werk tussen je vaste adressen, en elke bestemming die je al twee keer " +
                            "hetzelfde hebt vastgelegd, komt er dan meteen bevestigd in — mét het doel " +
                            "van de vorige keer. Alleen een onbekende bestemming vraagt nog om een tik.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            val beheer = context.getSystemService(PowerManager::class.java)
                            val vrij = beheer?.isIgnoringBatteryOptimizations(context.packageName) == true
                            if (vrij) {
                                viewModel.meld("De app is al uitgezonderd van batterijbeperking.")
                            } else {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                runCatching { context.startActivity(intent) }
                                    .onFailure { viewModel.meld("Open Instellingen → Apps → Batterij.") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Batterijbeperking uitzetten") }
                    Text(
                        "Android mag apps op de achtergrond afknijpen. Voor een app die moet merken " +
                            "dat je instapt, is dat fataal: zet de beperking uit.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (koppelstand.auto == null) {
                        Button(onClick = { kiesAuto() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Auto kiezen")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.ontkoppelAuto(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Koppeling verbreken") }
                    }
                    HorizontalDivider()
                    Text(
                        "Werkt het niet automatisch, dan blijft de knop \"Rit meten met gps\" gewoon " +
                            "beschikbaar. De kilometerstand blijft in beide gevallen leidend.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kilometerstand uit de auto", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Dit is niet nodig om de app te gebruiken. Laat het uit staan, tenzij je zelf " +
                            "Home Assistant draait — dat is een server die je thuis hebt staan. Staat dit " +
                            "uit, dan rekent de app met gps en vraagt hij af en toe om de kilometerstand " +
                            "van het dashboard. Dat is één getal per maand.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = bewaardeBron.aan,
                            onCheckedChange = {
                                viewModel.bewaarStandbron(context, bewaardeBron.copy(aan = it))
                            },
                        )
                        Text("  Ik draai Home Assistant", style = MaterialTheme.typography.bodyMedium)
                    }
                    // De velden pas tonen als de koppeling aan staat; anders staan er drie
                    // vakjes in beeld waar de meeste mensen niets mee kunnen.
                    if (bewaardeBron.aan) {
                        TekstVeld(
                            "Adres van Home Assistant",
                            bronAdres,
                            { bronAdres = it },
                            uitleg = "Bijvoorbeeld http://homeassistant.local:8123",
                        )
                        TekstVeld(
                            "Langlevend token",
                            bronToken,
                            { bronToken = it },
                            uitleg = "In Home Assistant: je naam linksonder → Beveiliging → onderaan.",
                        )
                        TekstVeld("Sensor", bronEntiteit, { bronEntiteit = it })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.bewaarStandbron(
                                        context,
                                        Standbroninstellingen.Config(
                                            aan = true,
                                            basisAdres = bronAdres,
                                            token = bronToken,
                                            entiteit = bronEntiteit,
                                        ),
                                    )
                                    viewModel.meld("Koppeling opgeslagen.")
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Opslaan") }
                            OutlinedButton(
                                onClick = { viewModel.proefStandbron(context) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Proef") }
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.zoekSensoren(
                                    context,
                                    Standbroninstellingen.Config(
                                        aan = true,
                                        basisAdres = bronAdres,
                                        token = bronToken,
                                        entiteit = bronEntiteit,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sensor zoeken") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vaste adressen", style = MaterialTheme.typography.labelLarge)
                    TekstVeld("Thuisadres", thuis, { thuis = it })
                    TekstVeld("Kantooradres", werk, { werk = it })
                    Text("Standaard karakter voor een nieuwe rit", style = MaterialTheme.typography.bodySmall)
                    SoortKeuze(voorkeuren.standaardSoort) {
                        viewModel.bewaarVoorkeuren(voorkeuren.copy(standaardSoort = it))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = voorkeuren.gpsMeting,
                            onCheckedChange = { viewModel.bewaarVoorkeuren(voorkeuren.copy(gpsMeting = it)) },
                        )
                        Text("  Gps-meting aanbieden", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(
                        onClick = {
                            viewModel.bewaarVoorkeuren(voorkeuren.copy(thuisadres = thuis.trim(), werkadres = werk.trim()))
                            viewModel.meld("Instellingen opgeslagen.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Adressen opslaan") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Waar dit op gebaseerd is", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "De registratie volgt de gegevens uit artikel 3.13 Uitvoeringsregeling loonbelasting 2011: " +
                            "merk, type en kenteken, de periode van terbeschikkingstelling en per rit de datum, de " +
                            "begin- en eindstand van de teller, het begin- en eindadres, de gereden route waar die " +
                            "afwijkt van de gebruikelijke, en het karakter van de rit.\n\n" +
                            "Woon-werkverkeer telt voor de loonheffing als zakelijk en voor de btw-correctie als " +
                            "privé; de app rekent beide apart uit. Dit is gereedschap, geen fiscaal advies — laat " +
                            "de uitkomst bij twijfel door je adviseur nalopen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
