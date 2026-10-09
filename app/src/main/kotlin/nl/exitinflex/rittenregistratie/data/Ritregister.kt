package nl.exitinflex.rittenregistratie.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.exitinflex.rittenregistratie.kern.Backup
import nl.exitinflex.rittenregistratie.kern.Controleresultaat
import nl.exitinflex.rittenregistratie.kern.Rit
import nl.exitinflex.rittenregistratie.kern.Rittencontrole
import nl.exitinflex.rittenregistratie.kern.Voertuig
import java.time.Instant
import java.time.LocalDate

/**
 * De enige plek waar ritten in en uit de database gaan. Houdt de
 * wijzigingsgeschiedenis bij en weigert wijzigingen aan vergrendelde ritten.
 */
class Ritregister(private val dao: RitDao) {

    private val json = Json { encodeDefaults = true }

    val voertuig: Flow<Voertuig?> = dao.voertuig().map { it?.naarModel() }

    val ritten: Flow<List<Rit>> = dao.alleRitten().map { lijst -> lijst.map { it.naarModel() } }

    /** Doorlopende controle op sluitendheid en op de 500-kilometergrens. */
    val controle: Flow<Controleresultaat?> = combine(voertuig, ritten) { auto, ritten ->
        auto?.let { Rittencontrole.controleer(it, ritten, LocalDate.now()) }
    }

    suspend fun rit(id: String): Rit? = dao.rit(id)?.naarModel()

    suspend fun voertuigNu(): Voertuig? = dao.voertuigNu()?.naarModel()

    /** Alle ritten, voor de app die eruit leert wat een bestemming betekent. */
    suspend fun alleRitten(): List<Rit> = dao.alleRittenNu().map { it.naarModel() }

    /** De rit met de hoogste tellerstand; het vertrekpunt voor de volgende. */
    suspend fun laatsteRit(): Rit? = dao.laatsteRit()?.naarModel()

    /** Tellerstand waarop de volgende rit moet beginnen om de reeks sluitend te houden. */
    suspend fun volgendeBeginstand(): Int {
        val auto = dao.voertuigNu() ?: return 0
        return dao.hoogsteStand(auto.id) ?: auto.beginstandKm
    }

    suspend fun bewaar(rit: Rit): Result<Unit> {
        val bestaand = dao.rit(rit.id)
        if (bestaand?.vergrendeldOp != null) {
            return Result.failure(
                IllegalStateException(
                    "Deze rit is vergrendeld omdat de periode is afgesloten. " +
                        "Corrigeer met een aanvullende rit in plaats van met een wijziging.",
                ),
            )
        }
        val nu = Instant.now()
        if (bestaand != null) {
            dao.bewaarRevisie(
                RitRevisieEntiteit(
                    ritId = rit.id,
                    moment = nu,
                    soortWijziging = "GEWIJZIGD",
                    vorigeVersie = json.encodeToString(bestaand.naarModel()),
                ),
            )
        }
        dao.bewaar(
            rit.copy(
                aangemaaktOp = bestaand?.aangemaaktOp ?: nu,
                gewijzigdOp = nu,
            ).naarEntiteit(),
        )
        return Result.success(Unit)
    }

    suspend fun verwijder(id: String): Result<Unit> {
        val bestaand = dao.rit(id) ?: return Result.success(Unit)
        if (bestaand.vergrendeldOp != null) {
            return Result.failure(IllegalStateException("Deze rit is vergrendeld en kan niet worden verwijderd."))
        }
        dao.bewaarRevisie(
            RitRevisieEntiteit(
                ritId = id,
                moment = Instant.now(),
                soortWijziging = "VERWIJDERD",
                vorigeVersie = json.encodeToString(bestaand.naarModel()),
            ),
        )
        dao.verwijder(id)
        return Result.success(Unit)
    }

    suspend fun bewaarVoertuig(voertuig: Voertuig) = dao.bewaarVoertuig(voertuig.naarEntiteit())

    /**
     * Sluit een periode af: alle ritten tot en met [totEnMet] worden vergrendeld.
     * Daarna zijn correcties alleen nog zichtbaar als aanvullende ritten.
     */
    suspend fun vergrendelTot(totEnMet: LocalDate): Int {
        val auto = dao.voertuigNu() ?: return 0
        return dao.vergrendelTot(auto.id, totEnMet, Instant.now())
    }

    suspend fun revisies(ritId: String): List<RitRevisieEntiteit> = dao.revisies(ritId)

    suspend fun maakBackup(): Backup = Backup(
        gemaaktOp = Instant.now(),
        voertuigen = listOfNotNull(dao.voertuigNu()?.naarModel()),
        ritten = dao.alleRittenNu().map { it.naarModel() },
    )

    /** Zet een back-up terug. Bestaande ritten met dezelfde id worden overschreven. */
    suspend fun herstel(backup: Backup) {
        backup.voertuigen.forEach { dao.bewaarVoertuig(it.naarEntiteit()) }
        backup.ritten.forEach { dao.bewaar(it.naarEntiteit()) }
    }
}
