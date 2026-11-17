package pl.agh.po.vpp.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.agh.po.vpp.domain.*;
import pl.agh.po.vpp.engine.asset.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PowerBalanceTest {
    @ParameterizedTest
    @CsvSource({"0,2", "400,0", "1000,-3"})
    void importRownowagaEksport(double irradiance, double expectedGrid) {
        var t = Instant.parse("2026-11-13T12:00:00Z");
        var c = new Conditions(t, new Weather(t, irradiance, 0, 25, 0), 1);
        var pv = new SolarPanel("house", "", AssetType.HOUSE, 5);
        var load = new House("house", "", ZoneId.of("UTC"), pv, 1,
                new ResidentialProfile(2, 0, 0, 0, 16, 3));
        var plant = new VirtualPowerPlant(List.of(load), "grid", List.of());
        var step = plant.step(c, 0.25);
        assertThat(step.gridKw()).isEqualTo(expectedGrid);
        assertThat(step.productionKw()).isEqualTo(5 * irradiance / 1000);
        assertThat(step.flows()).hasSize(2);
        assertThat(step.flowOf("house").orElseThrow().consumptionKw()).isEqualTo(2);
        assertThat(step.batteryKw()).isZero();
        assertThat(step.batterySocKwh()).isZero();
        assertThat(step.step()).isEqualTo(Duration.ofMinutes(15));
        EnergyBalanceValidator.validate(step);
        assertThatThrownBy(() -> plant.step(c, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plant.step(c, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void walidatorWykrywaBledneAgregatyPrzeplywyINaN() {
        var t = Instant.EPOCH;
        var c = new Conditions(t, new Weather(t, 0, 0, 20, 0), 1);
        var flows = List.of(new AssetFlow("h", AssetType.HOUSE, 0, 2), new AssetFlow("g", AssetType.GRID, 2, 0));
        for (double consumption : new double[]{1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var bad = new StepResult(t, Duration.ofMinutes(15), c, flows, 0, consumption, 0, consumption, 0);
            assertThatThrownBy(() -> EnergyBalanceValidator.validate(bad)).isInstanceOf(IllegalStateException.class);
        }
        var duplicate = new StepResult(t, Duration.ofMinutes(15), c, List.of(flows.get(1), flows.get(1)), 0, 4, 0, 4, 0);
        assertThatThrownBy(() -> EnergyBalanceValidator.validate(duplicate)).isInstanceOf(IllegalStateException.class);
        var unbalanced = new StepResult(t, Duration.ofMinutes(15), c, flows, 0, 2, 0, 3, 0);
        assertThatThrownBy(() -> EnergyBalanceValidator.validate(unbalanced)).isInstanceOf(IllegalStateException.class);
    }
}
