import CoreLocation
import Foundation
import RittenKern
import UIKit

/// Gps tijdens de rit.
///
/// Meten gaat door als de telefoon op slot zit en de app op de achtergrond is:
/// daarvoor is "Altijd" als locatietoestemming nodig. Daarnaast loopt de
/// melding van grote verplaatsingen mee; die wekt de app weer als iOS hem
/// tijdens een rit heeft gestopt.
@MainActor
final class Locatie: NSObject, ObservableObject {
    static let shared = Locatie()

    @Published private(set) var toestemming: CLAuthorizationStatus
    @Published private(set) var nauwkeurig = true

    private let beheer: CLLocationManager
    private var meet = false
    private var eenmalig: [CheckedContinuation<CLLocation?, Never>] = []

    /// Krijgt elke bruikbare positie tijdens een rit.
    var ontvanger: ((Positie) -> Void)?

    override init() {
        let beheer = CLLocationManager()
        self.beheer = beheer
        toestemming = beheer.authorizationStatus
        super.init()
        beheer.delegate = self
        beheer.desiredAccuracy = kCLLocationAccuracyBest
        beheer.distanceFilter = 10
        beheer.activityType = .automotiveNavigation
        beheer.pausesLocationUpdatesAutomatically = false
        nauwkeurig = beheer.accuracyAuthorization == .fullAccuracy
    }

    var magAltijd: Bool { toestemming == .authorizedAlways }
    var magTijdensGebruik: Bool { toestemming == .authorizedWhenInUse || toestemming == .authorizedAlways }
    var geweigerd: Bool { toestemming == .denied || toestemming == .restricted }

    /// Eerst "tijdens gebruik", daarna "altijd": zo vraagt iOS het graag.
    func vraagToestemming() {
        switch toestemming {
        case .notDetermined: beheer.requestWhenInUseAuthorization()
        case .authorizedWhenInUse: beheer.requestAlwaysAuthorization()
        default: break
        }
    }

    func start() {
        guard magTijdensGebruik else {
            Logboek.schrijf("Meten niet gestart: geen locatietoestemming")
            return
        }
        if magAltijd {
            beheer.allowsBackgroundLocationUpdates = true
            beheer.showsBackgroundLocationIndicator = true
            beheer.startMonitoringSignificantLocationChanges()
        }
        beheer.startUpdatingLocation()
        meet = true
    }

    func stop() {
        meet = false
        beheer.stopUpdatingLocation()
        beheer.stopMonitoringSignificantLocationChanges()
        beheer.allowsBackgroundLocationUpdates = false
    }

    /// Eén positie, bijvoorbeeld voor het beginadres of je thuisadres. Geeft
    /// binnen `wachten` seconden de beste positie die er is, of nil.
    func huidige(wachten: TimeInterval = 15) async -> CLLocation? {
        guard magTijdensGebruik else { return nil }
        if let recent = beheer.location, recent.timestamp.timeIntervalSinceNow > -30, recent.horizontalAccuracy <= 65 {
            return recent
        }
        return await withCheckedContinuation { (c: CheckedContinuation<CLLocation?, Never>) in
            eenmalig.append(c)
            beheer.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(wachten))
                self.beantwoord(self.beheer.location)
            }
        }
    }

    private func beantwoord(_ locatie: CLLocation?) {
        let wachtenden = eenmalig
        eenmalig = []
        wachtenden.forEach { $0.resume(returning: locatie) }
    }

    static func positie(_ l: CLLocation) -> Positie {
        Positie(
            breedte: l.coordinate.latitude,
            lengte: l.coordinate.longitude,
            nauwkeurigheid: l.horizontalAccuracy,
            moment: l.timestamp
        )
    }
}

extension Locatie: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        let vol = manager.accuracyAuthorization == .fullAccuracy
        Task { @MainActor in
            self.toestemming = status
            self.nauwkeurig = vol
            Logboek.schrijf("Locatietoestemming: \(Locatie.naam(status))\(vol ? "" : ", bij benadering")")
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            if let laatste = locations.last { self.beantwoord(laatste) }
            guard self.meet else { return }
            for l in locations { self.ontvanger?(Locatie.positie(l)) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            if let fout = error as? CLError, fout.code == .locationUnknown { return }
            self.beantwoord(nil)
            Logboek.schrijf("Locatiefout: \(error.localizedDescription)")
        }
    }

    static func naam(_ status: CLAuthorizationStatus) -> String {
        switch status {
        case .authorizedAlways: return "altijd"
        case .authorizedWhenInUse: return "tijdens gebruik"
        case .denied: return "geweigerd"
        case .restricted: return "beperkt"
        case .notDetermined: return "nog niet gevraagd"
        @unknown default: return "onbekend"
        }
    }
}

/// Van coördinaten naar een adres, in de vorm "Straat 12, Plaats". Zonder
/// netwerk valt hij terug op de coördinaten: liever die in de administratie
/// dan een leeg adres.
enum Adreszoeker {
    static func adres(_ p: Positie) async -> String {
        let locatie = CLLocation(latitude: p.breedte, longitude: p.lengte)
        do {
            let plekken = try await CLGeocoder().reverseGeocodeLocation(locatie, preferredLocale: Locale(identifier: "nl_NL"))
            if let plek = plekken.first {
                let straat = [plek.thoroughfare, plek.subThoroughfare].compactMap { $0 }.joined(separator: " ")
                let delen = [straat.isEmpty ? plek.name : straat, plek.locality].compactMap { $0 }.filter { !$0.isEmpty }
                if !delen.isEmpty { return delen.joined(separator: ", ") }
            }
        } catch {
            Logboek.schrijf("Adres zoeken mislukt: \(error.localizedDescription)")
        }
        return String(format: "%.5f, %.5f", p.breedte, p.lengte)
    }
}
