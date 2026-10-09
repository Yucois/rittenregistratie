import Foundation
import RittenKern

/// Voorbeeldgegevens voor schermafdrukken in de simulator. Alleen actief met
/// het startargument `-demo`; op een telefoon gebeurt hier niets.
@MainActor
enum Demo {
    static var actief: Bool { ProcessInfo.processInfo.arguments.contains("-demo") }

    static func zetKlaarAlsGevraagd() {
        let argumenten = ProcessInfo.processInfo.arguments
        if let i = argumenten.firstIndex(of: "-tab"), i + 1 < argumenten.count, let tab = Int(argumenten[i + 1]) {
            Navigatie.shared.tab = tab
        }
        guard actief else { return }
        let auto = Voertuig(
            id: "demo", merk: "Audi", type: "RS e-tron GT", kenteken: "X-123-YZ",
            terBeschikkingVanaf: Dag(Dag.vandaag().jaar, 1, 1), beginstandKm: 39_415
        )
        let thuis = "Dorpsstraat 12, Bussum"
        let werk = "Keizersgracht 100, Amsterdam"
        let vandaag = Dag.vandaag()
        var stand = auto.beginstandKm
        var ritten: [Rit] = []
        func voeg(_ dagenTerug: Int, _ km: Int, _ van: String, _ naar: String, _ soort: Ritsoort, _ doel: String = "", bevestigd: Bool = true) {
            let datum = Dag(Calendar.current.date(byAdding: .day, value: -dagenTerug, to: vandaag.begin())!)
            ritten.append(Rit(
                id: UUID().uuidString, voertuigId: auto.id, datum: datum,
                beginstandKm: stand, eindstandKm: stand + km, beginadres: van, eindadres: naar,
                soort: soort, doel: doel, vertrektijd: Kloktijd(8, 10), aankomsttijd: Kloktijd(8, 45),
                bron: .gps, gemetenKm: Double(km) - 0.4, bevestigd: bevestigd,
                aangemaaktOp: Date(), gewijzigdOp: Date()
            ))
            stand += km
        }
        voeg(9, 25, thuis, werk, .woonWerk)
        voeg(9, 25, werk, thuis, .woonWerk)
        voeg(7, 112, thuis, "Industrieweg 7, Zwolle", .zakelijk, "Bespreking Van Dijk")
        voeg(7, 112, "Industrieweg 7, Zwolle", thuis, .zakelijk, "Bespreking Van Dijk")
        voeg(3, 18, thuis, "Strandweg 1, Zandvoort", .prive)
        voeg(1, 25, thuis, werk, .woonWerk)
        voeg(0, 61, werk, "Stationsplein 3, Utrecht", .zakelijk, bevestigd: false)
        Register.shared.bewaar(auto)
        Register.shared.zetTerug(Backup(gemaaktOp: Date(), voertuigen: [auto], ritten: ritten))
        Register.shared.instellingen.thuisadres = thuis
        Register.shared.instellingen.werkadres = werk
        Register.shared.instellingen.laatsteIjking = vandaag
        Register.shared.instellingen.standBijLaatsteIjking = auto.beginstandKm
    }
}
