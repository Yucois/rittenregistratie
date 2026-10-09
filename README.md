# Rittenregistratie

Een app voor Android en iPhone die de ritten met je auto van de zaak automatisch bijhoudt,
zodat je met een **Verklaring geen privégebruik auto** kunt aantonen dat je
per jaar niet meer dan 500 km privé rijdt.

## Downloaden

**[Download de nieuwste versie](https://github.com/Yucois/rittenregistratie/releases/latest/download/rittenregistratie.apk)**
· [alle versies](https://github.com/Yucois/rittenregistratie/releases)

Werkt op Android 8 of nieuwer. **iPhone?** Zie [docs/iphone.md](docs/iphone.md):
die versie installeer je gratis via SideStore, met je eigen Apple-account.

## Installeren

1. Open de downloadlink hierboven **in Chrome** op je telefoon.
2. Chrome waarschuwt dat dit bestandstype schadelijk kan zijn: tik **Toch downloaden**.
3. Open het bestand en sta toe dat Chrome apps installeert. Tik **Installeren**.
4. Play Protect waarschuwt voor een onbekende app: tik **Toch installeren**.
   Dat komt doordat de app niet via de Play Store loopt.

Een nieuwe versie installeer je via dezelfde link over de oude heen; je ritten
blijven staan.

## Instellen

Open de app als je bij je auto bent, met de telefoon via bluetooth verbonden.
Bovenaan het rittenscherm staat **Aan de slag** met zeven stappen; elke stap
krijgt een vinkje als hij klaar is en heeft een knop naar de plek waar je hem
regelt. Twee zijn onmisbaar voor automatisch loggen: locatie op **Altijd
toestaan**, en de **batterijbeperking uit**.

## Wat de app doet

| | |
|---|---|
| Rit begint | zodra je telefoon met de bluetooth van je auto verbindt |
| Rit eindigt | een minuut nadat die verbinding wegvalt |
| Afstand | gemeten met gps, af en toe geijkt op de stand van het dashboard |
| Begin- en eindadres | opgezocht bij vertrek en aankomst |
| Zakelijk, woon-werk of privé | één tik op de melding na de rit; bekende ritten vult de app zelf in |
| Telling | de 500 privékilometers voor de bijtelling, en apart de btw (daar telt woon-werk als privé) |
| Export | rapport (pdf), CSV en een back-up |

De app legt per rit vast wat artikel 3.13 van de Uitvoeringsregeling
loonbelasting 2011 vraagt: datum, begin- en eindstand, begin- en eindadres, de
gereden route als die afwijkt, en het karakter van de rit. Zie
[docs/fiscale-eisen.md](docs/fiscale-eisen.md).

## Goed om te weten

- **Geen fiscaal advies.** De app is een hulpmiddel; voor een correcte
  registratie blijf je zelf verantwoordelijk.
- **Nieuw.** Loop de eerste weken je ritten wekelijks na.
- **Privé.** Je ritten staan alleen op je eigen telefoon. Er is geen account
  en er gaat niets naar een server. Voor het opzoeken van adressen gebruikt de
  app de adresdienst van Google op je telefoon.
- **Bewaren.** De registratie moet zeven jaar bewaard blijven. Maak elk
  kwartaal een export en zet die buiten je telefoon.

## Voor ontwikkelaars

```bash
./gradlew :kern:test           # fiscale rekenregels, draait zonder Android SDK
./gradlew :app:assembleDebug   # APK in app/build/outputs/apk/debug/
cd ios/RittenKern && swift test   # dezelfde rekenregels in Swift (macOS)
```

- `kern/` is gewone Kotlin zonder Android: model, controle op sluitendheid,
  jaartotalen, export, en `Ritbesluit` — de beslissing wanneer een rit begint
  en eindigt. Alles daarin staat onder test.
- `app/` is de Android-kant: opslag (Room), de meetdienst, de schermen.
- Het automatisch starten volgt het patroon van de
  [Home Assistant-app](https://github.com/home-assistant/android): Android
  wekt de app bij elke bluetooth-verbinding, de app vraagt het gekoppelde
  apparaat of het verbonden is, en de meetdienst start nooit uit zichzelf
  opnieuw.
- Elke push naar `main` bouwt een nieuwe versie en zet die als release klaar.
  Ondertekend wordt met een vaste sleutel uit het geheim `ONDERTEKENSLEUTEL`;
  zonder dat geheim wordt er geen release gemaakt.
- `ios/` is de iPhone-versie (SwiftUI). `ios/RittenKern` is de vertaling van
  `kern/` naar Swift met dezelfde testgevallen; een back-up is uitwisselbaar
  tussen beide versies. Een iPhone-app mag de bluetooth van de auto niet zelf
  volgen, dus het signaal komt van een automatisering in Opdrachten die de
  acties *Rit begint* en *Rit eindigt* aanroept. Het Xcode-project maakt
  XcodeGen uit `ios/project.yml`. De workflow `iPhone-app` test, start de app
  in een simulator, bouwt een niet-ondertekende `.ipa` en zet die met een
  SideStore-bron onder de release met tag `ios`.
- Kilometerstand rechtstreeks uit de auto, via Home Assistant:
  [docs/kilometerstand-uit-de-auto.md](docs/kilometerstand-uit-de-auto.md).
