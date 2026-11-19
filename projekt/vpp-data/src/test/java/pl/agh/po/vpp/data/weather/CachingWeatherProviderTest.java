package pl.agh.po.vpp.data.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherProvider;
import pl.agh.po.vpp.domain.WeatherUnavailableException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingWeatherProviderTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Location KRAKOW = new Location(50.0717, 20.0377, WARSAW);
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate PAST_DAY = LocalDate.of(2026, 10, 1);

    @TempDir
    Path cacheDir;

    /** Provider pod spodem: pogoda syntetyczna z licznikiem wywołań (zamiast Open-Meteo). */
    private static final class CountingProvider implements WeatherProvider {
        private final WeatherProvider real = new SyntheticWeatherProvider(42);
        int calls;

        @Override
        public List<Weather> weatherFor(Timeline timeline, Location location) {
            calls++;
            return real.weatherFor(timeline, location);
        }
    }

    /** Provider udający brak internetu. */
    private static final WeatherProvider OFFLINE = (timeline, location) -> {
        throw new WeatherUnavailableException("brak internetu");
    };

    private static Timeline day(LocalDate date) {
        return Timeline.of(date, WARSAW);
    }

    @Test
    void pierwszeZapytanieIdzieDalejIZapisujePlik() {
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, cacheDir, NOW);

        List<Weather> result = cache.weatherFor(day(PAST_DAY), KRAKOW);

        assertThat(inner.calls).isEqualTo(1);
        assertThat(result).hasSize(96);
        assertThat(cacheDir.resolve("50.0717_20.0377").resolve("2026-10-01.json")).isRegularFile();
    }

    @Test
    void drugieZapytanieCzytaZPlikuBezWolaniaProvidera() {
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, cacheDir, NOW);
        List<Weather> first = cache.weatherFor(day(PAST_DAY), KRAKOW);

        List<Weather> second = cache.weatherFor(day(PAST_DAY), KRAKOW);

        assertThat(inner.calls).isEqualTo(1);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void zapisanyDzienDzialaBezInternetu() {
        new CachingWeatherProvider(new CountingProvider(), cacheDir, NOW).weatherFor(day(PAST_DAY), KRAKOW);

        List<Weather> offline = new CachingWeatherProvider(OFFLINE, cacheDir, NOW).weatherFor(day(PAST_DAY), KRAKOW);

        assertThat(offline).hasSize(96);
    }

    @Test
    void niezapisanyDzienBezInternetuToBladProvidera() {
        CachingWeatherProvider cache = new CachingWeatherProvider(OFFLINE, cacheDir, NOW);

        assertThatThrownBy(() -> cache.weatherFor(day(PAST_DAY), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("brak internetu");
    }

    @Test
    void rozneDniIMiejscaToOsobnePliki() {
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, cacheDir, NOW);
        Location gdansk = new Location(54.3520, 18.6466, WARSAW);

        cache.weatherFor(day(PAST_DAY), KRAKOW);
        cache.weatherFor(day(PAST_DAY.plusDays(1)), KRAKOW);
        cache.weatherFor(day(PAST_DAY), gdansk);

        assertThat(inner.calls).isEqualTo(3);
        assertThat(cacheDir.resolve("50.0717_20.0377").resolve("2026-10-02.json")).isRegularFile();
        assertThat(cacheDir.resolve("54.3520_18.6466").resolve("2026-10-01.json")).isRegularFile();
    }

    @Test
    void dzisiejszejIPrzyszlejDobyNieZapisuje() {
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, cacheDir, NOW);

        cache.weatherFor(day(LocalDate.of(2026, 11, 18)), KRAKOW);
        cache.weatherFor(day(LocalDate.of(2026, 11, 18)), KRAKOW);
        cache.weatherFor(day(LocalDate.of(2026, 11, 20)), KRAKOW);

        assertThat(inner.calls).isEqualTo(3);
        assertThat(cacheDir.resolve("50.0717_20.0377").resolve("2026-11-18.json")).doesNotExist();
        assertThat(cacheDir.resolve("50.0717_20.0377").resolve("2026-11-20.json")).doesNotExist();
    }

    @Test
    void uszkodzonyPlikOznaczaPonownePobranie() throws IOException {
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, cacheDir, NOW);
        Path file = cache.fileFor(day(PAST_DAY), KRAKOW);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ to nie jest poprawny JSON");

        List<Weather> result = cache.weatherFor(day(PAST_DAY), KRAKOW);

        assertThat(inner.calls).isEqualTo(1);
        assertThat(result).hasSize(96);
        assertThat(Files.readString(file)).contains("\"weather\"");
    }

    @Test
    void plikZInnymKrokiemJestIgnorowany() {
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, cacheDir, NOW);
        Timeline hourly = new Timeline(PAST_DAY, Duration.ofHours(1), WARSAW);
        cache.weatherFor(day(PAST_DAY), KRAKOW);

        List<Weather> result = cache.weatherFor(hourly, KRAKOW);

        assertThat(inner.calls).isEqualTo(2);
        assertThat(result).hasSize(24);
    }

    @Test
    void bladZapisuNiePsujeWyniku() throws IOException {
        Path notADirectory = Files.writeString(cacheDir.resolve("plik.txt"), "zajęte");
        CountingProvider inner = new CountingProvider();
        CachingWeatherProvider cache = new CachingWeatherProvider(inner, notADirectory, NOW);

        List<Weather> result = cache.weatherFor(day(PAST_DAY), KRAKOW);

        assertThat(result).hasSize(96);
    }

    @Test
    void nieZostawiaPlikowTymczasowych() throws IOException {
        new CachingWeatherProvider(new CountingProvider(), cacheDir, NOW).weatherFor(day(PAST_DAY), KRAKOW);

        try (var files = Files.list(cacheDir.resolve("50.0717_20.0377"))) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("2026-10-01.json");
        }
    }
}
