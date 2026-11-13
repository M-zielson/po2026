package pl.agh.po.vpp.data.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CsvExporterTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);

    private final CsvExporter exporter = new CsvExporter();

    /**
     * Krok z zachowanym bilansem: produkcja 50, zużycie 30, bateria ładuje 15, więc eksport 5 kW
     * (productionKw + gridKw = consumptionKw + batteryKw).
     */
    private static StepResult step(Instant time) {
        Weather weather = new Weather(time, 412.5, 6.25, 8.5, 30);
        Conditions conditions = new Conditions(time, weather, 0.615);
        return new StepResult(time, Timeline.DEFAULT_STEP, conditions, List.of(), 50, 30, 15, -5, 120.125);
    }

    private static SimulationResult resultFor(LocalDate date) {
        Timeline timeline = Timeline.of(date, WARSAW);
        List<StepResult> steps = timeline.steps().stream().map(CsvExporterTest::step).toList();
        return new SimulationResult(timeline, OperatorSettings.defaults(), steps);
    }

    private List<String> csvLines(SimulationResult result) {
        StringWriter out = new StringWriter();
        exporter.write(result, out);
        return out.toString().lines().toList();
    }

    @Test
    void pierwszaLiniaToNaglowek() {
        List<String> lines = csvLines(resultFor(DAY));

        assertThat(lines.getFirst()).isEqualTo(
                "time,production_kw,consumption_kw,battery_kw,grid_kw,import_kw,export_kw,battery_soc_kwh,"
                        + "price_pln_per_kwh,irradiance_wm2,wind_ms,temperature_c,cloud_pct");
    }

    @Test
    void jedenWierszNaKazdyKrok() {
        assertThat(csvLines(resultFor(DAY))).hasSize(1 + 96);
    }

    @Test
    void wierszZawieraWartosciKroku() {
        String row = csvLines(resultFor(DAY)).get(1);

        assertThat(row).isEqualTo(
                "2026-11-13T00:00+01:00,50.000,30.000,15.000,-5.000,0.000,5.000,120.125,"
                        + "0.615,412.500,6.250,8.500,30.000");
    }

    @Test
    void wartosciOdczytaneZPowrotemZgadzajaSieZWynikiem() {
        SimulationResult result = resultFor(DAY);
        String[] fields = csvLines(result).get(1).split(",");
        StepResult first = result.steps().getFirst();

        assertThat(fields).hasSize(CsvExporter.COLUMNS.size());
        assertThat(Double.parseDouble(fields[1])).isCloseTo(first.productionKw(), within(0.001));
        assertThat(Double.parseDouble(fields[4])).isCloseTo(first.gridKw(), within(0.001));
        assertThat(Double.parseDouble(fields[7])).isCloseTo(first.batterySocKwh(), within(0.001));
    }

    @Test
    void wDniuZmianyCzasuKazdyCzasJestJednoznaczny() {
        List<String> rows = csvLines(resultFor(LocalDate.of(2026, 10, 25))).stream().skip(1).toList();
        List<String> times = rows.stream().map(row -> row.split(",")[0]).toList();

        assertThat(rows).hasSize(100);
        assertThat(times).doesNotHaveDuplicates();
        assertThat(times).contains("2026-10-25T02:15+02:00", "2026-10-25T02:15+01:00");
    }

    @Test
    void polskieUstawieniaJavyNieZmieniajaFormatuLiczb() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("pl", "PL"));

            List<String> lines = csvLines(resultFor(DAY));

            assertThat(lines).allSatisfy(line ->
                    assertThat(line.split(",")).hasSize(CsvExporter.COLUMNS.size()));
            assertThat(lines.get(1)).contains("50.000");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void niemalZeroNieJestZapisywaneJakoMinusZero() {
        Instant time = Timeline.of(DAY, WARSAW).start();
        Conditions conditions = new Conditions(time, new Weather(time, 0, 0, 0, 0), 0.5);
        StepResult step = new StepResult(time, Timeline.DEFAULT_STEP, conditions, List.of(), 0, 0, 0, -0.0001, 0);
        SimulationResult result = new SimulationResult(Timeline.of(DAY, WARSAW), OperatorSettings.defaults(), List.of(step));

        assertThat(csvLines(result).get(1)).doesNotContain("-0.000");
    }

    @Test
    void pustaSymulacjaDajeSamNaglowek() {
        SimulationResult empty = new SimulationResult(Timeline.of(DAY, WARSAW), OperatorSettings.defaults(), List.of());

        assertThat(csvLines(empty)).hasSize(1);
    }

    @Test
    void zapisDoPliku(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("wynik.csv");

        exporter.write(resultFor(DAY), file);

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1 + 96);
        assertThat(lines.getFirst()).startsWith("time,");
    }

    @Test
    void zlaSciezkaDajeBladZeSciezka(@TempDir Path dir) {
        Path file = dir.resolve("nie-ma-takiego-katalogu").resolve("wynik.csv");

        assertThatThrownBy(() -> exporter.write(resultFor(DAY), file))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("nie-ma-takiego-katalogu");
    }
}
