package pl.agh.po.vpp.data.price;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import pl.agh.po.vpp.domain.Timeline;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Testy właściwościowe (jqwik): dla dowolnego seeda i dnia roku ceny są dodatnie i w rozsądnym zakresie. */
class SyntheticPriceProviderPropertiesTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Property(tries = 300)
    void cenyZawszeDodatnieIWRozsadnymZakresie(@ForAll long seed,
                                               @ForAll @IntRange(min = 0, max = 364) int dayOfYear) {
        Timeline timeline = Timeline.of(LocalDate.of(2026, 1, 1).plusDays(dayOfYear), WARSAW);

        List<Double> prices = new SyntheticPriceProvider(seed).pricesFor(timeline);

        assertThat(prices).hasSize(timeline.size());
        assertThat(prices).allSatisfy(price -> assertThat(price).isBetween(0.2, 2.0));
    }
}
