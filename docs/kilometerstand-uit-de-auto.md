# Kilometerstand uit de auto, via Home Assistant

Dit is optioneel. Zonder deze koppeling rekent de app met gps en vraag je
ongeveer één keer per maand de stand van het dashboard op; dat volstaat.

Draai je thuis [Home Assistant](https://www.home-assistant.io/), en heeft je
automerk daar een integratie die de kilometerstand als sensor levert, dan kan
de app die stand bij vertrek en aankomst overnemen. Dan hoef je nooit meer te
ijken.

## Waarom via Home Assistant, en niet rechtstreeks uit de app van je auto?

Automerken bieden voor particulieren zelden een officiële koppeling. Wie de
gegevens rechtstreeks wil ophalen, moet de inlogprocedure van de app van het
merk nabootsen. Die is niet gedocumenteerd en verandert zonder aankondiging.
De integraties voor Home Assistant doen dat werk en worden door een
gemeenschap bijgehouden; je wachtwoord blijft dan in Home Assistant en niet in
deze app.

## Instellen

1. Installeer in Home Assistant de integratie voor je automerk en controleer
   onder Ontwikkelhulpmiddelen → Statussen dat er een sensor is met de
   kilometerstand (vaak met `mileage` of `odometer` in de naam).
2. Maak een token: klik linksonder op je naam → **Beveiliging** → onderaan
   **Langlevende toegangstokens** → *Token aanmaken*. Kopieer het meteen.
3. In de app: **Auto** → kaart *Kilometerstand uit de auto* → zet **Ik draai
   Home Assistant** aan. Vul het adres in (bijvoorbeeld
   `http://homeassistant.local:8123`) en het token, en tik **Sensor zoeken**.
4. Tik **Proef**. Er hoort een melding te komen met de huidige stand.

## Als het niet werkt

| Melding | Wat er aan de hand is |
|---|---|
| "Home Assistant weigert het token" | token verlopen of verkeerd geplakt; maak een nieuwe |
| "De sensor … bestaat daar niet" | de naam klopt niet; gebruik **Sensor zoeken** |
| "Home Assistant was niet bereikbaar" | verkeerd adres, of de telefoon zit niet op hetzelfde netwerk |
| "De sensor geeft geen getal terug" | de integratie staat op `unavailable`; kijk in Home Assistant of die nog inlogt |
