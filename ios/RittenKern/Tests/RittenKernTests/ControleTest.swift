import XCTest
@testable import RittenKern

final class ControleTest: XCTestCase {

    let peildatum = Dag(2026, 6, 30)

    func testEenAansluitendeReeksIsSluitend() {
        let resultaat = Rittencontrole.controleer(audi, [
            rit("1", begin: 10_000, eind: 10_120),
            rit("2", begin: 10_120, eind: 10_200, soort: .woonWerk, doel: ""),
        ], peildatum)
        XCTAssertTrue(resultaat.sluitend, resultaat.bevindingen.map(\.melding).joined(separator: ", "))
        XCTAssertEqual(10_200, resultaat.laatsteStand)
    }

    func testEenGatTussenTweeRittenIsEenFoutEnTeltAlsPrive() {
        let resultaat = Rittencontrole.controleer(audi, [
            rit("1", begin: 10_000, eind: 10_120),
            rit("2", begin: 10_150, eind: 10_200),
        ], peildatum)
        XCTAssertFalse(resultaat.sluitend)
        XCTAssertEqual(["hiaat"], resultaat.fouten.map(\.code))
        let jaar = resultaat.jaar(2026)!
        XCTAssertEqual(30, jaar.nietVerantwoordeKm)
        XCTAssertEqual(0, jaar.priveKmLoonheffing)
        XCTAssertEqual(30, jaar.priveKmLoonheffingWorstCase)
    }

    func testEenGatTussenDeBeginstandVanDeAutoEnDeEersteRitValtOp() {
        let resultaat = Rittencontrole.controleer(audi, [rit("1", begin: 10_040, eind: 10_100)], peildatum)
        XCTAssertEqual(["hiaat.start"], resultaat.fouten.map(\.code))
        XCTAssertEqual(40, resultaat.jaar(2026)!.nietVerantwoordeKm)
    }

    func testOverlappendeTellerstandenWordenGemeld() {
        let resultaat = Rittencontrole.controleer(audi, [
            rit("1", begin: 10_000, eind: 10_120),
            rit("2", begin: 10_100, eind: 10_200),
        ], peildatum)
        XCTAssertEqual(["overlap"], resultaat.fouten.map(\.code))
    }

    func testJaartotalenScheidenLoonheffingEnBtw() {
        let resultaat = Rittencontrole.controleer(audi, [
            rit("1", begin: 10_000, eind: 10_100),
            rit("2", begin: 10_100, eind: 10_140, soort: .woonWerk),
            rit("3", begin: 10_140, eind: 10_200, soort: .prive, doel: ""),
            rit("4", begin: 10_200, eind: 10_300, omrij: 10, route: "Via Zeist"),
        ], peildatum)
        let jaar = resultaat.jaar(2026)!
        XCTAssertEqual(300, jaar.totaalKm)
        XCTAssertEqual(200, jaar.zakelijkKm)
        XCTAssertEqual(40, jaar.woonWerkKm)
        XCTAssertEqual(60, jaar.priveKm)
        XCTAssertEqual(10, jaar.priveOmrijKm)
        XCTAssertEqual(70, jaar.priveKmLoonheffing)
        XCTAssertEqual(110, jaar.priveKmBtw)
        XCTAssertEqual(430, jaar.restantTot500)
        XCTAssertFalse(jaar.grensOverschreden)
        XCTAssertEqual(0.3667, jaar.btwPriveAandeel, accuracy: 0.001)
    }

    func testBovenDe500PrivekilometersVolgtEenFout() {
        let resultaat = Rittencontrole.controleer(audi, [rit("1", begin: 10_000, eind: 10_600, soort: .prive, doel: "")], peildatum)
        XCTAssertTrue(resultaat.fouten.contains { $0.code == "grens.overschreden" })
        XCTAssertEqual(-100, resultaat.jaar(2026)!.restantTot500)
    }

    func testDePrognoseRekentHetJaarUitOpBasisVanHetVerstrekenDeel() {
        let jaar = Jaaroverzicht(
            jaar: 2026, aantalRitten: 1, totaalKm: 200, zakelijkKm: 0, woonWerkKm: 0,
            priveKm: 200, priveOmrijKm: 0, nietVerantwoordeKm: 0
        )
        // 1 juli: 182 van de 365 dagen verstreken, dus ruim het dubbele.
        XCTAssertEqual(401, jaar.prognosePriveKm(Dag(2026, 7, 1)))
        XCTAssertEqual(200, jaar.prognosePriveKm(Dag(2027, 2, 1)))
    }

    func testRittenVanEenAnderVoertuigTellenNietMee() {
        let vreemd = rit("x", begin: 90_000, eind: 90_100).met { $0.voertuigId = "auto-2" }
        let resultaat = Rittencontrole.controleer(audi, [rit("1", begin: 10_000, eind: 10_100), vreemd], peildatum)
        XCTAssertTrue(resultaat.sluitend)
        XCTAssertEqual(100, resultaat.jaar(2026)!.totaalKm)
    }

    func testGatenWordenPerJaarToegerekendAanDeRitErna() {
        let resultaat = Rittencontrole.controleer(audi, [
            rit("1", datum: Dag(2026, 12, 30), begin: 10_000, eind: 10_100),
            rit("2", datum: Dag(2027, 1, 3), begin: 10_180, eind: 10_200),
        ], Dag(2027, 2, 1))
        XCTAssertEqual(0, resultaat.jaar(2026)!.nietVerantwoordeKm)
        XCTAssertEqual(80, resultaat.jaar(2027)!.nietVerantwoordeKm)
    }
}
