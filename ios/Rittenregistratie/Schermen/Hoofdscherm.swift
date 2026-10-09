import RittenKern
import SwiftUI

struct Hoofdscherm: View {
    @EnvironmentObject private var register: Register
    @ObservedObject private var navigatie = Navigatie.shared
    @State private var aanDeSlag = false

    var body: some View {
        TabView(selection: $navigatie.tab) {
            NavigationStack { RittenScherm() }
                .tabItem { Label("Ritten", systemImage: "car") }
                .tag(0)
            NavigationStack { OverzichtScherm() }
                .tabItem { Label("Overzicht", systemImage: "chart.bar") }
                .tag(1)
            NavigationStack { InstellingenScherm() }
                .tabItem { Label("Instellingen", systemImage: "gearshape") }
                .tag(2)
        }
        .onAppear {
            if register.voertuig == nil && !Demo.actief { aanDeSlag = true }
        }
        .sheet(isPresented: $aanDeSlag) {
            NavigationStack {
                AanDeSlagScherm()
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) { Button("Klaar") { aanDeSlag = false } }
                    }
            }
        }
    }
}

/// Kleine bouwstenen die op meer schermen terugkomen.
struct Waarschuwingskaart: View {
    let titel: String
    let tekst: String
    var kleur: Color = .orange
    var systeemicoon = "exclamationmark.triangle.fill"

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: systeemicoon).foregroundStyle(kleur).font(.title3)
            VStack(alignment: .leading, spacing: 2) {
                Text(titel).font(.subheadline.weight(.semibold))
                Text(tekst).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

extension Ritsoort {
    var kleur: Color {
        switch self {
        case .zakelijk: return .blue
        case .woonWerk: return .teal
        case .prive: return .orange
        }
    }

    var icoon: String {
        switch self {
        case .zakelijk: return "briefcase.fill"
        case .woonWerk: return "house.and.flag.fill"
        case .prive: return "person.fill"
        }
    }
}

enum Opmaak {
    static let datum: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "EEEE d MMMM"
        return f
    }()

    static let maand: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "LLLL yyyy"
        return f
    }()

    static let tijd: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "nl_NL")
        f.dateFormat = "HH:mm"
        return f
    }()

    static func dag(_ d: Dag) -> String { datum.string(from: d.begin()).capitalizedFirst }

    static func km(_ waarde: Double) -> String { String(format: "%.1f", locale: Locale(identifier: "nl_NL"), waarde) + " km" }
}

extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
