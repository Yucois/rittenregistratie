package nl.exitinflex.rittenregistratie.data

import androidx.room.TypeConverter
import nl.exitinflex.rittenregistratie.kern.Ritbron
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Alles als ISO-tekst in de database: leesbaar bij inspectie en stabiel over versies heen. */
class Converters {
    @TypeConverter fun datumNaarTekst(waarde: LocalDate?): String? = waarde?.toString()
    @TypeConverter fun tekstNaarDatum(waarde: String?): LocalDate? = waarde?.let(LocalDate::parse)

    @TypeConverter fun tijdNaarTekst(waarde: LocalTime?): String? = waarde?.toString()
    @TypeConverter fun tekstNaarTijd(waarde: String?): LocalTime? = waarde?.let(LocalTime::parse)

    @TypeConverter fun momentNaarTekst(waarde: Instant?): String? = waarde?.toString()
    @TypeConverter fun tekstNaarMoment(waarde: String?): Instant? = waarde?.let(Instant::parse)

    @TypeConverter fun soortNaarTekst(waarde: Ritsoort): String = waarde.name
    @TypeConverter fun tekstNaarSoort(waarde: String): Ritsoort = Ritsoort.valueOf(waarde)

    @TypeConverter fun bronNaarTekst(waarde: Ritbron): String = waarde.name
    @TypeConverter fun tekstNaarBron(waarde: String): Ritbron = Ritbron.valueOf(waarde)
}
