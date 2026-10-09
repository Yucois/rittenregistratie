import Foundation
import RittenKern
import UIKit

/// Voert uit wat `Ritverloop` besluit: meten starten, wachten, afronden.
///
/// De signalen komen van de automatisering in Opdrachten (auto verbonden,
/// auto weg), van de knoppen in de app en van iOS zelf (app gestart). Alles
/// loopt hierdoorheen, zodat er maar één plek is die over een rit beslist.
@MainActor
final class Ritregelaar: ObservableObject {
    static let shared = Ritregelaar()

    @Published private(set) var meting: Ritmeting?

    private var wachtTaak: Task<Void, Never>?
    private var zoektStartadres = false
    private var laatstBewaard = Date.distantPast
    private var achtergrondtaak: UIBackgroundTaskIdentifier = .invalid

    private init() {
        meting = Metingopslag.lees()
        Locatie.shared.ontvanger = { [weak self] positie in self?.ontvang(positie) }
    }

    private var register: Register { .shared }

    private var toestand: Ritverloop.Toestand {
        Ritverloop.Toestand(
            automatischAan: register.instellingen.automatischAan,
            ritLoopt: meting != nil,
            ritHandmatig: meting?.handmatig ?? false,
            autoWegSinds: meting?.autoWegSinds,
            laatsteBeweging: meting?.laatsteBeweging
        )
    }

    func verwerk(_ gebeurtenis: Ritverloop.Gebeurtenis) async {
        switch gebeurtenis {
        case .autoVerbonden: register.instellingen.laatsteAutomatischeStart = Date()
        case .autoWeg: register.instellingen.laatsteAutomatischeStop = Date()
        default: break
        }
        let actie = Ritverloop.beslis(gebeurtenis, toestand, nu: Date())
        if gebeurtenis != .wachttijdVoorbij || actie != .niets {
            Logboek.schrijf("\(Ritregelaar.naam(gebeurtenis)) → \(Ritregelaar.naam(actie))")
        }
        switch actie {
        case .niets:
            // Na een herstart door iOS loopt de rit nog; dan moet het meten ook weer lopen.
            if gebeurtenis == .appGestart, meting != nil { Locatie.shared.start() }
        case .begin:
            await begin(handmatig: false)
        case .gaDoor:
            wachtTaak?.cancel()
            meting?.autoWegSinds = nil
            bewaarMeting(direct: true)
        case let .wachtOpAfronden(seconden):
            if meting?.autoWegSinds == nil {
                meting?.autoWegSinds = Date()
                bewaarMeting(direct: true)
            }
            // Het meten laten doorlopen houdt de app wakker tijdens het wachten,
            // ook als iOS hem eerder had gestopt.
            Locatie.shared.start()
            plan(over: seconden)
        case .afronden:
            await rondAf()
        case .afrondenEnBegin:
            await rondAf()
            await begin(handmatig: false)
        }
    }

    func startHandmatig() async {
        guard meting == nil else { return }
        await begin(handmatig: true)
    }

    func rondHandmatigAf() async {
        await rondAf()
    }

    /// De lopende meting weggooien, bijvoorbeeld als je meereed met iemand anders.
    func verwerp() {
        wachtTaak?.cancel()
        Locatie.shared.stop()
        meting = nil
        Metingopslag.wis()
        Meldingen.wisRitGestart()
        Logboek.schrijf("Lopende rit weggegooid")
    }

    // MARK: Uitvoering

    private func begin(handmatig: Bool) async {
        guard register.voertuig != nil else {
            Meldingen.probleem(
                "Rit niet gestart",
                "Er is nog geen auto ingesteld. Open de app en vul onder Instellingen je auto in."
            )
            return
        }
        if !Locatie.shared.magAltijd {
            Meldingen.probleem(
                "Rit wordt niet goed gemeten",
                "De app mag je locatie niet altijd gebruiken. Zet bij Instellingen → Privacy → Locatievoorzieningen → Ritten de toegang op Altijd."
            )
        }
        meting = Ritmeting(gestartOp: Date(), handmatig: handmatig)
        bewaarMeting(direct: true)
        Locatie.shared.start()
        Meldingen.ritGestart()

        // Het beginadres: de plek waar je nu bent.
        if let l = await Locatie.shared.huidige() {
            let p = Locatie.positie(l)
            meting?.verwerk(p)
            let adres = await Adreszoeker.adres(p)
            if meting != nil, meting?.startAdres.isEmpty == true {
                meting?.startAdres = adres
                bewaarMeting(direct: true)
            }
        }
    }

    private func ontvang(_ positie: Positie) {
        guard meting != nil else { return }
        if meting?.verwerk(positie) == true { bewaarMeting(direct: false) }
        // Kwam er bij het vertrek nog geen positie (parkeergarage), dan geldt de eerste goede.
        if meting?.startAdres.isEmpty == true, let start = meting?.start, !zoektStartadres {
            zoektStartadres = true
            Task {
                let adres = await Adreszoeker.adres(start)
                zoektStartadres = false
                if meting?.startAdres.isEmpty == true {
                    meting?.startAdres = adres
                    bewaarMeting(direct: true)
                }
            }
        }
    }

    private func plan(over seconden: TimeInterval) {
        wachtTaak?.cancel()
        houdWakker()
        wachtTaak = Task { [weak self] in
            try? await Task.sleep(for: .seconds(seconden))
            guard !Task.isCancelled else { return }
            await self?.verwerk(.wachttijdVoorbij)
        }
    }

    private func rondAf() async {
        wachtTaak?.cancel()
        guard let m = meting else { return }
        houdWakker()
        defer { laatWakker() }
        Locatie.shared.stop()
        meting = nil
        Metingopslag.wis()
        Meldingen.wisRitGestart()

        guard let voertuig = register.voertuig else {
            Meldingen.probleem("Rit niet bewaard", "Er is nog geen auto ingesteld. Vul onder Instellingen je auto in.")
            return
        }
        var eindadres = ""
        if let laatste = m.laatste { eindadres = await Adreszoeker.adres(laatste) }

        let uitkomst = Ritopbouw.maak(
            m,
            voertuig: voertuig,
            ritten: register.ritten,
            eindadres: eindadres,
            voorkeuren: register.instellingen.voorkeuren,
            id: UUID().uuidString,
            nu: Date()
        )
        switch uitkomst {
        case let .teKort(geenPunten):
            Logboek.schrijf("Rit van \(String(format: "%.1f", m.afstandKm)) km niet bewaard (te kort)")
            if geenPunten {
                // Een rit van nul kilometer na een echte verbinding met de auto betekent
                // bijna altijd dat de meting niet werkte. Dat moet je te zien krijgen.
                Meldingen.probleem(
                    "Rit niet gemeten",
                    "De auto was verbonden, maar er kwam geen enkele locatie binnen. Vul de rit met de hand in en controleer de locatietoestemming."
                )
            }
        case let .rit(rit, reden):
            register.bewaar(rit)
            Meldingen.ritAfgerond(rit, reden: reden)
            Logboek.schrijf("Rit bewaard: \(rit.afstandKm) km, \(rit.soort.label)\(rit.bevestigd ? "" : ", te bevestigen")")
        }
    }

    private func bewaarMeting(direct: Bool) {
        guard let meting else { return }
        // Tijdens het rijden niet bij elke positie naar schijf: eens per kwart minuut is genoeg.
        if !direct && Date().timeIntervalSince(laatstBewaard) < 15 { return }
        Metingopslag.schrijf(meting)
        laatstBewaard = Date()
    }

    /// Extra tijd van iOS om het afronden af te maken als de app net naar de achtergrond gaat.
    private func houdWakker() {
        guard achtergrondtaak == .invalid else { return }
        achtergrondtaak = UIApplication.shared.beginBackgroundTask(withName: "Rit afronden") { [weak self] in
            Task { @MainActor in self?.laatWakker() }
        }
    }

    private func laatWakker() {
        guard achtergrondtaak != .invalid else { return }
        UIApplication.shared.endBackgroundTask(achtergrondtaak)
        achtergrondtaak = .invalid
    }

    static func naam(_ g: Ritverloop.Gebeurtenis) -> String {
        switch g {
        case .autoVerbonden: return "Auto verbonden"
        case .autoWeg: return "Auto weg"
        case .wachttijdVoorbij: return "Wachttijd voorbij"
        case .appGestart: return "App gestart"
        }
    }

    static func naam(_ a: Ritverloop.Actie) -> String {
        switch a {
        case .niets: return "niets"
        case .begin: return "rit begint"
        case .gaDoor: return "rit gaat door"
        case let .wachtOpAfronden(s): return "afronden over \(Int(s)) s"
        case .afronden: return "rit afronden"
        case .afrondenEnBegin: return "vorige rit afronden, nieuwe begint"
        }
    }
}
