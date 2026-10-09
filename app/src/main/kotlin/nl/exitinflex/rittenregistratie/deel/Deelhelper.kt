package nl.exitinflex.rittenregistratie.deel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Schrijft een export naar de cache en biedt hem aan het deelmenu aan, zodat
 * het bestand naar de boekhouder, de e-mail of de cloudopslag kan.
 */
object Deelhelper {

    fun deel(context: Context, bestandsnaam: String, inhoud: String, mime: String, titel: String) {
        val uri = schrijf(context, bestandsnaam, inhoud)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, titel)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, titel))
    }

    fun schrijf(context: Context, bestandsnaam: String, inhoud: String): Uri {
        val map = File(context.cacheDir, "export").apply { mkdirs() }
        val bestand = File(map, bestandsnaam)
        bestand.writeText(inhoud)
        return FileProvider.getUriForFile(context, "${context.packageName}.bestanden", bestand)
    }

    fun lees(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Het bestand kon niet worden gelezen.")
}
