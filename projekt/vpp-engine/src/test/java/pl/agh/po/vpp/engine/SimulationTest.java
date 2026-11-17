package pl.agh.po.vpp.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.domain.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SimulationTest {
    private static final Location LOCATION = new Location(50, 20, ZoneId.of("Europe/Warsaw"));
    private static final WeatherProvider WEATHER = (t, l) -> t.steps().stream()
            .map(time -> new Weather(time, 500, 5, 20, 10)).toList();
    private static final PriceProvider PRICES = t -> Collections.nCopies(t.size(), -0.2);

    private Simulation simulation(WeatherProvider weather, PriceProvider prices, List<SimulationListener> listeners) {
        return new Simulation(new VirtualPowerPlant(List.of(), "grid", List.of()), LOCATION,
                OperatorSettings.defaults(), weather, prices, listeners);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-13,96", "2026-03-29,92", "2026-10-25,100"})
    void osCzasuDeterminizmISluchacze(LocalDate date, int count) {
        var timeline = Timeline.of(date, LOCATION.zone());
        var received = new ArrayList<StepResult>();
        var finished = new ArrayList<SimulationResult>();
        var listener = new SimulationListener() {
            public void onStep(StepResult step) { received.add(step); }
            public void onFinished(SimulationResult result) {
                assertThat(received).hasSize(count);
                finished.add(result);
            }
        };
        var simulation = simulation(WEATHER, PRICES, List.of(listener));
        var result = simulation.run(timeline);
        assertThat(result.steps()).hasSize(count).containsExactlyElementsOf(received);
        assertThat(finished).containsExactly(result);
        assertThat(result.steps()).extracting(StepResult::time).containsExactlyElementsOf(timeline.steps());
        assertThat(result.steps()).allSatisfy(s -> {
            assertThat(s.step()).isEqualTo(Duration.ofMinutes(15));
            assertThat(s.conditions().weather().time()).isEqualTo(s.time());
            assertThat(s.conditions().pricePlnPerKwh()).isEqualTo(-0.2);
        });
        assertThat(result.steps().getLast().time().plus(result.steps().getLast().step())).isEqualTo(timeline.end());
        assertThat(simulation(WEATHER, PRICES, List.of()).run(timeline)).isEqualTo(result);
    }

    @Test
    void godzinnyKrok() {
        var timeline = new Timeline(LocalDate.of(2026, 3, 29), Duration.ofHours(1), LOCATION.zone());
        var result = simulation(WEATHER, PRICES, List.of()).run(timeline);
        assertThat(result.steps()).hasSize(23).allSatisfy(s -> assertThat(s.stepHours()).isEqualTo(1));
    }

    @Test
    void zleDaneNieEmitujaZdarzen() {
        var timeline = Timeline.of(LocalDate.of(2026, 11, 13), LOCATION.zone());
        var listener = new SimulationListener() {
            public void onStep(StepResult step) { fail("Nie wolno emitować kroku"); }
            public void onFinished(SimulationResult result) { fail("Nie wolno emitować zakończenia"); }
        };
        List<WeatherProvider> invalidWeather = List.of(
                (t, l) -> null,
                (t, l) -> List.of(),
                (t, l) -> Collections.nCopies(t.size() + 1, WEATHER.weatherFor(t, l).getFirst()),
                (t, l) -> Collections.nCopies(t.size(), null),
                (t, l) -> Collections.nCopies(t.size(), WEATHER.weatherFor(t, l).getFirst()),
                (t, l) -> t.steps().stream().map(time -> new Weather(time, Double.NaN, 0, 20, 0)).toList());
        for (var weather : invalidWeather) {
            assertThatThrownBy(() -> simulation(weather, PRICES, List.of(listener)).run(timeline))
                    .isInstanceOf(IllegalStateException.class);
        }
        List<PriceProvider> invalidPrices = List.of(t -> null, t -> List.of(),
                t -> Collections.nCopies(t.size() + 1, 1.0), t -> Collections.nCopies(t.size(), null),
                t -> Collections.nCopies(t.size(), Double.NaN));
        for (var prices : invalidPrices) {
            assertThatThrownBy(() -> simulation(WEATHER, prices, List.of(listener)).run(timeline))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void blednaStrefaINieobslugiwaneUstawienia() {
        assertThatThrownBy(() -> simulation(WEATHER, PRICES, List.of()).run(
                Timeline.of(LocalDate.of(2026, 11, 13), ZoneId.of("UTC"))))
                .isInstanceOf(IllegalArgumentException.class);
        var settings = new OperatorSettings(null, null, List.of(), 0.0, 42);
        assertThatThrownBy(() -> new Simulation(new VirtualPowerPlant(List.of(), "g", List.of()),
                LOCATION, settings, WEATHER, PRICES, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
