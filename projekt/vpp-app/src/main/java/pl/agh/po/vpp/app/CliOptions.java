package pl.agh.po.vpp.app;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Opcje trybu konsolowego, wczytane z argumentów programu.
 *
 * @param date       dzień symulacji
 * @param seed       ziarno danych syntetycznych (ten sam seed = ten sam wynik)
 * @param estateFile plik opisu osiedla
 * @param outputFile plik wynikowy CSV
 */
public record CliOptions(LocalDate date, long seed, Path estateFile, Path outputFile) {

    public static final Path DEFAULT_ESTATE_FILE = Path.of("data", "estate.json");
    public static final long DEFAULT_SEED = 42L;

    public static final String USAGE = """
            Użycie: java -jar vpp-app.jar [opcje]
              --date RRRR-MM-DD   dzień symulacji (domyślnie dzisiaj)
              --seed LICZBA       ziarno danych syntetycznych (domyślnie 42)
              --estate PLIK       plik osiedla (domyślnie data/estate.json)
              --out PLIK          plik wynikowy CSV (domyślnie wynik-<data>.csv)
              --help              ta pomoc""";

    public CliOptions {
        if (date == null || estateFile == null || outputFile == null) {
            throw new IllegalArgumentException("Brak daty, pliku osiedla albo pliku wynikowego");
        }
    }

    /**
     * Wczytuje opcje z argumentów w postaci {@code --nazwa wartość}.
     *
     * @param today data przyjmowana, gdy brak {@code --date} (parametr, żeby testy nie zależały od zegara)
     * @throws IllegalArgumentException przy nieznanej opcji, brakującej albo błędnej wartości
     */
    public static CliOptions parse(String[] args, LocalDate today) {
        LocalDate date = today;
        long seed = DEFAULT_SEED;
        Path estateFile = DEFAULT_ESTATE_FILE;
        Path outputFile = null;

        for (int i = 0; i < args.length; i += 2) {
            String option = args[i];
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Brak wartości dla opcji " + option);
            }
            String value = args[i + 1];
            switch (option) {
                case "--date" -> date = parseDate(value);
                case "--seed" -> seed = parseSeed(value);
                case "--estate" -> estateFile = Path.of(value);
                case "--out" -> outputFile = Path.of(value);
                default -> throw new IllegalArgumentException("Nieznana opcja: " + option);
            }
        }

        if (outputFile == null) {
            outputFile = Path.of("wynik-" + date + ".csv");
        }
        return new CliOptions(date, seed, estateFile, outputFile);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Błędna data: " + value + " (oczekiwano RRRR-MM-DD)", e);
        }
    }

    private static long parseSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seed musi być liczbą całkowitą: " + value, e);
        }
    }
}
