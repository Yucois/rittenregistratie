package nl.exitinflex.rittenregistratie.ui.thema

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Diep = Color(0xFF0B2B3C)
private val Staal = Color(0xFF3E6F8A)
private val Zand = Color(0xFFB08A4A)

private val LichtSchema = lightColorScheme(
    primary = Diep,
    secondary = Staal,
    tertiary = Zand,
)

private val DonkerSchema = darkColorScheme(
    primary = Color(0xFF9CC7DE),
    secondary = Color(0xFF8FB4C7),
    tertiary = Color(0xFFDCC08A),
)

@Composable
fun RittenTheme(
    donker: Boolean = isSystemInDarkTheme(),
    dynamisch: Boolean = true,
    inhoud: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val schema = when {
        dynamisch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (donker) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        donker -> DonkerSchema
        else -> LichtSchema
    }
    MaterialTheme(colorScheme = schema, content = inhoud)
}
