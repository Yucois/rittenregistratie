# Wat een rittenregistratie moet bevatten

Naslag bij de app in `rittenregistratie/`. Bedoeld voor de situatie: een auto
van de zaak, een **Verklaring geen privégebruik auto**, en dus de plicht om aan
te tonen dat er per kalenderjaar niet meer dan 500 kilometer privé is gereden.

## De wettelijke lijst

Artikel 3.13 Uitvoeringsregeling loonbelasting 2011 noemt als minimum:

**Over de auto**

- merk, type en kenteken;
- de periode waarin de auto ter beschikking stond.

**Per rit**

1. de datum;
2. de beginstand en de eindstand van de kilometerteller;
3. het beginadres en het eindadres;
4. de gereden route, als die afwijkt van de meest gebruikelijke;
5. het karakter van de rit (zakelijk of privé).

De praktijk voegt daar één eis aan toe die nergens los staat opgeschreven maar
in elke controle terugkomt: **de reeks moet sluiten.** De eindstand van rit N is
de beginstand van rit N+1. Kilometers die tussen twee ritten vallen zijn niet
verantwoord, en onverantwoorde kilometers worden als privé aangemerkt.

## De 500-kilometergrens

- Blijf je op jaarbasis onder 500 privékilometers, dan volgt geen bijtelling.
- Kom je erboven, ook met één kilometer, dan vervalt de verklaring en volgt
  bijtelling over het hele jaar dat de auto ter beschikking stond — plus, bij
  een onjuiste verklaring, een naheffing bij jou in plaats van bij de werkgever.
- Privé-omrijkilometers binnen een zakelijke rit tellen mee voor die 500.

## Woon-werkverkeer: twee antwoorden tegelijk

Dezelfde kilometer valt fiscaal twee kanten op:

| | Woon-werkverkeer telt als |
|---|---|
| Loonheffing / bijtelling (de 500 km) | **zakelijk** |
| Btw-correctie privégebruik auto | **privé** |

Daarom houdt de app beide totalen apart bij. Wie alleen op de
loonheffingsbril stuurt, mist aan het eind van het jaar de btw-correctie in de
laatste aangifte.

## Wat een registratie in de praktijk onderuit haalt

- Gaten in de tellerstanden, ook kleine.
- Afgeronde afstanden in plaats van werkelijke tellerstanden.
- Adressen als "klant" of "kantoor" zonder straat en plaats.
- Een bestand dat achteraf in één keer is ingevuld: dat is te zien aan de
  bestandsgeschiedenis en het wekt argwaan.
- Ritten die niet stroken met tankbonnen, laadsessies, agenda of telefoondata.
  Bij een elektrische auto zijn de laadmomenten en -locaties een
  controlemiddel; zorg dat je registratie daarmee te rijmen valt.

## Bewaren

De administratie valt onder de fiscale bewaarplicht van zeven jaar. Exporteer
periodiek (de app maakt een JSON-back-up en een rapport) en bewaar dat buiten
de telefoon.

## Bronnen

- Artikel 3.13 Uitvoeringsregeling loonbelasting 2011 (rittenregistratie).
- Artikel 13bis Wet op de loonbelasting 1964 (bijtelling, 500-kilometergrens).
- Belastingdienst, "Rittenregistratie" en "Btw en privégebruik auto van de zaak:
  woon-werkverkeer".

Dit is een samenvatting voor de bouw van de app, geen fiscaal advies. Laat de
opzet één keer door je eigen adviseur bekijken.
