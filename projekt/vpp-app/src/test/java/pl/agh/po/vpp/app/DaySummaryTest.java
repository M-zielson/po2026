package pl.agh.po.vpp.app;

import org.junit.jupiter.api.Test;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DaySummaryTest {

    private static final Timeline TIMELINE = Timeline.of(LocalDate.of(2026, 11, 13), ZoneId.of("Europe/Warsaw"));

    private static StepResult step(Instant time, double productionKw, double consumptionKw) {
        Conditions conditions = new Conditions(time, new Weather(time, 0, 0, 0, 0), 0.5);
        double gridKw = consumptionKw - productionKw;
        return new StepResult(time, TIMELINE.step(), conditions, List.of(), productionKw, consumptionKw, 0, gridKw, 0);
    }

    @Test
    void sumujeEnergieZKrokowPo15Minut() {
        Instant t0 = TIMELINE.steps().get(0);
        Instant t1 = TIMELINE.steps().get(1);
        // krok 1: 20 kW importu przez 0,25 h = 5 kWh; krok 2: 40 kW eksportu przez 0,25 h = 10 kWh
        SimulationResult result = new SimulationResult(TIMELINE, OperatorSettings.defaults(),
                List.of(step(t0, 0, 20), step(t1, 60, 20)));

        DaySummary summary = DaySummary.of(result);

        assertThat(summary.productionKwh()).isCloseTo(15, within(1e-9));
        assertThat(summary.consumptionKwh()).isCloseTo(10, within(1e-9));
        assertThat(summary.importKwh()).isCloseTo(5, within(1e-9));
        assertThat(summary.exportKwh()).isCloseTo(10, within(1e-9));
    }

    @Test
    void formatZKropkaDziesietna() {
        assertThat(new DaySummary(412.34, 1830.6, 1418.26, 0).format())
                .contains("412.3", "1830.6", "1418.3", "0.0");
    }
}
