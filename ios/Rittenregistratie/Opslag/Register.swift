import Foundation
import RittenKern

/// Wat de gebruiker instelt naast de auto zelf.
struct Instellingen: Codable, Equatable {
    var thuisadres = ""
    var werkadres = ""
    var standaardSoort: Ritsoort = .zakelijk
    /// Ritten starten en stoppen op het signaal van de automatisering in Opdrachten.
    var automatischAan = true
    /// Ritten waar de app zeker van is (woon-werk, vaste bestemming) zonder tik bevestigen.
    var automatischBevestigen = true
    var laatsteIjking: Dag?
    var standBijLaatsteIjking: Int?
    /// Wanneer de automatisering voor het laatst een rit startte; bewijs dat hij werkt.
    var laatsteAutomatischeStart: Date?
    var laatsteAutomatischeStop: Date?

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        thuisadres = try c.decodeIfPresent(String.self, forKey: .thuisadres) ?? ""
        werkadres = try c.decodeIfPresent(String.self, forKey: .werkadres) ?? ""
        standaardSoort = try c.decodeIfPresent(Ritsoort.self, forKey: .standaardSoort) ?? .zakelijk
        automatischAan = try c.decodeIfPresent(Bool.self, forKey: .automatischAan) ?? true
        automatischBevestigen = try c.decodeIfPresent(Bool.self, forKey: .automatischBevestigen) ?? true
        laatsteIjking = try c.decodeIfPresent(Dag.self, forKey: .laatsteIjking)
        standBijLaatsteIjking = try c.decodeIfPresent(Int.self, forKey: .standBijLaatsteIjking)
        laatsteAutomatischeStart = try c.decodeIfPresent(Date.self, forKey: .laatsteAutomatischeStart)
        laatsteAutomatischeStop = try c.decodeIfPresent(Date.self, forKey: .laatsteAutomatischeStop)
    }

    var voorkeuren: Ritopbouw.Voorkeuren {
        Ritopbouw.Voorkeuren(
            thuisadres: thuisadres,
            werkadres: werkadres,
            standaardSoort: standaardSoort,
            automatischBevestigen: automatischBevestigen
        )
    }
}

/// De administratie op de telefoon: één auto, alle ritten, de instellingen.
///
/// Eén JSON-bestand in de app-map. Klein genoeg (een paar honderd ritten per
/// jaar) om in zijn geheel te schrijven, en zo atomisch: een rit raakt nooit
/// half bewaard. Het bestand is ook leesbaar terwijl de telefoon op slot zit,
/// want ritten worden juist afgerond als de telefoon in je zak zit.
@MainActor
final class Register: ObservableObject {
    static let shared = Register()

    @Published private(set) var voertuig: Voertuig?
    @Published private(set) var ritten: [Rit] = []
    @Published var instellingen = Instellingen() {
        didSet { if instellingen != oldValue { schrijf() } }
    }
    @Published private(set) var fout: String?

    private struct Bestand: Codable {
        var voertuig: Voertuig?
        var ritten: [Rit]
        var instellingen: Instellingen
    }

    private let url: URL
    private var laden = false

    init(url: URL? = nil) {
        if let url {
            self.url = url
        } else {
            let map = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            try? FileManager.default.createDirectory(at: map, withIntermediateDirectories: true)
            self.url = map.appendingPathComponent("register.json")
        }
        lees()
    }

    private func lees() {
        guard let data = try? Data(contentsOf: url) else { return }
        do {
            let bestand = try Tijdstempel.decoder.decode(Bestand.self, from: data)
            laden = true
            voertuig = bestand.voertuig
            ritten = bestand.ritten
            instellingen = bestand.instellingen
            laden = false
        } catch {
            // Nooit overschrijven wat we niet konden lezen: bewaar het apart.
            let reserve = url.deletingPathExtension().appendingPathExtension("onleesbaar-\(Int(Date().timeIntervalSince1970)).json")
            try? FileManager.default.copyItem(at: url, to: reserve)
            fout = "De opgeslagen ritten konden niet worden gelezen. Een kopie staat apart bewaard."
        }
    }

    private func schrijf() {
        if laden { return }
        do {
            let data = try Tijdstempel.encoder.encode(Bestand(voertuig: voertuig, ritten: ritten, instellingen: instellingen))
            try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            fout = nil
        } catch {
            fout = "Opslaan mislukt: \(error.localizedDescription)"
            Logboek.schrijf("Opslaan mislukt: \(error.localizedDescription)")
        }
    }

    // MARK: Auto

    func bewaar(_ nieuw: Voertuig) {
        voertuig = nieuw
        schrijf()
    }

    // MARK: Ritten

    var eigenRitten: [Rit] {
        guard let voertuig else { return [] }
        return ritten.filter { $0.voertuigId == voertuig.id }
    }

    var volgendeBeginstand: Int {
        guard let voertuig else { return 0 }
        return Ritopbouw.volgendeBeginstand(voertuig, ritten)
    }

    func rit(_ id: String) -> Rit? { ritten.first { $0.id == id } }

    func bewaar(_ rit: Rit) {
        var r = rit
        r.gewijzigdOp = Date()
        if let i = ritten.firstIndex(where: { $0.id == r.id }) {
            ritten[i] = r
        } else {
            ritten.append(r)
        }
        schrijf()
    }

    /// Eén tik op een melding of in de lijst: het karakter vastleggen en bevestigen.
    func bevestig(_ id: String, soort: Ritsoort? = nil) {
        guard var r = rit(id) else { return }
        if let soort { r.soort = soort }
        r.bevestigd = true
        bewaar(r)
    }

    func verwijder(_ id: String) {
        ritten.removeAll { $0.id == id }
        schrijf()
    }

    func controle(_ peildatum: Dag = .vandaag()) -> Controleresultaat? {
        guard let voertuig else { return nil }
        return Rittencontrole.controleer(voertuig, ritten, peildatum)
    }

    // MARK: Ijken

    var kmSindsIjking: Int {
        guard voertuig != nil else { return 0 }
        let vanaf = instellingen.standBijLaatsteIjking ?? voertuig!.beginstandKm
        return volgendeBeginstand - vanaf
    }

    var ijkenNodig: Bool {
        guard voertuig != nil else { return false }
        return Ijking.ijkenNodig(
            standenKomenUitDeAuto: false,
            laatsteIjking: instellingen.laatsteIjking,
            kmSindsIjking: kmSindsIjking,
            vandaag: .vandaag()
        )
    }

    func ijkingVastgelegd(stand: Int) {
        instellingen.laatsteIjking = .vandaag()
        instellingen.standBijLaatsteIjking = stand
    }

    // MARK: Back-up

    func backup() -> Backup {
        Backup(gemaaktOp: Date(), voertuigen: voertuig.map { [$0] } ?? [], ritten: ritten)
    }

    func zetTerug(_ backup: Backup) {
        voertuig = backup.voertuigen.last
        ritten = backup.ritten
        schrijf()
    }
}
