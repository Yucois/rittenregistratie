@file:OptIn(ExperimentalMaterial3Api::class)

package nl.exitinflex.rittenregistratie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.exitinflex.rittenregistratie.deel.Deelhelper
import nl.exitinflex.rittenregistratie.ui.schermen.BewerkScherm
import nl.exitinflex.rittenregistratie.ui.schermen.ControleScherm
import nl.exitinflex.rittenregistratie.ui.schermen.ExportScherm
import nl.exitinflex.rittenregistratie.ui.schermen.InstellingenScherm
import nl.exitinflex.rittenregistratie.ui.schermen.LiveScherm
import nl.exitinflex.rittenregistratie.ui.schermen.RittenScherm

private data class Tabblad(val route: String, val label: String, val icoon: ImageVector)

private val tabbladen = listOf(
    Tabblad(Routes.RITTEN, "Ritten", Icons.Default.DirectionsCar),
    Tabblad(Routes.CONTROLE, "Controle", Icons.Default.Checklist),
    Tabblad(Routes.EXPORT, "Export", Icons.Default.Share),
    Tabblad(Routes.INSTELLINGEN, "Auto", Icons.Default.Settings),
)

object Routes {
    const val RITTEN = "ritten"
    const val BEWERK = "bewerk"
    const val LIVE = "live"
    const val CONTROLE = "controle"
    const val EXPORT = "export"
    const val INSTELLINGEN = "instellingen"
}

@Composable
fun Rittenapp(
    teOpenenRit: String? = null,
    opRitGeopend: () -> Unit = {},
    viewModel: RitViewModel = viewModel(factory = RitViewModel.Fabriek),
) {
    val navigatie = rememberNavController()
    val context = LocalContext.current
    val meldingstand = remember { SnackbarHostState() }
    val melding by viewModel.melding.collectAsState()
    val export by viewModel.export.collectAsState()
    val huidigeRoute by navigatie.currentBackStackEntryAsState()

    // Zonder toestemming voor meldingen komt de bevestigingsvraag na een
    // automatisch vastgelegde rit nergens terecht. Daarom meteen bij het openen
    // vragen, en niet pas als je zelf een meting start.
    val meldingenVrager = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val gegeven = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!gegeven) meldingenVrager.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(teOpenenRit) {
        if (!teOpenenRit.isNullOrBlank()) {
            viewModel.kiesRit(teOpenenRit)
            navigatie.navigate(Routes.BEWERK)
            opRitGeopend()
        }
    }

    LaunchedEffect(melding) {
        melding?.let {
            meldingstand.showSnackbar(it)
            viewModel.meldingGelezen()
        }
    }

    LaunchedEffect(export) {
        export?.let {
            runCatching { Deelhelper.deel(context, it.naam, it.inhoud, it.mime, it.titel) }
                .onFailure { fout -> viewModel.meld("Delen lukte niet: ${fout.message}") }
            viewModel.exportVerwerkt()
        }
    }

    val route = huidigeRoute?.destination?.route
    val toonBalk = route == null || tabbladen.any { it.route == route }

    Scaffold(
        snackbarHost = { SnackbarHost(meldingstand) },
        bottomBar = {
            if (toonBalk) {
                NavigationBar {
                    val bestemming = huidigeRoute?.destination
                    tabbladen.forEach { tab ->
                        NavigationBarItem(
                            selected = bestemming?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navigatie.navigate(tab.route) {
                                    popUpTo(navigatie.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icoon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { ruimte ->
        NavHost(
            navController = navigatie,
            startDestination = Routes.RITTEN,
            modifier = Modifier.padding(ruimte),
        ) {
            composable(Routes.RITTEN) {
                RittenScherm(
                    viewModel = viewModel,
                    opNieuweRit = {
                        viewModel.kiesRit(null)
                        navigatie.navigate(Routes.BEWERK)
                    },
                    opRit = { id ->
                        viewModel.kiesRit(id)
                        navigatie.navigate(Routes.BEWERK)
                    },
                    opLive = { navigatie.navigate(Routes.LIVE) },
                    opAuto = {
                        navigatie.navigate(Routes.INSTELLINGEN) {
                            popUpTo(navigatie.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.BEWERK) {
                BewerkScherm(
                    viewModel = viewModel,
                    opTerug = { navigatie.popBackStack() },
                )
            }
            composable(Routes.LIVE) {
                LiveScherm(
                    viewModel = viewModel,
                    opAfronden = {
                        navigatie.popBackStack()
                        navigatie.navigate(Routes.BEWERK)
                    },
                    opTerug = { navigatie.popBackStack() },
                )
            }
            composable(Routes.CONTROLE) {
                ControleScherm(
                    viewModel = viewModel,
                    opRit = { id ->
                        viewModel.kiesRit(id)
                        navigatie.navigate(Routes.BEWERK)
                    },
                )
            }
            composable(Routes.EXPORT) { ExportScherm(viewModel = viewModel) }
            composable(Routes.INSTELLINGEN) { InstellingenScherm(viewModel = viewModel) }
        }
    }
}
