import Foundation

/// Tot wanneer iOS de app laat openen.
///
/// Een app die met een gratis Apple-account is geïnstalleerd (via SideStore of
/// AltStore) werkt zeven dagen; daarna moet hij worden vernieuwd. Verloopt hij,
/// dan start hij niet meer en worden er geen ritten vastgelegd — en ontbrekende
/// kilometers tellen bij een controle als privé. Daarom waarschuwt de app op tijd.
enum Ondertekening {
    static let verlooptOp: Date? = {
        guard let pad = Bundle.main.path(forResource: "embedded", ofType: "mobileprovision"),
              let data = FileManager.default.contents(atPath: pad),
              let tekst = String(data: data, encoding: .isoLatin1),
              let sleutel = tekst.range(of: "<key>ExpirationDate</key>"),
              let begin = tekst.range(of: "<date>", range: sleutel.upperBound..<tekst.endIndex),
              let eind = tekst.range(of: "</date>", range: begin.upperBound..<tekst.endIndex)
        else { return nil }
        let f = ISO8601DateFormatter()
        return f.date(from: String(tekst[begin.upperBound..<eind.lowerBound]))
    }()

    /// Hele dagen tot het verlopen; nil als er geen vervaldatum is (simulator, App Store).
    static var dagenOver: Int? {
        guard let verlooptOp else { return nil }
        return Int(floor(verlooptOp.timeIntervalSinceNow / 86_400))
    }

    static var bijnaVerlopen: Bool { (dagenOver ?? 99) <= 2 }

    static var beschrijving: String? {
        guard let verlooptOp else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "EEEE d MMMM 'om' HH:mm"
        return f.string(from: verlooptOp)
    }
}
