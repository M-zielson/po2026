# Symulator VPP – Osiedle Słoneczne

Symulator wirtualnej elektrowni (VPP, *Virtual Power Plant*) jednego osiedla w Krakowie.
Osiedle ma panele fotowoltaiczne na dachach, wiatrak i magazyn energii, a operator decyduje,
jak nimi zarządzać, i widzi skutki na wykresach oraz na animowanej makiecie 3D.

Program odpowiada na pytanie: **ile prądu osiedle wyprodukuje samo, ile musi kupić, ile sprzeda
i ile to kosztuje** – przy prawdziwej pogodzie z danego dnia i przy wybranej strategii sterowania.

Projekt zaliczeniowy z Programowania Obiektowego (AGH, 2026/27).

## Autorzy

| Kto | Zakres |
|---|---|
| **Michał Zieliński** | silnik symulacji (`vpp-engine`): panele, wiatrak, magazyn, strategie, zdarzenia operatora, pętla symulacji; scena 3D |
| **Michał Wnęk** | dane i aplikacja (`vpp-data`, `vpp-app`): pogoda z Open-Meteo, ceny, wczytywanie osiedla, metryki, eksport CSV, serwer HTTP; panel operatora |
| wspólnie | kontrakt `vpp-domain`, plik osiedla, diagramy UML, tryb konsolowy |

## Stan projektu

| Kamień milowy | Cel | Stan |
|---|---|---|
| M0 Start | kontrakt `vpp-domain`, szkielet modułów, CI, diagramy | ✅ gotowe |
| M1 Szkielet end-to-end | tryb konsolowy liczy dobę z danych syntetycznych i zapisuje CSV | 🔄 w toku: gotowe `estate.json` i `EstateLoader` |
| M2 Bateria i aplikacja | prawdziwa pogoda, bateria i wiatrak, panel z wykresami | ⏳ |
| M3 Sterowanie i animacja | zmiana strategii, sterowanie magazynem, awarie, animowana scena | ⏳ |
| M4 Stabilizacja | pokrycie testami ≥ 80%, dokumentacja, poprawki | ⏳ |

Termin oddania: 27.01.2027.

## Architektura

Wielomodułowy projekt Maven w czystej Javie 25. Silnik nie wie nic o plikach, HTTP ani interfejsie
użytkownika, a moduły silnika i danych **nie zależą od siebie** – rozmawiają wyłącznie przez wspólny
kontrakt w `vpp-domain`.

```
              vpp-domain  (kontrakt: rekordy i interfejsy)
               ▲        ▲
               │        │
        vpp-engine    vpp-data
               ▲        ▲
               └── vpp-app ──┘   (składa wszystko w całość)
```

| Moduł | Zawartość | Zależy od |
|---|---|---|
| `vpp-domain` | rekordy przepływające między modułami (`EstateConfig`, `OperatorSettings`, `Timeline`, `Conditions`, `StepResult`, `AssetFlow`…) i interfejsy (`WeatherProvider`, `PriceProvider`, `SimulationListener`, `DispatchStrategy`…) | – |
| `vpp-engine` | modele elementów osiedla, magazyn, strategie sterowania, pętla symulacji | `vpp-domain` |
| `vpp-data` | pogoda (Open-Meteo, dane syntetyczne), ceny energii, wczytywanie osiedla, metryki, eksport CSV | `vpp-domain` |
| `vpp-app` | tryb konsolowy (`Main`) i serwer HTTP (REST, SSE, serwowanie frontendu) | wszystkie powyższe |
| `vpp-frontend` *(planowany)* | panel operatora i scena 3D (Vite, React, TypeScript, Three.js) | API z `vpp-app` |

Kontrakt `vpp-domain` zmieniamy tylko osobnym Pull Requestem, który zatwierdzają obaj autorzy.

### Struktura katalogów

```
projekt/
├── pom.xml          parent Maven
├── data/            estate.json (później: cache pogody, ceny)
├── uml/             diagramy PlantUML
├── vpp-domain/
├── vpp-engine/
├── vpp-data/
└── vpp-app/
```

## Konwencje

- **Jednostki:** moc w kW, energia w kWh, czas przez `java.time` (`Instant` dla chwil, `LocalTime`
  dla godzin w ciągu doby). Nazwy pól mówią o jednostce: `powerKw`, `capacityKwh`, `pricePlnPerKwh`.
- **Krok symulacji:** domyślnie 15 minut, doba od lokalnej północy do północy – zwykle 96 kroków,
  w dni zmiany czasu 92 albo 100.
- **Znaki** na poziomie całego osiedla (`StepResult`):

  | Pole | `+` | `−` |
  |---|---|---|
  | `batteryKw` | ładowanie magazynu | rozładowanie |
  | `gridKw` | import z sieci (kupujemy) | eksport do sieci (sprzedajemy) |

  `productionKw` (panele + wiatrak) i `consumptionKw` (budynki) są zawsze ≥ 0.
- **Bilans energii** w każdym kroku:

  ```
  productionKw + gridKw = consumptionKw + batteryKw
  ```

- **Moc pojedynczego elementu** (`AssetFlow`) to dwie nieujemne wartości z punktu widzenia sieci
  osiedla: `productionKw` (oddaje) i `consumptionKw` (pobiera). Budynek ma obie naraz – panele na
  dachu i zużycie mieszkań.
- Kod po angielsku, Javadoc, komentarze i dokumentacja po polsku.

## Model fizyczny i strategie

*Do uzupełnienia przez Michała Zielińskiego:* wzory produkcji paneli (z poprawką temperaturową) i
wiatraka (krzywa mocy), sprawność i limity magazynu, opis strategii regułowej i cenowej.

## Plik osiedla `estate.json`

Osiedle jest opisane w pliku [`data/estate.json`](data/estate.json) – zmiana liczb nie wymaga
zmiany kodu. Wczytuje go klasa `EstateLoader` z modułu `vpp-data`.

```json
{
  "name": "Osiedle Słoneczne",
  "location": { "latitude": 50.0717, "longitude": 20.0377, "zone": "Europe/Warsaw" },
  "assets": [
    { "id": "block-1", "type": "APARTMENT_BLOCK", "name": "Blok 1", "pvPeakKw": 30, "dwellings": 40, "position": { "x": -60, "z": -40 } },
    { "id": "wind-1",  "type": "WIND_TURBINE",    "name": "Wiatrak", "ratedPowerKw": 50 },
    { "id": "battery", "type": "BATTERY",         "ratedPowerKw": 100, "capacityKwh": 200 },
    { "id": "grid",    "type": "GRID",            "ratedPowerKw": 250 }
  ]
}
```

### Lokalizacja

| Pole | Opis |
|---|---|
| `latitude` | szerokość geograficzna, od −90 do 90 |
| `longitude` | długość geograficzna, od −180 do 180 |
| `zone` | strefa czasowa w formacie IANA, np. `Europe/Warsaw` |

### Elementy osiedla

| Pole | Opis | Dotyczy |
|---|---|---|
| `id` | unikalny identyfikator, np. `block-1` | wszystkich (wymagane) |
| `type` | rodzaj elementu – patrz niżej | wszystkich (wymagane) |
| `name` | nazwa do wyświetlenia; domyślnie równa `id` | wszystkich |
| `pvPeakKw` | moc szczytowa paneli na dachu [kW] | budynków |
| `ratedPowerKw` | moc znamionowa [kW] | wiatraka, magazynu (moc ładowania/rozładowania), przyłącza |
| `capacityKwh` | pojemność [kWh] | magazynu |
| `dwellings` | liczba mieszkań, skaluje zużycie | budynków |
| `position` | położenie na makiecie 3D w metrach (`x` na wschód, `z` na południe); domyślnie `(0, 0)` | wszystkich |

Pominięte pola liczbowe mają wartość 0.

| `type` | Element |
|---|---|
| `APARTMENT_BLOCK` | blok mieszkalny |
| `HOUSE` | dom jednorodzinny |
| `SCHOOL` | szkoła |
| `WIND_TURBINE` | wiatrak |
| `BATTERY` | magazyn energii |
| `GRID` | przyłącze do sieci |

### Reguły walidacji

Plik niespełniający którejkolwiek reguły jest odrzucany z wyjątkiem `InvalidEstateException`,
którego komunikat wskazuje plik, element i problem (przy błędzie składni także numer linii):

- osiedle ma nazwę, lokalizację i co najmniej jeden element,
- `id` elementów są niepuste i unikalne,
- żadna wartość liczbowa nie jest ujemna,
- wiatrak ma `ratedPowerKw > 0`, magazyn ma `capacityKwh > 0` i `ratedPowerKw > 0`,
- jest **dokładnie jedno** przyłącze `GRID` i **najwyżej jeden** magazyn `BATTERY`,
- nieznane pola są błędem – literówka typu `ratedPowerKW` nie zostanie po cichu zignorowana.

## Wymagania i budowanie

- Java 25 (JDK)
- Maven 3.9+

Budowanie i testy wszystkich modułów (to samo uruchamia CI na GitHubie):

```bash
cd projekt
mvn -B verify
```

Raport pokrycia testami (JaCoCo) dla każdego modułu: `<moduł>/target/site/jacoco/index.html`.

## Diagramy UML

Diagramy w PlantUML, w katalogu [`uml/`](uml/):

| Plik | Co pokazuje |
|---|---|
| [`01-przypadki-uzycia.puml`](uml/01-przypadki-uzycia.puml) | co może zrobić operator osiedla; Open-Meteo jako system zewnętrzny |
| [`02-sekwencja-symulacja-dnia.puml`](uml/02-sekwencja-symulacja-dnia.puml) | przebieg symulacji jednej doby krok po kroku |
| [`02-sekwencja-zmiana-strategii.puml`](uml/02-sekwencja-zmiana-strategii.puml) | zmiana strategii przez operatora *(w przygotowaniu)* |
| [`03-diagram-klas.puml`](uml/03-diagram-klas.puml) | klasy kontraktu `vpp-domain` i modułu `vpp-data` |

Podgląd: plugin *PlantUML Integration* w IntelliJ IDEA (diagram klas wymaga Graphviza).

## Technologie

| Do czego | Narzędzie |
|---|---|
| język, budowanie | Java 25, Maven (wielomodułowy) |
| JSON | Jackson |
| testy | JUnit 5, AssertJ, jqwik (testy właściwościowe) |
| pokrycie testami | JaCoCo |
| CI | GitHub Actions (`mvn -B verify`) |
| *planowane:* serwer HTTP | Javalin (REST, SSE) |
| *planowane:* pogoda | Open-Meteo API |
| *planowane:* frontend | Vite, React, TypeScript, Three.js, Apache ECharts |

---

Zasady kursu (zapis na temat, terminy, wymagania co do katalogu `uml/`) są opisane w sekcji
**Projekt** w głównym [README](../README.md) repozytorium.
