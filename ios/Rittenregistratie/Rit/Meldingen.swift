import Foundation
import RittenKern
import UserNotifications

/// Meldingen na een rit en bij problemen.
///
/// Na een rit krijg je één melding met drie knoppen: zakelijk, woon-werk of
/// privé. Eén tik bevestigt de rit, zonder de app te openen.
enum Meldingen {
    static let ritCategorie = "rit"
    static let ritSleutel = "ritId"

    enum Knop: String, CaseIterable {
        case zakelijk = "rit.zakelijk"
        case woonWerk = "rit.woonwerk"
        case prive = "rit.prive"

        var soort: Ritsoort {
            switch self {
            case .zakelijk: return .zakelijk
            case .woonWerk: return .woonWerk
            case .prive: return .prive
            }
        }
    }

    static func registreer() {
        let knoppen = Knop.allCases.map {
            UNNotificationAction(identifier: $0.rawValue, title: $0.soort.label, options: [])
        }
        let categorie = UNNotificationCategory(identifier: ritCategorie, actions: knoppen, intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([categorie])
    }

    static func vraagToestemming() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func toegestaan() async -> Bool {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        return s.authorizationStatus == .authorized || s.authorizationStatus == .provisional
    }

    static func ritAfgerond(_ rit: Rit, reden: String) {
        let inhoud = UNMutableNotificationContent()
        let km = "\(rit.afstandKm) km"
        if rit.bevestigd {
            inhoud.title = "\(rit.soort.label), \(km)"
            inhoud.body = "\(rit.beginadres) → \(rit.eindadres)\n\(reden) Klopt het niet? Kies hieronder."
        } else {
            inhoud.title = "Rit van \(km): wat was het?"
            inhoud.body = "\(rit.beginadres) → \(rit.eindadres)\nVoorstel: \(rit.soort.label). \(reden)"
        }
        inhoud.sound = rit.bevestigd ? nil : .default
        inhoud.categoryIdentifier = ritCategorie
        inhoud.userInfo = [ritSleutel: rit.id]
        inhoud.threadIdentifier = "ritten"
        verstuur("rit-\(rit.id)", inhoud)
    }

    static func probleem(_ titel: String, _ tekst: String) {
        let inhoud = UNMutableNotificationContent()
        inhoud.title = titel
        inhoud.body = tekst
        inhoud.sound = .default
        inhoud.threadIdentifier = "problemen"
        verstuur("probleem-\(titel)", inhoud)
        Logboek.schrijf("Melding: \(titel). \(tekst)")
    }

    static func ritGestart() {
        let inhoud = UNMutableNotificationContent()
        inhoud.title = "Rit gestart"
        inhoud.body = "De app meet je rit. Na afloop krijg je een melding."
        inhoud.threadIdentifier = "ritten"
        verstuur("rit-gestart", inhoud)
    }

    static func wisRitGestart() {
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["rit-gestart"])
    }

    /// Een dag voor het verlopen van de ondertekening, en op de dag zelf.
    static func planVerloopwaarschuwing() {
        let centrum = UNUserNotificationCenter.current()
        let ids = ["verloopt-morgen", "verloopt-vandaag"]
        centrum.removePendingNotificationRequests(withIdentifiers: ids)
        guard let verloopt = Ondertekening.verlooptOp else { return }
        for (id, voor) in zip(ids, [86_400.0, 3 * 3_600.0]) {
            let moment = verloopt.addingTimeInterval(-voor)
            guard moment > Date() else { continue }
            let inhoud = UNMutableNotificationContent()
            inhoud.title = "Vernieuw de Rittenregistratie"
            inhoud.body = "De app verloopt \(id == "verloopt-morgen" ? "morgen" : "over een paar uur"). " +
                "Open SideStore en tik op vernieuwen, anders worden er geen ritten vastgelegd."
            inhoud.sound = .default
            let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: moment)
            centrum.add(UNNotificationRequest(identifier: id, content: inhoud, trigger: UNCalendarNotificationTrigger(dateMatching: c, repeats: false)))
        }
    }

    private static func verstuur(_ id: String, _ inhoud: UNMutableNotificationContent) {
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: inhoud, trigger: nil))
    }
}
