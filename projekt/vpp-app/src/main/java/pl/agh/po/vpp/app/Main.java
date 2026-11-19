package pl.agh.po.vpp.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.agh.po.vpp.data.EstateLoader;
import pl.agh.po.vpp.data.export.CsvExporter;
import pl.agh.po.vpp.data.metrics.MetricsCalculator;
import pl.agh.po.vpp.data.price.SyntheticPriceProvider;
import pl.agh.po.vpp.data.weather.CachingWeatherProvider;
import pl.agh.po.vpp.data.weather.OpenMeteoWeatherProvider;
import pl.agh.po.vpp.data.weather.SyntheticWeatherProvider;
import pl.agh.po.vpp.domain.EstateConfig;
import pl.agh.po.vpp.domain.InvalidEstateException;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationListener;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.WeatherProvider;
import pl.agh.po.vpp.domain.WeatherUnavailableException;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Tryb konsolowy: liczy jedną dobę osiedla (pogoda z Open-Meteo albo syntetyczna, ceny syntetyczne)
 * i zapisuje wyniki do CSV.
 * Uruchomienie: {@code java -jar vpp-app/target/vpp-app.jar --date 2026-11-13}; opcje w {@link CliOptions#USAGE}.
 */
public final class Main {

    static final int EXIT_OK = 0;
    static final int EXIT_DATA_ERROR = 1;
    static final int EXIT_USAGE_ERROR = 2;
    static final int EXIT_SIMULATION_ERROR = 3;

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Cały program bez {@code System.exit}, żeby dało się go przetestować.
     *
     * @return kod wyjścia: 0 sukces, 1 błąd danych (osiedle, zapis pliku), 2 błędne argumenty,
     *         3 błąd symulacji w silniku
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, new EngineDaySimulator());
    }

    /** Jak {@link #run(String[], PrintStream, PrintStream)}, z podanym silnikiem (testy podstawiają własny). */
    static int run(String[] args, PrintStream out, PrintStream err, DaySimulator simulator) {
        if (Arrays.asList(args).contains("--help")) {
            out.println(CliOptions.USAGE);
            return EXIT_OK;
        }

        CliOptions options;
        try {
            options = CliOptions.parse(args, LocalDate.now());
        } catch (IllegalArgumentException e) {
            err.println("Błąd: " + e.getMessage());
            err.println(CliOptions.USAGE);
            return EXIT_USAGE_ERROR;
        }

        try {
            EstateConfig estate = new EstateLoader().load(options.estateFile());
            log.info("Wczytano osiedle '{}' z {}", estate.name(), options.estateFile());

            Timeline timeline = Timeline.of(options.date(), estate.location().zone());
            OperatorSettings settings = new OperatorSettings(null, null, List.of(), null, options.seed());

            out.printf("%s (%d elementów), %s, seed %d%n",
                    estate.name(), estate.assets().size(), options.date(), options.seed());
            out.println("Silnik M1: profile budynków i PV; wiatrak i magazyn energii nieaktywne do M2.");
            out.println("Pogoda: " + describe(options));

            List<SimulationListener> listeners = List.of(new ConsoleProgressListener(out, timeline.size()));
            SimulationResult result = simulator.simulate(estate, timeline, settings,
                    weatherProvider(options),
                    new SyntheticPriceProvider(options.seed()),
                    listeners);

            new CsvExporter().write(result, options.outputFile());
            log.info("Zapisano {} kroków do {}", result.steps().size(), options.outputFile());

            out.println(ConsoleMetricsFormatter.format(new MetricsCalculator().calculate(result)));
            out.println("Zapisano: " + options.outputFile());
            return EXIT_OK;
        } catch (InvalidEstateException | UncheckedIOException e) {
            log.debug("Szczegóły błędu", e);
            err.println("Błąd: " + e.getMessage());
            return EXIT_DATA_ERROR;
        } catch (WeatherUnavailableException e) {
            // Pogodę pobiera się przed pierwszym krokiem, więc pasek postępu jeszcze się nie zaczął.
            log.debug("Szczegóły błędu pogody", e);
            err.println("Błąd: " + e.getMessage());
            err.println("Użyj --weather synthetic albo dnia zapisanego w " + options.cacheDir()
                    + " (działa też bez internetu)");
            return EXIT_DATA_ERROR;
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Silnik przerwał dobę (np. niezgodny bilans, niepoprawne dane providera, nieobsługiwane
            // ustawienia). To błąd programu, nie danych użytkownika, więc pełny ślad idzie do logu.
            out.println(); // zamyka linię paska postępu, jeśli symulacja przerwała się w połowie
            log.error("Symulacja przerwana", e);
            err.println("Błąd symulacji: " + e.getMessage());
            return EXIT_SIMULATION_ERROR;
        }
    }

    /** Pogoda wybrana opcją {@code --weather}: Open-Meteo zawsze przez cache w plikach (dekorator). */
    static WeatherProvider weatherProvider(CliOptions options) {
        return switch (options.weather()) {
            case OPENMETEO -> new CachingWeatherProvider(new OpenMeteoWeatherProvider(), options.cacheDir());
            case SYNTHETIC -> new SyntheticWeatherProvider(options.seed());
        };
    }

    private static String describe(CliOptions options) {
        return switch (options.weather()) {
            case OPENMETEO -> "Open-Meteo (cache: " + options.cacheDir() + ")";
            case SYNTHETIC -> "syntetyczna (seed " + options.seed() + ")";
        };
    }
}
