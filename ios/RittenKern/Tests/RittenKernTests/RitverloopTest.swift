import XCTest
@testable import RittenKern

/// De gevallen die in het echt voorkomen, met wat de app dan moet doen.
final class RitverloopTest: XCTestCase {

    let nu = moment("2026-06-30T08:00:00Z")

    func t(
        aan: Bool = true, loopt: Bool = false, handmatig: Bool = false,
        wegSinds: TimeInterval? = nil, bewogen: TimeInterval? = nil
    ) -> Ritverloop.Toestand {
        Ritverloop.Toestand(
            automatischAan: aan,
            ritLoopt: loopt,
            ritHandmatig: handmatig,
            autoWegSinds: wegSinds.map { nu.addingTimeInterval(-$0) },
            laatsteBeweging: bewogen.map { nu.addingTimeInterval(-$0) }
        )
    }

    func testInstappenStartEenRit() {
        XCTAssertEqual(.begin, Ritverloop.beslis(.autoVerbonden, t(), nu: nu))
    }

    func testMetAutomatischUitGebeurtErNiets() {
        XCTAssertEqual(.niets, Ritverloop.beslis(.autoVerbonden, t(aan: false), nu: nu))
    }

    func testUitstappenRondtNietMeteenAfMaarWachtEven() {
        XCTAssertEqual(.wachtOpAfronden(seconden: Ritverloop.wachttijd), Ritverloop.beslis(.autoWeg, t(loopt: true), nu: nu))
    }

    func testNaDeWachttijdWordtDeRitAfgerond() {
        XCTAssertEqual(.afronden, Ritverloop.beslis(.wachttijdVoorbij, t(loopt: true, wegSinds: 91), nu: nu))
    }

    func testEenVerbindingDieEvenWegvaltMaaktErGeenTweeRittenVan() {
        // Tanken, of het infotainment start opnieuw: binnen de wachttijd terug.
        XCTAssertEqual(.gaDoor, Ritverloop.beslis(.autoVerbonden, t(loopt: true, wegSinds: 30), nu: nu))
    }

    func testGestoptTijdensHetWachtenEnDeVolgendeOchtendWeerInstappenGeeftTweeRitten() {
        XCTAssertEqual(.afrondenEnBegin, Ritverloop.beslis(.autoVerbonden, t(loopt: true, wegSinds: 9 * 3600), nu: nu))
    }

    func testEenTeVroegeWekkerWachtDeRestVanDeTijd() {
        XCTAssertEqual(.wachtOpAfronden(seconden: 60), Ritverloop.beslis(.wachttijdVoorbij, t(loopt: true, wegSinds: 30), nu: nu))
    }

    func testDoorIosGestoptTijdensHetWachtenRondtBijHerstartAlsnogAf() {
        XCTAssertEqual(.afronden, Ritverloop.beslis(.appGestart, t(loopt: true, wegSinds: 600), nu: nu))
    }

    func testHerstartTijdensDeRitLaatDeRitDoorlopen() {
        XCTAssertEqual(.niets, Ritverloop.beslis(.appGestart, t(loopt: true, bewogen: 5), nu: nu))
    }

    func testEenTweedeVerbindingTijdensDeRitDoetNiets() {
        // Bluetooth en CarPlay melden zich allebei, of de automatisering gaat twee keer af.
        XCTAssertEqual(.niets, Ritverloop.beslis(.autoVerbonden, t(loopt: true, bewogen: 60), nu: nu))
    }

    func testEenGemistVerbrekenSluitDeOudeRitAfEnBegintEenNieuwe() {
        // Gisteravond niet afgerond (iOS miste de automatisering), vanochtend weer ingestapt.
        XCTAssertEqual(.afrondenEnBegin, Ritverloop.beslis(.autoVerbonden, t(loopt: true, bewogen: 10 * 3600), nu: nu))
    }

    func testVerbrekenZonderLopendeRitDoetNiets() {
        XCTAssertEqual(.niets, Ritverloop.beslis(.autoWeg, t(), nu: nu))
    }

    func testEenTweedeVerbrekenVerlengtDeWachttijdNiet() {
        XCTAssertEqual(.wachtOpAfronden(seconden: 50), Ritverloop.beslis(.autoWeg, t(loopt: true, wegSinds: 40), nu: nu))
    }

    func testEenHandmatigeRitTrektZichNietsAanVanDeAuto() {
        // Met de hand gestart, bijvoorbeeld in een leenauto: alleen jij rondt hem af.
        XCTAssertEqual(.niets, Ritverloop.beslis(.autoWeg, t(loopt: true, handmatig: true), nu: nu))
        XCTAssertEqual(.niets, Ritverloop.beslis(.autoVerbonden, t(loopt: true, handmatig: true, bewogen: 10 * 3600), nu: nu))
        XCTAssertEqual(.niets, Ritverloop.beslis(.appGestart, t(loopt: true, handmatig: true, wegSinds: 600), nu: nu))
    }

    func testDeAutoUitzettenTijdensEenRitRondtNietsAfZonderSignaal() {
        XCTAssertEqual(.niets, Ritverloop.beslis(.wachttijdVoorbij, t(loopt: true), nu: nu))
    }
}

final class RitmetingTest: XCTestCase {

    let start = moment("2026-06-30T08:00:00Z")

    func p(_ breedte: Double, _ lengte: Double, na seconden: TimeInterval, nauwkeurig: Double = 10) -> Positie {
        Positie(breedte: breedte, lengte: lengte, nauwkeurigheid: nauwkeurig, moment: start.addingTimeInterval(seconden))
    }

    func testDeAfstandTeltDeStukkenOp() {
        var m = Ritmeting(gestartOp: start, handmatig: false)
        m.verwerk(p(52.0, 5.0, na: 0))
        m.verwerk(p(52.01, 5.0, na: 60)) // ruim een kilometer naar het noorden
        m.verwerk(p(52.02, 5.0, na: 120))
        XCTAssertEqual(2.22, m.afstandKm, accuracy: 0.02)
        XCTAssertEqual(3, m.aantalPunten)
    }

    func testOnnauwkeurigeFixesTellenNietMee() {
        var m = Ritmeting(gestartOp: start, handmatig: false)
        m.verwerk(p(52.0, 5.0, na: 0))
        XCTAssertFalse(m.verwerk(p(52.05, 5.0, na: 60, nauwkeurig: 400)))
        XCTAssertEqual(0, m.afstandMeter)
    }

    func testEenOnmogelijkeSprongIsEenUitschieter() {
        var m = Ritmeting(gestartOp: start, handmatig: false)
        m.verwerk(p(52.0, 5.0, na: 0))
        // 11 km in 10 seconden.
        XCTAssertFalse(m.verwerk(p(52.1, 5.0, na: 10)))
        XCTAssertEqual(0, m.afstandMeter)
    }

    func testStilstaanMetGpsRuisIsGeenBeweging() {
        var m = Ritmeting(gestartOp: start, handmatig: false)
        m.verwerk(p(52.0, 5.0, na: 0))
        m.verwerk(p(52.0002, 5.0, na: 600))
        m.verwerk(p(52.0, 5.0002, na: 1200))
        XCTAssertEqual(start, m.laatsteBeweging)
        m.verwerk(p(52.01, 5.0, na: 1300))
        XCTAssertEqual(start.addingTimeInterval(1300), m.laatsteBeweging)
    }

    func testDeMetingOverleeftOpslaan() throws {
        var m = Ritmeting(gestartOp: start, handmatig: true)
        m.startAdres = "Dorpsstraat 12, Bussum"
        m.verwerk(p(52.0, 5.0, na: 0))
        m.verwerk(p(52.01, 5.0, na: 60))
        m.autoWegSinds = start.addingTimeInterval(70)
        let terug = try Tijdstempel.decoder.decode(Ritmeting.self, from: Tijdstempel.encoder.encode(m))
        XCTAssertEqual(m, terug)
    }
}

final class RitopbouwTest: XCTestCase {

    let nu = moment("2026-06-30T09:00:00Z")
    let voorkeuren = Ritopbouw.Voorkeuren(thuisadres: "Dorpsstraat 12, Bussum", werkadres: "Keizersgracht 100, Amsterdam")
    let amsterdam = TimeZone(identifier: "Europe/Amsterdam")!

    func meting(km: Double, punten: Int = 10, startAdres: String = "Dorpsstraat 12, Bussum") -> Ritmeting {
        var m = Ritmeting(gestartOp: moment("2026-06-30T06:40:00Z"), handmatig: false)
        m.afstandMeter = km * 1000
        m.aantalPunten = punten
        m.startAdres = startAdres
        m.laatste = Positie(breedte: 52.37, lengte: 4.88, nauwkeurigheid: 5, moment: moment("2026-06-30T07:25:00Z"))
        return m
    }

    func maak(_ m: Ritmeting, naar: String = "Keizersgracht 100, Amsterdam", ritten: [Rit] = []) -> Ritopbouw.Uitkomst {
        Ritopbouw.maak(m, voertuig: audi, ritten: ritten, eindadres: naar, voorkeuren: voorkeuren, id: "n", nu: nu, tijdzone: amsterdam)
    }

    func testDeRitSluitAanOpDeVorigeEnKrijgtDeGemetenAfstand() {
        guard case let .rit(r, _) = maak(meting(km: 24.6), ritten: [rit("1", begin: 10_000, eind: 10_120)]) else {
            return XCTFail("Geen rit")
        }
        XCTAssertEqual(10_120, r.beginstandKm)
        XCTAssertEqual(10_145, r.eindstandKm)
        XCTAssertEqual(.gps, r.bron)
        XCTAssertEqual(24.6, r.gemetenKm!, accuracy: 0.001)
    }

    func testWoonWerkWordtMeteenBevestigd() {
        guard case let .rit(r, _) = maak(meting(km: 24.6)) else { return XCTFail("Geen rit") }
        XCTAssertEqual(.woonWerk, r.soort)
        XCTAssertTrue(r.bevestigd)
        XCTAssertEqual(10_000, r.beginstandKm) // eerste rit: vanaf de beginstand van de auto
    }

    func testEenOnbekendeBestemmingWachtOpBevestiging() {
        guard case let .rit(r, _) = maak(meting(km: 12), naar: "Industrieweg 7, Zwolle") else { return XCTFail("Geen rit") }
        XCTAssertFalse(r.bevestigd)
    }

    func testDatumEnTijdenInNederlandseTijd() {
        guard case let .rit(r, _) = maak(meting(km: 24.6)) else { return XCTFail("Geen rit") }
        XCTAssertEqual(Dag(2026, 6, 30), r.datum)
        XCTAssertEqual(Kloktijd(8, 40), r.vertrektijd)
        XCTAssertEqual(Kloktijd(9, 25), r.aankomsttijd)
    }

    func testZonderStartadresGeldtHetEindadresVanDeVorigeRit() {
        guard case let .rit(r, _) = maak(meting(km: 5, startAdres: ""), ritten: [rit("1", begin: 10_000, eind: 10_120)]) else {
            return XCTFail("Geen rit")
        }
        XCTAssertEqual("Klantlaan 2, Utrecht", r.beginadres)
    }

    func testEenKorteMetingIsGeenRit() {
        XCTAssertEqual(.teKort(geenPunten: false), maak(meting(km: 0.1)))
        XCTAssertEqual(.teKort(geenPunten: true), maak(meting(km: 0, punten: 0)))
    }

    func testEenKorteMaarEchteRitTeltMinstensEenKilometer() {
        guard case let .rit(r, _) = maak(meting(km: 0.4)) else { return XCTFail("Geen rit") }
        XCTAssertEqual(1, r.afstandKm)
    }
}
