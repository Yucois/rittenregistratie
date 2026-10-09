@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import nl.exitinflex.rittenregistratie.kern.Bevinding
import nl.exitinflex.rittenregistratie.kern.Ernst
import nl.exitinflex.rittenregistratie.kern.Jaaroverzicht
import nl.exitinflex.rittenregistratie.kern.PRIVE_GRENS_KM
import nl.exitinflex.rittenregistratie.kern.Rit
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val dagFormaat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
val kortFormaat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")

/**
 * De teller waar het bij een Verklaring geen privégebruik auto om draait:
 * hoeveel van de 500 privékilometers zijn op, en waar loopt het op uit.
 */
@Composable
fun Privemeter(overzicht: Jaaroverzicht?, peildatum: LocalDate = LocalDate.now(), modifier: Modifier = Modifier) {
    val gebruikt = overzicht?.priveKmLoonheffingWorstCase ?: 0
    val deel = (gebruikt.toFloat() / PRIVE_GRENS_KM).coerceIn(0f, 1f)
    val kleur = when {
        gebruikt > PRIVE_GRENS_KM -> MaterialTheme.colorScheme.error
        deel > 0.7f -> Color(0xFFB07A00)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Privé ${overzicht?.jaar ?: peildatum.year}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text("$gebruikt van $PRIVE_GRENS_KM km", style = MaterialTheme.typography.headlineSmall, color = kleur)
            LinearProgressIndicator(progress = { deel }, color = kleur, modifier = Modifier.fillMaxWidth())
            if (overzicht != null) {
                val prognose = overzicht.prognosePriveKm(peildatum)
                Text(
                    "Op dit tempo ${prognose} km over heel ${overzicht.jaar}." +
                        if (prognose > PRIVE_GRENS_KM) " Dat is boven de grens." else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (overzicht.nietVerantwoordeKm > 0) {
                    Text(
                        "Waarvan ${overzicht.nietVerantwoordeKm} km niet verantwoord; die tellen bij een controle als privé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Btw-correctie: ${overzicht.priveKmBtw} km privé inclusief woon-werk " +
                        "(${"%.1f".format(overzicht.btwPriveAandeel * 100)}%).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun SoortKeuze(soort: Ritsoort, modifier: Modifier = Modifier, onSoort: (Ritsoort) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ritsoort.entries.forEach { optie ->
            FilterChip(
                selected = optie == soort,
                onClick = { onSoort(optie) },
                label = { Text(optie.label) },
            )
        }
    }
}

@Composable
fun TekstVeld(
    label: String,
    waarde: String,
    onWaarde: (String) -> Unit,
    modifier: Modifier = Modifier,
    enkelRegel: Boolean = true,
    uitleg: String? = null,
    ingeschakeld: Boolean = true,
) {
    OutlinedTextField(
        value = waarde,
        onValueChange = onWaarde,
        label = { Text(label) },
        singleLine = enkelRegel,
        enabled = ingeschakeld,
        supportingText = uitleg?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun KmVeld(
    label: String,
    waarde: Int,
    onWaarde: (Int) -> Unit,
    modifier: Modifier = Modifier,
    uitleg: String? = null,
    ingeschakeld: Boolean = true,
) {
    var tekst by remember(waarde) { mutableStateOf(if (waarde == 0) "" else waarde.toString()) }
    OutlinedTextField(
        value = tekst,
        onValueChange = { nieuw ->
            val schoon = nieuw.filter { it.isDigit() }.take(8)
            tekst = schoon
            onWaarde(schoon.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = ingeschakeld,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = uitleg?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun DatumVeld(
    label: String,
    datum: LocalDate,
    modifier: Modifier = Modifier,
    onDatum: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = datum.format(dagFormaat),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.DateRange, contentDescription = "Kies datum") }
        },
        modifier = modifier.fillMaxWidth(),
    )
    if (open) {
        val stand = rememberDatePickerState(
            initialSelectedDateMillis = datum.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    stand.selectedDateMillis?.let {
                        onDatum(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("Kiezen") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuleren") } },
        ) {
            DatePicker(state = stand)
        }
    }
}

@Composable
fun RitRegel(rit: Rit, foutmelding: String?, onKlik: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onKlik, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rit.datum.format(kortFormaat), style = MaterialTheme.typography.labelLarge)
                Text("${rit.afstandKm} km", style = MaterialTheme.typography.titleMedium)
            }
            Text("${rit.beginadres} → ${rit.eindadres}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    rit.soort.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rit.soort == Ritsoort.PRIVE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    "${rit.beginstandKm} → ${rit.eindstandKm}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rit.vergrendeld) {
                    Text("vergrendeld", style = MaterialTheme.typography.labelSmall)
                }
                if (!rit.bevestigd) {
                    Text(
                        "bevestigen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (rit.doel.isNotBlank()) {
                Text(rit.doel, style = MaterialTheme.typography.bodySmall)
            }
            if (foutmelding != null) {
                Text(foutmelding, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun Bevindingregel(bevinding: Bevinding, modifier: Modifier = Modifier) {
    val kleur = when (bevinding.ernst) {
        Ernst.FOUT -> MaterialTheme.colorScheme.error
        Ernst.WAARSCHUWING -> Color(0xFFB07A00)
        Ernst.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            when (bevinding.ernst) {
                Ernst.FOUT -> "Fout"
                Ernst.WAARSCHUWING -> "Let op"
                Ernst.INFO -> "Ter info"
            },
            style = MaterialTheme.typography.labelSmall,
            color = kleur,
        )
        Text(bevinding.melding, style = MaterialTheme.typography.bodyMedium)
    }
}
