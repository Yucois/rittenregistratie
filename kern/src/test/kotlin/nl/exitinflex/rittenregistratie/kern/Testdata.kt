package nl.exitinflex.rittenregistratie.kern

import java.time.Instant
import java.time.LocalDate

internal val audi = Voertuig(
    id = "auto-1",
    merk = "Audi",
    type = "RS e-tron GT",
    kenteken = "X-123-YZ",
    terBeschikkingVanaf = LocalDate.of(2026, 1, 1),
    beginstandKm = 10_000,
)

internal fun rit(
    id: String,
    datum: LocalDate = LocalDate.of(2026, 3, 2),
    begin: Int,
    eind: Int,
    soort: Ritsoort = Ritsoort.ZAKELIJK,
    beginadres: String = "Kantoorweg 1, Amsterdam",
    eindadres: String = "Klantlaan 2, Utrecht",
    doel: String = "Bespreking",
    omrij: Int = 0,
    route: String? = null,
    gemeten: Double? = null,
) = Rit(
    id = id,
    voertuigId = audi.id,
    datum = datum,
    beginstandKm = begin,
    eindstandKm = eind,
    beginadres = beginadres,
    eindadres = eindadres,
    soort = soort,
    doel = doel,
    priveOmrijkilometers = omrij,
    afwijkendeRoute = route,
    gemetenKm = gemeten,
    aangemaaktOp = Instant.parse("2026-03-02T08:00:00Z"),
    gewijzigdOp = Instant.parse("2026-03-02T08:00:00Z"),
)
