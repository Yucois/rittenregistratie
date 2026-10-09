import RittenKern
import SwiftUI

struct RittenScherm: View {
    @EnvironmentObject private var register: Register
    @EnvironmentObject private var regelaar: Ritregelaar
    @EnvironmentObject private var locatie: Locatie
    @ObservedObject private var navigatie = Navigatie.shared

    @State private var bewerken: Rit?
    @State private var nieuw = false
    @State private var ijken = false
    @State private var meldingenUit = false

    private var teBevestigen: [Rit] {
        register.eigenRitten.filter { !$0.bevestigd }.sorted { $0.beginstandKm > $1.beginstandKm }
    }

    private struct Maand: Identifiable {
        let naam: String
        var ritten: [Rit]
        var id: String { naam }
    }

    private struct Probleem: Identifiable {
        let titel: String
        let tekst: String
        let kleur: Color
        var id: String { titel }
    }

    private var perMaand: [Maand] {
        let bevestigd = register.eigenRitten.filter(\.bevestigd).sorted {
            $0.beginstandKm != $1.beginstandKm ? $0.beginstandKm > $1.beginstandKm : $0.datum > $1.datum
        }
        var groepen: [Maand] = []
        for rit in bevestigd {
            let naam = Opmaak.maand.string(from: rit.datum.begin()).capitalizedFirst
            if groepen.last?.naam == naam {
                groepen[groepen.count - 1].ritten.append(rit)
            } else {
                groepen.append(Maand(naam: naam, ritten: [rit]))
            }
        }
        return groepen
    }

    var body: some View {
        List {
            meldingen
            lopendeRit

            if !teBevestigen.isEmpty {
                Section {
                    ForEach(teBevestigen) { rit in
                        TeBevestigenRij(rit: rit) { soort in
                            withAnimation { register.bevestig(rit.id, soort: soort) }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { bewerken = rit }
                    }
                } header: {
                    Text("Te bevestigen")
                } footer: {
                    Text("Tik op het juiste karakter om de rit te bevestigen, of op de rit om hem aan te passen.")
                }
            }

            ForEach(perMaand) { maand in
                Section(maand.naam) {
                    ForEach(maand.ritten) { rit in
                        Button { bewerken = rit } label: { RitRij(rit: rit) }
                            .buttonStyle(.plain)
                    }
                }
            }

            if register.eigenRitten.isEmpty && regelaar.meting == nil {
                Section {
                    Text("Nog geen ritten. Zodra je in de auto stapt en de automatisering in Opdrachten is ingesteld, begint de app vanzelf. Je kunt ook hierboven een rit met de hand starten.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Ritten")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { nieuw = true } label: { Label("Rit toevoegen", systemImage: "plus") }
                    .disabled(register.voertuig == nil)
            }
        }
        .sheet(item: $bewerken) { rit in
            NavigationStack { RitBewerkenScherm(rit: rit, isNieuw: false) }
        }
        .sheet(isPresented: $nieuw) {
            NavigationStack { RitBewerkenScherm(rit: nieuweRit(), isNieuw: true) }
        }
        .sheet(isPresented: $ijken) {
            NavigationStack { IjkScherm() }
        }
        .task {
            meldingenUit = !(await Meldingen.toegestaan())
        }
        .onChange(of: navigatie.openRitId) { _, id in
            if let id, let rit = register.rit(id) { bewerken = rit }
            navigatie.openRitId = nil
        }
        .onAppear {
            if let id = navigatie.openRitId, let rit = register.rit(id) { bewerken = rit }
            navigatie.openRitId = nil
        }
    }

    @ViewBuilder
    private var meldingen: some View {
        let problemen = problemenLijst
        if !problemen.isEmpty {
            Section {
                ForEach(problemen) { p in
                    Waarschuwingskaart(titel: p.titel, tekst: p.tekst, kleur: p.kleur)
                }
                if register.ijkenNodig {
                    Button("Kilometerstand nu invullen") { ijken = true }
                }
            }
        }
    }

    private var problemenLijst: [Probleem] {
        var p: [Probleem] = []
        if let fout = register.fout { p.append(Probleem(titel: "Opslag", tekst: fout, kleur: .red)) }
        if register.voertuig == nil {
            p.append(Probleem(titel: "Nog niet ingesteld", tekst: "Vul onder Instellingen je auto in en volg de stappen bij Aan de slag.", kleur: .orange))
        }
        if locatie.geweigerd {
            p.append(Probleem(titel: "Geen locatie", tekst: "Zonder locatie kan de app geen ritten meten. Zet de toegang aan bij Instellingen → Ritten → Locatie.", kleur: .red))
        } else if register.voertuig != nil && !locatie.magAltijd {
            p.append(Probleem(titel: "Locatie niet op Altijd", tekst: "Met de telefoon op slot meet de app dan niet. Zet de toegang op Altijd.", kleur: .orange))
        }
        if meldingenUit {
            p.append(Probleem(titel: "Meldingen staan uit", tekst: "Je krijgt na een rit geen vraag om hem te bevestigen.", kleur: .orange))
        }
        if let dagen = Ondertekening.dagenOver, dagen <= 2 {
            p.append(Probleem(
                titel: dagen < 1 ? "App verloopt vandaag" : "App verloopt over \(dagen) dag\(dagen == 1 ? "" : "en")",
                tekst: "Open SideStore en vernieuw de app. Een verlopen app legt geen ritten vast.",
                kleur: .red
            ))
        }
        if register.ijkenNodig {
            p.append(Probleem(
                titel: "Tijd om de teller na te lopen",
                tekst: "Sinds de vorige keer is er \(register.kmSindsIjking) km geregistreerd. Vul de echte kilometerstand in, zodat de reeks klopt.",
                kleur: .blue
            ))
        }
        return p
    }

    @ViewBuilder
    private var lopendeRit: some View {
        Section {
            if let m = regelaar.meting {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Image(systemName: "location.fill").foregroundStyle(.green)
                        Text(m.handmatig ? "Rit loopt (met de hand gestart)" : "Rit loopt").font(.headline)
                    }
                    Text("Sinds \(Opmaak.tijd.string(from: m.gestartOp)) · \(Opmaak.km(m.afstandKm))")
                        .foregroundStyle(.secondary)
                    if !m.startAdres.isEmpty {
                        Text("Vanaf \(m.startAdres)").font(.footnote).foregroundStyle(.secondary)
                    }
                    if let weg = m.autoWegSinds {
                        Text("Auto weg om \(Opmaak.tijd.string(from: weg)); de rit wordt zo afgerond.")
                            .font(.footnote).foregroundStyle(.orange)
                    }
                }
                Button {
                    Task { await regelaar.rondHandmatigAf() }
                } label: {
                    Label("Rit nu afronden", systemImage: "flag.checkered")
                }
                Button(role: .destructive) {
                    regelaar.verwerp()
                } label: {
                    Label("Weggooien (ik reed niet zelf)", systemImage: "trash")
                }
            } else {
                Button {
                    Task { await regelaar.startHandmatig() }
                } label: {
                    Label("Rit met de hand starten", systemImage: "play.fill")
                }
                .disabled(register.voertuig == nil)
            }
        } footer: {
            if regelaar.meting == nil, register.voertuig != nil {
                Text("Volgende rit begint bij \(register.volgendeBeginstand) km.")
            }
        }
    }

    private func nieuweRit() -> Rit {
        let stand = register.volgendeBeginstand
        let vorige = register.eigenRitten.opStand().last
        return Rit(
            id: UUID().uuidString,
            voertuigId: register.voertuig?.id ?? "",
            datum: .vandaag(),
            beginstandKm: stand,
            eindstandKm: stand,
            beginadres: vorige?.eindadres ?? register.instellingen.thuisadres,
            eindadres: "",
            soort: register.instellingen.standaardSoort,
            bron: .handmatig,
            aangemaaktOp: Date(),
            gewijzigdOp: Date()
        )
    }
}

struct RitRij: View {
    let rit: Rit

    private var onderregel: String {
        var delen = [Opmaak.dag(rit.datum)]
        if let vertrek = rit.vertrektijd { delen.append(vertrek.description) }
        delen.append(rit.soort.label)
        if !rit.doel.isEmpty { delen.append(rit.doel) }
        return delen.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: rit.bron == .correctie ? "gauge.with.needle" : rit.soort.icoon)
                .foregroundStyle(rit.soort.kleur)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                if rit.bron == .correctie {
                    Text("Correctie na ijken").font(.subheadline.weight(.medium))
                } else {
                    Text(rit.eindadres.isEmpty ? "Onbekende bestemming" : rit.eindadres)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                }
                Text(onderregel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text("\(rit.afstandKm) km").font(.subheadline.monospacedDigit())
        }
    }
}

struct TeBevestigenRij: View {
    let rit: Rit
    let kies: (Ritsoort) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            RitRij(rit: rit)
            if rit.bron != .correctie, !rit.beginadres.isEmpty {
                Text("Van \(rit.beginadres)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            HStack(spacing: 8) {
                ForEach(Ritsoort.allCases, id: \.self) { soort in
                    Button { kies(soort) } label: {
                        Text(soort.label).font(.footnote.weight(.semibold)).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(soort == rit.soort ? soort.kleur : .gray)
                }
            }
        }
        .padding(.vertical, 4)
    }
}
