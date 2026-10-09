import Foundation
import RittenKern

/// De lopende rit op schijf, zodat hij een herstart van de app overleeft: iOS
/// stopt apps op de achtergrond wanneer het wil.
enum Metingopslag {
    private static var url: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("lopende-rit.json")
    }

    static func lees() -> Ritmeting? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? Tijdstempel.decoder.decode(Ritmeting.self, from: data)
    }

    static func schrijf(_ meting: Ritmeting) {
        guard let data = try? Tijdstempel.encoder.encode(meting) else { return }
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    static func wis() {
        try? FileManager.default.removeItem(at: url)
    }
}
