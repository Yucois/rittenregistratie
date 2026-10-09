import RittenKern
import SwiftUI

struct InstellingenScherm: View {
    @EnvironmentObject private var register: Register
    @EnvironmentObject private var locatie: Locatie

    @State private var merk = ""
    @State private var type = ""
    @State private var kenteken = ""
    @State private var vanaf = Date()
    @State private var heeftEinde = false
    @State private var tot = Date()
    @State private var beginstand = ""
    @State private var opgeslagen = false
    @State private var ijken = false
    @State private var zoekt: String?

    private var autoKlopt: Bool {
        !merk.trimmingCharacters(in: .whitespaces).isEmpty &&
            !kenteken.trimmingCharacters(in: .whitespaces).isEmpty &&
            (Int(beginstand) ?? -1) >= 0
    }

    var body: some View {
        Form {
            Section {
                NavigationLink { AanDeSlagScherm() } label: {
                    Label("Aan de slag", systemImage: "checklist")
                }
            }

            Section {
                TextField("Merk, bijvoorbeeld Audi", text: $merk)
                TextField("Type, bijvoorbeeld RS e-tron GT", text: $type)
                TextField("Kenteken", text: $kenteken).textInputAutocapitalization(.characters)
                DatePicker("Ter beschikking vanaf", selection: $vanaf, displayedComponents: .date)
                Toggle("Einddatum", isOn: $heeftEinde)
                if heeftEinde {
                    DatePicker("Ter beschikking tot", selection: $tot, displayedComponents: .date)
                }
                LabeledContent("Kilometerstand bij ontvangst") {
                    TextField("km", text: $beginstand).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                Button(opgeslagen ? "Opgeslagen" : "Auto opslaan") { bewaarAuto() }
                    .disabled(!autoKlopt)
            } header: {
                Text("Auto")
            } footer: {
                Text("De kilometerstand bij ontvangst is het begin van de reeks. Begin je halverwege met de app, vul dan de stand van vandaag in.")
            }

            Section {
                adres("Thuisadres", \.thuisadres)
                adres("Werkadres", \.werkadres)
            } header: {
                Text("Vaste adressen")
            } footer: {
                Text("Een rit tussen thuis en werk wordt vanzelf woon-werk. Voor de 500 km telt dat als zakelijk, voor de btw als privé.")
            }

            Section {
                Toggle("Ritten automatisch vastleggen", isOn: $register.instellingen.automatischAan)
                Toggle("Zekere ritten zelf bevestigen", isOn: $register.instellingen.automatischBevestigen)
                Picker("Onbekende bestemming", selection: $register.instellingen.standaardSoort) {
                    ForEach(Ritsoort.allCases, id: \.self) { Text($0.label).tag($0) }
                }
            } header: {
                Text("Ritten")
            } footer: {
                Text("Zeker is: tussen thuis en werk, of een bestemming die je al twee keer hetzelfde hebt vastgelegd. Al het andere vraagt om één tik.")
            }

            Section {
                Button("Teller nalopen (ijken)") { ijken = true }
                    .disabled(register.voertuig == nil)
                if let dag = register.instellingen.laatsteIjking, let stand = register.instellingen.standBijLaatsteIjking {
                    LabeledContent("Laatst", value: "\(dag.nederlands), \(stand) km")
                }
            } header: {
                Text("Kilometerstand")
            } footer: {
                Text("De gps meet net iets anders dan de teller. Vul eens per maand de echte stand in; het verschil komt er als aparte, zichtbare correctierit bij.")
            }

            Section("App") {
                LabeledContent("Locatie", value: Locatie.naam(locatie.toestemming))
                if !locatie.nauwkeurig {
                    Text("Locatie staat op 'bij benadering'. Zet 'Nauwkeurige locatie' aan, anders kloppen de adressen niet.")
                        .font(.footnote).foregroundStyle(.orange)
                }
                if let verloopt = Ondertekening.beschrijving {
                    LabeledContent("Werkt tot", value: verloopt)
                }
                LabeledContent("Versie", value: versie)
                NavigationLink("Logboek") { LogboekScherm() }
            }
        }
        .navigationTitle("Instellingen")
        .onAppear(perform: laadAuto)
        .sheet(isPresented: $ijken) { NavigationStack { IjkScherm() } }
    }

    private var versie: String {
        let i = Bundle.main.infoDictionary
        return "\(i?["CFBundleShortVersionString"] as? String ?? "?") (\(i?["CFBundleVersion"] as? String ?? "?"))"
    }

    private func adres(_ titel: String, _ pad: WritableKeyPath<Instellingen, String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            TextField(titel, text: Binding(
                get: { register.instellingen[keyPath: pad] },
                set: { register.instellingen[keyPath: pad] = $0 }
            ), axis: .vertical)
            Button {
                Task {
                    zoekt = titel
                    defer { zoekt = nil }
                    if !locatie.magTijdensGebruik { locatie.vraagToestemming() }
                    if let l = await locatie.huidige() {
                        register.instellingen[keyPath: pad] = await Adreszoeker.adres(Locatie.positie(l))
                    }
                }
            } label: {
                Label(zoekt == titel ? "Zoeken…" : "Ik ben hier nu", systemImage: "location")
            }
            .buttonStyle(.bordered)
            .font(.caption)
        }
    }

    private func laadAuto() {
        guard let v = register.voertuig else { return }
        merk = v.merk
        type = v.type
        kenteken = v.kenteken
        vanaf = v.terBeschikkingVanaf.begin()
        heeftEinde = v.terBeschikkingTot != nil
        tot = v.terBeschikkingTot?.begin() ?? Date()
        beginstand = String(v.beginstandKm)
    }

    private func bewaarAuto() {
        let v = Voertuig(
            id: register.voertuig?.id ?? UUID().uuidString,
            merk: merk.trimmingCharacters(in: .whitespaces),
            type: type.trimmingCharacters(in: .whitespaces),
            kenteken: kenteken.trimmingCharacters(in: .whitespaces).uppercased(),
            terBeschikkingVanaf: Dag(vanaf),
            terBeschikkingTot: heeftEinde ? Dag(tot) : nil,
            beginstandKm: Int(beginstand) ?? 0
        )
        register.bewaar(v)
        opgeslagen = true
        Task {
            try? await Task.sleep(for: .seconds(2))
            opgeslagen = false
        }
    }
}

struct IjkScherm: View {
    @EnvironmentObject private var register: Register
    @Environment(\.dismiss) private var sluit
    @State private var stand = ""
    @State private var uitkomst: Ijking.Uitkomst?

    var body: some View {
        Form {
            Section {
                LabeledContent("Volgens de registratie", value: "\(register.volgendeBeginstand) km")
                LabeledContent("Op de teller") {
                    TextField("km", text: $stand).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
            } footer: {
                Text("Lees de kilometerstand af in de auto of in de app van je auto.")
            }
            if let uitkomst {
                Section {
                    switch uitkomst {
                    case .gelijk:
                        Label("De registratie loopt gelijk met de teller.", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
                    case let .correctie(_, verschil):
                        Text("Er zijn \(verschil) km niet vastgelegd. Die komen erbij als correctierit, voorlopig als privé. Waren het zakelijke kilometers? Pas de correctierit dan aan en leg uit waardoor ze niet zijn geregistreerd.")
                    case let .onmogelijk(melding):
                        Text(melding).foregroundStyle(.red)
                    }
                }
            }
        }
        .navigationTitle("Teller nalopen")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Annuleer") { sluit() } }
            ToolbarItem(placement: .confirmationAction) {
                Button(knoptekst) { verwerk() }.disabled(Int(stand) == nil)
            }
        }
        .onChange(of: stand) { _, _ in uitkomst = nil }
    }

    private var knoptekst: String {
        if case .correctie? = uitkomst { return "Correctie toevoegen" }
        if case .gelijk? = uitkomst { return "Klaar" }
        return "Controleer"
    }

    private func verwerk() {
        guard let voertuig = register.voertuig, let werkelijk = Int(stand) else { return }
        switch uitkomst {
        case let .correctie(rit, _)?:
            register.bewaar(rit)
            register.ijkingVastgelegd(stand: werkelijk)
            sluit()
        case .gelijk?:
            register.ijkingVastgelegd(stand: werkelijk)
            sluit()
        default:
            uitkomst = Ijking.ijk(
                voertuig,
                laatsteStand: register.volgendeBeginstand,
                werkelijkeStand: werkelijk,
                datum: .vandaag(),
                ritId: UUID().uuidString
            )
        }
    }
}

struct LogboekScherm: View {
    @State private var regels = Logboek.regels

    var body: some View {
        List {
            if regels.isEmpty {
                Text("Nog niets gebeurd.").foregroundStyle(.secondary)
            }
            ForEach(Array(regels.enumerated()), id: \.offset) { _, regel in
                Text(regel).font(.caption.monospaced())
            }
        }
        .navigationTitle("Logboek")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                ShareLink(item: regels.joined(separator: "\n")) { Image(systemName: "square.and.arrow.up") }
            }
        }
        .refreshable { regels = Logboek.regels }
    }
}
