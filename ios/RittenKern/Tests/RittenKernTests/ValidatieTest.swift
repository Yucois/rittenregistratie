import XCTest
@testable import RittenKern

final class ValidatieTest: XCTestCase {

    func codes(_ rit: Rit, _ voertuig: Voertuig? = audi) -> [String] {
        Rittencontrole.controleerRit(rit, voertuig).map(\.code)
    }

    func testEenVolledigeRitLevertGeenBevindingenOp() {
        XCTAssertEqual([], codes(rit("1", begin: 10_000, eind: 10_100)))
    }

    func testDeEindstandMoetHogerZijnDanDeBeginstand() {
        XCTAssertTrue(codes(rit("1", begin: 10_100, eind: 10_100)).contains("stand.oplopend"))
    }

    func testAdressenZijnVerplicht() {
        let kaal = rit("1", begin: 10_000, eind: 10_010, beginadres: " ", eindadres: "")
        XCTAssertEqual(["adres.begin", "adres.eind"], codes(kaal))
    }

    func testOmrijkilometersVragenOmEenRoutebeschrijving() {
        XCTAssertTrue(codes(rit("1", begin: 10_000, eind: 10_100, omrij: 15)).contains("route.afwijkend"))
        XCTAssertFalse(codes(rit("1", begin: 10_000, eind: 10_100, omrij: 15, route: "Via Vianen")).contains("route.afwijkend"))
    }

    func testOmrijkilometersPassenBinnenDeRit() {
        XCTAssertTrue(codes(rit("1", begin: 10_000, eind: 10_020, omrij: 30, route: "Omweg")).contains("omrij.tegroot"))
    }

    func testEenZakelijkeRitZonderDoelGeeftEenWaarschuwing() {
        XCTAssertEqual(["doel.leeg"], codes(rit("1", begin: 10_000, eind: 10_010, doel: "")))
    }

    func testEenRitBuitenDeTerbeschikkingstellingIsEenFout() {
        let vorigJaar = rit("1", datum: Dag(2025, 12, 30), begin: 10_000, eind: 10_010)
        XCTAssertTrue(codes(vorigJaar).contains("datum.buitenperiode"))
    }

    func testEenGrootVerschilMetDeGpsMetingGeeftEenWaarschuwing() {
        XCTAssertTrue(codes(rit("1", begin: 10_000, eind: 10_100, gemeten: 60.0)).contains("meting.afwijking"))
        XCTAssertFalse(codes(rit("1", begin: 10_000, eind: 10_100, gemeten: 98.0)).contains("meting.afwijking"))
    }

    func testZonderVoertuigWordtDePeriodeNietGecontroleerd() {
        let vorigJaar = rit("1", datum: Dag(2025, 12, 30), begin: 10_000, eind: 10_010)
        XCTAssertEqual([], codes(vorigJaar, nil))
    }

    func testEenOnbevestigdeRitLevertEenWaarschuwingOpGeenFout() {
        let automatisch = rit("1", begin: 10_000, eind: 10_100).met { $0.bevestigd = false }
        let bevindingen = Rittencontrole.controleerRit(automatisch, audi)
        XCTAssertEqual(["rit.onbevestigd"], bevindingen.map(\.code))
        XCTAssertTrue(bevindingen.allSatisfy { $0.ernst == .waarschuwing })
    }

    func testOnbevestigdeRittenWordenGeteldMaarTellenWelMeeInDeKilometers() {
        let resultaat = Rittencontrole.controleer(audi, [
            rit("1", begin: 10_000, eind: 10_100),
            rit("2", begin: 10_100, eind: 10_150).met { $0.bevestigd = false },
        ], Dag(2026, 6, 30))
        let jaar = resultaat.jaar(2026)!
        XCTAssertEqual(1, jaar.onbevestigdeRitten)
        XCTAssertEqual(150, jaar.totaalKm)
        // Een rit die nog bevestigd moet worden mag de reeks niet onsluitend maken.
        XCTAssertTrue(resultaat.sluitend)
    }
}
