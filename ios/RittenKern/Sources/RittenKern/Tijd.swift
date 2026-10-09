import Foundation

/// Een kalenderdag zonder tijdzone, zoals `LocalDate` in de Android-app.
/// Opgeslagen als ISO-8601 (`2026-03-02`), zodat een back-up leesbaar blijft en
/// uitwisselbaar is met de Android-versie.
public struct Dag: Hashable, Comparable, Codable, CustomStringConvertible, Sendable {
    public let jaar: Int
    public let maand: Int
    public let dag: Int

    public init(_ jaar: Int, _ maand: Int, _ dag: Int) {
        self.jaar = jaar
        self.maand = maand
        self.dag = dag
    }

    public init?(iso: String) {
        let delen = iso.prefix(10).split(separator: "-").compactMap { Int($0) }
        guard delen.count == 3, (1...12).contains(delen[1]), (1...31).contains(delen[2]) else { return nil }
        self.init(delen[0], delen[1], delen[2])
    }

    /// De dag waarop `moment` valt in `tijdzone`.
    public init(_ moment: Date, tijdzone: TimeZone = .current) {
        var kalender = Calendar(identifier: .gregorian)
        kalender.timeZone = tijdzone
        let c = kalender.dateComponents([.year, .month, .day], from: moment)
        self.init(c.year ?? 1970, c.month ?? 1, c.day ?? 1)
    }

    public static func vandaag() -> Dag { Dag(Date()) }

    public var description: String { String(format: "%04d-%02d-%02d", jaar, maand, dag) }

    /// Zoals op een Nederlands formulier: `02-03-2026`.
    public var nederlands: String { String(format: "%02d-%02d-%04d", dag, maand, jaar) }

    public static func < (a: Dag, b: Dag) -> Bool {
        (a.jaar, a.maand, a.dag) < (b.jaar, b.maand, b.dag)
    }

    private static let utc: Calendar = {
        var k = Calendar(identifier: .gregorian)
        k.timeZone = TimeZone(identifier: "UTC")!
        return k
    }()

    private var middagUtc: Date {
        Dag.utc.date(from: DateComponents(year: jaar, month: maand, day: dag, hour: 12))!
    }

    /// 1 voor 1 januari, 365 of 366 voor 31 december.
    public var dagVanHetJaar: Int {
        Dag.utc.ordinality(of: .day, in: .year, for: middagUtc) ?? 1
    }

    /// Aantal dagen van deze dag tot `andere`; negatief als `andere` eerder ligt.
    public func dagen(tot andere: Dag) -> Int {
        Dag.utc.dateComponents([.day], from: middagUtc, to: andere.middagUtc).day ?? 0
    }

    /// Begin van deze dag in `tijdzone`, voor datumkiezers.
    public func begin(tijdzone: TimeZone = .current) -> Date {
        var kalender = Calendar(identifier: .gregorian)
        kalender.timeZone = tijdzone
        return kalender.date(from: DateComponents(year: jaar, month: maand, day: dag))!
    }

    public init(from decoder: Decoder) throws {
        let tekst = try decoder.singleValueContainer().decode(String.self)
        guard let dag = Dag(iso: tekst) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Geen datum: \(tekst)"))
        }
        self = dag
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(description)
    }
}

/// Een tijdstip op de dag, op de minuut, zoals `LocalTime` in de Android-app.
public struct Kloktijd: Hashable, Comparable, Codable, CustomStringConvertible, Sendable {
    public let uur: Int
    public let minuut: Int

    public init(_ uur: Int, _ minuut: Int) {
        self.uur = uur
        self.minuut = minuut
    }

    public init?(iso: String) {
        let delen = iso.split(separator: ":").prefix(2).compactMap { Int($0) }
        guard delen.count == 2, (0...23).contains(delen[0]), (0...59).contains(delen[1]) else { return nil }
        self.init(delen[0], delen[1])
    }

    public init(_ moment: Date, tijdzone: TimeZone = .current) {
        var kalender = Calendar(identifier: .gregorian)
        kalender.timeZone = tijdzone
        let c = kalender.dateComponents([.hour, .minute], from: moment)
        self.init(c.hour ?? 0, c.minute ?? 0)
    }

    public var description: String { String(format: "%02d:%02d", uur, minuut) }

    public static func < (a: Kloktijd, b: Kloktijd) -> Bool { (a.uur, a.minuut) < (b.uur, b.minuut) }

    public init(from decoder: Decoder) throws {
        let tekst = try decoder.singleValueContainer().decode(String.self)
        guard let tijd = Kloktijd(iso: tekst) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Geen tijd: \(tekst)"))
        }
        self = tijd
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(description)
    }
}

/// ISO-8601 voor tijdstempels, met en zonder fracties van seconden: de
/// Android-app schrijft ze soms met, soms zonder.
public enum Tijdstempel {
    private static let zonder: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    private static let met: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    public static func tekst(_ moment: Date) -> String { zonder.string(from: moment) }

    public static func lees(_ tekst: String) -> Date? { zonder.date(from: tekst) ?? met.date(from: tekst) }

    /// Opslag met milliseconden, zodat heen en terug lezen niets verandert.
    public static func codeer(_ moment: Date, _ encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(met.string(from: moment))
    }

    public static func decodeer(_ decoder: Decoder) throws -> Date {
        let tekst = try decoder.singleValueContainer().decode(String.self)
        guard let moment = lees(tekst) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Geen tijdstempel: \(tekst)"))
        }
        return moment
    }

    public static var encoder: JSONEncoder {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .custom { moment, encoder in try codeer(moment, encoder) }
        e.outputFormatting = [.prettyPrinted, .sortedKeys]
        return e
    }

    public static var decoder: JSONDecoder {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { decoder in try decodeer(decoder) }
        return d
    }
}

enum Getal {
    private static let nl = Locale(identifier: "nl_NL")

    /// Eén decimaal met een komma, zoals de Android-app het op een Nederlandse telefoon toont.
    static func eenDecimaal(_ waarde: Double) -> String {
        String(format: "%.1f", locale: nl, waarde)
    }
}
