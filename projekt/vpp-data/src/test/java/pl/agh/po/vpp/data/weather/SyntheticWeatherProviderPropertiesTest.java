package pl.agh.po.vpp.data.weather;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy właściwościowe (jqwik): zamiast kilku wybranych przykładów jqwik losuje setki
 * kombinacji seeda i dnia roku i sprawdza, że pogoda zawsze jest fizycznie sensowna.
 */
class SyntheticWeatherProviderPropertiesTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Location KRAKOW = new Location(50.0717, 20.0377, WARSAW);

    @Property(tries = 300)
    void pogodaZawszeWZakresachFizycznych(@ForAll long seed,
                                          @ForAll @IntRange(min = 0, max = 364) int dayOfYear) {
        Timeline timeline = Timeline.of(LocalDate.of(2026, 1, 1).plusDays(dayOfYear), WARSAW);

        List<Weather> result = new SyntheticWeatherProvider(seed).weatherFor(timeline, KRAKOW);

        assertThat(result).hasSize(timeline.size());
        assertThat(result).allSatisfy(w -> {
            assertThat(w.tiltedIrradianceWm2()).isBetween(0.0, 1200.0);
            assertThat(w.windSpeed80mMs()).isBetween(0.0, 30.0);
            assertThat(w.cloudCoverPct()).isBetween(0.0, 100.0);
            assertThat(w.temperatureC()).isBetween(-35.0, 40.0);
        });
    }
}
