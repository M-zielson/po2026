# po2026
Repozytorium dla przedmiotu Programowanie Obiektowe.

### Termin deklaracji projektu - 15.12.2026
### Termin oddania (push do repo) projektu - 27.01.2027
### Termin oddania (push do repo) projektu laboratoryjnego - 27.01.2027

## Zasady zapisu na projekt (Pull Request)

Lista tematów projektów znajduje się w pliku [`Projekty.txt`](Projekty.txt). Zapisy odbywają się wyłącznie przez Pull Request na GitHubie — to samo w sobie jest małym ćwiczeniem z pracy z Gitem, które przyda się przy oddawaniu projektu.

Krok po kroku:

1. **Zróbcie fork** tego repozytorium (przycisk `Fork` w prawym górnym rogu) albo, jeśli macie uprawnienia zapisu, sklonujcie repo i utwórzcie nowy branch.
2. **Wybierzcie temat** z listy w `Projekty.txt` i dopiszcie pod nim wasze imiona i nazwiska (2 osoby na temat). Jeśli chcecie zaproponować własny temat — dopiszcie go na końcu listy, w kategorii D, razem z waszymi nazwiskami.
3. **Sprawdźcie, czy temat jest jeszcze wolny** — jeśli ktoś już się pod nim wpisał, wybierzcie inny lub zaproponujcie własny.
4. **Utwórzcie commit** ze zmianą wyłącznie w pliku `Projekty.txt` (jeden temat = jedna linijka z nazwiskami — nie modyfikujcie innych zapisów).
5. **Otwórzcie Pull Request** (`Create Pull Request`) do brancha `main` tego repozytorium, z krótkim opisem, np. "Zapis: [nr tematu] - [Imię Nazwisko], [Imię Nazwisko]".
6. **Poczekajcie na zatwierdzenie** — PR zostanie zmergowany po sprawdzeniu, że temat nie jest zajęty i że zmiana dotyczy tylko waszego wpisu. Dopiero po zmergowaniu PR-a temat jest uznawany za zarezerwowany.

Uwagi:
- W razie konfliktu (dwie osoby/dwa zespoły zgłaszają PR na ten sam temat niemal równocześnie) decyduje kolejność zmergowania PR-a, nie utworzenia.
- Jeden PR = jeden zespół/jeden temat. Nie łączcie zapisu z innymi zmianami w repo.
- Zmiany w plikach innych niż `Projekty.txt` w ramach tego samego PR-a nie będą przyjmowane bez wcześniejszego uzgodnienia.

## Laboratoria

Materiały do poszczególnych laboratoriów będą dodawane na bieżąco w trakcie semestru.

## Projekt

Projekt rozwijacie w **tym samym forku**, w którym macie laboratorium — nie zakładajcie osobnego repozytorium, nie wysyłajcie zaproszeń do współpracy. Fork, który stworzyliście przy zapisie na temat (patrz sekcja wyżej), jest jedynym repozytorium projektu przez cały semestr.

### Struktura katalogów

W korzeniu forka, obok katalogu `lab/`, jest katalog `projekt/`:

```
po2026/
  lab/              (bez zmian — jak dotychczas)
  projekt/
    uml/            ← diagramy projektowe (patrz niżej)
    ...             ← reszta: wasz kod, dowolny system budowania
```

**Jedyny narzucony wymóg to podkatalog `projekt/uml/`.** Poza nim macie pełną dowolność — Maven, Gradle, Spring Boot, dowolna struktura pakietów — zgodnie z technologią, którą wybraliście dla swojego tematu.

### Diagramy UML — PlantUML, tekstowo

Diagramy projektowe (przypadki użycia, sekwencji, klas) piszecie jako kod w **PlantUML** (pliki `.puml`), nie rysujecie ich w narzędziu graficznym. Trafiają do repo jako zwykłe pliki tekstowe w `projekt/uml/` — bez eksportowania do PNG/SVG.

Dlaczego tekstowo: historia zmian w plikach `.puml` pokazuje, jak wasz projekt ewoluował — to dla nas (i dla was) czytelniejsze niż porównywanie obrazków, i jest dowodem, że diagramy powstawały w trakcie pracy nad projektem, a nie na końcu "bo wymagane".

**Jak podglądać diagram podczas pisania:**
- **IntelliJ IDEA** — zainstalujcie plugin **PlantUML Integration** (Settings → Plugins → wyszukajcie "PlantUML Integration"). Pokazuje podgląd `.puml` na żywo w osobnym panelu, bez opuszczania IDE.
- Alternatywnie: [plantuml.com/plantuml](https://www.plantuml.com/plantuml/uml/) — wklejcie kod, zobaczycie diagram w przeglądarce, bez instalacji.

Rekomendowane nazewnictwo plików (kolejność = kolejność, w jakiej powstają diagramy):
```
projekt/uml/
  01-przypadki-uzycia.puml
  02-sekwencja-<nazwa-przypadku-uzycia>.puml   (jeden plik na przypadek użycia)
  03-diagram-klas.puml
  04-diagram-obiektow.puml                      (opcjonalnie)
```
