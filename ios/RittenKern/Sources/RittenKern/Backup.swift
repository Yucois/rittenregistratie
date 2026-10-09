import Foundation

/// Volledige, leesbare kopie van de administratie. De Belastingdienst kan de
/// registratie tot zeven jaar terug opvragen; een telefoon gaat zo lang niet mee.
///
/// Hetzelfde formaat als de back-up van de Android-app: wie van telefoon
/// wisselt, neemt zijn ritten mee.
public struct Backup: Codable, Equatable, Sendable {
    public static let huidigeVersie = 1

    public var versie: Int
    public var gemaaktOp: Date
    public var voertuigen: [Voertuig]
    public var ritten: [Rit]

    public init(versie: Int = Backup.huidigeVersie, gemaaktOp: Date, voertuigen: [Voertuig], ritten: [Rit]) {
        self.versie = versie
        self.gemaaktOp = gemaaktOp
        self.voertuigen = voertuigen
        self.ritten = ritten
    }
}

public enum BackupOpslag {

    public struct NieuwereVersie: LocalizedError {
        public let versie: Int
        public var errorDescription: String? {
            "Deze back-up komt uit een nieuwere versie van de app (versie \(versie))."
        }
    }

    public static func bestandsnaam(_ gemaaktOp: Date) -> String {
        "rittenregistratie-backup-\(Tijdstempel.tekst(gemaaktOp).prefix(10)).json"
    }

    public static func schrijf(_ backup: Backup) throws -> Data {
        try Tijdstempel.encoder.encode(backup)
    }

    public static func lees(_ inhoud: Data) throws -> Backup {
        let backup = try Tijdstempel.decoder.decode(Backup.self, from: inhoud)
        if backup.versie > Backup.huidigeVersie { throw NieuwereVersie(versie: backup.versie) }
        return backup
    }
}
