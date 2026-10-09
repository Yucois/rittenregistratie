package nl.exitinflex.rittenregistratie.data

import android.content.Context

data class Gekoppeldeauto(val adres: String, val naam: String)

/**
 * De koppeling met de bluetooth van de auto, en of er automatisch gelogd wordt.
 *
 * Deze twee staan bewust in gewone voorkeuren en niet in de DataStore: een
 * ontvanger die wakker wordt van een bluetooth-verbinding moet meteen weten of
 * hij iets moet doen, zonder eerst op een coroutine te wachten.
 */
object Autoinstellingen {

    private const val OPSLAG = "auto_koppeling"
    private const val AAN = "automatisch"
    private const val ADRES = "adres"
    private const val NAAM = "naam"

    fun automatisch(context: Context): Boolean =
        opslag(context).getBoolean(AAN, false)

    fun zetAutomatisch(context: Context, aan: Boolean) {
        opslag(context).edit().putBoolean(AAN, aan).apply()
    }

    fun auto(context: Context): Gekoppeldeauto? {
        val opslag = opslag(context)
        val adres = opslag.getString(ADRES, null) ?: return null
        return Gekoppeldeauto(adres, opslag.getString(NAAM, "").orEmpty())
    }

    fun zetAuto(context: Context, auto: Gekoppeldeauto) {
        opslag(context).edit()
            .putString(ADRES, auto.adres)
            .putString(NAAM, auto.naam)
            .putBoolean(AAN, true)
            .apply()
    }

    fun wisAuto(context: Context) {
        opslag(context).edit().remove(ADRES).remove(NAAM).putBoolean(AAN, false).apply()
    }

    private fun opslag(context: Context) =
        context.applicationContext.getSharedPreferences(OPSLAG, Context.MODE_PRIVATE)
}
