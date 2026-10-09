# Rittenregistratie op de iPhone

De iPhone-versie doet hetzelfde als de Android-app: een rit begint vanzelf als
je instapt, eindigt als je uitstapt, en na afloop tik je aan of het zakelijk,
woon-werk of privé was.

Hij staat niet in de App Store. Je installeert hem gratis met **SideStore**,
met je eigen Apple-account. Dat kost één keer zo'n half uur, met een computer
erbij. Daarna heb je de computer niet meer nodig.

## Wat je nodig hebt

- Een iPhone met iOS 17 of nieuwer, met een toegangscode.
- Eén keer een computer (Windows of Mac) en een USB-kabel voor de iPhone.
- Wifi. Mobiel internet is niet genoeg voor de installatie.
- Een Apple-account. Je eigen account werkt. Wil je dat liever niet, maak dan
  een apart account aan voor SideStore.

## 1. SideStore installeren (eenmalig)

**Op de iPhone**

1. Installeer **LocalDevVPN** uit de App Store.
2. Open LocalDevVPN en tik **Connect**. Tik **Sta toe** als iOS vraagt om een
   VPN-configuratie toe te voegen.

**Op de computer**

3. Windows: installeer eerst iTunes, rechtstreeks van
   [apple.com/itunes](https://www.apple.com/itunes/download/win64). Mac: niet nodig.
4. Installeer **iloader**:
   [Windows](https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.msi) ·
   [Mac](https://github.com/nab138/iloader/releases/latest/download/iloader-darwin-universal.dmg).
5. Sluit de iPhone met de kabel aan. Tik op de iPhone **Vertrouw** en vul je
   toegangscode in.
6. Open iloader, log in met je Apple-account (let op hoofdletters in het
   wachtwoord), kies je iPhone en kies **Install SideStore (Stable)**.

**Weer op de iPhone**

7. Ga naar **Instellingen → Algemeen → VPN en apparaatbeheer**. Tik onder
   *Ontwikkelaarsapp* op je Apple-account, tik **Vertrouw** en daarna
   **Sta toe en herstart**. Vul je toegangscode in.
8. Ga naar **Instellingen → Privacy en beveiliging**, scrol helemaal naar
   beneden en zet **Ontwikkelaarsmodus** aan. De iPhone start opnieuw.
9. Open LocalDevVPN en tik **Connect**.
10. Open SideStore en log in met hetzelfde Apple-account.
11. Ga naar **My Apps** en tik op **7 DAYS** naast SideStore. Vraagt hij om een
    certificaat te vernieuwen, tik dan **Yes**. SideStore gaat even dicht en
    komt na een paar seconden terug.

## 2. Rittenregistratie installeren

1. Zorg dat LocalDevVPN verbonden is.
2. Open SideStore, ga naar **Sources**, tik op **+** en plak dit adres:

   ```
   https://github.com/Yucois/rittenregistratie/releases/download/ios/bron.json
   ```

3. Tik bij Rittenregistratie op **Free** of **Install**.

Nieuwe versies zie je daarna vanzelf in SideStore, bij *My Apps* of bij de bron.

## 3. Instellen in de app

Open **Ritten**. Je krijgt het scherm *Aan de slag* te zien, met zes stappen
die een vinkje krijgen als ze klaar zijn:

1. **Meldingen** toestaan. Na elke rit vraagt de app wat het was.
2. **Je auto**: merk, kenteken en de kilometerstand van vandaag (onder Instellingen).
3. **Thuis- en werkadres**. Dan herkent de app woon-werkritten zelf.
4. **Locatie op Altijd**. iOS vraagt dat in twee stappen: eerst *Bij gebruik
   van app*, later *Wijzig in Altijd*. Laat ook *Nauwkeurige locatie* aan.
5. **Automatisering bij instappen.** Open de app **Opdrachten** →
   **Automatisering** → **+**. Kies **CarPlay** als je auto dat heeft, anders
   **Bluetooth** en dan je auto. Kies *Verbindt* en zet **Direct uitvoeren** aan.
   Zoek als actie **Rit begint** (van Ritten).
6. **Automatisering bij uitstappen.** Hetzelfde, maar met *Verbreekt* en de
   actie **Rit eindigt**.

Waarom zo? Een iPhone-app mag niet zelf zien dat je telefoon met de auto
verbindt. Opdrachten mag dat wel, en geeft het door. Alleen jouw eigen auto
start dus een rit, niet lopen en niet meerijden met een ander.

Stap 5 en 6 krijgen hun vinkje na je eerste echte rit. Valt de verbinding even
weg, bijvoorbeeld bij het tanken, dan blijft het één rit: de app wacht
anderhalve minuut voordat hij afrondt.

## 4. Elke week: vernieuwen

Met een gratis Apple-account werkt een app via SideStore **zeven dagen**.
Daarna opent hij niet meer, en een app die niet opent legt geen ritten vast.
Ontbrekende kilometers tellen bij een controle als privé. Vernieuwen is dus
belangrijk:

- SideStore vernieuwt op de achtergrond zolang **LocalDevVPN verbonden** is.
- Lukt dat niet, open dan SideStore → **My Apps** en tik op de dagenteller
  naast Rittenregistratie (en naast SideStore zelf).
- De app waarschuwt je een dag van tevoren met een melding, en toont bovenaan
  het rittenscherm hoeveel dagen hij nog werkt.

Is hij toch verlopen? Je ritten blijven staan. Vernieuw hem in SideStore en hij
werkt weer. Vul daarna de kilometerstand in (*Instellingen → Teller nalopen*):
de kilometers van tussendoor komen er dan als correctierit bij.

## Als het niet werkt

| Wat je ziet | Wat je doet |
|---|---|
| Er begint geen rit als je instapt | Kijk in Opdrachten of de automatisering op **Direct uitvoeren** staat. Kijk onder *Instellingen → Logboek* of er "Auto verbonden" staat. |
| Rit begint, maar 0 km | Locatie staat niet op *Altijd*, of *Nauwkeurige locatie* staat uit. |
| Geen melding na de rit | Meldingen voor Ritten staan uit: *Instellingen → Meldingen → Ritten*. |
| Twee ritten waar het er één was | De verbinding was langer dan anderhalve minuut weg. Verwijder de kortste en pas de andere aan. |
| SideStore: installeren of vernieuwen mislukt | Staat LocalDevVPN op **Connect**? Zit je op wifi? |
| SideStore: "pairing file" verlopen | Na een iOS-update kan dat gebeuren. Draai iloader nog eens met de iPhone aan de kabel. |

## Goed om te weten

- **Drie apps.** Met een gratis Apple-account kan SideStore drie apps tegelijk
  installeren, SideStore zelf meegerekend.
- **Je gegevens blijven op je telefoon.** Geen account, geen server. Maak elk
  kwartaal een back-up onder *Overzicht → Back-up van alles* en zet die in
  iCloud Drive of mail hem naar jezelf. Een back-up van de Android-versie kun
  je op de iPhone terugzetten, en andersom.
- **Liever niet elke week vernieuwen?** Met een betaald Apple Developer-account
  (€ 99 per jaar) werkt de app een jaar lang en kan hij ook via TestFlight.
  Vraag het als dat nodig blijkt.
- **Geen fiscaal advies.** De app is een hulpmiddel; je blijft zelf
  verantwoordelijk voor een juiste registratie.
