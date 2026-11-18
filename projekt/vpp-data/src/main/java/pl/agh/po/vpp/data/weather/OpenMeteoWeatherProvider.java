package pl.agh.po.vpp.data.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherProvider;
import pl.agh.po.vpp.domain.WeatherUnavailableException;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prawdziwa pogoda z Open-Meteo (bez klucza, do użytku niekomercyjnego).
 * <ul>
 *   <li>Forecast API – od ok. {@value #FORECAST_PAST_DAYS} dni wstecz do {@value #FORECAST_FUTURE_DAYS}
 *       dni naprzód, dane co 15 minut,</li>
 *   <li>Historical Weather API – starsze dni, dane co godzinę.</li>
 * </ul>
 * Dane pobieramy w UTC dla wszystkich dni UTC, które obejmuje lokalna doba, i dopasowujemy do kroków
 * {@link Timeline}. Promieniowanie w Open-Meteo to średnia z okresu <b>kończącego się</b> o podanej
 * godzinie, więc krok zaczynający się o 12:00 dostaje wartość z 12:15 (albo z 13:00 przy danych
 * godzinowych). Temperatura, wiatr i zachmurzenie to wartości chwilowe – przy danych godzinowych
 * interpolujemy je liniowo między pełnymi godzinami.
 * Każdy problem kończy się {@link WeatherUnavailableException} z czytelnym komunikatem.
 */
public class OpenMeteoWeatherProvider implements WeatherProvider {

    static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    static final String ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
    /** Forecast API przyjmuje ok. 93 dni wstecz; bierzemy dzień zapasu. */
    static final int FORECAST_PAST_DAYS = 92;
    static final int FORECAST_FUTURE_DAYS = 15;

    /** Źródło danych Open-Meteo: adres, nazwa bloku z danymi w odpowiedzi i jego rozdzielczość. */
    enum Source {
        FORECAST(FORECAST_URL, "minutely_15", Duration.ofMinutes(15)),
        ARCHIVE(ARCHIVE_URL, "hourly", Duration.ofHours(1));

        final String url;
        final String block;
        final Duration resolution;

        Source(String url, String block, Duration resolution) {
            this.url = url;
            this.block = block;
            this.resolution = resolution;
        }
    }

    /** Wielkości pobierane z Open-Meteo (nazwy parametrów API). */
    private enum Variable {
        IRRADIANCE("global_tilted_irradiance"),
        WIND("wind_speed_80m"),
        TEMPERATURE("temperature_2m"),
        CLOUD("cloud_cover");

        final String apiName;

        Variable(String apiName) {
            this.apiName = apiName;
        }
    }

    private final HttpFetcher fetcher;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenMeteoWeatherProvider() {
        this(new JdkHttpFetcher(), Clock.systemUTC());
    }

    /**
     * @param fetcher pobieranie HTTP (testy podstawiają zapisane odpowiedzi)
     * @param clock   zegar wyznaczający „dziś” przy wyborze źródła danych
     */
    public OpenMeteoWeatherProvider(HttpFetcher fetcher, Clock clock) {
        if (fetcher == null || clock == null) {
            throw new IllegalArgumentException("Wymagane pobieranie HTTP i zegar");
        }
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Override
    public List<Weather> weatherFor(Timeline timeline, Location location) {
        LocalDate firstUtcDay = timeline.start().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastUtcDay = timeline.end().atZone(ZoneOffset.UTC).toLocalDate();
        Source source = sourceFor(timeline.date(), firstUtcDay, lastUtcDay);

        URI uri = requestUri(source, location, firstUtcDay, lastUtcDay);
        Series series = parse(fetch(uri), source);

        List<Weather> result = new ArrayList<>(timeline.size());
        for (Instant time : timeline.steps()) {
            result.add(series.weatherAt(time));
        }
        return List.copyOf(result);
    }

    /** Forecast API dla ostatnich ~3 miesięcy i najbliższych 2 tygodni, Historical API dla starszych dni. */
    Source sourceFor(LocalDate day, LocalDate firstUtcDay, LocalDate lastUtcDay) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (lastUtcDay.isAfter(today.plusDays(FORECAST_FUTURE_DAYS))) {
            throw new WeatherUnavailableException("Brak pogody dla " + day + ": prognoza Open-Meteo sięga najwyżej "
                    + FORECAST_FUTURE_DAYS + " dni naprzód (do " + today.plusDays(FORECAST_FUTURE_DAYS) + ")");
        }
        return firstUtcDay.isBefore(today.minusDays(FORECAST_PAST_DAYS)) ? Source.ARCHIVE : Source.FORECAST;
    }

    static URI requestUri(Source source, Location location, LocalDate firstUtcDay, LocalDate lastUtcDay) {
        String variables = Arrays.stream(Variable.values()).map(v -> v.apiName).collect(Collectors.joining(","));
        return URI.create(String.format(Locale.ROOT,
                "%s?latitude=%.4f&longitude=%.4f&%s=%s&tilt=%.0f&azimuth=0"
                        + "&wind_speed_unit=ms&timezone=GMT&start_date=%s&end_date=%s",
                source.url, location.latitude(), location.longitude(), source.block, variables,
                SyntheticWeatherProvider.PANEL_TILT_DEG, firstUtcDay, lastUtcDay));
    }

    private HttpFetcher.HttpResult fetch(URI uri) {
        try {
            return fetcher.get(uri);
        } catch (IOException e) {
            throw new WeatherUnavailableException(
                    "Nie udało się pobrać pogody z Open-Meteo (" + uri.getHost() + "): " + e.getMessage(), e);
        }
    }

    private Series parse(HttpFetcher.HttpResult response, Source source) {
        JsonNode root;
        try {
            root = mapper.readTree(response.body());
        } catch (JsonProcessingException e) {
            String why = response.isOk() ? "niepoprawną odpowiedź" : "kod HTTP " + response.statusCode();
            throw new WeatherUnavailableException("Open-Meteo zwróciło " + why, e);
        }
        if (root.path("error").asBoolean(false)) {
            throw new WeatherUnavailableException(
                    "Open-Meteo odrzuciło zapytanie: " + root.path("reason").asText("brak opisu"));
        }
        if (!response.isOk()) {
            throw new WeatherUnavailableException("Open-Meteo odpowiedziało kodem HTTP " + response.statusCode());
        }
        JsonNode block = root.path(source.block);
        if (!block.path("time").isArray()) {
            throw new WeatherUnavailableException("Odpowiedź Open-Meteo nie zawiera danych '" + source.block + "'");
        }
        return Series.from(block, source.resolution);
    }

    /** Szereg czasowy z odpowiedzi: wartości każdej wielkości dla kolejnych chwil UTC. */
    private static final class Series {

        private final Duration resolution;
        private final Map<Instant, Integer> index = new HashMap<>();
        private final Map<Variable, double[]> values = new HashMap<>();

        private Series(Duration resolution) {
            this.resolution = resolution;
        }

        static Series from(JsonNode block, Duration resolution) {
            Series series = new Series(resolution);
            JsonNode times = block.get("time");
            for (int i = 0; i < times.size(); i++) {
                series.index.put(LocalDateTime.parse(times.get(i).asText()).toInstant(ZoneOffset.UTC), i);
            }
            for (Variable variable : Variable.values()) {
                JsonNode array = block.path(variable.apiName);
                if (!array.isArray() || array.size() != times.size()) {
                    throw new WeatherUnavailableException("Odpowiedź Open-Meteo nie zawiera wielkości " + variable.apiName);
                }
                double[] data = new double[times.size()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = array.get(i).isNumber() ? array.get(i).asDouble() : Double.NaN;
                }
                series.values.put(variable, data);
            }
            return series;
        }

        Weather weatherAt(Instant time) {
            long resolutionSeconds = resolution.toSeconds();
            long epochSecond = time.getEpochSecond();
            Instant periodStart = Instant.ofEpochSecond(epochSecond - Math.floorMod(epochSecond, resolutionSeconds));
            Instant periodEnd = periodStart.plus(resolution);
            double fraction = (double) Duration.between(periodStart, time).toSeconds() / resolutionSeconds;

            double irradiance = value(Variable.IRRADIANCE, periodEnd);
            double wind = instantaneous(Variable.WIND, periodStart, periodEnd, fraction);
            double temperature = instantaneous(Variable.TEMPERATURE, periodStart, periodEnd, fraction);
            double cloud = instantaneous(Variable.CLOUD, periodStart, periodEnd, fraction);
            try {
                return new Weather(time, irradiance, wind, temperature, cloud);
            } catch (IllegalArgumentException e) {
                throw new WeatherUnavailableException("Open-Meteo zwróciło wartości poza zakresem dla " + time, e);
            }
        }

        /** Wartość chwilowa: dokładna na granicy okresu, w środku – interpolacja liniowa. */
        private double instantaneous(Variable variable, Instant start, Instant end, double fraction) {
            double atStart = value(variable, start);
            if (fraction == 0) {
                return atStart;
            }
            return atStart + (value(variable, end) - atStart) * fraction;
        }

        private double value(Variable variable, Instant time) {
            Integer i = index.get(time);
            double result = i == null ? Double.NaN : values.get(variable)[i];
            if (Double.isNaN(result)) {
                throw new WeatherUnavailableException(
                        "Brak wartości " + variable.apiName + " dla " + time + " w odpowiedzi Open-Meteo");
            }
            return result;
        }
    }
}
