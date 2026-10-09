import XCTest
@testable import RittenKern

final class ExportTest: XCTestCase {

    let ritten = [
        rit("1", begin: 10_000, eind: 10_100),
        rit("2", begin: 10_100, eind: 10_160, soort: .prive, doel: ""),
    ]

    private func regels(_ csv: String) -> [String] {
        csv.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: "\r\n")
    }

    func testCsvHeeftEenKopMetAlleVerplichteVelden() {
        let csv = CsvExport.naarCsv(audi, ritten)
        XCTAssertTrue(csv.hasPrefix("\u{FEFF}"))
        let kop = regels(String(csv.dropFirst()))[0]
        XCTAssertTrue(kop.hasPrefix("\"Datum\";\"Kenteken\";\"Beginstand (km)\""))
        XCTAssertTrue(kop.contains("\"Gereden route (indien afwijkend)\""))
        XCTAssertEqual(CsvExport.kolommen.count, kop.components(separatedBy: ";").count)
    }

    func testCsvBevatEenRegelPerRitInTellerstandvolgorde() {
        let r = regels(CsvExport.naarCsv(audi, ritten.reversed()))
        XCTAssertEqual(3, r.count)
        XCTAssertTrue(r[1].contains("\"10000\";\"10100\";\"100\""))
        XCTAssertTrue(r[2].contains("\"10100\";\"10160\";\"60\""))
        XCTAssertTrue(r[1].hasPrefix("\"02-03-2026\""))
    }

    func testAanhalingstekensEnRegeleindesBrekenHetCsvNiet() {
        XCTAssertEqual("\"Klant \"\"De Vries\"\"\"", CsvExport.veld("Klant \"De Vries\""))
        XCTAssertEqual("\"regel een regel twee\"", CsvExport.veld("regel een\r\nregel twee"))
        XCTAssertEqual("\"a b\"", CsvExport.veld("a\nb"))
    }

    func testBestandsnaamBevatKentekenEnJaar() {
        XCTAssertEqual("rittenregistratie-X123YZ-2026.csv", CsvExport.bestandsnaam(audi, 2026))
    }

    func testHtmlRapportToontSamenvattingEnRitten() {
        let controle = Rittencontrole.controleer(audi, ritten, Dag(2026, 6, 30))
        let html = HtmlRapport.naarHtml(audi, ritten, 2026, controle, opgemaaktOp: Dag(2026, 6, 30))
        XCTAssertTrue(html.contains("Audi RS e-tron GT (X-123-YZ)"))
        XCTAssertTrue(html.contains("440 km over")) // 500 - 60 privékilometers
        XCTAssertTrue(html.contains("Klantlaan 2, Utrecht"))
        XCTAssertTrue(html.hasPrefix("<!doctype html>"))
        XCTAssertTrue(html.contains("37,5%")) // 60 van 160 km, met een komma
    }

    func testHtmlOntsnaptAanGevaarlijkeTekens() {
        XCTAssertEqual("Jansen &amp; Co &lt;b&gt;", HtmlRapport.esc("Jansen & Co <b>"))
    }
}

final class BackupTest: XCTestCase {

    func testBackupGaatHeenEnWeerZonderVerlies() throws {
        let backup = Backup(gemaaktOp: moment("2026-06-30T10:00:00Z"), voertuigen: [audi], ritten: [
            rit("1", begin: 10_000, eind: 10_100).met {
                $0.vertrektijd = Kloktijd(8, 15)
                $0.gemetenKm = 98.4
                $0.bevestigd = false
            },
        ])
        let terug = try BackupOpslag.lees(BackupOpslag.schrijf(backup))
        XCTAssertEqual(backup, terug)
        XCTAssertEqual("rittenregistratie-backup-2026-06-30.json", BackupOpslag.bestandsnaam(backup.gemaaktOp))
    }

    /// Letterlijk wat de Android-app schrijft; wie overstapt, neemt zijn ritten mee.
    func testEenBackupVanDeAndroidAppIsTeLezen() throws {
        let backup = try BackupOpslag.lees(Data(androidBackup.utf8))
        XCTAssertEqual(1, backup.voertuigen.count)
        XCTAssertEqual(Dag(2027, 1, 1), backup.voertuigen[0].terBeschikkingTot)
        XCTAssertEqual(2, backup.ritten.count)
        let eerste = backup.ritten[0]
        XCTAssertEqual(Kloktijd(8, 15), eerste.vertrektijd)
        XCTAssertEqual(Kloktijd(9, 5), eerste.aankomsttijd)
        XCTAssertEqual(.gps, eerste.bron)
        XCTAssertEqual(98.4, eerste.gemetenKm!, accuracy: 0.0001)
        XCTAssertFalse(eerste.bevestigd)
        XCTAssertNil(eerste.afwijkendeRoute)
        XCTAssertEqual(.woonWerk, backup.ritten[1].soort)
        XCTAssertEqual(moment("2026-06-30T10:00:00.123Z"), backup.gemaaktOp)
        // En wat de iPhone ervan maakt, is weer hetzelfde.
        let terug = try BackupOpslag.lees(BackupOpslag.schrijf(backup))
        XCTAssertEqual(backup.ritten, terug.ritten)
        XCTAssertEqual(backup.voertuigen, terug.voertuigen)
        XCTAssertEqual(backup.gemaaktOp.timeIntervalSince1970, terug.gemaaktOp.timeIntervalSince1970, accuracy: 0.002)
    }

    func testEenBackupUitEenNieuwereVersieWordtGeweigerd() {
        let nieuwer = androidBackup.replacingOccurrences(of: "\"versie\": 1", with: "\"versie\": 9")
        XCTAssertThrowsError(try BackupOpslag.lees(Data(nieuwer.utf8)))
    }

    let androidBackup = """
    {
        "versie": 1,
        "gemaaktOp": "2026-06-30T10:00:00.123Z",
        "voertuigen": [
            {
                "id": "auto-1",
                "merk": "Audi",
                "type": "RS e-tron GT",
                "kenteken": "X-123-YZ",
                "terBeschikkingVanaf": "2026-01-01",
                "terBeschikkingTot": "2027-01-01",
                "beginstandKm": 10000
            }
        ],
        "ritten": [
            {
                "id": "1",
                "voertuigId": "auto-1",
                "datum": "2026-03-02",
                "beginstandKm": 10000,
                "eindstandKm": 10100,
                "beginadres": "Kantoorweg 1, Amsterdam",
                "eindadres": "Klantlaan 2, Utrecht",
                "soort": "ZAKELIJK",
                "doel": "Bespreking",
                "afwijkendeRoute": null,
                "priveOmrijkilometers": 0,
                "vertrektijd": "08:15",
                "aankomsttijd": "09:05:30",
                "opmerking": null,
                "bron": "GPS",
                "gemetenKm": 98.4,
                "bevestigd": false,
                "aangemaaktOp": "2026-03-02T08:00:00Z",
                "gewijzigdOp": "2026-03-02T08:00:00Z",
                "vergrendeldOp": null
            },
            {
                "id": "2",
                "voertuigId": "auto-1",
                "datum": "2026-03-02",
                "beginstandKm": 10100,
                "eindstandKm": 10160,
                "beginadres": "Kantoorweg 1, Amsterdam",
                "eindadres": "Klantlaan 2, Utrecht",
                "soort": "WOON_WERK",
                "doel": "",
                "afwijkendeRoute": null,
                "priveOmrijkilometers": 0,
                "vertrektijd": null,
                "aankomsttijd": null,
                "opmerking": null,
                "bron": "HANDMATIG",
                "gemetenKm": null,
                "bevestigd": true,
                "aangemaaktOp": "2026-03-02T08:00:00Z",
                "gewijzigdOp": "2026-03-02T08:00:00Z",
                "vergrendeldOp": null
            }
        ]
    }
    """
}
