import RittenKern
import SwiftUI

struct RitBewerkenScherm: View {
    @EnvironmentObject private var register: Register
    @Environment(\.dismiss) private var sluit

    @State var rit: Rit
    let isNieuw: Bool

    @State private var datum = Date()
    @State private var begin = ""
    @State private var eind = ""
    @State private var omwegAan = false
    @State private var omrij = ""
    @State private var verwijderen = false

    private var voorbeeld: Rit {
        var r = rit
        r.datum = Dag(datum)
        r.beginstandKm = Int(begin) ?? 0
        r.eindstandKm = Int(eind) ?? 0
        r.priveOmrijkilometers = omwegAan ? (Int(omrij) ?? 0) : 0
        if !omwegAan { r.afwijkendeRoute = nil }
        return r
    }

    private var bevindingen: [Bevinding] { Rittencontrole.controleerRit(voorbeeld, register.voertuig) }

    private var heeftFout: Bool {
        bevindingen.contains { $0.ernst == .fout && $0.code != "rit.onbevestigd" }
    }

    var body: some View {
        Form {
            Section {
                Picker("Karakter", selection: $rit.soort) {
                    ForEach(Ritsoort.allCases, id: \.self) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                if rit.soort == .zakelijk {
                    TextField("Zakelijk doel of relatie", text: $rit.doel)
                }
            } header: {
                Text("Wat was het?")
            } footer: {
                Text(uitleg(rit.soort))
            }

            Section("Wanneer") {
                DatePicker("Datum", selection: $datum, displayedComponents: .date)
                    .environment(\.locale, Locale(identifier: "nl_NL"))
                if let v = rit.vertrektijd {
                    LabeledContent("Vertrek", value: v.description)
                }
                if let a = rit.aankomsttijd {
                    LabeledContent("Aankomst", value: a.description)
                }
            }

            Section {
                LabeledContent("Beginstand") {
                    TextField("km", text: $begin).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                LabeledContent("Eindstand") {
                    TextField("km", text: $eind).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                LabeledContent("Afstand", value: "\(voorbeeld.afstandKm) km")
                if let gemeten = rit.gemetenKm {
                    LabeledContent("Gemeten met gps", value: Opmaak.km(gemeten))
                }
            } header: {
                Text("Kilometerstand")
            } footer: {
                if rit.bron == .gps {
                    Text("De standen zijn berekend uit de gps-meting. Een paar procent verschil met de teller is normaal; dat haal je recht door af en toe de echte stand in te vullen (ijken).")
                }
            }

            if rit.bron != .correctie {
                Section("Route") {
                    AdresVeld(titel: "Van", adres: $rit.beginadres)
                    AdresVeld(titel: "Naar", adres: $rit.eindadres)
                    Toggle("Afgeweken van de gebruikelijke route", isOn: $omwegAan)
                    if omwegAan {
                        TextField(
                            "Gereden route",
                            text: Binding(get: { rit.afwijkendeRoute ?? "" }, set: { rit.afwijkendeRoute = $0 }),
                            axis: .vertical
                        )
                        LabeledContent("Privé omrijden") {
                            TextField("km", text: $omrij).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                        }
                    }
                }
            }

            Section(rit.bron == .correctie ? "Toelichting (verplicht)" : "Opmerking") {
                TextField(
                    rit.bron == .correctie ? "Waardoor zijn deze kilometers niet vastgelegd?" : "Optioneel",
                    text: Binding(get: { rit.opmerking ?? "" }, set: { rit.opmerking = $0.isEmpty ? nil : $0 }),
                    axis: .vertical
                )
            }

            let zichtbaar = bevindingen.filter { $0.code != "rit.onbevestigd" }
            if !zichtbaar.isEmpty {
                Section("Controle") {
                    ForEach(zichtbaar, id: \.code) { b in
                        Label(b.melding, systemImage: b.ernst == .fout ? "xmark.octagon.fill" : "exclamationmark.triangle.fill")
                            .foregroundStyle(b.ernst == .fout ? .red : .orange)
                            .font(.footnote)
                    }
                }
            }

            Section {
                LabeledContent("Vastgelegd via", value: CsvBron.naam(rit.bron))
                if !isNieuw {
                    Button("Rit verwijderen", role: .destructive) { verwijderen = true }
                }
            }
        }
        .navigationTitle(isNieuw ? "Nieuwe rit" : (rit.bevestigd ? "Rit" : "Rit bevestigen"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Annuleer") { sluit() } }
            ToolbarItem(placement: .confirmationAction) {
                Button(rit.bevestigd || isNieuw ? "Bewaar" : "Bevestig") {
                    var r = voorbeeld
                    r.bevestigd = true
                    register.bewaar(r)
                    sluit()
                }
                .disabled(heeftFout)
            }
        }
        .confirmationDialog("Deze rit verwijderen?", isPresented: $verwijderen, titleVisibility: .visible) {
            Button("Verwijderen", role: .destructive) {
                register.verwijder(rit.id)
                sluit()
            }
        } message: {
            Text("De kilometers van deze rit worden dan een gat in de reeks. Dat telt bij een controle als privé.")
        }
        .onAppear {
            datum = rit.datum.begin()
            begin = String(rit.beginstandKm)
            eind = rit.eindstandKm > rit.beginstandKm ? String(rit.eindstandKm) : ""
            omwegAan = rit.priveOmrijkilometers > 0 || !(rit.afwijkendeRoute ?? "").isEmpty
            omrij = rit.priveOmrijkilometers > 0 ? String(rit.priveOmrijkilometers) : ""
        }
    }

    private func uitleg(_ soort: Ritsoort) -> String {
        switch soort {
        case .zakelijk: return "Telt niet mee voor de 500 privékilometers."
        case .woonWerk: return "Telt als zakelijk voor de 500 km, maar als privé voor de btw."
        case .prive: return "Telt mee voor de 500 privékilometers per jaar."
        }
    }
}

/// Adres met snelknoppen voor thuis en werk.
struct AdresVeld: View {
    @EnvironmentObject private var register: Register
    let titel: String
    @Binding var adres: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField(titel, text: $adres, axis: .vertical)
            HStack(spacing: 8) {
                if !register.instellingen.thuisadres.isEmpty {
                    Button("Thuis") { adres = register.instellingen.thuisadres }
                }
                if !register.instellingen.werkadres.isEmpty {
                    Button("Werk") { adres = register.instellingen.werkadres }
                }
            }
            .buttonStyle(.bordered)
            .font(.caption)
        }
    }
}

enum CsvBron {
    static func naam(_ bron: Ritbron) -> String {
        switch bron {
        case .handmatig: return "Met de hand"
        case .gps: return "Gps-meting"
        case .auto: return "Tellerstand uit de auto"
        case .importeren: return "Import"
        case .correctie: return "Correctie na ijken"
        }
    }
}
