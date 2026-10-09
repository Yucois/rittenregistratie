import Foundation

/// Bepaalt zelf wat een rit is, op basis van je vaste adressen en van wat je
/// eerder bij dezelfde bestemming hebt vastgelegd.
///
/// Het doel is dat je op den duur niets meer hoeft te kiezen: een bestemming
/// die je twee keer als zakelijk hebt bevestigd, wordt de derde keer vanzelf
/// zakelijk, met hetzelfde doel erbij. Alleen een onbekende bestemming vraagt
/// nog om een tik.
///
/// De zekerheid bepaalt of de app het zelf afhandelt. Dat is bewust streng:
/// automatisch vastleggen mag nooit betekenen dat er iets in de administratie
/// komt wat je niet zou hebben ingevuld.
public enum Ritclassificatie {

    public enum Zekerheid: Sendable { case hoog, middel, laag }

    public struct Voorstel: Equatable, Sendable {
        public let soort: Ritsoort
        public let doel: String
        public let zekerheid: Zekerheid
        public let reden: String

        /// Bij deze zekerheid handelt de app de rit zelf af; je krijgt hem alleen te zien.
        public var zelfAfhandelen: Bool { zekerheid == .hoog }
    }

    /// Vanaf dit aantal eerdere, bevestigde ritten naar dezelfde plek is de app er zeker van.
    private static let zekerVanaf = 2

    public static func voorstel(
        _ beginadres: String,
        _ eindadres: String,
        thuis thuisadres: String,
        werk werkadres: String,
        geschiedenis: [Rit] = [],
        standaard: Ritsoort = .zakelijk
    ) -> Voorstel {
        let vanThuis = komtOvereen(beginadres, thuisadres)
        let vanWerk = komtOvereen(beginadres, werkadres)
        let naarThuis = komtOvereen(eindadres, thuisadres)
        let naarWerk = komtOvereen(eindadres, werkadres)
        if (vanThuis && naarWerk) || (vanWerk && naarThuis) {
            return Voorstel(soort: .woonWerk, doel: "", zekerheid: .hoog, reden: "Tussen je thuisadres en je kantooradres.")
        }

        // Alleen bevestigde ritten zijn leermateriaal; van een onbevestigde rit
        // staat immers nog niet vast dat hij klopt.
        let eerder = geschiedenis
            .filter { $0.bevestigd && komtOvereen($0.eindadres, eindadres) }
            .sorted { a, b in
                if a.datum != b.datum { return a.datum > b.datum }
                return a.beginstandKm > b.beginstandKm
            }

        if !eerder.isEmpty {
            var soorten: [Ritsoort] = []
            var aantallen: [Ritsoort: Int] = [:]
            for rit in eerder {
                if aantallen[rit.soort] == nil { soorten.append(rit.soort) }
                aantallen[rit.soort, default: 0] += 1
            }
            if soorten.count == 1 {
                let soort = soorten[0]
                return Voorstel(
                    soort: soort,
                    doel: eerder.first { !$0.doel.trimmingCharacters(in: .whitespaces).isEmpty }?.doel ?? "",
                    zekerheid: eerder.count >= zekerVanaf ? .hoog : .middel,
                    reden: "Deze bestemming heb je \(eerder.count)× als \(soort.label.lowercased()) vastgelegd."
                )
            }
            // Bij gelijke aantallen wint de soort die het recentst voorkwam, net als op Android.
            var vaakste = soorten[0]
            for soort in soorten where aantallen[soort]! > aantallen[vaakste]! { vaakste = soort }
            return Voorstel(
                soort: vaakste,
                doel: "",
                zekerheid: .laag,
                reden: "Deze bestemming heb je eerder verschillend vastgelegd."
            )
        }

        return Voorstel(
            soort: standaard,
            doel: "",
            zekerheid: .laag,
            reden: "Onbekende bestemming; de standaardkeuze is ingevuld."
        )
    }

    /// Adressen uit een geocoder en handmatig ingetypte adressen schrijven zelden
    /// precies hetzelfde. Vergelijken gebeurt daarom op de eerste woorden —
    /// straat en huisnummer — zonder hoofdletters en leestekens.
    public static func komtOvereen(_ a: String, _ b: String) -> Bool {
        let links = sleutel(a)
        let rechts = sleutel(b)
        if links.isEmpty || rechts.isEmpty { return false }
        return links == rechts || links.hasPrefix(rechts) || rechts.hasPrefix(links)
    }

    private static func sleutel(_ adres: String) -> String {
        let toegestaan = Set("abcdefghijklmnopqrstuvwxyz0123456789 ")
        let schoon = String(adres.lowercased().map { toegestaan.contains($0) ? $0 : " " })
        return schoon.split(separator: " ").prefix(3).joined(separator: " ")
    }
}
