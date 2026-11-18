package pl.agh.po.vpp.data.metrics;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import pl.agh.po.vpp.domain.Conditions;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Timeline;
import pl.agh.po.vpp.domain.Weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Testy właściwościowe (jqwik): dla losowych, zbilansowanych dób metryki muszą spełniać
 * bilans energii całej doby, a wskaźniki leżeć w zakresie 0–1.
 */
class MetricsCalculatorPropertiesTest {

    private static final Timeline TIMELINE = Timeline.of(LocalDate.of(2026, 11, 18), ZoneId.of("Europe/Warsaw"));

    /** Losowe moce jednego kroku [kW] i cena [PLN/kWh]. */
    record StepPowers(double productionKw, double consumptionKw, double batteryKw, double price) {
    }

    @Provide
    Arbitrary<List<StepPowers>> days() {
        Arbitrary<Double> power = Arbitraries.doubles().between(0, 200);
        Arbitrary<Double> battery = Arbitraries.doubles().between(-100, 100);
        Arbitrary<Double> price = Arbitraries.doubles().between(0.1, 2.0);
        return Combinators.combine(power, power, battery, price).as(StepPowers::new)
                .list().ofMaxSize(TIMELINE.size());
    }

    private static SimulationResult toResult(List<StepPowers> powers) {
        List<StepResult> steps = new ArrayList<>();
        for (int i = 0; i < powers.size(); i++) {
            StepPowers p = powers.get(i);
            Instant time = TIMELINE.steps().get(i);
            Conditions conditions = new Conditions(time, new Weather(time, 0, 0, 0, 0), p.price());
            double gridKw = p.consumptionKw() + p.batteryKw() - p.productionKw();
            steps.add(new StepResult(time, TIMELINE.step(), conditions, List.of(),
                    p.productionKw(), p.consumptionKw(), p.batteryKw(), gridKw, 0));
        }
        return new SimulationResult(TIMELINE, OperatorSettings.defaults(), steps);
    }

    @Property(tries = 300)
    void bilansEnergiiCalejDoby(@ForAll("days") List<StepPowers> powers,
                                @ForAll @DoubleRange(min = 0, max = 1) double exportPriceFactor) {
        DayMetrics m = new MetricsCalculator(exportPriceFactor).calculate(toResult(powers));

        // to, co weszło do osiedla = to, co z niego wyszło lub zostało w magazynie
        double in = m.productionKwh() + m.importKwh() + m.batteryDischargeKwh();
        double out = m.consumptionKwh() + m.exportKwh() + m.batteryChargeKwh();
        assertThat(in).isCloseTo(out, within(1e-6));
        assertThat(m.netCostPln()).isCloseTo(m.importCostPln() - m.exportRevenuePln(), within(1e-9));
    }

    @Property(tries = 300)
    void wskaznikiZawszeWZakresieAlboBrak(@ForAll("days") List<StepPowers> powers) {
        DayMetrics m = new MetricsCalculator().calculate(toResult(powers));

        if (m.selfSufficiency() != null) {
            assertThat(m.selfSufficiency()).isBetween(0.0, 1.0);
        }
        if (m.selfConsumption() != null) {
            assertThat(m.selfConsumption()).isBetween(0.0, 1.0);
        }
        assertThat(m.peakImportKw()).isGreaterThanOrEqualTo(0.0);
        assertThat(m.peakExportKw()).isGreaterThanOrEqualTo(0.0);
    }
}
