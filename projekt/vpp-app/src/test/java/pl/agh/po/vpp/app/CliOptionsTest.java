package pl.agh.po.vpp.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliOptionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private static CliOptions parse(String... args) {
        return CliOptions.parse(args, TODAY);
    }

    @Test
    void bezArgumentowWartosciDomyslne() {
        CliOptions options = parse();

        assertThat(options.date()).isEqualTo(TODAY);
        assertThat(options.seed()).isEqualTo(42);
        assertThat(options.estateFile()).isEqualTo(Path.of("data", "estate.json"));
        assertThat(options.outputFile()).isEqualTo(Path.of("wynik-2026-11-13.csv"));
    }

    @Test
    void wszystkieOpcje() {
        CliOptions options = parse("--date", "2026-06-21", "--seed", "7",
                "--estate", "inne.json", "--out", "lato.csv");

        assertThat(options.date()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(options.seed()).isEqualTo(7);
        assertThat(options.estateFile()).isEqualTo(Path.of("inne.json"));
        assertThat(options.outputFile()).isEqualTo(Path.of("lato.csv"));
    }

    @Test
    void domyslnaNazwaPlikuWynikowegoZawieraPodanaDate() {
        assertThat(parse("--date", "2026-06-21").outputFile()).isEqualTo(Path.of("wynik-2026-06-21.csv"));
    }

    @Test
    void bladnaDataToBlad() {
        assertThatThrownBy(() -> parse("--date", "13.11.2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("13.11.2026")
                .hasMessageContaining("RRRR-MM-DD");
    }

    @Test
    void seedMusiBycLiczba() {
        assertThatThrownBy(() -> parse("--seed", "abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void nieznanaOpcjaToBlad() {
        assertThatThrownBy(() -> parse("--dzien", "2026-11-13"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--dzien");
    }

    @Test
    void opcjaBezWartosciToBlad() {
        assertThatThrownBy(() -> parse("--date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--date");
    }
}
