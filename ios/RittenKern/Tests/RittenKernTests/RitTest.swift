import XCTest
@testable import RittenKern

final class RitTest: XCTestCase {

    func testAfstandIsHetVerschilTussenDeTellerstanden() {
        XCTAssertEqual(42, rit("a", begin: 10_000, eind: 10_042).afstandKm)
    }

    func testWoonWerkTeltZakelijkVoorDeLoonheffingEnPriveVoorDeBtw() {
        let woonWerk = rit("a", begin: 10_000, eind: 10_030, soort: .woonWerk)
        XCTAssertEqual(0, woonWerk.priveKmLoonheffing)
        XCTAssertEqual(30, woonWerk.priveKmBtw)
    }

    func testEenPriveritTeltBeideKantenOpAlsPrive() {
        let prive = rit("a", begin: 10_000, eind: 10_030, soort: .prive)
        XCTAssertEqual(30, prive.priveKmLoonheffing)
        XCTAssertEqual(30, prive.priveKmBtw)
        XCTAssertEqual(0, prive.zakelijkeKmLoonheffing)
    }

    func testOmrijkilometersBinnenEenZakelijkeRitTellenAlsPrive() {
        let zakelijk = rit("a", begin: 10_000, eind: 10_100, omrij: 12, route: "Via Breukelen, kind opgehaald")
        XCTAssertEqual(12, zakelijk.priveKmLoonheffing)
        XCTAssertEqual(88, zakelijk.zakelijkeKmLoonheffing)
    }

    func testMeetverschilVergelijktTellerstandenMetDeGpsMeting() {
        XCTAssertEqual(1.5, rit("a", begin: 10_000, eind: 10_100, gemeten: 98.5).meetverschilKm!, accuracy: 0.001)
    }

    func testEenVergrendeldeRitIsAlsZodanigHerkenbaar() {
        let open = rit("a", begin: 10_000, eind: 10_010)
        XCTAssertFalse(open.vergrendeld)
        XCTAssertTrue(open.met { $0.vergrendeldOp = Date() }.vergrendeld)
    }
}
