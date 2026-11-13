package pl.agh.po.vpp.data.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticWeatherProviderTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Location KRAKOW = new Location(50.0717, 20.0377, WARSAW);
    private static final LocalDate SUMMER = LocalDate.of(2026, 6, 21);

    private static List<Weather> weather(long seed, LocalDate date) {
        return new SyntheticWeatherProvider(seed).weatherFor(Timeline.of(date, WARSAW), KRAKOW);
    }

    @ParameterizedTest(name = "{0}: {1} kroków")
    @CsvSource({"2026-11-05, 96", "2026-03-29, 92", "2026-10-25, 100"})
    void jedenWpisNaKazdyKrokOsiCzasu(LocalDate date, int steps) {
        Timeline timeline = Timeline.of(date, WARSAW);

        List<Weather> result = new SyntheticWeatherProvider(42).weatherFor(timeline, KRAKOW);

        assertThat(result).hasSize(steps);
        assertThat(result).extracting(Weather::time).containsExactlyElementsOf(timeline.steps());
    }

    @Test
    void tenSamSeedDajeTaSamaPogode() {
        assertThat(weather(42, SUMMER)).isEqualTo(weather(42, SUMMER));
    }

    @Test
    void innySeedDajeInnaPogode() {
        assertThat(weather(42, SUMMER)).isNotEqualTo(weather(43, SUMMER));
    }

    @Test
    void kolejneDniMajaInnaPogode() {
        assertThat(weather(42, SUMMER)).isNotEqualTo(weather(42, SUMMER.plusDays(1)));
    }

    @Test
    void wNocyNieMaSlonca() {
        List<Weather> summerDay = weather(42, SUMMER);

        assertThat(summerDay)
                .filteredOn(w -> {
                    int hour = w.time().atZone(WARSAW).getHour();
                    return hour < 3 || hour >= 22;
                })
                .isNotEmpty()
                .allSatisfy(w -> assertThat(w.tiltedIrradianceWm2()).isZero());
    }

    @Test
    void wSloneczneLatoWPoludnieJestOk1000WNaMetr() {
        Instant summerSolarNoon = Instant.parse("2026-06-21T10:40:00Z");
        Instant winterSolarNoon = Instant.parse("2026-12-21T10:40:00Z");

        double summer = SyntheticWeatherProvider.clearSkyTiltedIrradianceWm2(summerSolarNoon, KRAKOW);
        double winter = SyntheticWeatherProvider.clearSkyTiltedIrradianceWm2(winterSolarNoon, KRAKOW);

        assertThat(summer).isBetween(850.0, 1150.0);
        assertThat(winter).isBetween(300.0, 700.0);
        assertThat(summer).isGreaterThan(winter);
    }

    @Test
    void chmuryTylkoOslabiajaSlonce() {
        Timeline timeline = Timeline.of(SUMMER, WARSAW);

        assertThat(weather(7, SUMMER)).allSatisfy(w -> {
            Instant midStep = w.time().plus(timeline.step().dividedBy(2));
            double clearSky = SyntheticWeatherProvider.clearSkyTiltedIrradianceWm2(midStep, KRAKOW);
            assertThat(w.tiltedIrradianceWm2()).isLessThanOrEqualTo(clearSky);
        });
    }

    @Test
    void wspolczynnikChmur() {
        assertThat(SyntheticWeatherProvider.cloudFactor(0)).isEqualTo(1.0);
        assertThat(SyntheticWeatherProvider.cloudFactor(100)).isEqualTo(0.25);
    }

    @Test
    void latemJestCieplejNizZima() {
        for (long seed = 1; seed <= 20; seed++) {
            double summerMean = weather(seed, LocalDate.of(2026, 7, 15)).stream()
                    .mapToDouble(Weather::temperatureC).average().orElseThrow();
            double winterMean = weather(seed, LocalDate.of(2026, 1, 15)).stream()
                    .mapToDouble(Weather::temperatureC).average().orElseThrow();

            assertThat(summerMean).as("seed %d", seed).isGreaterThan(winterMean + 10);
        }
    }
}
