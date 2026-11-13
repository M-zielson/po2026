package pl.agh.po.vpp.data.export;

import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Weather;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Zapisuje wyniki symulacji doby do CSV: nagłówek i jeden wiersz na krok.
 * Format standardowy: separator przecinek, kropka dziesiętna, UTF-8, czas lokalny z przesunięciem strefy
 * (np. {@code 2026-11-13T17:45+01:00}), żeby powtórzona godzina przy zmianie czasu była jednoznaczna.
 */
public class CsvExporter {

    /** Kolumny w kolejności zapisu; nazwy jak w kodzie, z jednostką w nazwie. */
    public static final List<String> COLUMNS = List.of(
            "time",
            "production_kw", "consumption_kw",
            "battery_kw", "grid_kw",
            "import_kw", "export_kw",
            "battery_soc_kwh",
            "price_pln_per_kwh",
            "irradiance_wm2", "wind_ms", "temperature_c", "cloud_pct");

    private static final String SEPARATOR = ",";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mmxxx");
    /** Wartości mniejsze co do modułu zapisujemy jako 0, żeby nie pojawiało się "-0.000". */
    private static final double ZERO_THRESHOLD = 0.0005;

    /** Zapisuje wyniki do pliku (nadpisuje istniejący). */
    public void write(SimulationResult result, Path file) {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeTo(result, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Nie można zapisać pliku CSV: " + file, e);
        }
    }

    /** Zapisuje wyniki do dowolnego strumienia znaków (np. odpowiedzi HTTP). Nie zamyka go. */
    public void write(SimulationResult result, Writer out) {
        try {
            writeTo(result, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Nie można zapisać danych CSV", e);
        }
    }

    private void writeTo(SimulationResult result, Writer out) throws IOException {
        ZoneId zone = result.timeline().zone();
        out.write(String.join(SEPARATOR, COLUMNS));
        out.write('\n');
        for (StepResult step : result.steps()) {
            out.write(row(step, zone));
            out.write('\n');
        }
        out.flush();
    }

    private static String row(StepResult step, ZoneId zone) {
        Weather weather = step.conditions().weather();
        return String.join(SEPARATOR,
                TIME_FORMAT.format(step.time().atZone(zone)),
                number(step.productionKw()),
                number(step.consumptionKw()),
                number(step.batteryKw()),
                number(step.gridKw()),
                number(step.importKw()),
                number(step.exportKw()),
                number(step.batterySocKwh()),
                number(step.conditions().pricePlnPerKwh()),
                number(weather.tiltedIrradianceWm2()),
                number(weather.windSpeed80mMs()),
                number(weather.temperatureC()),
                number(weather.cloudCoverPct()));
    }

    /**
     * Liczba z kropką dziesiętną i 3 miejscami po przecinku (dla kW to dokładność 1 W).
     * Locale.ROOT jest konieczne: domyślne polskie ustawienia dałyby przecinek, który rozbiłby kolumny.
     */
    private static String number(double value) {
        double printed = Math.abs(value) < ZERO_THRESHOLD ? 0 : value;
        return String.format(Locale.ROOT, "%.3f", printed);
    }
}
