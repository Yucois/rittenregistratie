import XCTest
@testable import RittenKern

final class RitclassificatieTest: XCTestCase {

    let thuis = "Dorpsstraat 12, Bussum"
    let werk = "Keizersgracht 100, Amsterdam"
    let klant = "Industrieweg 7, Zwolle"

    func eerdereRit(_ eindadres: String, _ soort: Ritsoort, _ doel: String, dag: Int, stand: Int) -> Rit {
        rit("h\(dag)", datum: Dag(2026, 3, dag), begin: stand, eind: stand + 50, soort: soort, eindadres: eindadres, doel: doel)
    }

    func testTussenThuisEnKantoorIsHetWoonWerkEnIsDeAppErZekerVan() {
        let v = Ritclassificatie.voorstel(thuis, werk, thuis: thuis, werk: werk)
        XCTAssertEqual(.woonWerk, v.soort)
        XCTAssertTrue(v.zelfAfhandelen)
    }

    func testEenBestemmingDieTweeKeerZakelijkWasWordtZelfAfgehandeld() {
        let geschiedenis = [
            eerdereRit(klant, .zakelijk, "Bespreking Van Dijk", dag: 3, stand: 10_000),
            eerdereRit(klant, .zakelijk, "Bespreking Van Dijk", dag: 10, stand: 10_100),
        ]
        let v = Ritclassificatie.voorstel(thuis, klant, thuis: thuis, werk: werk, geschiedenis: geschiedenis)
        XCTAssertEqual(.zakelijk, v.soort)
        XCTAssertEqual("Bespreking Van Dijk", v.doel)
        XCTAssertTrue(v.zelfAfhandelen)
    }

    func testNaEenEnkeleEerdereRitIsHetEenVoorstelGeenZekerheid() {
        let geschiedenis = [eerdereRit(klant, .zakelijk, "Kennismaking", dag: 3, stand: 10_000)]
        let v = Ritclassificatie.voorstel(thuis, klant, thuis: thuis, werk: werk, geschiedenis: geschiedenis)
        XCTAssertEqual(.middel, v.zekerheid)
        XCTAssertFalse(v.zelfAfhandelen)
    }

    func testHetMeestRecenteDoelWordtOvergenomen() {
        let geschiedenis = [
            eerdereRit(klant, .zakelijk, "Oude afspraak", dag: 3, stand: 10_000),
            eerdereRit(klant, .zakelijk, "Kwartaaloverleg", dag: 18, stand: 10_200),
        ]
        XCTAssertEqual("Kwartaaloverleg", Ritclassificatie.voorstel(thuis, klant, thuis: thuis, werk: werk, geschiedenis: geschiedenis).doel)
    }

    func testEenBestemmingDieEerderVerschillendIsVastgelegdVraagtOmEenKeuze() {
        let geschiedenis = [
            eerdereRit(klant, .zakelijk, "Bespreking", dag: 3, stand: 10_000),
            eerdereRit(klant, .prive, "", dag: 10, stand: 10_100),
            eerdereRit(klant, .zakelijk, "Bespreking", dag: 12, stand: 10_200),
        ]
        let v = Ritclassificatie.voorstel(thuis, klant, thuis: thuis, werk: werk, geschiedenis: geschiedenis)
        XCTAssertEqual(.zakelijk, v.soort)
        XCTAssertEqual(.laag, v.zekerheid)
        XCTAssertFalse(v.zelfAfhandelen)
    }

    func testOnbevestigdeRittenTellenNietMeeAlsLeermateriaal() {
        let geschiedenis = [
            eerdereRit(klant, .zakelijk, "Bespreking", dag: 3, stand: 10_000).met { $0.bevestigd = false },
            eerdereRit(klant, .zakelijk, "Bespreking", dag: 10, stand: 10_100).met { $0.bevestigd = false },
        ]
        let v = Ritclassificatie.voorstel(thuis, klant, thuis: thuis, werk: werk, geschiedenis: geschiedenis, standaard: .prive)
        XCTAssertEqual(.prive, v.soort)
        XCTAssertEqual(.laag, v.zekerheid)
    }

    func testEenOnbekendeBestemmingValtTerugOpDeStandaardkeuze() {
        let v = Ritclassificatie.voorstel(thuis, "Strandweg 1, Zandvoort", thuis: thuis, werk: werk, standaard: .prive)
        XCTAssertEqual(.prive, v.soort)
        XCTAssertFalse(v.zelfAfhandelen)
    }

    func testKleineVerschillenInSchrijfwijzeStaanEenMatchNietInDeWeg() {
        XCTAssertTrue(Ritclassificatie.komtOvereen("Dorpsstraat 12", "dorpsstraat 12, Bussum"))
        XCTAssertFalse(Ritclassificatie.komtOvereen("Dorpsstraat 12", "Dorpsweg 12"))
        XCTAssertFalse(Ritclassificatie.komtOvereen("", "Dorpsstraat 12"))
    }
}
