import Foundation

private func kentekenSleutel(_ kenteken: String) -> String {
    String(kenteken.filter { $0.isLetter || $0.isNumber }).uppercased()
}

/// CSV-export van de rittenregistratie. Puntkomma als scheidingsteken en een
/// BOM vooraan, zodat Nederlands Excel het bestand in één keer goed opent.
public enum CsvExport {

    public static let kolommen = [
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
    ]

    public static func bestandsnaam(_ voertuig: Voertuig, _ jaar: Int) -> String {
        "rittenregistratie-\(kentekenSleutel(voertuig.kenteken))-\(jaar).csv"
    }

    public static func naarCsv(_ voertuig: Voertuig, _ ritten: [Rit], metBom: Bool = true) -> String {
        var uit = metBom ? "\u{FEFF}" : ""
        uit += kolommen.map(veld).joined(separator: ";") + "\r\n"
        for rit in ritten.opStand() {
            let velden: [String] = [
                rit.datum.nederlands,
                voertuig.kenteken,
                String(rit.beginstandKm),
                String(rit.eindstandKm),
                String(rit.afstandKm),
                rit.beginadres,
                rit.eindadres,
                rit.soort.label,
                rit.doel,
                rit.afwijkendeRoute ?? "",
                rit.priveOmrijkilometers > 0 ? String(rit.priveOmrijkilometers) : "",
                rit.vertrektijd?.description ?? "",
                rit.aankomsttijd?.description ?? "",
                rit.opmerking ?? "",
                herkomst(rit.bron),
                Tijdstempel.tekst(rit.aangemaaktOp),
            ]
            uit += velden.map(veld).joined(separator: ";") + "\r\n"
        }
        return uit
    }

    static func herkomst(_ bron: Ritbron) -> String {
        switch bron {
        case .handmatig: return "handmatig"
        case .gps: return "gps"
        case .auto: return "tellerstand uit de auto"
        case .importeren: return "import"
        case .correctie: return "correctie na ijking"
        }
    }

    /// Waarde tussen aanhalingstekens; interne aanhalingstekens verdubbeld, regeleindes weg.
    public static func veld(_ waarde: String) -> String {
        // "\r\n" is in Swift één teken; daarom eerst per Unicode-scalar vervangen.
        var schoon = ""
        var vorigeWasCr = false
        for s in waarde.unicodeScalars {
            if s == "\r" {
                schoon += " "
                vorigeWasCr = true
                continue
            }
            if s == "\n" {
                if !vorigeWasCr { schoon += " " }
                vorigeWasCr = false
                continue
            }
            vorigeWasCr = false
            schoon.unicodeScalars.append(s)
        }
        return "\"" + schoon.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }
}

/// Print- en pdf-klaar overzicht. Eén bestand zonder externe afhankelijkheden,
/// zodat het te bewaren, te mailen en via "Afdrukken → Opslaan als pdf" om te
/// zetten is.
public enum HtmlRapport {

    public static func bestandsnaam(_ voertuig: Voertuig, _ jaar: Int) -> String {
        "rittenregistratie-\(kentekenSleutel(voertuig.kenteken))-\(jaar).html"
    }

    public static func naarHtml(
        _ voertuig: Voertuig,
        _ ritten: [Rit],
        _ jaar: Int,
        _ controle: Controleresultaat,
        opgemaaktOp: Dag = .vandaag()
    ) -> String {
        let vanJaar = ritten.filter { $0.datum.jaar == jaar }.opStand()
        let overzicht = controle.jaar(jaar)
        let bevindingen = controle.bevindingen.filter { bev in
            bev.ritId == nil || vanJaar.contains { $0.id == bev.ritId }
        }

        var h = """
        <!doctype html>
        <html lang="nl">
        <head>
        <meta charset="utf-8">
        <title>Rittenregistratie \(esc(voertuig.kenteken)) \(jaar)</title>
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
        """
        h += "<h1>Rittenregistratie \(esc(voertuig.omschrijving)) — \(jaar)</h1>"
        h += "<p class=\"sub\">Opgemaakt op \(opgemaaktOp.nederlands)</p>"

        h += "<div class=\"kaart\"><dl>"
        h += "<dt>Merk</dt><dd>\(esc(voertuig.merk))</dd>"
        h += "<dt>Type</dt><dd>\(esc(voertuig.type))</dd>"
        h += "<dt>Kenteken</dt><dd>\(esc(voertuig.kenteken))</dd>"
        h += "<dt>Ter beschikking</dt><dd>\(voertuig.terBeschikkingVanaf.nederlands) t/m " +
            (voertuig.terBeschikkingTot?.nederlands ?? "heden") + "</dd>"
        h += "</dl></div>"

        func rij(_ kop: String, _ waarde: String, ruw: Bool = false) {
            h += "<tr><th>\(esc(kop))</th><td>\(ruw ? waarde : esc(waarde))</td></tr>"
        }

        if let o = overzicht {
            h += "<h2>Samenvatting \(jaar)</h2>"
            h += "<table><tbody>"
            rij("Aantal ritten", String(o.aantalRitten))
            rij("Totaal gereden", "\(o.totaalKm) km")
            rij("Zakelijk", "\(o.zakelijkKm) km")
            rij("Woon-werkverkeer", "\(o.woonWerkKm) km")
            rij("Privé", "\(o.priveKm) km")
            if o.priveOmrijKm > 0 { rij("Privé-omrijkilometers", "\(o.priveOmrijKm) km") }
            if o.nietVerantwoordeKm > 0 {
                rij("Niet verantwoord", "<span class=\"fout\">\(o.nietVerantwoordeKm) km</span>", ruw: true)
            }
            rij(
                "Privé voor de loonheffing (grens 500 km)",
                "\(o.priveKmLoonheffingWorstCase) km — " +
                    (o.grensOverschreden ? "<span class=\"fout\">grens overschreden</span>" : "\(o.restantTot500) km over"),
                ruw: true
            )
            rij(
                "Privé voor de btw-correctie (incl. woon-werk)",
                "\(o.priveKmBtw) km (\(Getal.eenDecimaal(o.btwPriveAandeel * 100))%)"
            )
            h += "</tbody></table>"
        }

        if !bevindingen.isEmpty {
            h += "<h2>Controlepunten</h2><ul class=\"bevindingen\">"
            for b in bevindingen {
                let klasse: String
                switch b.ernst {
                case .fout: klasse = "fout"
                case .waarschuwing: klasse = "waarschuwing"
                case .info: klasse = ""
                }
                h += "<li class=\"\(klasse)\">\(esc(b.melding))</li>"
            }
            h += "</ul>"
        }

        h += "<h2>Ritten</h2>"
        h += "<table><thead><tr>"
        let koppen = ["Datum", "Begin", "Eind", "Km", "Van", "Naar", "Karakter", "Doel / relatie", "Route indien afwijkend"]
        for (i, kop) in koppen.enumerated() {
            h += "<th\((1...3).contains(i) ? " class=\"num\"" : "")>\(esc(kop))</th>"
        }
        h += "</tr></thead><tbody>"
        for rit in vanJaar {
            h += "<tr>"
            h += "<td>\(rit.datum.nederlands)</td>"
            h += "<td class=\"num\">\(rit.beginstandKm)</td>"
            h += "<td class=\"num\">\(rit.eindstandKm)</td>"
            h += "<td class=\"num\">\(rit.afstandKm)</td>"
            h += "<td>\(esc(rit.beginadres))</td>"
            h += "<td>\(esc(rit.eindadres))</td>"
            h += "<td>\(esc(rit.soort.label))</td>"
            h += "<td>\(esc(rit.doel))</td>"
            var route = esc(rit.afwijkendeRoute ?? "")
            if rit.priveOmrijkilometers > 0 {
                if !route.isEmpty { route += " — " }
                route += "\(rit.priveOmrijkilometers) km privé omrijden"
            }
            h += "<td>\(route)</td>"
            h += "</tr>"
        }
        h += "</tbody></table>"
        h += "<p class=\"stempel\">Vastgelegd met de rittenregistratie-app. De registratie bevat per rit de datum, " +
            "de begin- en eindstand van de kilometerteller, het begin- en eindadres, de gereden route waar die " +
            "afwijkt van de gebruikelijke, en het karakter van de rit.</p>"
        h += "</body></html>"
        return h
    }

    public static func esc(_ waarde: String) -> String {
        waarde
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }
}
