import Foundation

/// De laatste gebeurtenissen rond het starten en stoppen van ritten. Staat
/// onder Instellingen, zodat je kunt zien wat er gebeurde als een rit ontbreekt.
enum Logboek {
    private static let sleutel = "logboek"
    private static let maximum = 80

    static func schrijf(_ tekst: String) {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "d MMM HH:mm:ss"
        var regels = UserDefaults.standard.stringArray(forKey: sleutel) ?? []
        regels.insert("\(f.string(from: Date()))  \(tekst)", at: 0)
        UserDefaults.standard.set(Array(regels.prefix(maximum)), forKey: sleutel)
    }

    static var regels: [String] { UserDefaults.standard.stringArray(forKey: sleutel) ?? [] }

    static func wis() { UserDefaults.standard.removeObject(forKey: sleutel) }
}
