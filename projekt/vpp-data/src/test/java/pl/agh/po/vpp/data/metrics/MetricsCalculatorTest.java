package pl.agh.po.vpp.data.metrics;

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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MetricsCalculatorTest {

    private static final Timeline TIMELINE = Timeline.of(LocalDate.of(2026, 11, 18), ZoneId.of("Europe/Warsaw"));
    private static final double EPS = 1e-9;

    /** Krok 15 min; gridKw wynika z bilansu: production + grid = consumption + battery. */
    private static StepResult step(int index, double productionKw, double consumptionKw, double batteryKw, double price) {
        Instant time = TIMELINE.steps().get(index);
        Conditions conditions = new Conditions(time, new Weather(time, 0, 0, 0, 0), price);
        double gridKw = consumptionKw + batteryKw - productionKw;
        return new StepResult(time, TIMELINE.step(), conditions, List.of(),
                productionKw, consumptionKw, batteryKw, gridKw, 0);
    }

    private static SimulationResult day(StepResult... steps) {
        return new SimulationResult(TIMELINE, OperatorSettings.defaults(), List.of(steps));
    }

    /**
     * Mały scenariusz liczony ręcznie (krok 15 min = 0,25 h):
     * <pre>
     * krok | prod | zuż | bateria | sieć        | cena | energia [kWh]
     *  1   |   0  |  20 |    0    | +20 import  | 0.40 | zuż 5,    import 5    → koszt 2.00
     *  2   |  40  |  20 |  +10    | −10 eksport | 0.50 | prod 10, zuż 5, ład. 2.5, eksport 2.5 → przychód 1.25
     *  3   |  60  |  25 |    0    | −35 eksport | 0.60 | prod 15, zuż 6.25, eksport 8.75     → przychód 5.25
     *  4   |   0  |  40 |  −10    | +30 import  | 1.00 | zuż 10, rozład. 2.5, import 7.5    → koszt 7.50
     * </pre>
     * Suma: produkcja 25, zużycie 26.25, import 12.5, eksport 11.25, ładowanie 2.5, rozładowanie 2.5.
     * Samowystarczalność (26.25 − 12.5) / 26.25 = 0.5238…; autokonsumpcja (25 − 11.25) / 25 = 0.55.
     * Koszt importu 9.50, przychód z eksportu 6.50, koszt dnia 3.00. Szczyt importu 30 kW, eksportu 35 kW.
     */
    private static SimulationResult handCalculatedDay() {
        return day(
                step(0, 0, 20, 0, 0.40),
                step(1, 40, 20, 10, 0.50),
                step(2, 60, 25, 0, 0.60),
                step(3, 0, 40, -10, 1.00));
    }

    @Test
    void energieZgadzajaSieZRecznymiObliczeniami() {
        DayMetrics m = new MetricsCalculator().calculate(handCalculatedDay());

        assertThat(m.productionKwh()).isCloseTo(25, within(EPS));
        assertThat(m.consumptionKwh()).isCloseTo(26.25, within(EPS));
        assertThat(m.importKwh()).isCloseTo(12.5, within(EPS));
        assertThat(m.exportKwh()).isCloseTo(11.25, within(EPS));
        assertThat(m.batteryChargeKwh()).isCloseTo(2.5, within(EPS));
        assertThat(m.batteryDischargeKwh()).isCloseTo(2.5, within(EPS));
    }

    @Test
    void wskaznikiZgadzajaSieZRecznymiObliczeniami() {
        DayMetrics m = new MetricsCalculator().calculate(handCalculatedDay());

        assertThat(m.selfSufficiency()).isCloseTo(13.75 / 26.25, within(EPS));
        assertThat(m.selfConsumption()).isCloseTo(0.55, within(EPS));
    }

    @Test
    void kosztyZgadzajaSieZRecznymiObliczeniami() {
        DayMetrics m = new MetricsCalculator().calculate(handCalculatedDay());

        assertThat(m.importCostPln()).isCloseTo(9.50, within(EPS));
        assertThat(m.exportRevenuePln()).isCloseTo(6.50, within(EPS));
        assertThat(m.netCostPln()).isCloseTo(3.00, within(EPS));
    }

    @Test
    void mocSzczytowa() {
        DayMetrics m = new MetricsCalculator().calculate(handCalculatedDay());

        assertThat(m.peakImportKw()).isCloseTo(30, within(EPS));
        assertThat(m.peakExportKw()).isCloseTo(35, within(EPS));
    }

    @Test
    void wspolczynnikCenySprzedazySkalujeTylkoPrzychod() {
        DayMetrics half = new MetricsCalculator(0.5).calculate(handCalculatedDay());

        assertThat(half.importCostPln()).isCloseTo(9.50, within(EPS));
        assertThat(half.exportRevenuePln()).isCloseTo(3.25, within(EPS));
        assertThat(half.netCostPln()).isCloseTo(6.25, within(EPS));
    }

    @Test
    void bezImportuSamowystarczalnosc100Procent() {
        DayMetrics m = new MetricsCalculator().calculate(day(step(0, 50, 20, 0, 0.5)));

        assertThat(m.selfSufficiency()).isEqualTo(1.0);
    }

    @Test
    void bezProdukcjiAutokonsumpcjaNieMaWartosci() {
        DayMetrics m = new MetricsCalculator().calculate(day(step(0, 0, 20, 0, 0.5)));

        assertThat(m.selfConsumption()).isNull();
        assertThat(m.selfSufficiency()).isZero();
    }

    @Test
    void bezZuzyciaSamowystarczalnoscNieMaWartosci() {
        DayMetrics m = new MetricsCalculator().calculate(day(step(0, 30, 0, 0, 0.5)));

        assertThat(m.selfSufficiency()).isNull();
        assertThat(m.selfConsumption()).isZero();
    }

    @Test
    void ladowanieMagazynuZSieciNieDajeUjemnejSamowystarczalnosci() {
        // zużycie 10 kW, ładowanie 20 kW, import 30 kW > zużycie
        DayMetrics m = new MetricsCalculator().calculate(day(step(0, 0, 10, 20, 0.5)));

        assertThat(m.selfSufficiency()).isZero();
    }

    @Test
    void pustaDobaDajeZeraIBrakWskaznikow() {
        DayMetrics m = new MetricsCalculator().calculate(day());

        assertThat(m.productionKwh()).isZero();
        assertThat(m.netCostPln()).isZero();
        assertThat(m.selfSufficiency()).isNull();
        assertThat(m.selfConsumption()).isNull();
    }

    @Test
    void ujemnyWspolczynnikToBlad() {
        assertThatThrownBy(() -> new MetricsCalculator(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metrykiPozaZakresemToBlad() {
        assertThatThrownBy(() -> new DayMetrics(0, 0, 0, 0, 0, 0, 1.2, null, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selfSufficiency");
        assertThatThrownBy(() -> new DayMetrics(-1, 0, 0, 0, 0, 0, null, null, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productionKwh");
    }

    @Test
    void dlugiDzienZKrokamiCaleDoby() {
        List<StepResult> steps = new ArrayList<>();
        for (int i = 0; i < TIMELINE.size(); i++) {
            steps.add(step(i, 0, 4, 0, 1.0));
        }

        DayMetrics m = new MetricsCalculator().calculate(
                new SimulationResult(TIMELINE, OperatorSettings.defaults(), steps));

        // 4 kW przez 24 h = 96 kWh, po 1 PLN/kWh
        assertThat(m.consumptionKwh()).isCloseTo(96, within(EPS));
        assertThat(m.importCostPln()).isCloseTo(96, within(EPS));
    }
}
