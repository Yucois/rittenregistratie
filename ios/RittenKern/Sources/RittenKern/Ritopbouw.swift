import Foundation

/// Maakt van een gps-meting een rit.
///
/// De rit wordt meteen bewaard, als onbevestigd: een rit die pas wordt
/// opgeslagen nadat je hem hebt ingevuld, ben je kwijt zodra je het vergeet —
/// en een gemiste rit is precies het gat dat de registratie onsluitend maakt.
public enum Ritopbouw {

    /// Onder deze afstand is er niet gereden maar bijvoorbeeld alleen de radio aan geweest.
    public static let minimaleAfstandKm = 0.3

    public struct Voorkeuren: Sendable {
        public var thuisadres: String
        public var werkadres: String
        public var standaardSoort: Ritsoort
        public var automatischBevestigen: Bool

        public init(thuisadres: String, werkadres: String, standaardSoort: Ritsoort = .zakelijk, automatischBevestigen: Bool = true) {
            self.thuisadres = thuisadres
            self.werkadres = werkadres
            self.standaardSoort = standaardSoort
            self.automatischBevestigen = automatischBevestigen
        }
    }

    public enum Uitkomst: Equatable, Sendable {
        /// Te kort om een rit te zijn. `geenPunten`: er kwam geen enkele locatie
        /// binnen, dus de meting werkte niet en dat moet je te zien krijgen.
        case teKort(geenPunten: Bool)
        case rit(Rit, reden: String)
    }

    public static func maak(
        _ meting: Ritmeting,
        voertuig: Voertuig,
        ritten: [Rit],
        eindadres: String,
        voorkeuren: Voorkeuren,
        id: String,
        nu: Date,
        tijdzone: TimeZone = .current
    ) -> Uitkomst {
        if meting.afstandKm < minimaleAfstandKm {
            return .teKort(geenPunten: meting.aantalPunten == 0)
        }
        let eigen = ritten.filter { $0.voertuigId == voertuig.id }.opStand()
        let beginadres = meting.startAdres.isEmpty ? (eigen.last?.eindadres ?? "") : meting.startAdres
        let voorstel = Ritclassificatie.voorstel(
            beginadres,
            eindadres,
            thuis: voorkeuren.thuisadres,
            werk: voorkeuren.werkadres,
            geschiedenis: eigen,
            standaard: voorkeuren.standaardSoort
        )
        // De laatst geregistreerde stand is het vertrekpunt: zo sluit de reeks.
        // Heeft de gps eerder te ruim of te krap gemeten, dan haalt het ijken dat recht.
        let beginstand = eigen.last?.eindstandKm ?? voertuig.beginstandKm
        let eindstand = beginstand + max(1, Int(meting.afstandKm.rounded()))
        let rit = Rit(
            id: id,
            voertuigId: voertuig.id,
            datum: Dag(meting.gestartOp, tijdzone: tijdzone),
            beginstandKm: beginstand,
            eindstandKm: eindstand,
            beginadres: beginadres,
            eindadres: eindadres,
            soort: voorstel.soort,
            doel: voorstel.doel,
            vertrektijd: Kloktijd(meting.gestartOp, tijdzone: tijdzone),
            aankomsttijd: Kloktijd(meting.laatste?.moment ?? nu, tijdzone: tijdzone),
            bron: .gps,
            gemetenKm: meting.afstandKm,
            // Alleen wat de app met zekerheid kan plaatsen gaat er bevestigd in;
            // de rest wacht op één tik, zodat er niets in de administratie komt
            // wat je zelf niet zou hebben ingevuld.
            bevestigd: voorstel.zelfAfhandelen && voorkeuren.automatischBevestigen,
            aangemaaktOp: nu,
            gewijzigdOp: nu
        )
        return .rit(rit, reden: voorstel.reden)
    }

    /// De tellerstand waar de volgende rit begint.
    public static func volgendeBeginstand(_ voertuig: Voertuig, _ ritten: [Rit]) -> Int {
        ritten.filter { $0.voertuigId == voertuig.id }.opStand().last?.eindstandKm ?? voertuig.beginstandKm
    }
}
