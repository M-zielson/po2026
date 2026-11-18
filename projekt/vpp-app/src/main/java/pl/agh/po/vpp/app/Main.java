package pl.agh.po.vpp.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.agh.po.vpp.data.EstateLoader;
import pl.agh.po.vpp.data.export.CsvExporter;
import pl.agh.po.vpp.data.metrics.MetricsCalculator;
import pl.agh.po.vpp.data.price.SyntheticPriceProvider;
import pl.agh.po.vpp.data.weather.SyntheticWeatherProvider;
import pl.agh.po.vpp.domain.EstateConfig;
import pl.agh.po.vpp.domain.InvalidEstateException;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationListener;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.Timeline;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Tryb konsolowy: liczy jedną dobę osiedla na danych syntetycznych i zapisuje wyniki do CSV.
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

            List<SimulationListener> listeners = List.of(new ConsoleProgressListener(out, timeline.size()));
            SimulationResult result = simulator.simulate(estate, timeline, settings,
                    new SyntheticWeatherProvider(options.seed()),
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
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Silnik przerwał dobę (np. niezgodny bilans, niepoprawne dane providera, nieobsługiwane
            // ustawienia). To błąd programu, nie danych użytkownika, więc pełny ślad idzie do logu.
            out.println(); // zamyka linię paska postępu, jeśli symulacja przerwała się w połowie
            log.error("Symulacja przerwana", e);
            err.println("Błąd symulacji: " + e.getMessage());
            return EXIT_SIMULATION_ERROR;
        }
    }
}
