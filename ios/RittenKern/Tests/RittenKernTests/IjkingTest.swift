import XCTest
@testable import RittenKern

final class IjkingTest: XCTestCase {

    let vandaag = Dag(2026, 6, 30)
    let nu = moment("2026-06-30T12:00:00Z")

    func correctie(_ laatste: Int, _ werkelijk: Int) -> (Rit, Int)? {
        if case let .correctie(rit, verschil) = Ijking.ijk(audi, laatsteStand: laatste, werkelijkeStand: werkelijk, datum: vandaag, ritId: "c1", nu: nu) {
            return (rit, verschil)
        }
        return nil
    }

    func testEenGelijkeStandLevertGeenCorrectieOp() {
        XCTAssertEqual(.gelijk, Ijking.ijk(audi, laatsteStand: 12_000, werkelijkeStand: 12_000, datum: vandaag, ritId: "c1", nu: nu))
    }

    func testHetVerschilKomtErAlsAparteCorrectieritIn() throws {
        let (rit, verschil) = try XCTUnwrap(correctie(12_000, 12_085))
        XCTAssertEqual(85, verschil)
        XCTAssertEqual(12_000, rit.beginstandKm)
        XCTAssertEqual(12_085, rit.eindstandKm)
        XCTAssertEqual(.prive, rit.soort)
        XCTAssertEqual(.correctie, rit.bron)
        XCTAssertFalse(rit.bevestigd)
    }

    func testEenCorrectieritMaaktDeReeksWeerSluitend() throws {
        let (c, _) = try XCTUnwrap(correctie(10_500, 10_560))
        let resultaat = Rittencontrole.controleer(audi, [rit("1", begin: 10_000, eind: 10_500), c], vandaag)
        XCTAssertTrue(resultaat.fouten.isEmpty, resultaat.fouten.map(\.melding).joined(separator: ", "))
        XCTAssertEqual(0, resultaat.jaar(2026)!.nietVerantwoordeKm)
        // De niet-vastgelegde kilometers tellen nu als privé, zoals een controle ze ook telt.
        XCTAssertEqual(60, resultaat.jaar(2026)!.priveKm)
    }

    func testEenCorrectieritZonderToelichtingIsEenFout() throws {
        let (c, _) = try XCTUnwrap(correctie(10_500, 10_560))
        XCTAssertTrue(Rittencontrole.controleerRit(c.met { $0.opmerking = nil }, audi).contains { $0.code == "correctie.zondertoelichting" })
        // Met toelichting blijft alleen de bevestiging over.
        XCTAssertEqual(["rit.onbevestigd"], Rittencontrole.controleerRit(c, audi).map(\.code))
    }

    func testEenLagereTellerstandDanDeRegistratieKanNiet() {
        guard case .onmogelijk = Ijking.ijk(audi, laatsteStand: 12_000, werkelijkeStand: 11_900, datum: vandaag, ritId: "c1", nu: nu) else {
            return XCTFail("Een lagere stand had geweigerd moeten worden")
        }
    }

    func testIjkenIsOverbodigZodraDeStandenUitDeAutoKomen() {
        XCTAssertFalse(Ijking.ijkenNodig(standenKomenUitDeAuto: true, laatsteIjking: nil, kmSindsIjking: 5_000, vandaag: vandaag))
    }

    func testZonderEerdereIjkingIsHonderdKilometerGenoegAanleiding() {
        XCTAssertFalse(Ijking.ijkenNodig(standenKomenUitDeAuto: false, laatsteIjking: nil, kmSindsIjking: 40, vandaag: vandaag))
        XCTAssertTrue(Ijking.ijkenNodig(standenKomenUitDeAuto: false, laatsteIjking: nil, kmSindsIjking: 120, vandaag: vandaag))
    }

    func testNaEenMaandOfDuizendKilometerIsHetWeerTijd() {
        let vorige = Dag(2026, 6, 20)
        XCTAssertFalse(Ijking.ijkenNodig(standenKomenUitDeAuto: false, laatsteIjking: vorige, kmSindsIjking: 300, vandaag: vandaag))
        XCTAssertTrue(Ijking.ijkenNodig(standenKomenUitDeAuto: false, laatsteIjking: vorige, kmSindsIjking: 1_200, vandaag: vandaag))
        XCTAssertTrue(Ijking.ijkenNodig(standenKomenUitDeAuto: false, laatsteIjking: Dag(2026, 5, 1), kmSindsIjking: 300, vandaag: vandaag))
    }
}
