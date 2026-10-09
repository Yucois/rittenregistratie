package nl.exitinflex.rittenregistratie

import android.app.Application
import android.content.Context
import nl.exitinflex.rittenregistratie.data.Instellingen
import nl.exitinflex.rittenregistratie.data.RitDatabase
import nl.exitinflex.rittenregistratie.data.Ritregister
import nl.exitinflex.rittenregistratie.locatie.Adreszoeker
import nl.exitinflex.rittenregistratie.locatie.Ritmeter

/** Kleine, handgemaakte afhankelijkhedenbak; voor een app van deze omvang is een framework overdaad. */
class Bak(context: Context) {
    private val toepassing = context.applicationContext
    val register: Ritregister by lazy { Ritregister(RitDatabase.verkrijg(toepassing).ritDao()) }
    val instellingen: Instellingen by lazy { Instellingen(toepassing) }
    val adreszoeker: Adreszoeker by lazy { Adreszoeker(toepassing) }
}

class RitApp : Application() {

    lateinit var bak: Bak
        private set

    override fun onCreate() {
        super.onCreate()
        bak = Bak(this)
        // Een rit die liep toen het proces werd afgeschoten, komt hier terug.
        Ritmeter.herstel(this)
    }
}
