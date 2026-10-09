package nl.exitinflex.rittenregistratie.kern

/**
 * Beslist wat er met een rit moet gebeuren als er iets verandert: de bluetooth
 * verbindt of verbreekt, de telefoon start op, of de app wordt geopend.
 *
 * Deze beslissing staat bewust los van Android, zodat hij met tests vast te
 * leggen is. Eerdere versies namen hem verspreid door de achtergronddienst, en
 * daar sloop een fout in: een herstart door Android werd gelezen als "er begint
 * een rit", waardoor er ritten begonnen tijdens het lopen en in andere auto's.
 *
 * De hoofdregel: een automatische rit bestaat alleen zolang de gekoppelde auto
 * aantoonbaar verbonden is. Wat de app niet zeker weet, laat hij ongemoeid.
 */
object Ritbesluit {

    enum class Actie {
        /** Niets doen. */
        NIETS,

        /** Een nieuwe automatische rit beginnen. */
        BEGIN,

        /** Er loopt al een rit en de auto is er nog: zorgen dat er gemeten wordt. */
        GA_DOOR,

        /** De auto is weg: de lopende automatische rit afsluiten. */
        AFRONDEN,
    }

    data class Toestand(
        /** Staat automatisch loggen aan? */
        val automatischAan: Boolean,
        /** Het bluetooth-adres van de gekozen auto, of null als er geen gekozen is. */
        val autoAdres: String?,
        /**
         * Is de gekozen auto nu verbonden? Null als dat niet vast te stellen is;
         * dan doet de app niets, want gokken levert juist de verkeerde ritten op.
         */
        val autoVerbonden: Boolean?,
        /** Loopt er op dit moment een rit? */
        val ritLoopt: Boolean,
        /** Is de lopende rit met de hand gestart? */
        val ritHandmatig: Boolean,
    )

    fun beslis(toestand: Toestand): Actie = with(toestand) {
        // Een handmatig gestarte rit is van de bestuurder, niet van de bluetooth.
        if (ritLoopt && ritHandmatig) return Actie.NIETS

        // Automatisch loggen uit, of geen auto gekozen: een lopende automatische
        // rit netjes afsluiten, verder niets.
        if (!automatischAan || autoAdres.isNullOrBlank()) {
            return if (ritLoopt) Actie.AFRONDEN else Actie.NIETS
        }

        return when (autoVerbonden) {
            null -> Actie.NIETS
            true -> if (ritLoopt) Actie.GA_DOOR else Actie.BEGIN
            false -> if (ritLoopt) Actie.AFRONDEN else Actie.NIETS
        }
    }

    /**
     * Wat een bluetooth-bericht zegt over de gekozen auto. Een bericht over een
     * ander apparaat — een hoortoestel, de radio van een andere auto — zegt er
     * niets over, en levert dus null op.
     */
    fun verbondenVolgensBericht(
        berichtAdres: String?,
        autoAdres: String?,
        verbonden: Boolean,
    ): Boolean? {
        if (berichtAdres.isNullOrBlank() || autoAdres.isNullOrBlank()) return null
        return if (berichtAdres.equals(autoAdres, ignoreCase = true)) verbonden else null
    }
}
