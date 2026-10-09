package nl.exitinflex.rittenregistratie.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.exitinflex.rittenregistratie.RitApp
import nl.exitinflex.rittenregistratie.data.Autoinstellingen
import nl.exitinflex.rittenregistratie.data.Gekoppeldeauto
import nl.exitinflex.rittenregistratie.data.Instellingen
import nl.exitinflex.rittenregistratie.data.Ritregister
import nl.exitinflex.rittenregistratie.data.Voorkeuren
import nl.exitinflex.rittenregistratie.kern.Backup
import nl.exitinflex.rittenregistratie.kern.BackupOpslag
import nl.exitinflex.rittenregistratie.kern.Controleresultaat
import nl.exitinflex.rittenregistratie.kern.CsvExport
import nl.exitinflex.rittenregistratie.auto.HomeAssistantBron
import nl.exitinflex.rittenregistratie.auto.Proefuitkomst
import nl.exitinflex.rittenregistratie.auto.Sensorkeuze
import nl.exitinflex.rittenregistratie.auto.Standbroninstellingen
import nl.exitinflex.rittenregistratie.kern.HtmlRapport
import nl.exitinflex.rittenregistratie.kern.Ijking
import nl.exitinflex.rittenregistratie.kern.Rit
import nl.exitinflex.rittenregistratie.kern.Ritbron
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import nl.exitinflex.rittenregistratie.kern.Voertuig
import nl.exitinflex.rittenregistratie.locatie.Adreszoeker
import nl.exitinflex.rittenregistratie.locatie.Gekoppeldeapparaten
import nl.exitinflex.rittenregistratie.locatie.RitDienst
import nl.exitinflex.rittenregistratie.locatie.Ritregelaar
import nl.exitinflex.rittenregistratie.locatie.Ritafronder
import nl.exitinflex.rittenregistratie.locatie.Ritmeter
import nl.exitinflex.rittenregistratie.locatie.Ritmeting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

data class AppStand(
    val voertuig: Voertuig? = null,
    val ritten: List<Rit> = emptyList(),
    val controle: Controleresultaat? = null,
    val voorkeuren: Voorkeuren = Voorkeuren(),
)

data class Autokoppelstand(
    val auto: Gekoppeldeauto? = null,
    val automatisch: Boolean = false,
)

/** Eenmalige gebeurtenis: een bestand dat aan het deelmenu moet worden aangeboden. */
data class Exportbestand(
    val naam: String,
    val inhoud: String,
    val mime: String,
    val titel: String,
)

class RitViewModel(
    private val register: Ritregister,
    private val instellingen: Instellingen,
    private val adreszoeker: Adreszoeker,
) : ViewModel() {

    val stand: StateFlow<AppStand> = combine(
        register.voertuig,
        register.ritten,
        register.controle,
        instellingen.voorkeuren,
    ) { voertuig, ritten, controle, voorkeuren ->
        AppStand(voertuig, ritten, controle, voorkeuren)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStand())

    val meting: StateFlow<Ritmeting> = Ritmeter.meting

    private val _melding = MutableStateFlow<String?>(null)
    val melding: StateFlow<String?> = _melding.asStateFlow()

    /** De rit die in het bewerkscherm staat; nieuw of opgehaald. */
    private val _bewerkRit = MutableStateFlow<Rit?>(null)
    val bewerkRit: StateFlow<Rit?> = _bewerkRit.asStateFlow()

    private val _export = MutableStateFlow<Exportbestand?>(null)
    val export: StateFlow<Exportbestand?> = _export.asStateFlow()

    private val _autokoppeling = MutableStateFlow(Autokoppelstand())
    val autokoppeling: StateFlow<Autokoppelstand> = _autokoppeling.asStateFlow()

    private val _apparaten = MutableStateFlow<List<Gekoppeldeauto>>(emptyList())
    val apparaten: StateFlow<List<Gekoppeldeauto>> = _apparaten.asStateFlow()

    private val _standbron = MutableStateFlow(Standbroninstellingen.Config())
    val standbron: StateFlow<Standbroninstellingen.Config> = _standbron.asStateFlow()

    private val _sensoren = MutableStateFlow<List<Sensorkeuze>>(emptyList())
    val sensoren: StateFlow<List<Sensorkeuze>> = _sensoren.asStateFlow()

    /** Kilometers die de auto wel heeft gereden maar die niet in de registratie staan. */
    private val _nietVastgelegdeKm = MutableStateFlow(0)
    val nietVastgelegdeKm: StateFlow<Int> = _nietVastgelegdeKm.asStateFlow()

    fun meldingGelezen() {
        _melding.value = null
    }

    fun exportVerwerkt() {
        _export.value = null
    }

    fun meld(tekst: String) {
        _melding.value = tekst
    }

    // ---- Ritten ------------------------------------------------------------

    /** Zet een bestaande rit klaar om te bewerken, of maak een nieuwe aan als [id] leeg is. */
    fun kiesRit(id: String?) {
        viewModelScope.launch {
            _bewerkRit.value = if (id.isNullOrBlank()) nieuweRit() else register.rit(id)
        }
    }

    private suspend fun nieuweRit(): Rit {
        val voorkeuren = stand.value.voorkeuren
        val beginstand = register.volgendeBeginstand()
        val laatste = stand.value.ritten.maxByOrNull { it.eindstandKm }
        val nu = Instant.now()
        return Rit(
            id = UUID.randomUUID().toString(),
            voertuigId = register.voertuigNu()?.id.orEmpty(),
            datum = LocalDate.now(),
            beginstandKm = beginstand,
            eindstandKm = beginstand,
            // Vertrekken doe je waar je de vorige keer aankwam; dat houdt de reeks sluitend.
            beginadres = laatste?.eindadres.orEmpty().ifBlank { voorkeuren.thuisadres },
            eindadres = "",
            soort = voorkeuren.standaardSoort,
            aangemaaktOp = nu,
            gewijzigdOp = nu,
        )
    }

    fun bewerk(wijziging: (Rit) -> Rit) {
        _bewerkRit.value = _bewerkRit.value?.let(wijziging)
    }

    fun bewaarBewerkteRit(klaar: () -> Unit) {
        val rit = _bewerkRit.value ?: return
        viewModelScope.launch {
            val voertuig = register.voertuigNu()
            if (voertuig == null) {
                _melding.value = "Leg eerst de auto vast bij Instellingen."
                return@launch
            }
            // Handmatig opslaan is een bevestiging; de rit is daarmee af.
            val teBewaren = rit.copy(voertuigId = voertuig.id, bevestigd = true)
            register.bewaar(teBewaren)
                .onSuccess {
                    _bewerkRit.value = null
                    klaar()
                }
                .onFailure { _melding.value = it.message }
        }
    }

    fun verwijderRit(id: String, klaar: () -> Unit) {
        viewModelScope.launch {
            register.verwijder(id)
                .onSuccess { klaar() }
                .onFailure { _melding.value = it.message }
        }
    }

    fun vergrendelTot(datum: LocalDate) {
        viewModelScope.launch {
            val aantal = register.vergrendelTot(datum)
            _melding.value = "$aantal ritten vergrendeld tot en met $datum."
        }
    }

    // ---- Meting ------------------------------------------------------------

    /** Begint de gps-meting; de dienst op de voorgrond houdt hem levend als het scherm uit gaat. */
    fun startMeting(context: Context) {
        // De dienst begint zelf een nieuwe meting; zo is er één startpunt,
        // of de rit nu met de hand of door de auto wordt begonnen.
        RitDienst.startHandmatig(context)
    }

    /**
     * Stopt de meting, bewaart de rit meteen en zet hem klaar ter bevestiging.
     * Eerst bewaren, dan invullen: een rit die je vergeet in te vullen mag niet
     * verdwijnen, want dan valt er een gat in de reeks.
     */
    fun stopMeting(context: Context, klaar: () -> Unit) {
        RitDienst.stop(context)
        viewModelScope.launch {
            val gemeten = Ritmeter.stop(context)
            val rit = runCatching { Ritafronder.rondAf(context, gemeten) }.getOrNull()
            if (rit == null) {
                _melding.value = if (register.voertuigNu() == null) {
                    "Leg eerst de auto vast bij Instellingen."
                } else {
                    "Te korte rit; er is niets vastgelegd."
                }
                return@launch
            }
            _bewerkRit.value = rit
            klaar()
        }
    }

    fun annuleerMeting(context: Context) {
        Ritmeter.wis(context)
        RitDienst.stop(context)
    }

    // ---- Instellingen ------------------------------------------------------

    fun bewaarVoertuig(voertuig: Voertuig) {
        viewModelScope.launch {
            register.bewaarVoertuig(voertuig)
            _melding.value = "Auto opgeslagen."
        }
    }

    fun bewaarVoorkeuren(voorkeuren: Voorkeuren) {
        viewModelScope.launch { instellingen.bewaar(voorkeuren) }
    }

    // ---- Automatisch loggen ------------------------------------------------

    fun laadAutokoppeling(context: Context) {
        _autokoppeling.value = Autokoppelstand(
            auto = Autoinstellingen.auto(context),
            automatisch = Autoinstellingen.automatisch(context),
        )
    }

    /** De bluetooth-apparaten die al met de telefoon gekoppeld zijn. */
    fun laadApparaten(context: Context) {
        val gevonden = Gekoppeldeapparaten.lijst(context)
        _apparaten.value = gevonden
        if (gevonden.isEmpty()) {
            _melding.value = "Geen gekoppelde bluetooth-apparaten gevonden. Verbind eerst met de auto."
        }
    }

    fun apparatenGesloten() {
        _apparaten.value = emptyList()
    }

    fun koppelAuto(context: Context, auto: Gekoppeldeauto) {
        Autoinstellingen.zetAuto(context, auto)
        _apparaten.value = emptyList()
        laadAutokoppeling(context)
        herbeoordeel(context, "auto gekoppeld")
        _melding.value = "Gekoppeld met ${auto.naam}. Ritten starten voortaan vanzelf."
    }

    /** Opnieuw bekijken of er een rit moet beginnen of eindigen, bijvoorbeeld na een instelling. */
    fun herbeoordeel(context: Context, aanleiding: String) {
        val toepassing = context.applicationContext
        viewModelScope.launch { Ritregelaar.evalueer(toepassing, null, aanleiding, vanafVoorgrond = true) }
    }

    fun ontkoppelAuto(context: Context) {
        Autoinstellingen.wisAuto(context)
        herbeoordeel(context, "koppeling verbroken")
        laadAutokoppeling(context)
        _melding.value = "Koppeling verbroken."
    }

    fun zetAutomatisch(context: Context, aan: Boolean) {
        Autoinstellingen.zetAutomatisch(context, aan)
        herbeoordeel(context, if (aan) "automatisch aangezet" else "automatisch uitgezet")
        laadAutokoppeling(context)
    }

    // ---- Kilometerstand uit de auto ----------------------------------------

    fun laadStandbron(context: Context) {
        _standbron.value = Standbroninstellingen.config(context)
    }

    fun bewaarStandbron(context: Context, config: Standbroninstellingen.Config) {
        Standbroninstellingen.bewaar(context, config)
        laadStandbron(context)
    }

    /** Zoekt zelf naar de sensor met de kilometerstand, zodat je die naam niet hoeft op te zoeken. */
    fun zoekSensoren(context: Context, config: Standbroninstellingen.Config) {
        viewModelScope.launch {
            val gevonden = HomeAssistantBron(config).zoekSensoren()
            _sensoren.value = gevonden
            if (gevonden.isEmpty()) {
                _melding.value = "Geen sensor gevonden die op een kilometerstand lijkt."
            }
        }
    }

    fun sensorenGesloten() {
        _sensoren.value = emptyList()
    }

    /**
     * Vergelijkt de stand van de auto met de laatst geregistreerde stand. Zo zie
     * je een vergeten rit meteen, in plaats van pas bij een controle.
     */
    fun controleerTegenAuto(context: Context) {
        val bron = Standbroninstellingen.bron(context) ?: return
        viewModelScope.launch {
            val standNu = runCatching { bron.huidigeStand() }.getOrNull() ?: return@launch
            val laatste = register.volgendeBeginstand()
            _nietVastgelegdeKm.value = (standNu - laatste).coerceAtLeast(0)
        }
    }

    /** Haalt de stand één keer op, zodat je ziet of de koppeling werkt. */
    fun proefStandbron(context: Context) {
        val config = Standbroninstellingen.config(context)
        viewModelScope.launch {
            when (val uitkomst = HomeAssistantBron(config).haal()) {
                is Proefuitkomst.Gelukt ->
                    _melding.value = "Gelukt: de auto staat op ${uitkomst.stand} km."
                is Proefuitkomst.Mislukt ->
                    _melding.value = uitkomst.melding
            }
        }
    }

    /**
     * Legt de registratie naast de werkelijke tellerstand. Een verschil komt er
     * als aparte correctierit in, zodat de reeks sluitend blijft en zichtbaar is
     * waar de kilometers vandaan komen.
     */
    fun ijk(werkelijkeStand: Int) {
        viewModelScope.launch {
            val voertuig = register.voertuigNu()
            if (voertuig == null) {
                _melding.value = "Leg eerst de auto vast."
                return@launch
            }
            val laatste = register.volgendeBeginstand()
            val uitkomst = Ijking.ijk(
                voertuig = voertuig,
                laatsteStand = laatste,
                werkelijkeStand = werkelijkeStand,
                datum = LocalDate.now(),
                ritId = UUID.randomUUID().toString(),
            )
            when (uitkomst) {
                Ijking.Uitkomst.Gelijk -> {
                    _nietVastgelegdeKm.value = 0
                    instellingen.bewaar(stand.value.voorkeuren.copy(laatsteIjking = LocalDate.now()))
                    _melding.value = "De registratie loopt gelijk met de teller."
                }
                is Ijking.Uitkomst.Onmogelijk -> _melding.value = uitkomst.melding
                is Ijking.Uitkomst.Correctie -> {
                    register.bewaar(uitkomst.rit)
                    _nietVastgelegdeKm.value = 0
                    instellingen.bewaar(stand.value.voorkeuren.copy(laatsteIjking = LocalDate.now()))
                    _melding.value =
                        "${uitkomst.verschilKm} km stond niet in de registratie; toegevoegd als privé-correctie."
                }
            }
        }
    }

    // ---- Export ------------------------------------------------------------

    fun exporteerCsv(jaar: Int) {
        val voertuig = stand.value.voertuig ?: return meld("Leg eerst de auto vast.")
        val ritten = stand.value.ritten.filter { it.datum.year == jaar }
        _export.value = Exportbestand(
            naam = CsvExport.bestandsnaam(voertuig, jaar),
            inhoud = CsvExport.naarCsv(voertuig, ritten),
            mime = "text/csv",
            titel = "Rittenregistratie $jaar",
        )
    }

    fun exporteerRapport(jaar: Int) {
        val huidig = stand.value
        val voertuig = huidig.voertuig ?: return meld("Leg eerst de auto vast.")
        val controle = huidig.controle ?: return meld("Nog geen gegevens om te controleren.")
        _export.value = Exportbestand(
            naam = HtmlRapport.bestandsnaam(voertuig, jaar),
            inhoud = HtmlRapport.naarHtml(voertuig, huidig.ritten, jaar, controle),
            mime = "text/html",
            titel = "Rittenregistratie $jaar",
        )
    }

    fun exporteerBackup() {
        viewModelScope.launch {
            val backup = register.maakBackup()
            _export.value = Exportbestand(
                naam = BackupOpslag.bestandsnaam(backup.gemaaktOp),
                inhoud = BackupOpslag.schrijf(backup),
                mime = "application/json",
                titel = "Back-up rittenregistratie",
            )
        }
    }

    fun herstelBackup(inhoud: String) {
        viewModelScope.launch {
            runCatching { BackupOpslag.lees(inhoud) }
                .onSuccess { backup: Backup ->
                    register.herstel(backup)
                    _melding.value = "${backup.ritten.size} ritten teruggezet."
                }
                .onFailure { _melding.value = "Deze back-up kon niet worden gelezen: ${it.message}" }
        }
    }

    companion object {
        val Fabriek: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RitApp
                RitViewModel(app.bak.register, app.bak.instellingen, app.bak.adreszoeker)
            }
        }
    }
}
