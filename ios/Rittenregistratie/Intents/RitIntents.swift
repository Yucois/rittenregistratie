import AppIntents
import Foundation

/// "Rit begint": voor de automatisering in Opdrachten bij het verbinden met
/// CarPlay of de bluetooth van de auto.
struct RitBegintIntent: AppIntent {
    static var title: LocalizedStringResource = "Rit begint"
    static var description = IntentDescription(
        "Start het vastleggen van een rit. Zet deze actie in een automatisering die afgaat als je iPhone verbinding maakt met je auto."
    )
    static var openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await Ritregelaar.shared.verwerk(.autoVerbonden)
        return .result()
    }
}

/// "Rit eindigt": voor de automatisering bij het verbreken van de verbinding.
struct RitEindigtIntent: AppIntent {
    static var title: LocalizedStringResource = "Rit eindigt"
    static var description = IntentDescription(
        "Rondt de rit af als de auto niet binnen anderhalve minuut terugkomt. Zet deze actie in een automatisering die afgaat als je iPhone de verbinding met je auto verbreekt."
    )
    static var openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult {
        await Ritregelaar.shared.verwerk(.autoWeg)
        return .result()
    }
}

struct RitSnelkoppelingen: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: RitBegintIntent(),
            phrases: ["Rit begint in \(.applicationName)", "Start rit in \(.applicationName)"],
            shortTitle: "Rit begint",
            systemImageName: "car.fill"
        )
        AppShortcut(
            intent: RitEindigtIntent(),
            phrases: ["Rit eindigt in \(.applicationName)", "Stop rit in \(.applicationName)"],
            shortTitle: "Rit eindigt",
            systemImageName: "parkingsign.circle"
        )
    }
}
