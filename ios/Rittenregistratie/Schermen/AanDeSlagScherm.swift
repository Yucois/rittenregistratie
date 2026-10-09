import RittenKern
import SwiftUI
import UIKit

/// De stappen om de app te laten werken, met per stap of hij klaar is.
struct AanDeSlagScherm: View {
    @EnvironmentObject private var register: Register
    @EnvironmentObject private var locatie: Locatie
    @Environment(\.scenePhase) private var fase
    @State private var meldingenAan = false

    var body: some View {
        List {
            Section {
                Text("Eén keer instellen, daarna gaat het vanzelf: stap je in, dan begint de rit; stap je uit, dan krijg je een melding met de vraag of het zakelijk, woon-werk of privé was.")
                    .font(.callout)
            }

            Stap(nummer: 1, titel: "Meldingen toestaan", klaar: meldingenAan,
                 uitleg: "Na elke rit vraagt de app met een melding wat het was.") {
                Button("Meldingen toestaan") {
                    Task {
                        _ = await Meldingen.vraagToestemming()
                        meldingenAan = await Meldingen.toegestaan()
                        if !meldingenAan { openInstellingen() }
                    }
                }
            }

            Stap(nummer: 2, titel: "Je auto invullen", klaar: register.voertuig != nil,
                 uitleg: "Merk, kenteken en de kilometerstand van vandaag. Dat doe je onder Instellingen.") {
                EmptyView()
            }

            Stap(nummer: 3, titel: "Thuis- en werkadres", klaar: !register.instellingen.thuisadres.isEmpty && !register.instellingen.werkadres.isEmpty,
                 uitleg: "Dan herkent de app woon-werkritten zelf. Ook onder Instellingen; 'Ik ben hier nu' vult het adres in.") {
                EmptyView()
            }

            Stap(nummer: 4, titel: "Locatie: Altijd", klaar: locatie.magAltijd && locatie.nauwkeurig,
                 uitleg: locatieUitleg) {
                if locatie.geweigerd || locatie.magAltijd {
                    Button("Open instellingen") { openInstellingen() }
                } else {
                    Button(locatie.magTijdensGebruik ? "Zet op Altijd" : "Locatie toestaan") { locatie.vraagToestemming() }
                }
            }

            Stap(nummer: 5, titel: "Automatisering: instappen", klaar: register.instellingen.laatsteAutomatischeStart != nil,
                 uitleg: "Dit laat iOS een rit starten als je telefoon verbinding maakt met de auto.") {
                VStack(alignment: .leading, spacing: 6) {
                    Instructie("Open de app Opdrachten en tik onderaan op Automatisering.")
                    Instructie("Tik op + (of Nieuwe automatisering).")
                    Instructie("Kies CarPlay als je auto dat heeft, anders Bluetooth en dan de naam van je auto.")
                    Instructie("Kies Verbindt (bij Bluetooth: Is verbonden) en zet Direct uitvoeren aan.")
                    Instructie("Tik op Volgende, zoek \"Rit begint\" en kies die actie van Ritten.")
                    Button("Open Opdrachten") { open("shortcuts://") }.padding(.top, 4)
                }
            }

            Stap(nummer: 6, titel: "Automatisering: uitstappen", klaar: register.instellingen.laatsteAutomatischeStop != nil,
                 uitleg: "Dezelfde stappen, maar dan bij het verbreken van de verbinding.") {
                VStack(alignment: .leading, spacing: 6) {
                    Instructie("Nieuwe automatisering, weer CarPlay of Bluetooth met je auto.")
                    Instructie("Kies Verbreekt (bij Bluetooth: Is verbroken) en zet Direct uitvoeren aan.")
                    Instructie("Kies de actie \"Rit eindigt\" van Ritten.")
                    Text("De rit wordt anderhalve minuut na het verbreken afgerond. Valt de verbinding even weg, dan blijft het één rit.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            if Ondertekening.verlooptOp != nil {
                Stap(nummer: 7, titel: "Vernieuwen elke week", klaar: !Ondertekening.bijnaVerlopen,
                     uitleg: "Met een gratis Apple-account werkt de app zeven dagen. Vernieuw hem in SideStore voordat hij verloopt; de app waarschuwt een dag van tevoren.") {
                    if let verloopt = Ondertekening.beschrijving {
                        Text("Werkt tot \(verloopt).").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                Text("Werkt het? Na je eerste rit met de auto staan stap 5 en 6 op groen. Klopt er iets niet, kijk dan onder Instellingen → Logboek wat de app heeft gezien.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Aan de slag")
        .task { meldingenAan = await Meldingen.toegestaan() }
        .onChange(of: fase) { _, nieuw in
            if nieuw == .active { Task { meldingenAan = await Meldingen.toegestaan() } }
        }
    }

    private var locatieUitleg: String {
        if locatie.geweigerd { return "Locatie staat uit voor deze app. Zet hem aan in Instellingen → Ritten → Locatie → Altijd." }
        if locatie.magAltijd && !locatie.nauwkeurig { return "Zet ook 'Nauwkeurige locatie' aan, anders kloppen de adressen niet." }
        if locatie.magAltijd { return "In orde." }
        return "Met de telefoon op slot meet de app alleen met Altijd. iOS vraagt het in twee stappen: eerst 'Bij gebruik', daarna 'Wijzig in Altijd'."
    }

    private func openInstellingen() { open(UIApplication.openSettingsURLString) }

    private func open(_ adres: String) {
        if let url = URL(string: adres) { UIApplication.shared.open(url) }
    }
}

private struct Stap<Inhoud: View>: View {
    let nummer: Int
    let titel: String
    let klaar: Bool
    let uitleg: String
    @ViewBuilder let inhoud: () -> Inhoud

    var body: some View {
        Section {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: klaar ? "checkmark.circle.fill" : "\(nummer).circle")
                    .font(.title2)
                    .foregroundStyle(klaar ? .green : .accentColor)
                VStack(alignment: .leading, spacing: 4) {
                    Text(titel).font(.headline)
                    Text(uitleg).font(.footnote).foregroundStyle(.secondary)
                    inhoud().buttonStyle(.bordered)
                }
            }
            .padding(.vertical, 2)
        }
    }
}

private struct Instructie: View {
    let tekst: String
    init(_ tekst: String) { self.tekst = tekst }

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            Text("•")
            Text(tekst)
        }
        .font(.footnote)
    }
}
