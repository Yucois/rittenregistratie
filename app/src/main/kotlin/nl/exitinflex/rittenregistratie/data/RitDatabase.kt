package nl.exitinflex.rittenregistratie.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RitEntiteit::class, VoertuigEntiteit::class, RitRevisieEntiteit::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RitDatabase : RoomDatabase() {

    abstract fun ritDao(): RitDao

    companion object {
        @Volatile
        private var instantie: RitDatabase? = null

        fun verkrijg(context: Context): RitDatabase = instantie ?: synchronized(this) {
            instantie ?: Room.databaseBuilder(
                context.applicationContext,
                RitDatabase::class.java,
                "rittenregistratie.db",
            ).build().also { instantie = it }
        }
    }
}
