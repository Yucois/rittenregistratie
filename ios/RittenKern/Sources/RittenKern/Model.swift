import Foundation

/// Het karakter van de rit, zoals artikel 3.13 lid 1 onderdeel c sub 5
/// Uitvoeringsregeling loonbelasting 2011 dat vraagt.
///
/// Woon-werkverkeer staat er apart in omdat dezelfde kilometer fiscaal twee
/// kanten op valt: voor de loonheffing (de 500-kilometergrens bij een
/// Verklaring geen privégebruik auto) telt woon-werk als zakelijk, voor de
/// btw-correctie op het privégebruik telt hij als privé.
public enum Ritsoort: String, Codable, CaseIterable, Sendable {
    case zakelijk = "ZAKELIJK"
    case woonWerk = "WOON_WERK"
    case prive = "PRIVE"

    public var label: String {
        switch self {
        case .zakelijk: return "Zakelijk"
        case .woonWerk: return "Woon-werk"
        case .prive: return "Privé"
        }
    }

    /// Telt deze rit mee voor de 500 privékilometers per kalenderjaar?
    public var priveVoorLoonheffing: Bool { self == .prive }

    /// Telt deze rit mee voor de btw-correctie privégebruik auto?
    public var priveVoorBtw: Bool { self == .prive || self == .woonWerk }
}

/// Waar de gegevens vandaan komen; relevant voor de geloofwaardigheid van de administratie.
public enum Ritbron: String, Codable, Sendable {
    case handmatig = "HANDMATIG"
    case gps = "GPS"
    /// Stand rechtstreeks uit de auto overgenomen.
    case auto = "AUTO"
    case importeren = "IMPORT"
    /// Sluitpost na het ijken op de werkelijke tellerstand.
    case correctie = "CORRECTIE"
}

public struct Voertuig: Codable, Equatable, Sendable {
    public var id: String
    public var merk: String
    public var type: String
    public var kenteken: String
    public var terBeschikkingVanaf: Dag
    public var terBeschikkingTot: Dag?
    /// Kilometerstand op het moment dat de auto ter beschikking kwam.
    public var beginstandKm: Int

    public init(
        id: String,
        merk: String,
        type: String,
        kenteken: String,
        terBeschikkingVanaf: Dag,
        terBeschikkingTot: Dag? = nil,
        beginstandKm: Int
    ) {
        self.id = id
        self.merk = merk
        self.type = type
        self.kenteken = kenteken
        self.terBeschikkingVanaf = terBeschikkingVanaf
        self.terBeschikkingTot = terBeschikkingTot
        self.beginstandKm = beginstandKm
    }

    public var omschrijving: String { "\(merk) \(type) (\(kenteken))" }

    public func terBeschikking(op datum: Dag) -> Bool {
        datum >= terBeschikkingVanaf && (terBeschikkingTot == nil || datum <= terBeschikkingTot!)
    }
}

/// Eén rit. De velden dekken één op één de gegevens die de rittenregistratie
/// volgens artikel 3.13 URLB 2011 moet bevatten, aangevuld met velden die een
/// controle van de Belastingdienst overleefbaar maken (doel, tijdstippen,
/// herkomst van de gegevens, vergrendeling).
///
/// De veldnamen zijn gelijk aan die van de Android-app, zodat een back-up van
/// de ene versie in de andere te lezen is.
public struct Rit: Codable, Equatable, Identifiable, Sendable {
    public var id: String
    public var voertuigId: String
    public var datum: Dag
    public var beginstandKm: Int
    public var eindstandKm: Int
    public var beginadres: String
    public var eindadres: String
    public var soort: Ritsoort
    /// Zakelijk doel of de bezochte relatie. Niet wettelijk verplicht, wel het eerste dat een inspecteur vraagt.
    public var doel: String
    /// Verplicht in te vullen zodra er is afgeweken van de gebruikelijke route.
    public var afwijkendeRoute: String?
    /// Privé-omrijkilometers binnen een zakelijke rit. Tellen mee voor de 500-kilometergrens.
    public var priveOmrijkilometers: Int
    public var vertrektijd: Kloktijd?
    public var aankomsttijd: Kloktijd?
    public var opmerking: String?
    public var bron: Ritbron
    /// Door gps gemeten afstand, ter controle van de ingevoerde tellerstanden.
    public var gemetenKm: Double?
    /// Automatisch vastgelegde ritten worden meteen bewaard — een rit kwijtraken
    /// maakt de reeks onsluitend — maar gelden pas als af zodra jij het karakter
    /// en de standen hebt bevestigd.
    public var bevestigd: Bool
    public var aangemaaktOp: Date
    public var gewijzigdOp: Date
    /// Zodra een periode is afgesloten wordt de rit vergrendeld en alleen nog met een correctierit gewijzigd.
    public var vergrendeldOp: Date?

    public init(
        id: String,
        voertuigId: String,
        datum: Dag,
        beginstandKm: Int,
        eindstandKm: Int,
        beginadres: String,
        eindadres: String,
        soort: Ritsoort,
        doel: String = "",
        afwijkendeRoute: String? = nil,
        priveOmrijkilometers: Int = 0,
        vertrektijd: Kloktijd? = nil,
        aankomsttijd: Kloktijd? = nil,
        opmerking: String? = nil,
        bron: Ritbron = .handmatig,
        gemetenKm: Double? = nil,
        bevestigd: Bool = true,
        aangemaaktOp: Date = Date(timeIntervalSince1970: 0),
        gewijzigdOp: Date = Date(timeIntervalSince1970: 0),
        vergrendeldOp: Date? = nil
    ) {
        self.id = id
        self.voertuigId = voertuigId
        self.datum = datum
        self.beginstandKm = beginstandKm
        self.eindstandKm = eindstandKm
        self.beginadres = beginadres
        self.eindadres = eindadres
        self.soort = soort
        self.doel = doel
        self.afwijkendeRoute = afwijkendeRoute
        self.priveOmrijkilometers = priveOmrijkilometers
        self.vertrektijd = vertrektijd
        self.aankomsttijd = aankomsttijd
        self.opmerking = opmerking
        self.bron = bron
        self.gemetenKm = gemetenKm
        self.bevestigd = bevestigd
        self.aangemaaktOp = aangemaaktOp
        self.gewijzigdOp = gewijzigdOp
        self.vergrendeldOp = vergrendeldOp
    }

    enum CodingKeys: String, CodingKey {
        case id, voertuigId, datum, beginstandKm, eindstandKm, beginadres, eindadres, soort, doel
        case afwijkendeRoute, priveOmrijkilometers, vertrektijd, aankomsttijd, opmerking, bron
        case gemetenKm, bevestigd, aangemaaktOp, gewijzigdOp, vergrendeldOp
    }

    /// Ontbrekende velden krijgen dezelfde standaardwaarde als in de Android-app.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let epoch = Date(timeIntervalSince1970: 0)
        self.init(
            id: try c.decode(String.self, forKey: .id),
            voertuigId: try c.decode(String.self, forKey: .voertuigId),
            datum: try c.decode(Dag.self, forKey: .datum),
            beginstandKm: try c.decode(Int.self, forKey: .beginstandKm),
            eindstandKm: try c.decode(Int.self, forKey: .eindstandKm),
            beginadres: try c.decode(String.self, forKey: .beginadres),
            eindadres: try c.decode(String.self, forKey: .eindadres),
            soort: try c.decode(Ritsoort.self, forKey: .soort),
            doel: try c.decodeIfPresent(String.self, forKey: .doel) ?? "",
            afwijkendeRoute: try c.decodeIfPresent(String.self, forKey: .afwijkendeRoute),
            priveOmrijkilometers: try c.decodeIfPresent(Int.self, forKey: .priveOmrijkilometers) ?? 0,
            vertrektijd: try c.decodeIfPresent(Kloktijd.self, forKey: .vertrektijd),
            aankomsttijd: try c.decodeIfPresent(Kloktijd.self, forKey: .aankomsttijd),
            opmerking: try c.decodeIfPresent(String.self, forKey: .opmerking),
            bron: try c.decodeIfPresent(Ritbron.self, forKey: .bron) ?? .handmatig,
            gemetenKm: try c.decodeIfPresent(Double.self, forKey: .gemetenKm),
            bevestigd: try c.decodeIfPresent(Bool.self, forKey: .bevestigd) ?? true,
            aangemaaktOp: try c.decodeIfPresent(Date.self, forKey: .aangemaaktOp) ?? epoch,
            gewijzigdOp: try c.decodeIfPresent(Date.self, forKey: .gewijzigdOp) ?? epoch,
            vergrendeldOp: try c.decodeIfPresent(Date.self, forKey: .vergrendeldOp)
        )
    }

    public var afstandKm: Int { eindstandKm - beginstandKm }

    public var vergrendeld: Bool { vergrendeldOp != nil }

    /// Kilometers die meetellen voor de 500-kilometergrens van de loonheffing.
    public var priveKmLoonheffing: Int {
        soort.priveVoorLoonheffing ? afstandKm : min(priveOmrijkilometers, afstandKm)
    }

    /// Kilometers die als privé gelden voor de btw-correctie (inclusief woon-werkverkeer).
    public var priveKmBtw: Int {
        soort.priveVoorBtw ? afstandKm : min(priveOmrijkilometers, afstandKm)
    }

    public var zakelijkeKmLoonheffing: Int { afstandKm - priveKmLoonheffing }

    /// Verschil tussen de gps-meting en de ingevoerde tellerstanden, in kilometers.
    public var meetverschilKm: Double? { gemetenKm.map { Double(afstandKm) - $0 } }
}

extension Array where Element == Rit {
    /// Op tellerstand, zoals de registratie hoort te lopen.
    public func opStand() -> [Rit] {
        sorted { a, b in
            if a.beginstandKm != b.beginstandKm { return a.beginstandKm < b.beginstandKm }
            if a.datum != b.datum { return a.datum < b.datum }
            return a.id < b.id
        }
    }
}
