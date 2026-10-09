package nl.exitinflex.rittenregistratie.locatie

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nl.exitinflex.rittenregistratie.R
import nl.exitinflex.rittenregistratie.kern.Rit
import nl.exitinflex.rittenregistratie.kern.Ritsoort
import nl.exitinflex.rittenregistratie.ui.MainActivity

/**
 * De melding na een automatisch vastgelegde rit. Eén tik is genoeg: zakelijk,
 * woon-werk of privé. Wie meer wil invullen, opent de rit.
 */
object Ritmelding {

    const val KANAAL = "rit_bevestigen"

    fun toon(context: Context, rit: Rit) {
        maakKanaal(context)
        val beheer = NotificationManagerCompat.from(context)

        val openen = PendingIntent.getActivity(
            context,
            rit.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(MainActivity.EXTRA_RIT, rit.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val melding = NotificationCompat.Builder(context, KANAAL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(
                if (rit.bevestigd) {
                    "${rit.afstandKm} km vastgelegd als ${rit.soort.label.lowercase()}"
                } else {
                    "Rit van ${rit.afstandKm} km — bevestig het karakter"
                },
            )
            .setContentText("${rit.beginadres} → ${rit.eindadres}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${rit.beginadres} → ${rit.eindadres}\n" +
                        "Stand ${rit.beginstandKm} → ${rit.eindstandKm}.\n" +
                        Ritafronder.laatsteReden.ifBlank {
                            if (rit.bevestigd) "" else "Kies zakelijk, woon-werk of privé."
                        },
                ),
            )
            .setPriority(
                if (rit.bevestigd) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentIntent(openen)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Zakelijk", actie(context, rit.id, Ritsoort.ZAKELIJK))
            .addAction(0, "Woon-werk", actie(context, rit.id, Ritsoort.WOON_WERK))
            .addAction(0, "Privé", actie(context, rit.id, Ritsoort.PRIVE))
            .build()

        runCatching { beheer.notify(rit.id.hashCode(), melding) }
    }

    /** Iets ging mis wat de gebruiker moet weten; nooit stil laten falen. */
    fun probleem(context: Context, titel: String, tekst: String) {
        maakKanaal(context)
        val openen = PendingIntent.getActivity(
            context,
            PROBLEEM_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val melding = NotificationCompat.Builder(context, KANAAL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(titel)
            .setContentText(tekst)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tekst))
            .setContentIntent(openen)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(PROBLEEM_ID, melding) }
    }

    private const val PROBLEEM_ID = 4242

    fun verberg(context: Context, ritId: String) {
        NotificationManagerCompat.from(context).cancel(ritId.hashCode())
    }

    private fun actie(context: Context, ritId: String, soort: Ritsoort): PendingIntent {
        val intent = Intent(context, RitActieOntvanger::class.java)
            .setAction(RitActieOntvanger.ACTIE_BEVESTIG)
            .putExtra(RitActieOntvanger.EXTRA_RIT, ritId)
            .putExtra(RitActieOntvanger.EXTRA_SOORT, soort.name)
        return PendingIntent.getBroadcast(
            context,
            (ritId + soort.name).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun maakKanaal(context: Context) {
        val kanaal = NotificationChannel(
            KANAAL,
            context.getString(R.string.kanaal_bevestigen_naam),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.kanaal_bevestigen_uitleg) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(kanaal)
    }
}
