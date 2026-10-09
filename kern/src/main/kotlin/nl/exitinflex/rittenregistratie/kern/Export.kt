package nl.exitinflex.rittenregistratie.kern

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * CSV-export van de rittenregistratie. Puntkomma als scheidingsteken en een
 * BOM vooraan, zodat Nederlands Excel het bestand in één keer goed opent.
 */
object CsvExport {

    private val datum: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    val kolommen = listOf(
        "Datum",
        "Kenteken",
        "Beginstand (km)",
        "Eindstand (km)",
        "Afstand (km)",
        "Beginadres",
        "Eindadres",
        "Karakter",
        "Zakelijk doel / relatie",
        "Gereden route (indien afwijkend)",
        "Privé-omrijkilometers",
        "Vertrek",
        "Aankomst",
        "Opmerking",
        "Vastgelegd via",
        "Vastgelegd op",
    )

    fun bestandsnaam(voertuig: Voertuig, jaar: Int): String =
        "rittenregistratie-${voertuig.kenteken.filter { it.isLetterOrDigit() }.uppercase()}-$jaar.csv"

    fun naarCsv(voertuig: Voertuig, ritten: List<Rit>, metBom: Boolean = true): String {
        val sb = StringBuilder()
        if (metBom) sb.append('﻿')
        sb.append(kolommen.joinToString(";") { veld(it) }).append("\r\n")
        ritten.sortedWith(compareBy({ it.beginstandKm }, { it.datum })).forEach { rit ->
            sb.append(
                listOf(
                    rit.datum.format(datum),
                    voertuig.kenteken,
                    rit.beginstandKm.toString(),
                    rit.eindstandKm.toString(),
                    rit.afstandKm.toString(),
                    rit.beginadres,
                    rit.eindadres,
                    rit.soort.label,
                    rit.doel,
                    rit.afwijkendeRoute.orEmpty(),
                    if (rit.priveOmrijkilometers > 0) rit.priveOmrijkilometers.toString() else "",
                    rit.vertrektijd?.toString().orEmpty(),
                    rit.aankomsttijd?.toString().orEmpty(),
                    rit.opmerking.orEmpty(),
                    when (rit.bron) {
                        Ritbron.HANDMATIG -> "handmatig"
                        Ritbron.GPS -> "gps"
                        Ritbron.AUTO -> "tellerstand uit de auto"
                        Ritbron.IMPORT -> "import"
                        Ritbron.CORRECTIE -> "correctie na ijking"
                    },
                    rit.aangemaaktOp.toString(),
                ).joinToString(";") { veld(it) },
            ).append("\r\n")
        }
        return sb.toString()
    }

    /** Waarde tussen aanhalingstekens; interne aanhalingstekens verdubbeld, regeleindes weg. */
    internal fun veld(waarde: String): String {
        val schoon = waarde.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return "\"" + schoon.replace("\"", "\"\"") + "\""
    }
}

/**
 * Print- en pdf-klaar overzicht. Eén bestand zonder externe afhankelijkheden,
 * zodat het te bewaren, te mailen en via "Afdrukken → Opslaan als pdf" om te
 * zetten is.
 */
object HtmlRapport {

    private val datum: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun bestandsnaam(voertuig: Voertuig, jaar: Int): String =
        "rittenregistratie-${voertuig.kenteken.filter { it.isLetterOrDigit() }.uppercase()}-$jaar.html"

    fun naarHtml(
        voertuig: Voertuig,
        ritten: List<Rit>,
        jaar: Int,
        controle: Controleresultaat,
        opgemaaktOp: LocalDate = LocalDate.now(),
    ): String {
        val vanJaar = ritten.filter { it.datum.year == jaar }
            .sortedWith(compareBy({ it.beginstandKm }, { it.datum }))
        val overzicht = controle.jaar(jaar)
        val bevindingen = controle.bevindingen.filter { bev ->
            bev.ritId == null || vanJaar.any { it.id == bev.ritId }
        }

        return buildString {
            append(
                """
                <!doctype html>
                <html lang="nl">
                <head>
                <meta charset="utf-8">
                <title>Rittenregistratie ${esc(voertuig.kenteken)} $jaar</title>
                <style>
                  :root { color-scheme: light; }
                  body { font-family: -apple-system, "Segoe UI", Roboto, Arial, sans-serif; color: #111; margin: 24px; }
                  h1 { font-size: 20px; margin: 0 0 4px; }
                  h2 { font-size: 15px; margin: 24px 0 8px; }
                  p.sub { color: #555; margin: 0 0 16px; font-size: 12px; }
                  table { border-collapse: collapse; width: 100%; font-size: 11px; }
                  th, td { border: 1px solid #ccc; padding: 4px 6px; text-align: left; vertical-align: top; }
                  th { background: #f2f2f2; }
                  td.num, th.num { text-align: right; white-space: nowrap; }
                  .kaart { border: 1px solid #ccc; padding: 12px 16px; margin-bottom: 16px; }
                  .kaart dl { display: grid; grid-template-columns: max-content 1fr; gap: 2px 16px; margin: 0; font-size: 12px; }
                  .kaart dt { color: #555; }
                  .fout { color: #8a1c1c; }
                  .waarschuwing { color: #8a5a00; }
                  ul.bevindingen { font-size: 12px; padding-left: 18px; }
                  .stempel { margin-top: 24px; font-size: 11px; color: #555; }
                  @media print { body { margin: 8mm; } h2 { page-break-after: avoid; } tr { page-break-inside: avoid; } }
                </style>
                </head>
                <body>
                """.trimIndent(),
            )
            append("<h1>Rittenregistratie ${esc(voertuig.omschrijving)} — $jaar</h1>")
            append("<p class=\"sub\">Opgemaakt op ${opgemaaktOp.format(datum)}</p>")

            append("<div class=\"kaart\"><dl>")
            append("<dt>Merk</dt><dd>${esc(voertuig.merk)}</dd>")
            append("<dt>Type</dt><dd>${esc(voertuig.type)}</dd>")
            append("<dt>Kenteken</dt><dd>${esc(voertuig.kenteken)}</dd>")
            append(
                "<dt>Ter beschikking</dt><dd>${voertuig.terBeschikkingVanaf.format(datum)} t/m " +
                    (voertuig.terBeschikkingTot?.format(datum) ?: "heden") + "</dd>",
            )
            append("</dl></div>")

            if (overzicht != null) {
                append("<h2>Samenvatting $jaar</h2>")
                append("<table><tbody>")
                rij("Aantal ritten", overzicht.aantalRitten.toString())
                rij("Totaal gereden", "${overzicht.totaalKm} km")
                rij("Zakelijk", "${overzicht.zakelijkKm} km")
                rij("Woon-werkverkeer", "${overzicht.woonWerkKm} km")
                rij("Privé", "${overzicht.priveKm} km")
                if (overzicht.priveOmrijKm > 0) rij("Privé-omrijkilometers", "${overzicht.priveOmrijKm} km")
                if (overzicht.nietVerantwoordeKm > 0) {
                    rij(
                        "Niet verantwoord",
                        "<span class=\"fout\">${overzicht.nietVerantwoordeKm} km</span>",
                        ruw = true,
                    )
                }
                rij(
                    "Privé voor de loonheffing (grens 500 km)",
                    "${overzicht.priveKmLoonheffingWorstCase} km — " +
                        if (overzicht.grensOverschreden) {
                            "<span class=\"fout\">grens overschreden</span>"
                        } else {
                            "${overzicht.restantTot500} km over"
                        },
                    ruw = true,
                )
                rij(
                    "Privé voor de btw-correctie (incl. woon-werk)",
                    "${overzicht.priveKmBtw} km (${"%.1f".format(overzicht.btwPriveAandeel * 100)}%)",
                )
                append("</tbody></table>")
            }

            if (bevindingen.isNotEmpty()) {
                append("<h2>Controlepunten</h2><ul class=\"bevindingen\">")
                bevindingen.forEach {
                    val klasse = when (it.ernst) {
                        Ernst.FOUT -> "fout"
                        Ernst.WAARSCHUWING -> "waarschuwing"
                        Ernst.INFO -> ""
                    }
                    append("<li class=\"$klasse\">${esc(it.melding)}</li>")
                }
                append("</ul>")
            }

            append("<h2>Ritten</h2>")
            append("<table><thead><tr>")
            listOf(
                "Datum", "Begin", "Eind", "Km", "Van", "Naar", "Karakter", "Doel / relatie", "Route indien afwijkend",
            ).forEachIndexed { i, kop ->
                val num = i in 1..3
                append("<th${if (num) " class=\"num\"" else ""}>${esc(kop)}</th>")
            }
            append("</tr></thead><tbody>")
            vanJaar.forEach { rit ->
                append("<tr>")
                append("<td>${rit.datum.format(datum)}</td>")
                append("<td class=\"num\">${rit.beginstandKm}</td>")
                append("<td class=\"num\">${rit.eindstandKm}</td>")
                append("<td class=\"num\">${rit.afstandKm}</td>")
                append("<td>${esc(rit.beginadres)}</td>")
                append("<td>${esc(rit.eindadres)}</td>")
                append("<td>${esc(rit.soort.label)}</td>")
                append("<td>${esc(rit.doel)}</td>")
                val route = buildString {
                    append(esc(rit.afwijkendeRoute.orEmpty()))
                    if (rit.priveOmrijkilometers > 0) {
                        if (isNotEmpty()) append(" — ")
                        append("${rit.priveOmrijkilometers} km privé omrijden")
                    }
                }
                append("<td>$route</td>")
                append("</tr>")
            }
            append("</tbody></table>")
            append(
                "<p class=\"stempel\">Vastgelegd met de rittenregistratie-app. De registratie bevat per rit de datum, " +
                    "de begin- en eindstand van de kilometerteller, het begin- en eindadres, de gereden route waar die " +
                    "afwijkt van de gebruikelijke, en het karakter van de rit.</p>",
            )
            append("</body></html>")
        }
    }

    private fun StringBuilder.rij(kop: String, waarde: String, ruw: Boolean = false) {
        append("<tr><th>${esc(kop)}</th><td>${if (ruw) waarde else esc(waarde)}</td></tr>")
    }

    internal fun esc(waarde: String): String = waarde
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
