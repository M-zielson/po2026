package pl.agh.po.vpp.data.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.data.weather.HttpFetcher.HttpResult;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Testy na prawdziwych odpowiedziach Open-Meteo zapisanych w src/test/resources/open-meteo
 * (nagranych 18.11.2026) – bez łączenia się z internetem.
 */
class OpenMeteoWeatherProviderTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Location KRAKOW = new Location(50.0717, 20.0377, WARSAW);
    /** „Dziś” dla testów: od niego zależy wybór Forecast / Historical API. */
    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC);
    private static final double EPS = 1e-9;

    private final List<URI> requested = new ArrayList<>();

    private static String fixture(String name) {
        try (InputStream in = OpenMeteoWeatherProviderTest.class.getResourceAsStream("/open-meteo/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Brak pliku testowego " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Provider, który zamiast internetu dostaje podaną odpowiedź i zapamiętuje adresy zapytań. */
    private OpenMeteoWeatherProvider providerReturning(int status, String body) {
        return new OpenMeteoWeatherProvider(uri -> {
            requested.add(uri);
            return new HttpResult(status, body);
        }, TODAY);
    }

    private List<Weather> weather(String fixture, LocalDate date) {
        return providerReturning(200, fixture(fixture)).weatherFor(Timeline.of(date, WARSAW), KRAKOW);
    }

    // ---------- Forecast API: dane co 15 minut ----------

    @Test
    void prognozaDajeJedenWpisNaKazdyKrok() {
        Timeline timeline = Timeline.of(LocalDate.of(2026, 10, 1), WARSAW);

        List<Weather> result = providerReturning(200, fixture("forecast-2026-10-01.json")).weatherFor(timeline, KRAKOW);

        assertThat(result).hasSize(96);
        assertThat(result).extracting(Weather::time).containsExactlyElementsOf(timeline.steps());
    }

    @Test
    void promieniowanieJestZOkresuKonczacegoSieNaKoniecKroku() {
        // krok 12:00 czasu polskiego = 10:00 UTC; promieniowanie z 10:15 (średnia 10:00–10:15),
        // wielkości chwilowe z 10:00
        Weather noon = weather("forecast-2026-10-01.json", LocalDate.of(2026, 10, 1)).get(48);

        assertThat(noon.time()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(noon.tiltedIrradianceWm2()).isCloseTo(254.2, within(EPS));
        assertThat(noon.windSpeed80mMs()).isCloseTo(2.05, within(EPS));
        assertThat(noon.temperatureC()).isCloseTo(16.4, within(EPS));
        assertThat(noon.cloudCoverPct()).isCloseTo(100, within(EPS));
    }

    @Test
    void lokalnaDobaZaczynaSieDniaWczesniejWUtc() {
        List<Weather> result = weather("forecast-2026-10-01.json", LocalDate.of(2026, 10, 1));

        // północ czasu letniego = 22:00 UTC poprzedniego dnia
        assertThat(result.getFirst().time()).isEqualTo(Instant.parse("2026-09-30T22:00:00Z"));
        assertThat(result.getFirst().windSpeed80mMs()).isCloseTo(8.97, within(EPS));
        assertThat(result.getLast().time()).isEqualTo(Instant.parse("2026-10-01T21:45:00Z"));
        assertThat(result.getLast().temperatureC()).isCloseTo(12.0, within(EPS));
    }

    @Test
    void zapytanieDoForecastApi() {
        weather("forecast-2026-10-01.json", LocalDate.of(2026, 10, 1));

        assertThat(requested).hasSize(1);
        assertThat(requested.getFirst().toString())
                .startsWith("https://api.open-meteo.com/v1/forecast?")
                .contains("latitude=50.0717", "longitude=20.0377",
                        "minutely_15=global_tilted_irradiance,wind_speed_80m,temperature_2m,cloud_cover",
                        "tilt=35", "azimuth=0", "wind_speed_unit=ms", "timezone=GMT",
                        "start_date=2026-09-30", "end_date=2026-10-01");
    }

    @Test
    void zimaCzasUtcPrzesunietyOGodzine() {
        List<Weather> result = weather("forecast-2026-11-15.json", LocalDate.of(2026, 11, 15));

        assertThat(result).hasSize(96);
        assertThat(result.getFirst().time()).isEqualTo(Instant.parse("2026-11-14T23:00:00Z"));
        assertThat(requested.getFirst().toString()).contains("start_date=2026-11-14", "end_date=2026-11-15");
    }

    // ---------- Historical API: dane co godzinę ----------

    @Test
    void starszyDzienIdzieDoHistoricalApi() {
        weather("archive-2025-06-21.json", LocalDate.of(2025, 6, 21));

        assertThat(requested.getFirst().toString())
                .startsWith("https://archive-api.open-meteo.com/v1/archive?")
                .contains("hourly=global_tilted_irradiance", "start_date=2025-06-20", "end_date=2025-06-21");
    }

    @Test
    void daneGodzinoweNaPelnejGodzinie() {
        // krok 12:00 CEST = 10:00 UTC: promieniowanie ze średniej 10:00–11:00 (wpis z 11:00)
        Weather noon = weather("archive-2025-06-21.json", LocalDate.of(2025, 6, 21)).get(48);

        assertThat(noon.tiltedIrradianceWm2()).isCloseTo(978.8, within(EPS));
        assertThat(noon.windSpeed80mMs()).isCloseTo(1.02, within(EPS));
        assertThat(noon.temperatureC()).isCloseTo(21.1, within(EPS));
        assertThat(noon.cloudCoverPct()).isCloseTo(61, within(EPS));
    }

    @Test
    void daneGodzinoweInterpolowaneWSrodkuGodziny() {
        // krok 12:30 CEST = 10:30 UTC: w połowie między 10:00 i 11:00
        Weather halfPast = weather("archive-2025-06-21.json", LocalDate.of(2025, 6, 21)).get(50);

        assertThat(halfPast.tiltedIrradianceWm2()).isCloseTo(978.8, within(EPS));
        assertThat(halfPast.windSpeed80mMs()).isCloseTo((1.02 + 0.46) / 2, within(EPS));
        assertThat(halfPast.temperatureC()).isCloseTo((21.1 + 21.9) / 2, within(EPS));
        assertThat(halfPast.cloudCoverPct()).isCloseTo((61 + 0) / 2.0, within(EPS));
    }

    @ParameterizedTest(name = "{0}: {1} kroków")
    @CsvSource({"2025-03-30, archive-2025-03-30.json, 92", "2025-10-26, archive-2025-10-26.json, 100"})
    void dniZmianyCzasu(LocalDate date, String fixture, int steps) {
        Timeline timeline = Timeline.of(date, WARSAW);

        List<Weather> result = providerReturning(200, fixture(fixture)).weatherFor(timeline, KRAKOW);

        assertThat(result).hasSize(steps);
        assertThat(result).extracting(Weather::time).containsExactlyElementsOf(timeline.steps());
    }

    // ---------- wybór źródła ----------

    @Test
    void wyborZrodlaZaleznieOdDaty() {
        OpenMeteoWeatherProvider provider = providerReturning(200, "{}");
        LocalDate today = LocalDate.of(2026, 11, 18);

        assertThat(provider.sourceFor(today, today, today)).isEqualTo(OpenMeteoWeatherProvider.Source.FORECAST);
        assertThat(provider.sourceFor(today, today.minusDays(92), today))
                .isEqualTo(OpenMeteoWeatherProvider.Source.FORECAST);
        assertThat(provider.sourceFor(today, today.minusDays(93), today))
                .isEqualTo(OpenMeteoWeatherProvider.Source.ARCHIVE);
        assertThat(provider.sourceFor(today, today, today.plusDays(15)))
                .isEqualTo(OpenMeteoWeatherProvider.Source.FORECAST);
    }

    // ---------- błędy ----------

    @Test
    void zbytOdleglaPrzyszloscToBladBezZapytania() {
        OpenMeteoWeatherProvider provider = providerReturning(200, "{}");

        assertThatThrownBy(() -> provider.weatherFor(Timeline.of(LocalDate.of(2026, 12, 10), WARSAW), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("2026-12-10")
                .hasMessageContaining("15 dni");
        assertThat(requested).isEmpty();
    }

    @Test
    void bladOpisanyPrzezOpenMeteoTrafiaDoKomunikatu() {
        OpenMeteoWeatherProvider provider = providerReturning(400, fixture("error-out-of-range.json"));

        assertThatThrownBy(() -> provider.weatherFor(Timeline.of(LocalDate.of(2026, 10, 1), WARSAW), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("out of allowed range");
    }

    @Test
    void brakInternetuToBladZNazwaSerwera() {
        OpenMeteoWeatherProvider provider = new OpenMeteoWeatherProvider(uri -> {
            throw new IOException("Connection refused");
        }, TODAY);

        assertThatThrownBy(() -> provider.weatherFor(Timeline.of(LocalDate.of(2026, 10, 1), WARSAW), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("api.open-meteo.com")
                .hasMessageContaining("Connection refused");
    }

    @Test
    void bladSerweraBezJsonToBladZKodemHttp() {
        OpenMeteoWeatherProvider provider = providerReturning(503, "<html>Service Unavailable</html>");

        assertThatThrownBy(() -> provider.weatherFor(Timeline.of(LocalDate.of(2026, 10, 1), WARSAW), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("503");
    }

    @Test
    void brakujacaWartoscToBladZNazwaWielkosciICzasem() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(fixture("forecast-2026-10-01.json"));
        ObjectNode block = (ObjectNode) root.get("minutely_15");
        int index = 0;
        while (!block.get("time").get(index).asText().equals("2026-10-01T10:00")) {
            index++;
        }
        ((ArrayNode) block.get("cloud_cover")).set(index, mapper.nullNode());
        OpenMeteoWeatherProvider provider = providerReturning(200, mapper.writeValueAsString(root));

        assertThatThrownBy(() -> provider.weatherFor(Timeline.of(LocalDate.of(2026, 10, 1), WARSAW), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("cloud_cover")
                .hasMessageContaining("2026-10-01T10:00:00Z");
    }

    @Test
    void odpowiedzBezDanychToBlad() {
        OpenMeteoWeatherProvider provider = providerReturning(200, "{\"latitude\": 50.07}");

        assertThatThrownBy(() -> provider.weatherFor(Timeline.of(LocalDate.of(2026, 10, 1), WARSAW), KRAKOW))
                .isInstanceOf(WeatherUnavailableException.class)
                .hasMessageContaining("minutely_15");
    }
}
