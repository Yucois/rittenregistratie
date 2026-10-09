package nl.exitinflex.rittenregistratie.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface RitDao {

    @Query("SELECT * FROM rit ORDER BY beginstandKm DESC, datum DESC")
    fun alleRitten(): Flow<List<RitEntiteit>>

    @Query("SELECT * FROM rit WHERE voertuigId = :voertuigId ORDER BY beginstandKm DESC, datum DESC")
    fun rittenVan(voertuigId: String): Flow<List<RitEntiteit>>

    @Query("SELECT * FROM rit ORDER BY beginstandKm, datum")
    suspend fun alleRittenNu(): List<RitEntiteit>

    @Query("SELECT * FROM rit ORDER BY beginstandKm DESC, datum DESC LIMIT 1")
    suspend fun laatsteRit(): RitEntiteit?

    @Query("SELECT * FROM rit WHERE id = :id")
    suspend fun rit(id: String): RitEntiteit?

    @Upsert
    suspend fun bewaar(rit: RitEntiteit)

    @Query("DELETE FROM rit WHERE id = :id")
    suspend fun verwijder(id: String)

    @Query("SELECT MAX(eindstandKm) FROM rit WHERE voertuigId = :voertuigId")
    suspend fun hoogsteStand(voertuigId: String): Int?

    @Query(
        "UPDATE rit SET vergrendeldOp = :moment " +
            "WHERE voertuigId = :voertuigId AND datum <= :totEnMet AND vergrendeldOp IS NULL",
    )
    suspend fun vergrendelTot(voertuigId: String, totEnMet: LocalDate, moment: Instant): Int

    @Query("SELECT * FROM voertuig LIMIT 1")
    fun voertuig(): Flow<VoertuigEntiteit?>

    @Query("SELECT * FROM voertuig LIMIT 1")
    suspend fun voertuigNu(): VoertuigEntiteit?

    @Upsert
    suspend fun bewaarVoertuig(voertuig: VoertuigEntiteit)

    @Insert
    suspend fun bewaarRevisie(revisie: RitRevisieEntiteit)

    @Query("SELECT * FROM rit_revisie WHERE ritId = :ritId ORDER BY moment DESC")
    suspend fun revisies(ritId: String): List<RitRevisieEntiteit>

    @Query("SELECT COUNT(*) FROM rit_revisie")
    fun aantalRevisies(): Flow<Int>
}
