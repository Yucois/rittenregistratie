package nl.exitinflex.rittenregistratie.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import nl.exitinflex.rittenregistratie.kern.Rit
import nl.exitinflex.rittenregistratie.kern.Ritbron
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import nl.exitinflex.rittenregistratie.kern.Voertuig
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "voertuig")
data class VoertuigEntiteit(
    @PrimaryKey val id: String,
    val merk: String,
    val type: String,
    val kenteken: String,
    val terBeschikkingVanaf: LocalDate,
    val terBeschikkingTot: LocalDate?,
    val beginstandKm: Int,
)

@Entity(
    tableName = "rit",
    indices = [Index("voertuigId"), Index("beginstandKm"), Index("datum")],
)
data class RitEntiteit(
    @PrimaryKey val id: String,
    val voertuigId: String,
    val datum: LocalDate,
    val beginstandKm: Int,
    val eindstandKm: Int,
    val beginadres: String,
    val eindadres: String,
    val soort: Ritsoort,
    val doel: String,
    val afwijkendeRoute: String?,
    val priveOmrijkilometers: Int,
    val vertrektijd: LocalTime?,
    val aankomsttijd: LocalTime?,
    val opmerking: String?,
    val bron: Ritbron,
    val gemetenKm: Double?,
    val bevestigd: Boolean,
    val aangemaaktOp: Instant,
    val gewijzigdOp: Instant,
    val vergrendeldOp: Instant?,
)

/**
 * Wijzigingsgeschiedenis. Een rittenregistratie die achteraf spoorloos te
 * veranderen is, is bij een controle minder waard; hier blijft elke eerdere
 * versie van een rit bewaard.
 */
@Entity(tableName = "rit_revisie", indices = [Index("ritId")])
data class RitRevisieEntiteit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ritId: String,
    val moment: Instant,
    /** GEWIJZIGD of VERWIJDERD — wat er met de vorige versie gebeurde. */
    val soortWijziging: String,
    /** De vorige versie van de rit, als JSON. */
    val vorigeVersie: String,
)

fun RitEntiteit.naarModel(): Rit = Rit(
    id = id,
    voertuigId = voertuigId,
    datum = datum,
    beginstandKm = beginstandKm,
    eindstandKm = eindstandKm,
    beginadres = beginadres,
    eindadres = eindadres,
    soort = soort,
    doel = doel,
    afwijkendeRoute = afwijkendeRoute,
    priveOmrijkilometers = priveOmrijkilometers,
    vertrektijd = vertrektijd,
    aankomsttijd = aankomsttijd,
    opmerking = opmerking,
    bron = bron,
    gemetenKm = gemetenKm,
    bevestigd = bevestigd,
    aangemaaktOp = aangemaaktOp,
    gewijzigdOp = gewijzigdOp,
    vergrendeldOp = vergrendeldOp,
)

fun Rit.naarEntiteit(): RitEntiteit = RitEntiteit(
    id = id,
    voertuigId = voertuigId,
    datum = datum,
    beginstandKm = beginstandKm,
    eindstandKm = eindstandKm,
    beginadres = beginadres,
    eindadres = eindadres,
    soort = soort,
    doel = doel,
    afwijkendeRoute = afwijkendeRoute,
    priveOmrijkilometers = priveOmrijkilometers,
    vertrektijd = vertrektijd,
    aankomsttijd = aankomsttijd,
    opmerking = opmerking,
    bron = bron,
    gemetenKm = gemetenKm,
    bevestigd = bevestigd,
    aangemaaktOp = aangemaaktOp,
    gewijzigdOp = gewijzigdOp,
    vergrendeldOp = vergrendeldOp,
)

fun VoertuigEntiteit.naarModel(): Voertuig = Voertuig(
    id = id,
    merk = merk,
    type = type,
    kenteken = kenteken,
    terBeschikkingVanaf = terBeschikkingVanaf,
    terBeschikkingTot = terBeschikkingTot,
    beginstandKm = beginstandKm,
)

fun Voertuig.naarEntiteit(): VoertuigEntiteit = VoertuigEntiteit(
    id = id,
    merk = merk,
    type = type,
    kenteken = kenteken,
    terBeschikkingVanaf = terBeschikkingVanaf,
    terBeschikkingTot = terBeschikkingTot,
    beginstandKm = beginstandKm,
)
