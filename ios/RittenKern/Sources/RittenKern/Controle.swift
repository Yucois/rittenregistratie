import Foundation

public enum Ernst: Sendable { case fout, waarschuwing, info }

/// Eén constatering over de administratie. `code` is stabiel zodat de app er
/// teksten en acties aan kan hangen; `melding` is de uitleg voor de gebruiker.
public struct Bevinding: Equatable, Sendable {
    public let ernst: Ernst
    public let code: String
    public let melding: String
    public let ritId: String?

    public init(_ ernst: Ernst, _ code: String, _ melding: String, ritId: String? = nil) {
        self.ernst = ernst
        self.code = code
        self.melding = melding
        self.ritId = ritId
    }
}

/// De grens uit artikel 13bis Wet LB: maximaal 500 privékilometers per kalenderjaar.
public let priveGrensKm = 500

public struct Jaaroverzicht: Equatable, Sendable {
    public let jaar: Int
    public let aantalRitten: Int
    public let totaalKm: Int
    public let zakelijkKm: Int
    public let woonWerkKm: Int
    public let priveKm: Int
    public let priveOmrijKm: Int
    /// Kilometers tussen twee ritten die nergens verantwoord zijn.
    public let nietVerantwoordeKm: Int
    /// Automatisch vastgelegde ritten die nog op bevestiging wachten.
    public let onbevestigdeRitten: Int

    public init(
        jaar: Int, aantalRitten: Int, totaalKm: Int, zakelijkKm: Int, woonWerkKm: Int,
        priveKm: Int, priveOmrijKm: Int, nietVerantwoordeKm: Int, onbevestigdeRitten: Int = 0
    ) {
        self.jaar = jaar
        self.aantalRitten = aantalRitten
        self.totaalKm = totaalKm
        self.zakelijkKm = zakelijkKm
        self.woonWerkKm = woonWerkKm
        self.priveKm = priveKm
        self.priveOmrijKm = priveOmrijKm
        self.nietVerantwoordeKm = nietVerantwoordeKm
        self.onbevestigdeRitten = onbevestigdeRitten
    }

    /// Privékilometers voor de 500-kilometergrens (woon-werk telt hier als zakelijk).
    public var priveKmLoonheffing: Int { priveKm + priveOmrijKm }

    /// Wat de inspecteur telt als de gaten niet alsnog worden verklaard: onverantwoorde
    /// kilometers worden bij een controle als privé aangemerkt.
    public var priveKmLoonheffingWorstCase: Int { priveKmLoonheffing + nietVerantwoordeKm }

    /// Privékilometers voor de btw-correctie: woon-werkverkeer telt hier wél als privé.
    public var priveKmBtw: Int { priveKm + woonWerkKm + priveOmrijKm }

    public var restantTot500: Int { priveGrensKm - priveKmLoonheffingWorstCase }

    public var grensOverschreden: Bool { priveKmLoonheffingWorstCase > priveGrensKm }

    public var sluitend: Bool { nietVerantwoordeKm == 0 }

    /// Aandeel privé voor de btw-correctie, bijvoorbeeld 0.12 voor 12%.
    public var btwPriveAandeel: Double { totaalKm == 0 ? 0 : Double(priveKmBtw) / Double(totaalKm) }

    /// Lineaire prognose van de privékilometers over het hele jaar, op basis van
    /// het deel van het jaar dat op `peildatum` verstreken is.
    public func prognosePriveKm(_ peildatum: Dag) -> Int {
        if peildatum.jaar != jaar { return priveKmLoonheffingWorstCase }
        let verstreken = peildatum.dagVanHetJaar
        let lengte = Dag(jaar, 12, 31).dagVanHetJaar
        if verstreken <= 0 { return priveKmLoonheffingWorstCase }
        return Int((Double(priveKmLoonheffingWorstCase) * Double(lengte) / Double(verstreken)).rounded())
    }
}

public struct Controleresultaat: Sendable {
    public let bevindingen: [Bevinding]
    public let jaaroverzichten: [Jaaroverzicht]
    /// Laatst geregistreerde tellerstand; het vertrekpunt voor de volgende rit.
    public let laatsteStand: Int

    public var fouten: [Bevinding] { bevindingen.filter { $0.ernst == .fout } }
    public var waarschuwingen: [Bevinding] { bevindingen.filter { $0.ernst == .waarschuwing } }
    public var sluitend: Bool { fouten.isEmpty && jaaroverzichten.allSatisfy { $0.sluitend } }

    public func jaar(_ jaar: Int) -> Jaaroverzicht? { jaaroverzichten.first { $0.jaar == jaar } }
}

/// Controleert of de registratie voldoet aan de eisen en of hij sluitend is:
/// de eindstand van elke rit moet de beginstand van de volgende zijn.
public enum Rittencontrole {

    /// Boven dit verschil tussen gps-meting en tellerstanden volgt een waarschuwing.
    private static let meetverschilMargeKm = 3.0
    private static let meetverschilMargeDeel = 0.10
    private static let onwaarschijnlijkeRitlengteKm = 1500

    public static func controleerRit(_ rit: Rit, _ voertuig: Voertuig?) -> [Bevinding] {
        var b: [Bevinding] = []
        func fout(_ code: String, _ melding: String) { b.append(Bevinding(.fout, code, melding, ritId: rit.id)) }
        func waarschuwing(_ code: String, _ melding: String) {
            b.append(Bevinding(.waarschuwing, code, melding, ritId: rit.id))
        }
        func leeg(_ tekst: String?) -> Bool { (tekst ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

        if rit.eindstandKm <= rit.beginstandKm {
            fout("stand.oplopend", "De eindstand (\(rit.eindstandKm)) moet hoger zijn dan de beginstand (\(rit.beginstandKm)).")
        }
        if rit.bron == .correctie {
            // Een correctierit heeft per definitie geen adressen: hij dekt kilometers
            // die niet zijn vastgelegd. Wat hij wél moet hebben is een toelichting.
            if leeg(rit.opmerking) {
                fout("correctie.zondertoelichting", "Leg vast waardoor deze kilometers niet zijn geregistreerd.")
            }
        } else {
            if leeg(rit.beginadres) { fout("adres.begin", "Het beginadres ontbreekt.") }
            if leeg(rit.eindadres) { fout("adres.eind", "Het eindadres ontbreekt.") }
        }

        if rit.priveOmrijkilometers < 0 {
            fout("omrij.negatief", "Privé-omrijkilometers kunnen niet negatief zijn.")
        } else if rit.priveOmrijkilometers > 0 {
            if leeg(rit.afwijkendeRoute) {
                fout(
                    "route.afwijkend",
                    "Er zijn privé-omrijkilometers ingevuld; beschrijf dan ook de gereden route, want die wijkt af van de gebruikelijke."
                )
            }
            if rit.priveOmrijkilometers >= rit.afstandKm && rit.afstandKm > 0 {
                fout(
                    "omrij.tegroot",
                    "De privé-omrijkilometers (\(rit.priveOmrijkilometers)) passen niet binnen de rit van \(rit.afstandKm) km."
                )
            }
            if rit.soort == .prive {
                waarschuwing(
                    "omrij.dubbel",
                    "Bij een privérit telt de hele afstand al als privé; losse omrijkilometers zijn dan dubbelop."
                )
            }
        }

        if !rit.bevestigd {
            waarschuwing(
                "rit.onbevestigd",
                "Automatisch vastgelegd en nog niet bevestigd: controleer het karakter en de tellerstanden."
            )
        }

        if rit.soort == .zakelijk && leeg(rit.doel) {
            waarschuwing(
                "doel.leeg",
                "Geen zakelijk doel of relatie vermeld. Dat is het eerste waar een controle naar vraagt."
            )
        }

        if let voertuig, !voertuig.terBeschikking(op: rit.datum) {
            fout("datum.buitenperiode", "De rit valt buiten de periode waarin \(voertuig.kenteken) ter beschikking stond.")
        }

        if rit.afstandKm > onwaarschijnlijkeRitlengteKm {
            waarschuwing("afstand.groot", "Een rit van \(rit.afstandKm) km; controleer de tellerstanden op een typefout.")
        }

        if let gemeten = rit.gemetenKm, gemeten > 0 {
            let verschil = abs(Double(rit.afstandKm) - gemeten)
            let marge = max(meetverschilMargeKm, gemeten * meetverschilMargeDeel)
            if verschil > marge {
                waarschuwing(
                    "meting.afwijking",
                    "De tellerstanden geven \(rit.afstandKm) km, de gps-meting \(Getal.eenDecimaal(gemeten)) km."
                )
            }
        }

        return b
    }

    /// Controleert de hele reeks van één voertuig: aansluiting van de tellerstanden,
    /// dubbelingen en de jaartotalen.
    ///
    /// - Parameters:
    ///   - ritten: alle ritten van het voertuig, in willekeurige volgorde.
    ///   - peildatum: vandaag; ritten met een latere datum leveren een waarschuwing op.
    public static func controleer(_ voertuig: Voertuig, _ ritten: [Rit], _ peildatum: Dag = .vandaag()) -> Controleresultaat {
        let eigen = ritten.filter { $0.voertuigId == voertuig.id }
        var bevindingen: [Bevinding] = []
        for rit in eigen { bevindingen += controleerRit(rit, voertuig) }
        for rit in eigen where rit.datum > peildatum {
            bevindingen.append(Bevinding(.waarschuwing, "datum.toekomst", "De rit ligt in de toekomst.", ritId: rit.id))
        }

        let gesorteerd = eigen.opStand()

        // Gaten per jaar bijhouden: een gat valt in het jaar van de rit die erna komt.
        var gatenPerJaar: [Int: Int] = [:]

        if let eerste = gesorteerd.first {
            let startgat = eerste.beginstandKm - voertuig.beginstandKm
            if startgat > 0 {
                gatenPerJaar[eerste.datum.jaar, default: 0] += startgat
                bevindingen.append(Bevinding(
                    .fout,
                    "hiaat.start",
                    "Tussen de beginstand van de auto (\(voertuig.beginstandKm) km) en de eerste rit " +
                        "(\(eerste.beginstandKm) km) zitten \(startgat) onverantwoorde kilometers.",
                    ritId: eerste.id
                ))
            } else if startgat < 0 {
                bevindingen.append(Bevinding(
                    .fout,
                    "stand.voorbeginstand",
                    "De eerste rit begint onder de opgegeven beginstand van de auto (\(voertuig.beginstandKm) km).",
                    ritId: eerste.id
                ))
            }
        }

        if gesorteerd.count > 1 {
            for i in 1..<gesorteerd.count {
                let vorige = gesorteerd[i - 1]
                let huidige = gesorteerd[i]
                let gat = huidige.beginstandKm - vorige.eindstandKm
                if gat > 0 {
                    gatenPerJaar[huidige.datum.jaar, default: 0] += gat
                    bevindingen.append(Bevinding(
                        .fout,
                        "hiaat",
                        "\(gat) km tussen \(vorige.eindstandKm) en \(huidige.beginstandKm) zijn niet verantwoord. " +
                            "Onverklaarde kilometers worden bij een controle als privé aangemerkt.",
                        ritId: huidige.id
                    ))
                } else if gat < 0 {
                    bevindingen.append(Bevinding(
                        .fout,
                        "overlap",
                        "De rit begint bij \(huidige.beginstandKm) km terwijl de vorige rit al tot " +
                            "\(vorige.eindstandKm) km liep; de standen overlappen.",
                        ritId: huidige.id
                    ))
                }
                if huidige.datum < vorige.datum {
                    bevindingen.append(Bevinding(
                        .waarschuwing,
                        "volgorde.datum",
                        "De datum (\(huidige.datum)) ligt vóór die van de rit met een lagere tellerstand (\(vorige.datum)).",
                        ritId: huidige.id
                    ))
                }
            }
        }

        let jaren = Set(eigen.map { $0.datum.jaar }).union(gatenPerJaar.keys).sorted()
        let overzichten = jaren.map { jaar -> Jaaroverzicht in
            let vanJaar = eigen.filter { $0.datum.jaar == jaar }
            func som(_ soort: Ritsoort) -> Int { vanJaar.filter { $0.soort == soort }.reduce(0) { $0 + $1.afstandKm } }
            return Jaaroverzicht(
                jaar: jaar,
                aantalRitten: vanJaar.count,
                totaalKm: vanJaar.reduce(0) { $0 + $1.afstandKm },
                zakelijkKm: som(.zakelijk),
                woonWerkKm: som(.woonWerk),
                priveKm: som(.prive),
                priveOmrijKm: vanJaar.filter { $0.soort != .prive }
                    .reduce(0) { $0 + min($1.priveOmrijkilometers, $1.afstandKm) },
                nietVerantwoordeKm: gatenPerJaar[jaar] ?? 0,
                onbevestigdeRitten: vanJaar.filter { !$0.bevestigd }.count
            )
        }

        for o in overzichten where o.grensOverschreden {
            bevindingen.append(Bevinding(
                .fout,
                "grens.overschreden",
                "In \(o.jaar) staan \(o.priveKmLoonheffingWorstCase) privékilometers geregistreerd; " +
                    "boven de 500 vervalt de Verklaring geen privégebruik auto en volgt bijtelling."
            ))
        }

        return Controleresultaat(
            bevindingen: bevindingen,
            jaaroverzichten: overzichten,
            laatsteStand: gesorteerd.last?.eindstandKm ?? voertuig.beginstandKm
        )
    }
}
