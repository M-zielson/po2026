package pl.agh.po.vpp.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Testy całego trybu konsolowego: od argumentów do pliku CSV, bez wyłączania JVM. */
class MainTest {

    /** Testy uruchamiają się w katalogu modułu vpp-app, plik osiedla leży w projekt/data. */
    private static final String ESTATE = Path.of("..", "data", "estate.json").toString();

    private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args,
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8),
                new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return outBuffer.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBuffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void liczyDobeIZapisujeCsv(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("wynik.csv");

        int exitCode = run("--date", "2026-11-13", "--seed", "42", "--estate", ESTATE, "--out", csv.toString());

        assertThat(exitCode).isEqualTo(Main.EXIT_OK);
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1 + 96);
        assertThat(out()).contains("Osiedle Słoneczne", "96/96 kroków", "Import:", "Zapisano: " + csv);
        assertThat(err()).doesNotContain("Błąd");
    }

    @Test
    void tenSamSeedDajeTenSamPlik(@TempDir Path dir) throws IOException {
        Path first = dir.resolve("a.csv");
        Path second = dir.resolve("b.csv");

        run("--date", "2026-06-21", "--seed", "7", "--estate", ESTATE, "--out", first.toString());
        run("--date", "2026-06-21", "--seed", "7", "--estate", ESTATE, "--out", second.toString());

        assertThat(Files.readAllLines(first)).isEqualTo(Files.readAllLines(second));
    }

    @Test
    void brakPlikuOsiedlaToBladDanych(@TempDir Path dir) {
        int exitCode = run("--estate", "nie-ma-takiego.json", "--out", dir.resolve("w.csv").toString());

        assertThat(exitCode).isEqualTo(Main.EXIT_DATA_ERROR);
        assertThat(err()).contains("Błąd", "nie-ma-takiego.json");
    }

    @Test
    void nieDaSieZapisacPlikuToBladDanych(@TempDir Path dir) {
        Path csv = dir.resolve("brak-katalogu").resolve("w.csv");

        int exitCode = run("--estate", ESTATE, "--out", csv.toString());

        assertThat(exitCode).isEqualTo(Main.EXIT_DATA_ERROR);
        assertThat(err()).contains("brak-katalogu");
    }

    @Test
    void zlyArgumentToBladUzyciaZPomoca() {
        int exitCode = run("--date", "wczoraj");

        assertThat(exitCode).isEqualTo(Main.EXIT_USAGE_ERROR);
        assertThat(err()).contains("wczoraj", "Użycie:");
    }

    @Test
    void helpWypisujePomoc() {
        assertThat(run("--help")).isEqualTo(Main.EXIT_OK);
        assertThat(out()).contains("Użycie:", "--date", "--seed", "--estate", "--out");
    }
}
