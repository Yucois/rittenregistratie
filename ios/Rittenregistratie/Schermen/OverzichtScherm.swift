import RittenKern
import SwiftUI
import UniformTypeIdentifiers

struct OverzichtScherm: View {
    @EnvironmentObject private var register: Register

    @State private var jaar = Dag.vandaag().jaar
    @State private var importeren = false
    @State private var terugTeZetten: Backup?
    @State private var importfout: String?

    private var jaren: [Int] {
        let uitRitten = Set(register.eigenRitten.map { $0.datum.jaar })
        return uitRitten.union([Dag.vandaag().jaar]).sorted(by: >)
    }

    var body: some View {
        List {
            if let voertuig = register.voertuig, let controle = register.controle() {
                Section {
                    Picker("Jaar", selection: $jaar) {
                        ForEach(jaren, id: \.self) { Text(String($0)).tag($0) }
                    }
                }
                let overzicht = controle.jaar(jaar)
                    ?? Jaaroverzicht(jaar: jaar, aantalRitten: 0, totaalKm: 0, zakelijkKm: 0, woonWerkKm: 0, priveKm: 0, priveOmrijKm: 0, nietVerantwoordeKm: 0)
                grens(overzicht)
                kilometers(overzicht)
                bevindingenSectie(controle)
                export(voertuig, controle)
            } else {
                Section {
                    Text("Vul eerst onder Instellingen je auto in.").foregroundStyle(.secondary)
                }
            }
            Section {
                Button { importeren = true } label: { Label("Back-up terugzetten", systemImage: "square.and.arrow.down") }
            } footer: {
                Text("Ook een back-up uit de Android-versie van de app is hier terug te zetten.")
            }
        }
        .navigationTitle("Overzicht")
        .fileImporter(isPresented: $importeren, allowedContentTypes: [.json]) { uitkomst in
            do {
                let url = try uitkomst.get()
                let toegang = url.startAccessingSecurityScopedResource()
                defer { if toegang { url.stopAccessingSecurityScopedResource() } }
                terugTeZetten = try BackupOpslag.lees(Data(contentsOf: url))
            } catch {
                importfout = error.localizedDescription
            }
        }
        .alert(
            "Back-up terugzetten?",
            isPresented: Binding(get: { terugTeZetten != nil }, set: { if !$0 { terugTeZetten = nil } })
        ) {
            Button("Vervang alles", role: .destructive) {
                if let b = terugTeZetten { register.zetTerug(b) }
                terugTeZetten = nil
            }
            Button("Annuleer", role: .cancel) { terugTeZetten = nil }
        } message: {
            Text("De back-up bevat \(terugTeZetten?.ritten.count ?? 0) ritten. Wat nu in de app staat wordt vervangen.")
        }
        .alert(
            "Back-up niet te lezen",
            isPresented: Binding(get: { importfout != nil }, set: { if !$0 { importfout = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(importfout ?? "")
        }
    }

    private func grens(_ o: Jaaroverzicht) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
                    Text("\(o.priveKmLoonheffingWorstCase)").font(.largeTitle.weight(.semibold).monospacedDigit())
                    Text("van 500 privékilometers").foregroundStyle(.secondary)
                }
                ProgressView(value: min(1, Double(o.priveKmLoonheffingWorstCase) / Double(priveGrensKm)))
                    .tint(o.grensOverschreden ? .red : (o.priveKmLoonheffingWorstCase > 400 ? .orange : .green))
                if o.grensOverschreden {
                    Text("De grens is overschreden: de Verklaring geen privégebruik auto vervalt en er volgt bijtelling.")
                        .font(.footnote).foregroundStyle(.red)
                } else {
                    Text("Nog \(o.restantTot500) km over.").font(.footnote).foregroundStyle(.secondary)
                }
                if jaar == Dag.vandaag().jaar, o.priveKmLoonheffingWorstCase > 0 {
                    let prognose = o.prognosePriveKm(.vandaag())
                    Text("In dit tempo kom je dit jaar uit op \(prognose) km.")
                        .font(.footnote)
                        .foregroundStyle(prognose > priveGrensKm ? .red : .secondary)
                }
            }
            .padding(.vertical, 4)
        } header: {
            Text("Privé voor de loonheffing")
        } footer: {
            if o.nietVerantwoordeKm > 0 {
                Text("Inclusief \(o.nietVerantwoordeKm) km die niet verantwoord zijn. Bij een controle tellen die als privé.")
            }
        }
    }

    private func kilometers(_ o: Jaaroverzicht) -> some View {
        Section("Kilometers \(String(jaar))") {
            LabeledContent("Ritten", value: "\(o.aantalRitten)")
            LabeledContent("Totaal", value: "\(o.totaalKm) km")
            LabeledContent("Zakelijk", value: "\(o.zakelijkKm) km")
            LabeledContent("Woon-werk", value: "\(o.woonWerkKm) km")
            LabeledContent("Privé", value: "\(o.priveKm) km")
            if o.priveOmrijKm > 0 { LabeledContent("Privé omrijden", value: "\(o.priveOmrijKm) km") }
            if o.nietVerantwoordeKm > 0 {
                LabeledContent("Niet verantwoord") { Text("\(o.nietVerantwoordeKm) km").foregroundStyle(.red) }
            }
            LabeledContent(
                "Privé voor de btw",
                value: "\(o.priveKmBtw) km (\(String(format: "%.1f", locale: Locale(identifier: "nl_NL"), o.btwPriveAandeel * 100))%)"
            )
            if o.onbevestigdeRitten > 0 {
                LabeledContent("Nog te bevestigen") { Text("\(o.onbevestigdeRitten)").foregroundStyle(.orange) }
            }
        }
    }

    @ViewBuilder
    private func bevindingenSectie(_ controle: Controleresultaat) -> some View {
        let vanJaar = controle.bevindingen.filter { b in
            b.code != "rit.onbevestigd" &&
                (b.ritId == nil || register.rit(b.ritId!)?.datum.jaar == jaar)
        }
        Section {
            if vanJaar.isEmpty {
                Label("De registratie is sluitend", systemImage: "checkmark.seal.fill").foregroundStyle(.green)
            } else {
                ForEach(Array(vanJaar.enumerated()), id: \.offset) { _, b in
                    Label(b.melding, systemImage: b.ernst == .fout ? "xmark.octagon.fill" : "exclamationmark.triangle.fill")
                        .foregroundStyle(b.ernst == .fout ? .red : .orange)
                        .font(.footnote)
                }
            }
        } header: {
            Text("Controle")
        }
    }

    private func export(_ voertuig: Voertuig, _ controle: Controleresultaat) -> some View {
        Section {
            ShareLink(item: Exportbestand.csv(voertuig, register.ritten.filter { $0.datum.jaar == jaar }, jaar)) {
                Label("Rittenlijst (Excel)", systemImage: "tablecells")
            }
            ShareLink(item: Exportbestand.html(voertuig, register.ritten, jaar, controle)) {
                Label("Rapport (om te printen of als pdf)", systemImage: "doc.richtext")
            }
            ShareLink(item: Exportbestand.backup(register.backup())) {
                Label("Back-up van alles", systemImage: "externaldrive")
            }
        } header: {
            Text("Exporteren")
        } footer: {
            Text("Bewaar de back-up buiten de telefoon, bijvoorbeeld in iCloud Drive of per mail. De Belastingdienst kan tot zeven jaar terug vragen naar de rittenregistratie.")
        }
    }
}

/// Exportbestanden in een tijdelijke map, met een leesbare naam, om te delen.
enum Exportbestand {
    private static func schrijf(_ data: Data, _ naam: String) -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(naam)
        try? data.write(to: url, options: .atomic)
        return url
    }

    static func csv(_ v: Voertuig, _ ritten: [Rit], _ jaar: Int) -> URL {
        schrijf(Data(CsvExport.naarCsv(v, ritten).utf8), CsvExport.bestandsnaam(v, jaar))
    }

    static func html(_ v: Voertuig, _ ritten: [Rit], _ jaar: Int, _ controle: Controleresultaat) -> URL {
        schrijf(Data(HtmlRapport.naarHtml(v, ritten, jaar, controle).utf8), HtmlRapport.bestandsnaam(v, jaar))
    }

    static func backup(_ b: Backup) -> URL {
        schrijf((try? BackupOpslag.schrijf(b)) ?? Data(), BackupOpslag.bestandsnaam(b.gemaaktOp))
    }
}
