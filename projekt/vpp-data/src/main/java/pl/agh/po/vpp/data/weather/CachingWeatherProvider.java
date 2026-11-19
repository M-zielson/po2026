package pl.agh.po.vpp.data.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Pogoda z pamięcią w plikach (wzorzec Decorator): sam jest {@link WeatherProvider} i opakowuje
 * inny {@link WeatherProvider}, np. {@link OpenMeteoWeatherProvider}.
 * <ul>
 *   <li>dzień zapisany w katalogu cache – odczyt z pliku, bez wołania opakowanego providera
 *       (działa bez internetu),</li>
 *   <li>brak pliku – pogoda z opakowanego providera i zapis do pliku.</li>
 * </ul>
 * Zapisujemy tylko doby, które już się skończyły: prognoza na dziś i jutro jeszcze się zmienia.
 * Cache to tylko przyspieszenie – uszkodzony albo niezgodny plik oznacza ponowne pobranie,
 * a błąd zapisu tylko ostrzeżenie w logu.
 *
 * <p>Plik: {@code <cacheDir>/<szerokość>_<długość>/<data>.json}, np. {@code 50.0717_20.0377/2026-10-01.json}.
 */
public class CachingWeatherProvider implements WeatherProvider {

    private static final Logger log = LoggerFactory.getLogger(CachingWeatherProvider.class);
    private static final double COORDINATE_TOLERANCE = 1e-6;

    /** Zawartość pliku cache: dane razem z opisem, dla jakiej doby i miejsca zostały pobrane. */
    record CachedDay(double latitude, double longitude, ZoneId zone, LocalDate date, Duration step,
                     List<Weather> weather) {
    }

    private final WeatherProvider delegate;
    private final Path cacheDir;
    private final Clock clock;
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public CachingWeatherProvider(WeatherProvider delegate, Path cacheDir) {
        this(delegate, cacheDir, Clock.systemUTC());
    }

    /** @param clock zegar wyznaczający, czy doba już się skończyła (testy podają stały) */
    public CachingWeatherProvider(WeatherProvider delegate, Path cacheDir, Clock clock) {
        if (delegate == null || cacheDir == null || clock == null) {
            throw new IllegalArgumentException("Wymagany provider, katalog cache i zegar");
        }
        this.delegate = delegate;
        this.cacheDir = cacheDir;
        this.clock = clock;
    }

    @Override
    public List<Weather> weatherFor(Timeline timeline, Location location) {
        Path file = fileFor(timeline, location);
        Optional<List<Weather>> cached = read(file, timeline, location);
        if (cached.isPresent()) {
            log.debug("Pogoda {} z cache: {}", timeline.date(), file);
            return cached.get();
        }

        List<Weather> fresh = delegate.weatherFor(timeline, location);
        if (dayIsOver(timeline)) {
            write(file, new CachedDay(location.latitude(), location.longitude(), timeline.zone(),
                    timeline.date(), timeline.step(), fresh));
        } else {
            log.debug("Nie zapisuję pogody {} do cache – doba jeszcze trwa albo jest w przyszłości", timeline.date());
        }
        return fresh;
    }

    /** Ścieżka pliku cache dla doby i miejsca. */
    Path fileFor(Timeline timeline, Location location) {
        String place = String.format(Locale.ROOT, "%.4f_%.4f", location.latitude(), location.longitude());
        return cacheDir.resolve(place).resolve(timeline.date() + ".json");
    }

    private boolean dayIsOver(Timeline timeline) {
        return !timeline.end().isAfter(clock.instant());
    }

    private Optional<List<Weather>> read(Path file, Timeline timeline, Location location) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            CachedDay day = mapper.readValue(file.toFile(), CachedDay.class);
            if (!matches(day, timeline, location)) {
                log.warn("Plik cache {} nie pasuje do zapytania (miejsce, strefa, krok albo czasy) – pobieram ponownie", file);
                return Optional.empty();
            }
            return Optional.of(List.copyOf(day.weather()));
        } catch (IOException | RuntimeException e) {
            log.warn("Nie da się odczytać pliku cache {} – pobieram ponownie: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean matches(CachedDay day, Timeline timeline, Location location) {
        return day.weather() != null
                && Math.abs(day.latitude() - location.latitude()) < COORDINATE_TOLERANCE
                && Math.abs(day.longitude() - location.longitude()) < COORDINATE_TOLERANCE
                && timeline.zone().equals(day.zone())
                && timeline.date().equals(day.date())
                && timeline.step().equals(day.step())
                && day.weather().stream().map(Weather::time).toList().equals(timeline.steps());
    }

    /** Zapis przez plik tymczasowy i przeniesienie, żeby nikt nie odczytał pliku zapisanego do połowy. */
    private void write(Path file, CachedDay day) {
        Path temp = null;
        try {
            Files.createDirectories(file.getParent());
            // zwykły plik obok docelowego (Files.createTempFile nadałby uprawnienia tylko dla właściciela)
            temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
            mapper.writeValue(temp.toFile(), day);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Zapisano pogodę {} do cache: {}", day.date(), file);
        } catch (IOException e) {
            log.warn("Nie udało się zapisać pogody do cache {}: {}", file, e.getMessage());
            deleteQuietly(temp);
        }
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.debug("Nie usunięto pliku tymczasowego {}", temp);
        }
    }
}
