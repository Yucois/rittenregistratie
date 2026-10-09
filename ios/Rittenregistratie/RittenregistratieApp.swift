import RittenKern
import SwiftUI
import UserNotifications

@main
struct RittenregistratieApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var fase

    var body: some Scene {
        WindowGroup {
            Hoofdscherm()
                .environmentObject(Register.shared)
                .environmentObject(Ritregelaar.shared)
                .environmentObject(Locatie.shared)
        }
        .onChange(of: fase) { _, nieuw in
            if nieuw == .active { Meldingen.planVerloopwaarschuwing() }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Meldingen.registreer()
        Demo.zetKlaarAlsGevraagd()
        let doorLocatie = launchOptions?[.location] != nil
        Task { @MainActor in
            if doorLocatie { Logboek.schrijf("Gewekt door een verplaatsing") }
            await Ritregelaar.shared.verwerk(.appGestart)
        }
        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let ritId = response.notification.request.content.userInfo[Meldingen.ritSleutel] as? String
        let actie = response.actionIdentifier
        Task { @MainActor in
            if let ritId {
                if let knop = Meldingen.Knop(rawValue: actie) {
                    Register.shared.bevestig(ritId, soort: knop.soort)
                    Logboek.schrijf("Rit bevestigd via melding: \(knop.soort.label)")
                } else if actie == UNNotificationDefaultActionIdentifier {
                    Navigatie.shared.openRit(ritId)
                }
            }
            completionHandler()
        }
    }
}

/// Wat de app moet tonen als je op een melding tikt.
@MainActor
final class Navigatie: ObservableObject {
    static let shared = Navigatie()
    @Published var tab = 0
    @Published var openRitId: String?

    func openRit(_ id: String) {
        tab = 0
        openRitId = id
    }
}
