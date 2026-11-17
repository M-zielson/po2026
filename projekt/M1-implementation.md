# M1 — implementacja i odbiór

| Etap | Zakres | PR |
|---|---|---|
| M1.1 | SolarPanel, AbstractGenerator, stany | #10 |
| M1.2 | profile mieszkań i szkoły, kompozycja PV | #11 |
| M1.3 | bilans, przepływy i walidator | #12 |
| M1.4 | pętla doby, providery, DST i słuchacze | #13 |
| M1.5 | EstateBuilder, adapter i rzeczywisty silnik CLI | bieżący PR |

CLI wczytuje estate.json, tworzy osiedle, pobiera dane syntetyczne, liczy kroki,
wyświetla postęp i podsumowanie oraz zapisuje CSV. StubDaySimulator usunięto;
jego testy zastąpiono testami rzeczywistego adaptera. Kod domain, zależności i CI
pozostają bez zmian. Parametry profili i ograniczenia opisuje README.

## Odbiór przed M2

1. Sprawdzić CI i review ostatniego PR-a, następnie scalić go do main.
2. Na aktualnym main uruchomić `mvn -B clean verify` w projekt/.
3. Uruchomić JAR dla zwykłej doby i zmian czasu: 2026-06-21, 2026-03-29,
   2026-10-25. CSV powinien zawierać 96, 92 i 100 kroków.
4. Potwierdzić z osobą B znaki mocy, jednostki i zachowanie providerów.
5. Uzgodnić parametry baterii (SoC, sprawność, moc); rozpocząć M2.1 na nowym main.

M2.1: Battery, ograniczenia mocy/energii, sprawność sqrt(etaRt), EFC i testy.
Następnie M2.2: WindTurbine, a M2.3: RuleBasedStrategy oraz integracja obu zasobów.
Osoba B przygotowuje rzeczywistą pogodę i API. Nowe zależności wymagają uzgodnienia.

M1 nie obejmuje pracy baterii i wiatraka, rzeczywistego API pogodowego,
sterowania, limitu eksportu ani sceny 3D. Wiatrak i bateria mają zerowe przepływy.
Sieć pokrywa całą różnicę mocy. Szkoła nie uwzględnia ferii ani ogrzewania.
Tolerancja bilansu 1e-6 kWh dotyczy wyników silnika przed zaokrągleniem CSV.
