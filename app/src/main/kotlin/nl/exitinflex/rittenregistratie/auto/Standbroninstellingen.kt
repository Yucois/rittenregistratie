package nl.exitinflex.rittenregistratie.auto

import android.content.Context

/**
 * Waar de app de kilometerstand vandaan haalt. In gewone voorkeuren, omdat de
 * dienst die een rit afsluit er meteen bij moet kunnen.
 */
object Standbroninstellingen {

    data class Config(
        val aan: Boolean = false,
        val basisAdres: String = "",
        val token: String = "",
        /** De sensor met de kilometerstand; de app zoekt hem zelf op met Sensor zoeken. */
        val entiteit: String = "",
    ) {
        val compleet: Boolean
            get() = basisAdres.isNotBlank() && token.isNotBlank() && entiteit.isNotBlank()

        val bruikbaar: Boolean get() = aan && compleet
    }

    private const val OPSLAG = "standbron"

    fun config(context: Context): Config {
        val opslag = opslag(context)
        return Config(
            aan = opslag.getBoolean("aan", false),
            basisAdres = opslag.getString("adres", "").orEmpty(),
            token = opslag.getString("token", "").orEmpty(),
            entiteit = opslag.getString("entiteit", "").orEmpty(),
        )
    }

    fun bewaar(context: Context, config: Config) {
        opslag(context).edit()
            .putBoolean("aan", config.aan)
            .putString("adres", config.basisAdres.trim())
            .putString("token", config.token.trim())
            .putString("entiteit", config.entiteit.trim())
            .apply()
    }

    /** De bron die nu gebruikt wordt, of null als er geen koppeling is ingesteld. */
    fun bron(context: Context): Kilometerstandbron? {
        val config = config(context)
        return if (config.bruikbaar) HomeAssistantBron(config) else null
    }

    private fun opslag(context: Context) =
        context.applicationContext.getSharedPreferences(OPSLAG, Context.MODE_PRIVATE)
}
