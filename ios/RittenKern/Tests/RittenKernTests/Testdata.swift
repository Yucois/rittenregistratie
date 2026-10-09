import Foundation
@testable import RittenKern

let audi = Voertuig(
    id: "auto-1",
    merk: "Audi",
    type: "RS e-tron GT",
    kenteken: "X-123-YZ",
    terBeschikkingVanaf: Dag(2026, 1, 1),
    beginstandKm: 10_000
)

func moment(_ iso: String) -> Date { Tijdstempel.lees(iso)! }

func rit(
    _ id: String,
    datum: Dag = Dag(2026, 3, 2),
    begin: Int,
    eind: Int,
    soort: Ritsoort = .zakelijk,
    beginadres: String = "Kantoorweg 1, Amsterdam",
    eindadres: String = "Klantlaan 2, Utrecht",
    doel: String = "Bespreking",
    omrij: Int = 0,
    route: String? = nil,
    gemeten: Double? = nil
) -> Rit {
    Rit(
        id: id,
        voertuigId: audi.id,
        datum: datum,
        beginstandKm: begin,
        eindstandKm: eind,
        beginadres: beginadres,
        eindadres: eindadres,
        soort: soort,
        doel: doel,
        afwijkendeRoute: route,
        priveOmrijkilometers: omrij,
        gemetenKm: gemeten,
        aangemaaktOp: moment("2026-03-02T08:00:00Z"),
        gewijzigdOp: moment("2026-03-02T08:00:00Z")
    )
}

extension Rit {
    func met(_ aanpassen: (inout Rit) -> Void) -> Rit {
        var kopie = self
        aanpassen(&kopie)
        return kopie
    }
}
