package nl.exitinflex.rittenregistratie.locatie

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wat de app op dit moment doet en wat hij het laatst heeft gezien. Om in de
 * app te tonen: zonder dat is niet te zien waarom er wel of geen rit loopt.
 */
object Dienststand {

    private val _meet = MutableStateFlow(false)
    val meet: StateFlow<Boolean> = _meet.asStateFlow()

    private val _laatsteGebeurtenis = MutableStateFlow("")
    val laatsteGebeurtenis: StateFlow<String> = _laatsteGebeurtenis.asStateFlow()

    internal fun zetMeten(meet: Boolean) {
        _meet.value = meet
    }

    internal fun meld(tekst: String) {
        _laatsteGebeurtenis.value = tekst
    }
}
