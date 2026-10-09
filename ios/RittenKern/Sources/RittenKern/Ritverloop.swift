import Foundation

/// Wanneer een rit begint en eindigt.
///
/// Op de iPhone mag een app niet zelf zien dat de bluetooth van de auto
/// verbinding maakt. Dat signaal komt van een automatisering in de app
/// Opdrachten: "als mijn iPhone verbinding maakt met CarPlay of met de
/// bluetooth van de auto, start rit" en "bij verbreken, stop rit". Alleen de
/// eigen auto start dus een rit; lopen of meerijden in een andere auto niet.
///
/// Wat hier staat is de afweging bij elk signaal, los van iOS, zodat hij te
/// testen is op de gevallen die in het echt voorkomen.
public enum Ritverloop {

    /// Zo lang wacht de app na het verbreken voordat de rit wordt afgerond. Een
    /// verbinding die even wegvalt (tanken, opnieuw starten van het infotainment)
    /// maakt er zo geen twee ritten van.
    public static let wachttijd: TimeInterval = 90

    /// Komt er een nieuwe verbinding terwijl er nog een rit loopt waarin al zo
    /// lang niet is bewogen, dan is het verbreken gemist: de oude rit wordt
    /// afgesloten en er begint een nieuwe.
    public static let gemistVerbrekenNa: TimeInterval = 15 * 60

    public enum Gebeurtenis: Sendable {
        case autoVerbonden
        case autoWeg
        /// De wachttijd na het verbreken is om.
        case wachttijdVoorbij
        /// De app start of komt terug na te zijn gestopt door iOS.
        case appGestart
    }

    public struct Toestand: Sendable {
        public var automatischAan: Bool
        public var ritLoopt: Bool
        public var ritHandmatig: Bool
        /// Wanneer de auto weg was, als de rit daarop wacht.
        public var autoWegSinds: Date?
        /// Laatste keer dat de meting beweging zag.
        public var laatsteBeweging: Date?

        public init(
            automatischAan: Bool,
            ritLoopt: Bool,
            ritHandmatig: Bool = false,
            autoWegSinds: Date? = nil,
            laatsteBeweging: Date? = nil
        ) {
            self.automatischAan = automatischAan
            self.ritLoopt = ritLoopt
            self.ritHandmatig = ritHandmatig
            self.autoWegSinds = autoWegSinds
            self.laatsteBeweging = laatsteBeweging
        }
    }

    public enum Actie: Equatable, Sendable {
        case niets
        case begin
        /// De auto is terug binnen de wachttijd: de lopende rit gaat door.
        case gaDoor
        /// Rond af zodra deze tijd om is, tenzij de auto eerder terugkomt.
        case wachtOpAfronden(seconden: TimeInterval)
        case afronden
        /// De vorige rit heeft zijn einde gemist; sluit hem af en begin opnieuw.
        case afrondenEnBegin
    }

    public static func beslis(_ gebeurtenis: Gebeurtenis, _ t: Toestand, nu: Date) -> Actie {
        // Een rit die je zelf hebt gestart, stop je ook zelf. De auto heeft daar
        // niets over te zeggen: misschien rijd je in een andere auto.
        if t.ritLoopt && t.ritHandmatig { return .niets }

        func resterend(_ sinds: Date) -> TimeInterval { max(0, wachttijd - nu.timeIntervalSince(sinds)) }

        switch gebeurtenis {
        case .autoVerbonden:
            guard t.automatischAan else { return .niets }
            guard t.ritLoopt else { return .begin }
            if let sinds = t.autoWegSinds {
                // Terug binnen de wachttijd: dezelfde rit. Later terug betekent dat
                // iOS de app tijdens het wachten heeft gestopt; die rit is al voorbij.
                return nu.timeIntervalSince(sinds) < wachttijd ? .gaDoor : .afrondenEnBegin
            }
            if let beweging = t.laatsteBeweging, nu.timeIntervalSince(beweging) >= gemistVerbrekenNa {
                return .afrondenEnBegin
            }
            return .niets

        case .autoWeg:
            guard t.ritLoopt else { return .niets }
            if let sinds = t.autoWegSinds { return .wachtOpAfronden(seconden: resterend(sinds)) }
            return .wachtOpAfronden(seconden: wachttijd)

        case .wachttijdVoorbij, .appGestart:
            guard t.ritLoopt, let sinds = t.autoWegSinds else { return .niets }
            let rest = resterend(sinds)
            return rest <= 1 ? .afronden : .wachtOpAfronden(seconden: rest)
        }
    }
}

/// Eén positie, los van CoreLocation.
public struct Positie: Codable, Equatable, Sendable {
    public var breedte: Double
    public var lengte: Double
    /// Onnauwkeurigheid in meters; negatief betekent onbekend.
    public var nauwkeurigheid: Double
    public var moment: Date

    public init(breedte: Double, lengte: Double, nauwkeurigheid: Double, moment: Date) {
        self.breedte = breedte
        self.lengte = lengte
        self.nauwkeurigheid = nauwkeurigheid
        self.moment = moment
    }

    /// Afstand in meters over het aardoppervlak.
    public func afstand(tot p: Positie) -> Double {
        let r = 6_371_000.0
        let f1 = breedte * .pi / 180, f2 = p.breedte * .pi / 180
        let df = (p.breedte - breedte) * .pi / 180
        let dl = (p.lengte - lengte) * .pi / 180
        let a = sin(df / 2) * sin(df / 2) + cos(f1) * cos(f2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

/// De rit die op dit moment wordt gemeten.
///
/// De gps-meting is hulp bij het invullen, geen vervanging van de
/// kilometerteller: de fiscale registratie draait om de tellerstanden, en een
/// gps-spoor wijkt daar altijd een paar procent van af.
public struct Ritmeting: Codable, Equatable, Sendable {
    public var gestartOp: Date
    public var handmatig: Bool
    public var afstandMeter: Double = 0
    public var startAdres: String = ""
    public var start: Positie?
    public var laatste: Positie?
    public var aantalPunten: Int = 0
    public var laatsteBeweging: Date?
    public var autoWegSinds: Date?
    /// Plek waar de laatste beweging is vastgesteld.
    var bewegingAnker: Positie?

    /// Fixes die slechter zijn dan dit aantal meter worden niet meegeteld.
    static let maxOnnauwkeurigheid = 50.0
    /// Sprong die een hogere snelheid dan 250 km/u impliceert: een uitschieter, niet gereden.
    static let maxSnelheid = 70.0
    /// Pas wie zo ver van de vorige plek is, beweegt; kleiner is gps-ruis van een stilstaande telefoon.
    static let bewegingVanaf = 100.0

    public init(gestartOp: Date, handmatig: Bool) {
        self.gestartOp = gestartOp
        self.handmatig = handmatig
        self.laatsteBeweging = gestartOp
    }

    public var afstandKm: Double { afstandMeter / 1000 }

    /// Verwerkt een nieuwe positie; geeft terug of de meting is bijgewerkt.
    @discardableResult
    public mutating func verwerk(_ p: Positie) -> Bool {
        if p.nauwkeurigheid < 0 || p.nauwkeurigheid > Ritmeting.maxOnnauwkeurigheid { return false }
        if let vorige = laatste {
            if p.moment <= vorige.moment { return false }
            let sprong = vorige.afstand(tot: p)
            let seconden = max(1, p.moment.timeIntervalSince(vorige.moment))
            // Een sprong die een onmogelijke snelheid vraagt komt van een slechte fix, niet van de auto.
            if sprong >= Ritmeting.maxSnelheid * seconden { return false }
            afstandMeter += sprong
        }
        if let anker = bewegingAnker {
            if anker.afstand(tot: p) >= Ritmeting.bewegingVanaf {
                laatsteBeweging = p.moment
                bewegingAnker = p
            }
        } else {
            bewegingAnker = p
        }
        if start == nil { start = p }
        laatste = p
        aantalPunten += 1
        return true
    }
}
