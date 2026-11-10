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

Wszystkie generatory dziedziczą po klasie abstrakcyjnej AbstractGenerator (wzorzec Template Method), która w metodzie produceKw(Conditions) sprawdza stan zasobu (RUNNING, FAILED, DISABLED) i przycina wyliczoną moc surową rawPowerKw(Conditions) do przedziału $[0, P_{\text{rated}}]$.1. Panele fotowoltaiczne (SolarPanel)Moc instalacji fotowoltaicznej o powierzchni $A\text{ [m}^2]$ i sprawności znamionowej $\eta$ (co w warunkach standardowych STC: $G_{\text{STC}} = 1000\text{ W/m}^2$, $T_{\text{STC}} = 25^\circ\text{C}$ odpowiada mocy szczytowej $P_{\text{peak}} = A \cdot \eta \cdot 1\text{ kW/m}^2$, zapisanej w pliku osiedla jako pvPeakKw) zależy liniowo od natężenia promieniowania słonecznego $G\text{ [W/m}^2]$ oraz temperatury otoczenia $T\text{ [}^\circ\text{C}]$:$$P_{\text{raw}}(G, T) = A \cdot \eta \cdot \frac{G}{1000} \cdot \left(1 + \gamma \cdot (T - 25)\right) = P_{\text{peak}} \cdot \frac{G}{1000} \cdot \left(1 + \gamma \cdot (T - 25)\right)$$gdzie:$G$ – natężenie promieniowania słonecznego padającego na płaszczyznę panelu $[\text{W/m}^2]$,$T$ – temperatura powietrza $[1^\circ\text{C}]$,$\gamma = -0{,}004\text{ }^\circ\text{C}^{-1}$ ($-0{,}4\% / ^\circ\text{C}$) – temperaturowy współczynnik spadku mocy dla krzemowych modułów PV.Ostateczna moc oddawana przez instalację jest przycinana do mocy znamionowej inwertera / instalacji:$$P_{\text{PV}} = \min\left(P_{\text{peak}}, \max\left(0, P_{\text{raw}}(G, T)\right)\right)$$2. Turbina wiatrowa (WindTurbine)Moc wiatraka o mocy znamionowej $P_{\text{rated}}$ (ratedPowerKw) wyznaczana jest na podstawie prędkości wiatru $v\text{ [m/s]}$ na wysokości gondoli (80 m) zgodnie z przedziałową krzywą mocy:$$P_{\text{wind}}(v) = \begin{cases} 0 & \text{dla } v < v_{\text{cut-in}} \text{ lub } v \ge v_{\text{cut-out}} \\ P_{\text{rated}} \cdot \dfrac{v^3 - v_{\text{cut-in}}^3}{v_{\text{rated}}^3 - v_{\text{cut-in}}^3} & \text{dla } v_{\text{cut-in}} \le v < v_{\text{rated}} \\ P_{\text{rated}} & \text{dla } v_{\text{rated}} \le v < v_{\text{cut-out}} \end{cases}$$Domyślne progi prędkości wiatru wynoszą:$v_{\text{cut-in}} = 3{,}0\text{ m/s}$ – prędkość startu turbiny,$v_{\text{rated}} = 12{,}0\text{ m/s}$ – prędkość osiągania mocy znamionowej,$v_{\text{cut-out}} = 25{,}0\text{ m/s}$ – prędkość awaryjnego odstawienia (hamowania) turbiny przy zbyt silnym wietrze.3. Profile odbiorców (Load)Zużycie energii przez budynki zależy od pory doby i dnia tygodnia, a w przypadku budynków mieszkalnych jest skalowane liczbą lokali (dwellings):Blok mieszkalny (APARTMENT_BLOCK) i dom jednorodzinny (HOUSE): profil bazowy z wyraźnym szczytem porannym ($6\text{:}30\text{--}9\text{:}00$) oraz głównym szczytem wieczornym ($17\text{:}00\text{--}22\text{:}00$), a także podwyższonym zużyciem w chłodne dni.Szkoła (SCHOOL): wysokie zapotrzebowanie w godzinach lekcyjnych ($8\text{:}00\text{--}15\text{:}00$) w dni robocze oraz minimalny pobór bazowy (oświetlenie awaryjne, serwerownia) w nocy i w weekendy.4. Magazyn energii (Battery)Magazyn energii charakteryzuje się pojemnością całkowitą $E_{\text{cap}}$ (capacityKwh), maksymalną mocą ładowania i rozładowania $P_{\text{max}}$ (ratedPowerKw), dopuszczalnym przedziałem stanu naładowania $\text{SoC} \in [\text{SoC}_{\text{min}}, \text{SoC}_{\text{max}}]$ oraz sprawnością całego cyklu (round-trip efficiency) $\eta_{\text{rt}} \in (0, 1]$ (domyślnie $\eta_{\text{rt}} = 0{,}90$). Sprawność rozkładana jest symetrycznie na oba kierunki jako $\eta_{\text{one-way}} = \sqrt{\eta_{\text{rt}}}$:Przycięcie do limitu mocy przekształtnika:
$$P_{\text{clamped}} = \min\left(P_{\text{max}}, \max\left(-P_{\text{max}}, P_{\text{req}}\right)\right)$$Ładowanie ($P_{\text{clamped}} > 0$):
Energia, którą można jeszcze bezpiecznie wprowadzić do ogniw, wynosi $\Delta E_{\text{room}} = E_{\text{max}} - E(t)$. Maksymalna moc pobierana z sieci osiedla w kroku $\Delta t$ oraz nowy stan energii w magazynie to:
$$P_{\text{battery}} = \min\left(P_{\text{clamped}}, \frac{\Delta E_{\text{room}}}{\Delta t \cdot \sqrt{\eta_{\text{rt}}}}\right), \quad E(t + \Delta t) = E(t) + P_{\text{battery}} \cdot \Delta t \cdot \sqrt{\eta_{\text{rt}}}$$Rozładowanie ($P_{\text{clamped}} < 0$):
Dostępna energia w ogniwach powyżej progu minimalnego wynosi $\Delta E_{\text{avail}} = E(t) - E_{\text{min}}$. Rzeczywista moc oddawana do osiedla (ze znakiem ujemnym) oraz nowy stan energii to:
$$P_{\text{battery}} = -\min\left(\vert{}P_{\text{clamped}}\vert{}, \frac{\Delta E_{\text{avail}} \cdot \sqrt{\eta_{\text{rt}}}}{\Delta t}\right), \quad E(t + \Delta t) = E(t) - \frac{\vert{}P_{\text{battery}}\vert{} \cdot \Delta t}{\sqrt{\eta_{\text{rt}}}}$$Magazyn nie rzuca wyjątków przy żądaniach przekraczających jego możliwości – zwraca rzeczywistą zrealizowaną moc $P_{\text{battery}}$, a resztę bilansuje przyłącze sieciowe (GRID). Magazyn zlicza również liczbę ekwiwalentnych pełnych cykli pracy: $\text{EFC} = \frac{\sum \vert{}\Delta E_{\text{internal}}\vert{}}{2 \cdot E_{\text{cap}}}$.5. Strategie sterowania (DispatchStrategy) i dekoratoryW każdym kroku silnik oblicza bilans mocy netto osiedla przed użyciem magazynu:$$P_{\text{net}} = P_{\text{production}} - P_{\text{consumption}}$$Następnie wybrana strategia wyznacza pożądaną moc magazynu $P_{\text{battery}}$, a po jej fizycznym przycięciu przez obiekt Battery moc na przyłączu sieciowym wynika wprost z równania bilansu:$$P_{\text{grid}} = P_{\text{battery}} - P_{\text{net}}$$Strategia regułowa (RuleBasedStrategy – maksymalizacja autokonsumpcji):
Dąży do wyzerowania wymiany z siecią zewnętrzną ($P_{\text{grid}} \approx 0$). Przy nadwyżce produkcji ($P_{\text{net}} > 0$) cała nadwyżka kierowana jest do ładowania magazynu ($P_{\text{req}} = P_{\text{net}}$), a ewentualny nadmiar (gdy magazyn jest pełny lub ograniczony mocowo) jest eksportowany do sieci. Przy niedoborze ($P_{\text{net}} < 0$) deficyt jest pokrywany rozładowaniem magazynu ($P_{\text{req}} = P_{\text{net}}$), a brakująca część jest importowana z sieci.Strategia cenowa (PriceBasedStrategy – arbitraż cenowy):
Wykorzystuje zmienność cen energii w ciągu doby. Gdy bieżąca cena energii spada poniżej dolnego progu cenowego $c_{\text{low}}$ (tania energia), strategia wymusza ładowanie magazynu z pełną dostępną mocą $P_{\text{max}}$ (zarówno z nadwyżki OZE, jak i z taniego importu z sieci). Gdy cena przekracza górny próg $c_{\text{high}}$ (drogi szczyt wieczorny), magazyn jest rozładowywany na pokrycie zapotrzebowania osiedla oraz eksport do sieci. W przedziale cen umiarkowanych ($c_{\text{low}} \le c(t) \le c_{\text{high}}$) strategia zachowuje się jak strategia regułowa (chroni autokonsumpcję).Dekoratory sterowania (wzorzec Decorator):ManualOverride: opakowuje dowolną strategię bazową i pozwala operatorowi wymusić tryb pracy magazynu: AUTO (decyduje strategia bazowa), CHARGE_NOW (wymuszenie ładowania mocą $P_{\text{max}}$) lub HOLD_CHARGE (zakaz rozładowywania magazynu – zachowanie energii na szczyt wieczorny, dozwolone jedynie ładowanie z nadwyżek OZE).ExportLimit: opakowuje strategię i pilnuje, aby eksport mocy do sieci nie przekroczył limitu ustawionego przez operatora na stacji transformatorowej ($-P_{\text{grid}} \le P_{\text{export, max}}$).
####
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
