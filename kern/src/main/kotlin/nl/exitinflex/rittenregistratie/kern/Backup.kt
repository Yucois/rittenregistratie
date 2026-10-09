package nl.exitinflex.rittenregistratie.kern

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Volledige, leesbare kopie van de administratie. De Belastingdienst kan de
 * registratie tot zeven jaar terug opvragen; een telefoon gaat zo lang niet mee.
 */
@Serializable
data class Backup(
    val versie: Int = VERSIE,
    @Serializable(with = InstantSerializer::class)
    val gemaaktOp: Instant,
    val voertuigen: List<Voertuig>,
    val ritten: List<Rit>,
) {
    companion object { const val VERSIE = 1 }
}

object BackupOpslag {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun bestandsnaam(gemaaktOp: Instant): String =
        "rittenregistratie-backup-${gemaaktOp.toString().take(10)}.json"

    fun schrijf(backup: Backup): String = json.encodeToString(backup)

    fun lees(inhoud: String): Backup {
        val backup = json.decodeFromString<Backup>(inhoud)
        require(backup.versie <= Backup.VERSIE) {
            "Deze back-up komt uit een nieuwere versie van de app (versie ${backup.versie})."
        }
        return backup
    }
}
