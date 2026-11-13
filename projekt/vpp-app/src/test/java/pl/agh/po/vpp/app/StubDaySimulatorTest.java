package pl.agh.po.vpp.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.data.price.SyntheticPriceProvider;
import pl.agh.po.vpp.data.weather.SyntheticWeatherProvider;
import pl.agh.po.vpp.domain.AssetConfig;
import pl.agh.po.vpp.domain.AssetFlow;
import pl.agh.po.vpp.domain.AssetType;
import pl.agh.po.vpp.domain.EstateConfig;
import pl.agh.po.vpp.domain.Location;
import pl.agh.po.vpp.domain.OperatorSettings;
import pl.agh.po.vpp.domain.SimulationListener;
import pl.agh.po.vpp.domain.SimulationResult;
import pl.agh.po.vpp.domain.StepResult;
import pl.agh.po.vpp.domain.Timeline;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StubDaySimulatorTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    /** Piątek: szkoła ma lekcje. */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);

    /** Małe osiedle: blok (40 mieszkań, 30 kW PV), szkoła, wiatrak, magazyn, przyłącze. */
    private static final EstateConfig ESTATE = new EstateConfig("Testowe",
            new Location(50.07, 20.04, WARSAW),
            List.of(
                    new AssetConfig("block", AssetType.APARTMENT_BLOCK, "Blok", 30, 0, 0, 40, null),
                    new AssetConfig("school", AssetType.SCHOOL, "Szkoła", 20, 0, 0, 0, null),
                    new AssetConfig("wind", AssetType.WIND_TURBINE, "Wiatrak", 0, 50, 0, 0, null),
                    new AssetConfig("battery", AssetType.BATTERY, "Magazyn", 0, 100, 200, 0, null),
                    new AssetConfig("grid", AssetType.GRID, "Sieć", 0, 250, 0, 0, null)));

    private static SimulationResult simulate(LocalDate date, List<SimulationListener> listeners) {
        return new StubDaySimulator().simulate(ESTATE, Timeline.of(date, WARSAW), OperatorSettings.defaults(),
                new SyntheticWeatherProvider(42), new SyntheticPriceProvider(42), listeners);
    }

    @ParameterizedTest(name = "{0}: {1} kroków")
    @CsvSource({"2026-11-13, 96", "2026-03-29, 92", "2026-10-25, 100"})
    void jedenWynikNaKazdyKrok(LocalDate date, int steps) {
        assertThat(simulate(date, List.of()).steps()).hasSize(steps);
    }

    @Test
    void bilansEnergiiZgadzaSieWKazdymKroku() {
        assertThat(simulate(FRIDAY, List.of()).steps()).allSatisfy(step ->
                assertThat(step.balanceErrorKw()).isCloseTo(0, within(1e-9)));
    }

    @Test
    void sumaProdukcjiElementowRownaSumieZuzycia() {
        assertThat(simulate(FRIDAY, List.of()).steps()).allSatisfy(step -> {
            double produced = step.flows().stream().mapToDouble(AssetFlow::productionKw).sum();
            double consumed = step.flows().stream().mapToDouble(AssetFlow::consumptionKw).sum();
            assertThat(produced).isCloseTo(consumed, within(1e-9));
        });
    }

    @Test
    void kazdyElementMaSwojaMocWKazdymKroku() {
        assertThat(simulate(FRIDAY, List.of()).steps()).allSatisfy(step ->
                assertThat(step.flows()).extracting(AssetFlow::assetId)
                        .containsExactlyInAnyOrder("block", "school", "wind", "battery", "grid"));
    }

    @Test
    void wNocyNieMaProdukcjiICaleZuzycieToImport() {
        StepResult midnight = simulate(FRIDAY, List.of()).steps().getFirst();

        assertThat(midnight.productionKw()).isZero();
        assertThat(midnight.consumptionKw()).isEqualTo(40 * StubDaySimulator.LOAD_PER_DWELLING_KW);
        assertThat(midnight.importKw()).isEqualTo(midnight.consumptionKw());
    }

    @Test
    void szkolaZuzywaPradTylkoWGodzinachLekcji() {
        SimulationResult result = simulate(FRIDAY, List.of());
        double blockLoad = 40 * StubDaySimulator.LOAD_PER_DWELLING_KW;

        // 10:00 to krok nr 40 (10 godzin × 4 kroki), 20:00 to krok nr 80
        assertThat(result.steps().get(40).consumptionKw()).isEqualTo(blockLoad + StubDaySimulator.SCHOOL_LOAD_KW);
        assertThat(result.steps().get(80).consumptionKw()).isEqualTo(blockLoad);
    }

    @Test
    void sluchaczeDostajaKazdyKrokIKoniec() {
        int[] steps = {0};
        int[] finished = {0};
        SimulationListener counter = new SimulationListener() {
            @Override
            public void onStep(StepResult step) {
                steps[0]++;
            }

            @Override
            public void onFinished(SimulationResult result) {
                finished[0]++;
            }
        };

        simulate(FRIDAY, List.of(counter));

        assertThat(steps[0]).isEqualTo(96);
        assertThat(finished[0]).isEqualTo(1);
    }
}
