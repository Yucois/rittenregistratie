import Foundation

/// Het ijkmoment: de registratie naast de werkelijke kilometerstand leggen.
///
/// Zolang de standen uit een gps-meting komen loopt er onvermijdelijk verschil
/// op — een paar procent per rit is normaal. Dat verschil wegwerken door de
/// standen stilletjes bij te stellen zou de administratie onbetrouwbaar maken.
/// In plaats daarvan komt het verschil er als aparte correctierit in: zichtbaar,
/// verklaarbaar, en de reeks blijft sluitend.
///
/// De correctie geldt als privé. Dat is streng, en met opzet: kilometers die
/// niet zijn vastgelegd, merkt de Belastingdienst ook als privé aan. Wie ze
/// zakelijk wil verantwoorden, moet erbij zetten waardoor ze niet geregistreerd
/// zijn — en dan kan de soort met de hand worden gewijzigd.
public enum Ijking {

    public enum Uitkomst: Equatable, Sendable {
        /// De registratie loopt gelijk met de teller; er valt niets te corrigeren.
        case gelijk
        /// Er zijn kilometers niet vastgelegd; deze rit dekt ze af.
        case correctie(rit: Rit, verschilKm: Int)
        /// De opgegeven stand kan niet kloppen.
        case onmogelijk(melding: String)
    }

    public static func ijk(
        _ voertuig: Voertuig,
        laatsteStand: Int,
        werkelijkeStand: Int,
        datum: Dag,
        ritId: String,
        nu: Date = Date()
    ) -> Uitkomst {
        let verschil = werkelijkeStand - laatsteStand
        if verschil == 0 { return .gelijk }
        if verschil < 0 {
            return .onmogelijk(
                melding: "De teller staat op \(werkelijkeStand) km, lager dan de laatst geregistreerde stand " +
                    "(\(laatsteStand) km). Controleer of je de stand goed hebt overgenomen."
            )
        }
        return .correctie(
            rit: Rit(
                id: ritId,
                voertuigId: voertuig.id,
                datum: datum,
                beginstandKm: laatsteStand,
                eindstandKm: werkelijkeStand,
                beginadres: "",
                eindadres: "",
                soort: .prive,
                doel: "",
                opmerking: "Sluitpost na ijking op de werkelijke kilometerstand van \(werkelijkeStand) km.",
                bron: .correctie,
                bevestigd: false,
                aangemaaktOp: nu,
                gewijzigdOp: nu
            ),
            verschilKm: verschil
        )
    }

    /// Boven deze afwijking of dit aantal dagen is ijken op zijn plaats.
    private static let maxKmTussenIjkingen = 1_000
    private static let maxDagenTussenIjkingen = 30

    /// Of het tijd is om de teller na te lopen. Komen de standen uit de auto zelf,
    /// dan is ijken overbodig en geeft deze functie altijd false.
    public static func ijkenNodig(
        standenKomenUitDeAuto: Bool,
        laatsteIjking: Dag?,
        kmSindsIjking: Int,
        vandaag: Dag
    ) -> Bool {
        if standenKomenUitDeAuto { return false }
        if kmSindsIjking <= 0 { return false }
        guard let laatsteIjking else { return kmSindsIjking >= 100 }
        let dagen = laatsteIjking.dagen(tot: vandaag)
        return kmSindsIjking >= maxKmTussenIjkingen || dagen >= maxDagenTussenIjkingen
    }
}
