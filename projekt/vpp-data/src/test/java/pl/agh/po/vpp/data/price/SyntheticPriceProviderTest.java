package pl.agh.po.vpp.data.price;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.domain.Timeline;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticPriceProviderTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 21);

    private static List<Double> prices(long seed, LocalDate date) {
        return new SyntheticPriceProvider(seed).pricesFor(Timeline.of(date, WARSAW));
    }

    /** Średnia cena w godzinach lokalnych [fromHour, toHour). */
    private static double meanPrice(long seed, LocalDate date, int fromHour, int toHour) {
        Timeline timeline = Timeline.of(date, WARSAW);
        List<Double> prices = new SyntheticPriceProvider(seed).pricesFor(timeline);
        return IntStream.range(0, prices.size())
                .filter(i -> {
                    int hour = timeline.steps().get(i).atZone(WARSAW).getHour();
                    return hour >= fromHour && hour < toHour;
                })
                .mapToDouble(prices::get)
                .average()
                .orElseThrow();
    }

    private static double meanOverSeeds(LocalDate date) {
        return LongStream.rangeClosed(1, 20)
                .mapToDouble(seed -> meanPrice(seed, date, 0, 24))
                .average()
                .orElseThrow();
    }

    @ParameterizedTest(name = "{0}: {1} kroków")
    @CsvSource({"2026-11-05, 96", "2026-03-29, 92", "2026-10-25, 100"})
    void jednaCenaNaKazdyKrokOsiCzasu(LocalDate date, int steps) {
        assertThat(prices(42, date)).hasSize(steps);
    }

    @Test
    void tenSamSeedDajeTeSameCeny() {
        assertThat(prices(42, WEDNESDAY)).isEqualTo(prices(42, WEDNESDAY));
    }

    @Test
    void innySeedDajeInneCeny() {
        assertThat(prices(42, WEDNESDAY)).isNotEqualTo(prices(43, WEDNESDAY));
    }

    @Test
    void wieczorJestDrozszyNizNoc() {
        for (long seed = 1; seed <= 20; seed++) {
            double evening = meanPrice(seed, WEDNESDAY, 17, 21);
            double night = meanPrice(seed, WEDNESDAY, 1, 5);

            assertThat(evening).as("seed %d", seed).isGreaterThan(night * 2);
        }
    }

    @Test
    void wPoludnieJestTaniejNizWieczorem() {
        for (long seed = 1; seed <= 20; seed++) {
            double midday = meanPrice(seed, WEDNESDAY, 11, 15);
            double evening = meanPrice(seed, WEDNESDAY, 17, 21);

            assertThat(midday).as("seed %d", seed).isLessThan(evening);
        }
    }

    @Test
    void weekendJestSrednioTanszyNizDzienRoboczy() {
        assertThat(meanOverSeeds(SATURDAY)).isLessThan(meanOverSeeds(WEDNESDAY));
    }
}
