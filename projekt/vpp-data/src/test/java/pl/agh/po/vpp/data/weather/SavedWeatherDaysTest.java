package pl.agh.po.vpp.data.weather;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.data.EstateLoader;
import pl.agh.po.vpp.domain.EstateConfig;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;
import pl.agh.po.vpp.domain.WeatherProvider;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Dni prawdziwej pogody zapisane w repo (projekt/data/weather-cache) do pokazu bez internetu.
 * Provider pod cache'em zawsze zgłasza błąd, więc test przejdzie tylko wtedy, gdy dzień jest w pliku.
 */
class SavedWeatherDaysTest {

    private static final Path CACHE_DIR = Path.of("..", "data", "weather-cache");
    private static final EstateConfig ESTATE = new EstateLoader().load(Path.of("..", "data", "estate.json"));

    private static final WeatherProvider NO_INTERNET = (timeline, location) ->
            fail("Dzień " + timeline.date() + " nie jest zapisany w " + CACHE_DIR);

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "2025-06-21, słoneczne przesilenie letnie, 96",
            "2025-10-18, najbardziej wietrzny dzień jesieni 2025, 96",
            "2026-02-01, pochmurny i mroźny dzień zimy, 96",
            "2025-03-30, zmiana czasu na letni, 92",
            "2025-10-26, zmiana czasu na zimowy, 100",
            "2026-10-27, słoneczny dzień z danych co 15 minut, 96"})
    void zapisanyDzienWczytujeSieBezInternetu(LocalDate date, String description, int steps) {
        Timeline timeline = Timeline.of(date, ESTATE.location().zone());

        List<Weather> weather = new CachingWeatherProvider(NO_INTERNET, CACHE_DIR)
                .weatherFor(timeline, ESTATE.location());

        assertThat(weather).as(description).hasSize(steps);
        assertThat(weather).extracting(Weather::time).containsExactlyElementsOf(timeline.steps());
    }
}
