package pl.agh.po.vpp.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.agh.po.vpp.data.EstateLoader;
import pl.agh.po.vpp.data.export.CsvExporter;
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

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Cały program bez {@code System.exit}, żeby dało się go przetestować.
     *
     * @return kod wyjścia: 0 sukces, 1 błąd danych (osiedle, zapis pliku), 2 błędne argumenty
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
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
            out.println("Uwaga: silnik zastępczy (stub) – wiatrak i magazyn energii nie pracują.");

            // Jedyne miejsce do podmiany, gdy vpp-engine dostarczy prawdziwą symulację.
            DaySimulator simulator = new StubDaySimulator();
            List<SimulationListener> listeners = List.of(new ConsoleProgressListener(out, timeline.size()));
            SimulationResult result = simulator.simulate(estate, timeline, settings,
                    new SyntheticWeatherProvider(options.seed()),
                    new SyntheticPriceProvider(options.seed()),
                    listeners);

            new CsvExporter().write(result, options.outputFile());
            log.info("Zapisano {} kroków do {}", result.steps().size(), options.outputFile());

            out.println(DaySummary.of(result).format());
            out.println("Zapisano: " + options.outputFile());
            return EXIT_OK;
        } catch (InvalidEstateException | UncheckedIOException e) {
            log.debug("Szczegóły błędu", e);
            err.println("Błąd: " + e.getMessage());
            return EXIT_DATA_ERROR;
        }
    }
}
